package com.praval.f1calendar.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.praval.f1calendar.MainActivity
import com.praval.f1calendar.R
import com.praval.f1calendar.domain.model.SessionType
import kotlin.math.roundToLong

/**
 * Fires the reminder itself. Everything it needs travels in the intent extras, so the receiver
 * needs no injection and stays cheap to start.
 */
class SessionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val season = intent.getIntExtra(EXTRA_SEASON, 0)
        val round = intent.getIntExtra(EXTRA_ROUND, 0)
        val raceName = intent.getStringExtra(EXTRA_RACE_NAME) ?: return
        val sessionLabel = SessionType.fromName(intent.getStringExtra(EXTRA_SESSION).orEmpty())?.label
            ?: return
        val startMillis = intent.getLongExtra(EXTRA_START_MILLIS, 0L)

        val manager = NotificationManagerCompat.from(context)
        // The user can revoke either of these after the alarm was already scheduled.
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            NotificationIds.requestCode(season, round, CONTENT_INTENT_SLOT),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_SEASON, season)
                putExtra(MainActivity.EXTRA_OPEN_ROUND, round)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationIds.CHANNEL_SESSIONS)
            .setSmallIcon(R.drawable.ic_stat_flag)
            .setContentTitle(countdownText(sessionLabel, startMillis))
            .setContentText(raceName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setWhen(startMillis)
            .build()

        runCatching {
            manager.notify(NotificationIds.requestCode(season, round, NOTIFICATION_SLOT), notification)
        }
    }

    /**
     * Reads the remaining time at delivery rather than trusting the configured lead time — an
     * inexact alarm can land several minutes late, and "starts in 30 min" would then be a lie.
     */
    private fun countdownText(sessionLabel: String, startMillis: Long): String {
        val remainingMs = startMillis - System.currentTimeMillis()
        val minutes = (remainingMs / 60_000.0).roundToLong()
        return when {
            minutes <= 0L -> "$sessionLabel is starting now"
            minutes == 1L -> "$sessionLabel starts in 1 min"
            minutes < 60L -> "$sessionLabel starts in $minutes min"
            else -> {
                val hours = minutes / 60
                val rest = minutes % 60
                if (rest == 0L) {
                    "$sessionLabel starts in ${hours}h"
                } else {
                    "$sessionLabel starts in ${hours}h ${rest}m"
                }
            }
        }
    }

    companion object {
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_ROUND = "extra_round"
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_RACE_NAME = "extra_race_name"
        const val EXTRA_START_MILLIS = "extra_start_millis"

        // Slots keep the content-intent and notification ids from colliding with alarm request
        // codes, which are derived from the SessionType ordinal.
        private const val CONTENT_INTENT_SLOT = 90
        private const val NOTIFICATION_SLOT = 91
    }
}
