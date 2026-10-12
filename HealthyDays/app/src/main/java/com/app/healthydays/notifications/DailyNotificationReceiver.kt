package com.app.healthydays.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.healthydays.MainActivity
import com.app.healthydays.R
import java.util.Locale

class DailyNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Aseguramos que el canal existe
        NotificationHelper.createNotificationChannel(context)

        // Comprobamos permiso de notificaciones en Android 13+
        val notificationPermissionGranted =
            android.os.Build.VERSION.SDK_INT <
                    android.os.Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        if (!notificationPermissionGranted) {
            return
        }

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE )
        val language = prefs.getString("app_language", "en") ?: "en"
        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        val localizedContext = context.createConfigurationContext(configuration)

        // Intent para abrir la aplicación al pulsar la notificación
        val openAppIntent = Intent(
            context,
            MainActivity::class.java
        )

        val pendingIntent = PendingIntent.getActivity(
            context,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        // Construimos la notificación
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_notifications)
            .setContentTitle(localizedContext.getString(R.string.notification_title))
            .setContentText(localizedContext.getString(R.string.notification_message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Mostramos la notificación
        NotificationManagerCompat
            .from(context)
            .notify(1001, notification)
    }
}
