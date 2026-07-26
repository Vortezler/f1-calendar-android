package com.praval.f1calendar.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.praval.f1calendar.core.Res
import com.praval.f1calendar.data.repository.RaceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Keeps the cached calendar and the pending alarms in step.
 *
 * Session times move — races get rescheduled, and the API publishes provisional times months out —
 * so the alarms are rebuilt from a fresh calendar once a day rather than set once and forgotten.
 */
@HiltWorker
class ScheduleSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val raceRepository: RaceRepository,
    private val scheduler: NotificationScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val refresh = raceRepository.refreshCurrentSeason(force = true)
        if (refresh is Res.Error) {
            // Alarms already on the books stay valid; just try again later.
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
        scheduler.rescheduleAll()
        return Result.success()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val PERIODIC_NAME = "schedule-sync-periodic"
        private const val ONE_SHOT_NAME = "schedule-sync-now"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Used after a reboot or timezone change, where alarms are dropped by the system. */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScheduleSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
