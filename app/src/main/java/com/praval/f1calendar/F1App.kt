package com.praval.f1calendar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.praval.f1calendar.notifications.NotificationIds
import com.praval.f1calendar.notifications.ScheduleSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class F1App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // KEEP policy, so this is a no-op once the job already exists.
        ScheduleSyncWorker.enqueuePeriodic(this)
    }

    // minSdk is 26, so notification channels are always available — no version guard needed.
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val sessions = NotificationChannel(
            NotificationIds.CHANNEL_SESSIONS,
            getString(R.string.channel_sessions_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_sessions_description)
        }
        val results = NotificationChannel(
            NotificationIds.CHANNEL_RESULTS,
            getString(R.string.channel_results_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_results_description)
        }
        manager.createNotificationChannel(sessions)
        manager.createNotificationChannel(results)
    }
}
