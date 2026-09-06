package com.praval.f1calendar.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.praval.f1calendar.MainActivity
import com.praval.f1calendar.R
import com.praval.f1calendar.core.Res
import com.praval.f1calendar.core.dataOrNull
import com.praval.f1calendar.core.map
import com.praval.f1calendar.data.live.LiveRepository
import com.praval.f1calendar.data.repository.RaceRepository
import com.praval.f1calendar.domain.model.RaceResult
import com.praval.f1calendar.domain.model.SessionType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Checks whether a just-finished session's results are published yet and, if so, posts a
 * notification summarising them.
 *
 * Two sources are tried, in the order they become true. Jolpica is the app's own record but only
 * publishes a classification hours after the flag, so OpenF1's live timing — which has the same
 * order within seconds, and covers practice and sprint qualifying, which Jolpica never publishes at
 * all — is what actually carries the notification on the day. Neither having anything yet means the
 * session simply isn't classified, so the work retries with backoff up to [MAX_ATTEMPTS].
 */
@HiltWorker
class SessionResultWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val raceRepository: RaceRepository,
    private val liveRepository: LiveRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val season = inputData.getInt(KEY_SEASON, 0)
        val round = inputData.getInt(KEY_ROUND, 0)
        val type = SessionType.fromName(inputData.getString(KEY_SESSION).orEmpty())
            ?: return Result.failure()
        val raceName = inputData.getString(KEY_RACE_NAME) ?: return Result.failure()

        val body = officialSummary(season, round, type, raceName)
            ?: liveSummary(season, round, type, raceName)
            ?: return giveUpOrRetry()

        postNotification(season, round, type, body)
        return Result.success()
    }

    /** Jolpica's own classification, for the session types it carries. Null until it publishes. */
    private suspend fun officialSummary(
        season: Int,
        round: Int,
        type: SessionType,
        raceName: String,
    ): String? = when (type) {
        SessionType.RACE -> {
            val outcome = raceRepository.refreshResults(season, round, force = true).map {
                raceRepository.observeResults(season, round).first()
            }
            podiumText(raceName, type, outcome.dataOrNull().orEmpty())
        }

        SessionType.SPRINT ->
            podiumText(raceName, type, raceRepository.fetchSprintResults(season, round).dataOrNull().orEmpty())

        SessionType.QUALIFYING -> {
            val refresh = raceRepository.refreshQualifying(season, round, force = true)
            val pole = if (refresh is Res.Error) {
                null
            } else {
                raceRepository.observeQualifying(season, round).first().firstOrNull()
            }
            pole?.let { "${it.driver.fullName} takes pole for $raceName." }
        }

        // Jolpica has never published a practice or sprint-qualifying classification.
        else -> null
    }

    /** OpenF1's live timing, which is classified within seconds of the flag for every session. */
    private suspend fun liveSummary(
        season: Int,
        round: Int,
        type: SessionType,
        raceName: String,
    ): String? {
        val startsAt = raceRepository.observeRace(season, round).first()
            ?.session(type)?.startsAt
            ?: return null
        val rows = liveRepository.classification(season, type, startsAt).dataOrNull().orEmpty()
        val leader = rows.firstOrNull() ?: return null

        return when (type) {
            SessionType.RACE, SessionType.SPRINT -> podiumText(raceName, type, rows)
            SessionType.QUALIFYING -> "${leader.driver.fullName} takes pole for $raceName."
            SessionType.SPRINT_QUALIFYING ->
                "${leader.driver.fullName} takes sprint pole for $raceName."
            // A practice session has no prize, so the headline is simply who ended up on top.
            SessionType.FP1, SessionType.FP2, SessionType.FP3 -> buildString {
                append("${leader.driver.fullName} fastest in ${type.label} at $raceName")
                leader.time?.let { append(" ($it)") }
                append(".")
            }
        }
    }

    /** "X wins, Y and Z complete the podium" — or just the winner when fewer than three finish. */
    private fun podiumText(raceName: String, type: SessionType, results: List<RaceResult>): String? {
        val podium = results.filter { it.isClassified }.take(3)
        val winner = podium.firstOrNull() ?: return null
        val label = if (type == SessionType.SPRINT) "Sprint" else "Race"
        return if (podium.size >= 3) {
            "$raceName: ${winner.driver.fullName} wins! ${podium[1].driver.shortName} P2, " +
                "${podium[2].driver.shortName} P3."
        } else {
            "${winner.driver.fullName} wins the $label at $raceName."
        }
    }

    private fun giveUpOrRetry(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    private fun postNotification(season: Int, round: Int, type: SessionType, body: String) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            NotificationIds.resultRequestCode(season, round, CONTENT_INTENT_SLOT),
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_SEASON, season)
                putExtra(MainActivity.EXTRA_OPEN_ROUND, round)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationIds.CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_stat_flag)
            .setContentTitle("${type.label} results")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            manager.notify(NotificationIds.resultRequestCode(season, round, NOTIFICATION_SLOT), notification)
        }
    }

    companion object {
        const val KEY_SEASON = "key_season"
        const val KEY_ROUND = "key_round"
        const val KEY_SESSION = "key_session"
        const val KEY_RACE_NAME = "key_race_name"

        private const val MAX_ATTEMPTS = 6
        private const val CONTENT_INTENT_SLOT = 92
        private const val NOTIFICATION_SLOT = 93
    }
}
