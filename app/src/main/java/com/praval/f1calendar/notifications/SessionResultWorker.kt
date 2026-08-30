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
import com.praval.f1calendar.core.map
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
 * Race, qualifying and sprint all have a real classification to wait for, so an empty result is
 * treated as "not published yet" and retried (WorkManager's backoff, capped at [MAX_ATTEMPTS]).
 * Practice sessions and sprint qualifying have no classification endpoint at all — Jolpica never
 * published one — so those just announce that the session is over.
 */
@HiltWorker
class SessionResultWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val raceRepository: RaceRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val season = inputData.getInt(KEY_SEASON, 0)
        val round = inputData.getInt(KEY_ROUND, 0)
        val type = SessionType.fromName(inputData.getString(KEY_SESSION).orEmpty()) ?: return Result.failure()
        val raceName = inputData.getString(KEY_RACE_NAME) ?: return Result.failure()

        val notificationText = when (type) {
            SessionType.RACE -> classifiedText(raceName, "Race") {
                raceRepository.refreshResults(season, round, force = true).map {
                    raceRepository.observeResults(season, round).first()
                }
            } ?: return giveUpOrRetry()

            SessionType.QUALIFYING -> {
                val outcome = raceRepository.refreshQualifying(season, round, force = true)
                if (outcome is Res.Error) return giveUpOrRetry()
                val pole = raceRepository.observeQualifying(season, round).first().firstOrNull()
                    ?: return giveUpOrRetry()
                "${pole.driver.fullName} takes pole for $raceName."
            }

            SessionType.SPRINT -> classifiedText(raceName, "Sprint") {
                raceRepository.fetchSprintResults(season, round)
            } ?: return giveUpOrRetry()

            SessionType.FP1, SessionType.FP2, SessionType.FP3, SessionType.SPRINT_QUALIFYING ->
                "$raceName — ${type.label} has finished."
        }

        postNotification(season, round, type, notificationText)
        return Result.success()
    }

    /** Runs [fetch], and returns its summary sentence only once real classified rows come back. */
    private suspend fun classifiedText(
        raceName: String,
        label: String,
        fetch: suspend () -> Res<List<RaceResult>>,
    ): String? {
        val results = when (val outcome = fetch()) {
            is Res.Error -> return null
            is Res.Success -> outcome.data
            Res.Loading -> return null
        }
        val winner = results.firstOrNull { it.isClassified } ?: return null
        val podium = results.filter { it.isClassified }.take(3)
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
