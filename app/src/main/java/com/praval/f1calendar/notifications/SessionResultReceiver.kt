package com.praval.f1calendar.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Fires once a session is estimated to be over. Checking for results needs a suspend network
 * call, which a [BroadcastReceiver] can't make safely, so this only ever hands off to
 * [SessionResultWorker].
 */
class SessionResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val season = intent.getIntExtra(EXTRA_SEASON, 0)
        val round = intent.getIntExtra(EXTRA_ROUND, 0)
        val session = intent.getStringExtra(EXTRA_SESSION) ?: return
        val raceName = intent.getStringExtra(EXTRA_RACE_NAME) ?: return

        val request = OneTimeWorkRequestBuilder<SessionResultWorker>()
            .setInputData(
                Data.Builder()
                    .putInt(SessionResultWorker.KEY_SEASON, season)
                    .putInt(SessionResultWorker.KEY_ROUND, round)
                    .putString(SessionResultWorker.KEY_SESSION, session)
                    .putString(SessionResultWorker.KEY_RACE_NAME, raceName)
                    .build(),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()

        // One specific session's classification-check, so a stray duplicate broadcast should never
        // restart work that's already retrying.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "result-$season-$round-$session",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_ROUND = "extra_round"
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_RACE_NAME = "extra_race_name"
    }
}
