package com.praval.f1calendar.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms do not survive a reboot, an app reinstall, or (for wall-clock correctness) a timezone
 * change. Rather than doing the work inline on the broadcast thread, this hands off to
 * [ScheduleSyncWorker], which refreshes the calendar and rebuilds every alarm.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> ScheduleSyncWorker.enqueueOnce(context)
        }
    }
}
