package com.praval.f1calendar.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.praval.f1calendar.data.local.dao.ResultRuleDao
import com.praval.f1calendar.data.local.entity.ResultRuleEntity
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.data.repository.RaceRepository
import com.praval.f1calendar.domain.model.DefaultResultRules
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.ResultNotificationRule
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
 * Arms a one-shot alarm per session that fires once the session is estimated to be over, so
 * [SessionResultReceiver] can hand off to [SessionResultWorker] and post whatever classification
 * is available. Mirrors [NotificationScheduler]'s shape, but there is no lead time and no
 * per-weekend override — just "on for this session type" or not.
 */
@Singleton
class SessionResultScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val raceRepository: RaceRepository,
    private val ruleDao: ResultRuleDao,
    private val settings: SettingsStore,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun observeRules(): Flow<Map<SessionType, ResultNotificationRule>> =
        ruleDao.observeAll().map { rows -> mergeWithDefaults(rows) }

    suspend fun setRuleEnabled(type: SessionType, enabled: Boolean) {
        ruleDao.upsert(ResultRuleEntity(type.name, enabled))
        rescheduleAll()
    }

    suspend fun resetToDefaults() {
        ruleDao.clear()
        rescheduleAll()
    }

    private fun mergeWithDefaults(rows: List<ResultRuleEntity>): Map<SessionType, ResultNotificationRule> {
        val stored = rows.mapNotNull { row ->
            SessionType.fromName(row.session)?.let { it to ResultNotificationRule(it, row.enabled) }
        }.toMap()
        return SessionType.entries.associateWith { stored[it] ?: DefaultResultRules.forType(it) }
    }

    /** Rebuilds every pending result alarm for the current season, same horizon as the alarm scheduler. */
    suspend fun rescheduleAll() {
        val rules = mergeWithDefaults(ruleDao.getAll())
        val season = settings.resolvedCurrentSeasonNow().takeIf { it != 0 } ?: Year.now().value
        val races = raceRepository.racesForSeason(season)

        val now = Instant.now()
        val horizon = now.plus(Duration.ofDays(HORIZON_DAYS))

        races.forEach { race ->
            race.sessions.forEach { session ->
                val type = session.type
                val rule = rules.getValue(type)
                val triggerAt = estimatedFinish(race, type, session.startsAt)
                val shouldArm = rule.enabled &&
                    triggerAt != null &&
                    triggerAt.isAfter(now) &&
                    triggerAt.isBefore(horizon)

                if (shouldArm) {
                    arm(race, type, triggerAt.toEpochMilli())
                } else {
                    cancel(race.season, race.round, type)
                }
            }
        }
    }

    /**
     * When results are actually expected to be checkable: the grand prix uses [Race.endsAt]
     * directly (it already accounts for red flags via a flat cap), everything else adds a rough
     * session duration to its start, plus a fixed buffer for the API to catch up.
     */
    private fun estimatedFinish(race: Race, type: SessionType, start: Instant?): Instant? {
        val end = if (type == SessionType.RACE) race.endsAt else start?.plus(sessionDuration(type))
        return end?.plus(RESULT_BUFFER)
    }

    private fun sessionDuration(type: SessionType): Duration = when (type) {
        SessionType.FP1, SessionType.FP2, SessionType.FP3 -> Duration.ofHours(1)
        SessionType.QUALIFYING -> Duration.ofHours(1)
        SessionType.SPRINT_QUALIFYING, SessionType.SPRINT -> Duration.ofMinutes(45)
        SessionType.RACE -> Duration.ofHours(3)
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun arm(race: Race, type: SessionType, triggerAtMillis: Long) {
        val pendingIntent = buildPendingIntent(
            season = race.season,
            round = race.round,
            type = type,
            raceName = race.name,
            flags = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
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
            flags = PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(
        season: Int,
        round: Int,
        type: SessionType,
        raceName: String?,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, SessionResultReceiver::class.java).apply {
            data = "f1calendar://result/$season/$round/${type.name}".toUri()
            putExtra(SessionResultReceiver.EXTRA_SEASON, season)
            putExtra(SessionResultReceiver.EXTRA_ROUND, round)
            putExtra(SessionResultReceiver.EXTRA_SESSION, type.name)
            putExtra(SessionResultReceiver.EXTRA_RACE_NAME, raceName)
        }
        return PendingIntent.getBroadcast(
            context,
            NotificationIds.resultRequestCode(season, round, type.ordinal),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "SessionResultScheduler"
        const val HORIZON_DAYS = 10L
        val RESULT_BUFFER: Duration = Duration.ofMinutes(15)
    }
}
