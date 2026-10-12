package com.app.healthydays.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", false)

        if (notificationsEnabled) {
            val hour = prefs.getInt("notification_hour", 20)
            val minute = prefs.getInt("notification_minute", 0)

            NotificationHelper.createNotificationChannel(context)
            NotificationScheduler.scheduleDailyNotification(context, hour, minute)
        }
    }
}