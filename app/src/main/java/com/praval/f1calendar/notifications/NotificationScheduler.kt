package com.praval.f1calendar.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.praval.f1calendar.data.local.dao.ReminderDao
import com.praval.f1calendar.data.local.entity.ReminderEntity
import com.praval.f1calendar.data.prefs.SettingsStore
import com.praval.f1calendar.data.repository.RaceRepository
import com.praval.f1calendar.domain.model.Race
import com.praval.f1calendar.domain.model.SessionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the alarm side of session reminders.
 *
 * The database rows in `reminders` are the source of truth for *what* the user wants to be told
 * about; the alarms themselves are derived state that gets rebuilt whenever the schedule changes,
 * the lead time changes, the device reboots, or the timezone changes.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val raceRepository: RaceRepository,
    private val reminderDao: ReminderDao,
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

    fun observeReminders(season: Int, round: Int): Flow<Set<SessionType>> =
        reminderDao.observeForRace(season, round).map { rows ->
            rows.mapNotNull { SessionType.fromName(it.session) }.toSet()
        }

    fun observeReminderCount(): Flow<Int> = reminderDao.observeAll().map { it.size }

    suspend fun isReminderSet(season: Int, round: Int, type: SessionType): Boolean =
        reminderDao.getForRace(season, round).any { it.session == type.name }

    /** Cancels every pending alarm before dropping the rows, so nothing is left orphaned. */
    suspend fun clearAllReminders() {
        reminderDao.getAll().forEach { row ->
            SessionType.fromName(row.session)?.let { cancelOne(row.season, row.round, it) }
        }
        reminderDao.deleteAll()
    }

    suspend fun setReminder(race: Race, type: SessionType, enabled: Boolean) {
        val row = ReminderEntity(race.season, race.round, type.name)
        if (enabled) {
            reminderDao.upsert(row)
            if (settings.remindersEnabledNow()) {
                scheduleOne(race, type, settings.leadMinutesNow())
            }
        } else {
            reminderDao.delete(row)
            cancelOne(race.season, race.round, type)
        }
    }

    /** Toggles every session of a weekend at once — what the bell in the race detail bar does. */
    suspend fun setAllForRace(race: Race, enabled: Boolean) {
        if (enabled) {
            val remindable = race.sessions.filter { it.startsAt != null }.map { it.type }
            reminderDao.upsertAll(remindable.map { ReminderEntity(race.season, race.round, it.name) })
            if (settings.remindersEnabledNow()) {
                val lead = settings.leadMinutesNow()
                remindable.forEach { scheduleOne(race, it, lead) }
            }
        } else {
            reminderDao.deleteForRace(race.season, race.round)
            SessionType.entries.forEach { cancelOne(race.season, race.round, it) }
        }
    }

    /**
     * Rebuilds every pending alarm from the reminder rows. Safe to call repeatedly: scheduling an
     * alarm with the same PendingIntent replaces the previous one rather than stacking.
     */
    suspend fun rescheduleAll() {
        val remindersOn = settings.remindersEnabledNow()
        val lead = settings.leadMinutesNow()
        val rows = reminderDao.getAll()

        rows.groupBy { it.season }.forEach { (season, seasonRows) ->
            val races = raceRepository.racesForSeason(season).associateBy { it.round }
            seasonRows.forEach { row ->
                val type = SessionType.fromName(row.session) ?: return@forEach
                val race = races[row.round]
                if (race == null || !remindersOn) {
                    cancelOne(row.season, row.round, type)
                } else {
                    scheduleOne(race, type, lead)
                }
            }
        }
    }

    private fun scheduleOne(race: Race, type: SessionType, leadMinutes: Int) {
        val start = race.session(type)?.startsAt
        if (start == null) {
            // Sessions with no published time can't be reminded about.
            cancelOne(race.season, race.round, type)
            return
        }
        val triggerAt = start.toEpochMilli() - leadMinutes * 60_000L
        if (triggerAt <= System.currentTimeMillis()) {
            cancelOne(race.season, race.round, type)
            return
        }

        val pendingIntent = buildPendingIntent(
            season = race.season,
            round = race.round,
            type = type,
            raceName = race.name,
            startMillis = start.toEpochMilli(),
            flags = PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission can be revoked between the check above and the call.
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }
    }

    private fun cancelOne(season: Int, round: Int, type: SessionType) {
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

    /** Debug/diagnostic helper: the next reminder that will fire, if any. */
    suspend fun nextReminderAt(): Instant? {
        val lead = settings.leadMinutesNow()
        val now = System.currentTimeMillis()
        return reminderDao.getAll()
            .groupBy { it.season }
            .flatMap { (season, rows) ->
                val races = raceRepository.racesForSeason(season).associateBy { it.round }
                rows.mapNotNull { row ->
                    val type = SessionType.fromName(row.session) ?: return@mapNotNull null
                    races[row.round]?.session(type)?.startsAt?.toEpochMilli()
                        ?.minus(lead * 60_000L)
                        ?.takeIf { it > now }
                }
            }
            .minOrNull()
            ?.let(Instant::ofEpochMilli)
    }

    private companion object {
        const val TAG = "NotificationScheduler"
    }
}
