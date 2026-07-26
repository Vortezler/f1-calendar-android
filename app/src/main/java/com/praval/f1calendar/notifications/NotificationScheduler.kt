package com.praval.f1calendar.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.praval.f1calendar.data.local.dao.ReminderDao
import com.praval.f1calendar.data.local.dao.SessionRuleDao
import com.praval.f1calendar.data.local.entity.ReminderEntity
import com.praval.f1calendar.data.local.entity.SessionRuleEntity
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.data.repository.RaceRepository
import com.praval.f1calendar.domain.model.DefaultAlarmRules
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.SessionAlarmRule
import com.praval.f1calendar.domain.model.SessionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.Year
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns alarm *preferences* into actual pending alarms.
 *
 * Two layers decide whether a given session rings:
 *  1. a standing [SessionAlarmRule] per session type ("every qualifying, 30 minutes ahead"), and
 *  2. an optional per-session override for one specific weekend.
 *
 * Neither is an alarm by itself. [rescheduleAll] is the single place that reconciles those
 * preferences against the cached calendar and the clock, and it is idempotent — scheduling an
 * alarm with the same PendingIntent replaces the previous one rather than stacking.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val raceRepository: RaceRepository,
    private val reminderDao: ReminderDao,
    private val ruleDao: SessionRuleDao,
    private val settings: SettingsStore,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * True when the OS will honour an exact alarm. From Android 12 this is a user-grantable
     * permission that defaults to *denied* for apps that aren't alarm clocks, so reminders fall
     * back to inexact delivery (which can slip by several minutes) when it isn't held.
     */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    // region standing rules

    fun observeRules(): Flow<Map<SessionType, SessionAlarmRule>> =
        ruleDao.observeAll().map { rows -> mergeWithDefaults(rows) }

    suspend fun setRuleEnabled(type: SessionType, enabled: Boolean) {
        val current = currentRule(type)
        ruleDao.upsert(SessionRuleEntity(type.name, enabled, current.leadMinutes))
        rescheduleAll()
    }

    suspend fun setRuleLeadMinutes(type: SessionType, leadMinutes: Int) {
        val current = currentRule(type)
        ruleDao.upsert(SessionRuleEntity(type.name, current.enabled, leadMinutes))
        rescheduleAll()
    }

    suspend fun resetRulesToDefaults() {
        ruleDao.clear()
        reminderDao.deleteAll()
        rescheduleAll()
    }

    private suspend fun currentRule(type: SessionType): SessionAlarmRule =
        mergeWithDefaults(ruleDao.getAll()).getValue(type)

    private fun mergeWithDefaults(rows: List<SessionRuleEntity>): Map<SessionType, SessionAlarmRule> {
        val stored = rows.mapNotNull { row ->
            SessionType.fromName(row.session)?.let { it to SessionAlarmRule(it, row.enabled, row.leadMinutes) }
        }.toMap()
        return SessionType.entries.associateWith { stored[it] ?: DefaultAlarmRules.forType(it) }
    }

    // endregion

    // region per-weekend overrides

    /** Emits only the sessions this weekend that deviate from their type's standing rule. */
    fun observeOverrides(season: Int, round: Int): Flow<Map<SessionType, Boolean>> =
        reminderDao.observeForRace(season, round).map { rows ->
            rows.mapNotNull { row ->
                SessionType.fromName(row.session)?.let { it to row.enabled }
            }.toMap()
        }

    fun observeOverrideCount(): Flow<Int> = reminderDao.observeAll().map { it.size }

    suspend fun setOverride(race: Race, type: SessionType, enabled: Boolean) {
        val rule = currentRule(type)
        if (rule.enabled == enabled) {
            // Matching the standing rule again: drop the override instead of pinning it, so later
            // changes to the rule keep applying to this weekend.
            reminderDao.clearOverride(race.season, race.round, type.name)
        } else {
            reminderDao.upsert(ReminderEntity(race.season, race.round, type.name, enabled))
        }
        rescheduleAll()
    }

    suspend fun setAllForRace(race: Race, enabled: Boolean) {
        val rules = mergeWithDefaults(ruleDao.getAll())
        race.sessions.filter { it.startsAt != null }.forEach { session ->
            val rule = rules.getValue(session.type)
            if (rule.enabled == enabled) {
                reminderDao.clearOverride(race.season, race.round, session.type.name)
            } else {
                reminderDao.upsert(ReminderEntity(race.season, race.round, session.type.name, enabled))
            }
        }
        rescheduleAll()
    }

    suspend fun clearAllOverrides() {
        reminderDao.deleteAll()
        rescheduleAll()
    }

    // endregion

    /**
     * Rebuilds every pending alarm for the current season from the stored preferences.
     *
     * Only sessions inside [HORIZON_DAYS] are armed. A full season is ~24 weekends of up to five
     * sessions each, and holding 120 exact alarms for a year out is both wasteful and pointless —
     * the daily [ScheduleSyncWorker] rolls the window forward, and the API's provisional times for
     * distant rounds change anyway.
     */
    suspend fun rescheduleAll() {
        val remindersOn = settings.remindersEnabledNow()
        val rules = mergeWithDefaults(ruleDao.getAll())
        val overrides = reminderDao.getAll().associateBy { overrideKey(it.season, it.round, it.session) }

        val season = settings.resolvedCurrentSeasonNow().takeIf { it != 0 } ?: Year.now().value
        val races = raceRepository.racesForSeason(season)

        val now = Instant.now()
        val horizon = now.plus(Duration.ofDays(HORIZON_DAYS))

        races.forEach { race ->
            race.sessions.forEach { session ->
                val type = session.type
                val start = session.startsAt
                val rule = rules.getValue(type)
                val wanted = overrides[overrideKey(race.season, race.round, type.name)]?.enabled
                    ?: rule.enabled

                val triggerAt = start?.minusSeconds(rule.leadMinutes * 60L)
                val shouldArm = remindersOn &&
                    wanted &&
                    triggerAt != null &&
                    triggerAt.isAfter(now) &&
                    triggerAt.isBefore(horizon)

                if (shouldArm) {
                    arm(race, type, start.toEpochMilli(), triggerAt.toEpochMilli())
                } else {
                    cancel(race.season, race.round, type)
                }
            }
        }
    }

    /**
     * What the next alarm will be, for display in Settings. Recomputed from preferences rather
     * than read back from AlarmManager, which offers no way to enumerate pending alarms.
     */
    suspend fun nextAlarm(): Pair<Race, SessionType>? {
        if (!settings.remindersEnabledNow()) return null
        val rules = mergeWithDefaults(ruleDao.getAll())
        val overrides = reminderDao.getAll().associateBy { overrideKey(it.season, it.round, it.session) }
        val season = settings.resolvedCurrentSeasonNow().takeIf { it != 0 } ?: Year.now().value
        val now = Instant.now()

        return raceRepository.racesForSeason(season)
            .flatMap { race -> race.sessions.map { race to it } }
            .mapNotNull { (race, session) ->
                val rule = rules.getValue(session.type)
                val on = overrides[overrideKey(race.season, race.round, session.type.name)]?.enabled
                    ?: rule.enabled
                val trigger = session.startsAt?.minusSeconds(rule.leadMinutes * 60L)
                if (on && trigger != null && trigger.isAfter(now)) {
                    Triple(race, session.type, trigger)
                } else {
                    null
                }
            }
            .minByOrNull { it.third }
            ?.let { it.first to it.second }
    }

    private fun arm(race: Race, type: SessionType, startMillis: Long, triggerAtMillis: Long) {
        val pendingIntent = buildPendingIntent(
            season = race.season,
            round = race.round,
            type = type,
            raceName = race.name,
            startMillis = startMillis,
            flags = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission can be revoked between the check above and the call.
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    private fun cancel(season: Int, round: Int, type: SessionType) {
        val pendingIntent = buildPendingIntent(
            season = season,
            round = round,
            type = type,
            raceName = null,
            startMillis = 0L,
            flags = PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * The intent's `data` URI is what makes two sessions' alarms distinct — extras are explicitly
     * ignored by [Intent.filterEquals], which PendingIntent matching uses.
     */
    private fun buildPendingIntent(
        season: Int,
        round: Int,
        type: SessionType,
        raceName: String?,
        startMillis: Long,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            data = "f1calendar://session/$season/$round/${type.name}".toUri()
            putExtra(SessionAlarmReceiver.EXTRA_SEASON, season)
            putExtra(SessionAlarmReceiver.EXTRA_ROUND, round)
            putExtra(SessionAlarmReceiver.EXTRA_SESSION, type.name)
            putExtra(SessionAlarmReceiver.EXTRA_RACE_NAME, raceName)
            putExtra(SessionAlarmReceiver.EXTRA_START_MILLIS, startMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            NotificationIds.requestCode(season, round, type.ordinal),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun overrideKey(season: Int, round: Int, session: String) = "$season:$round:$session"

    private companion object {
        const val TAG = "NotificationScheduler"
        const val HORIZON_DAYS = 10L
    }
}
