package com.app.healthydays.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationsTest {

    private lateinit var app: Application
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun prefs() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- NotificationHelper ----------

    @Test
    fun createNotificationChannel_registersChannel() {
        NotificationHelper.createNotificationChannel(app)

        val channel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun createNotificationChannel_isIdempotent() {
        NotificationHelper.createNotificationChannel(app)
        NotificationHelper.createNotificationChannel(app)
        assertEquals(1, notificationManager.notificationChannels.count { it.id == NotificationHelper.CHANNEL_ID })
    }

    // ---------- NotificationScheduler ----------

    @Test
    fun scheduleDailyNotification_createsOneRepeatingDailyAlarm() {
        NotificationScheduler.scheduleDailyNotification(app, 20, 30)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(AlarmManager.INTERVAL_DAY, alarms[0].interval)
        assertEquals(AlarmManager.RTC_WAKEUP, alarms[0].type)
    }

    @Test
    fun scheduleDailyNotification_triggersAtRequestedTimeInTheFuture() {
        val before = System.currentTimeMillis()
        NotificationScheduler.scheduleDailyNotification(app, 20, 30)

        val trigger = shadowOf(alarmManager).scheduledAlarms[0].triggerAtTime
        assertTrue("La alarma debe ser futura", trigger > before)
        assertTrue("A lo sumo dentro de 24 h", trigger - before <= AlarmManager.INTERVAL_DAY)

        val cal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun scheduleDailyNotification_twice_replacesPreviousAlarm() {
        NotificationScheduler.scheduleDailyNotification(app, 8, 0)
        NotificationScheduler.scheduleDailyNotification(app, 21, 15)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        val cal = Calendar.getInstance().apply { timeInMillis = alarms[0].triggerAtTime }
        assertEquals(21, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.MINUTE))
    }

    @Test
    fun cancelDailyNotification_removesAlarm() {
        NotificationScheduler.scheduleDailyNotification(app, 20, 0)
        NotificationScheduler.cancelDailyNotification(app)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun cancelDailyNotification_withoutAlarm_doesNotFail() {
        NotificationScheduler.cancelDailyNotification(app)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    // ---------- BootReceiver ----------

    @Test
    fun bootReceiver_withNotificationsEnabled_rescheduleAtSavedTime() {
        prefs().edit()
            .putBoolean("notifications_enabled", true)
            .putInt("notification_hour", 7)
            .putInt("notification_minute", 45)
            .commit()

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        val cal = Calendar.getInstance().apply { timeInMillis = alarms[0].triggerAtTime }
        assertEquals(7, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, cal.get(Calendar.MINUTE))
        assertNotNull(notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID))
    }

    @Test
    fun bootReceiver_withNotificationsDisabled_doesNotSchedule() {
        prefs().edit().putBoolean("notifications_enabled", false).commit()

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun bootReceiver_ignoresOtherActionsAndNullIntent() {
        prefs().edit().putBoolean("notifications_enabled", true).commit()

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BATTERY_LOW))
        BootReceiver().onReceive(app, null)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun bootReceiver_usesDefaultTimeOf2000hWhenNothingSaved() {
        prefs().edit().putBoolean("notifications_enabled", true).commit()

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        val cal = Calendar.getInstance().apply {
            timeInMillis = shadowOf(alarmManager).scheduledAlarms[0].triggerAtTime
        }
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    // ---------- DailyNotificationReceiver ----------

    private fun title(n: Notification) = n.extras.getString(Notification.EXTRA_TITLE)
    private fun text(n: Notification) = n.extras.getString(Notification.EXTRA_TEXT)

    @Test
    fun dailyReceiver_postsNotificationInEnglishByDefault() {
        DailyNotificationReceiver().onReceive(app, Intent())

        val notifications = shadowOf(notificationManager).allNotifications
        assertEquals(1, notifications.size)
        assertEquals("How's your day going?", title(notifications[0]))
        assertEquals("Take a moment to review your habits and track your progress.", text(notifications[0]))
    }

    @Test
    fun dailyReceiver_usesSavedAppLanguage() {
        prefs().edit().putString("app_language", "es").commit()

        DailyNotificationReceiver().onReceive(app, Intent())

        val n = shadowOf(notificationManager).allNotifications.single()
        assertEquals("¿Qué tal va tu día?", title(n))
    }

    @Test
    fun dailyReceiver_supportsEveryAppLanguage() {
        listOf("en", "es", "eu", "fr").forEach { lang ->
            notificationManager.cancelAll()
            prefs().edit().putString("app_language", lang).commit()

            DailyNotificationReceiver().onReceive(app, Intent())

            val n = shadowOf(notificationManager).allNotifications.single()
            assertTrue("Título vacío para $lang", !title(n).isNullOrBlank())
            assertTrue("Texto vacío para $lang", !text(n).isNullOrBlank())
        }
    }

    @Test
    fun dailyReceiver_opensMainActivityOnTap() {
        DailyNotificationReceiver().onReceive(app, Intent())

        val n = shadowOf(notificationManager).allNotifications.single()
        assertNotNull(n.contentIntent)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun dailyReceiver_withoutPermission_postsNothing() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        DailyNotificationReceiver().onReceive(app, Intent())

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    @Config(sdk = [30])
    fun dailyReceiver_belowAndroid13_doesNotNeedPermission() {
        DailyNotificationReceiver().onReceive(app, Intent())

        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }
}
