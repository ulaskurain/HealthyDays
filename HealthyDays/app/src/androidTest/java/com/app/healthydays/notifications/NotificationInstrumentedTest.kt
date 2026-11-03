package com.app.healthydays.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import com.app.healthydays.AppTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Notificaciones reales en el dispositivo (necesita API 23+ para leer las notificaciones activas). */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
class NotificationInstrumentedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        else GrantPermissionRule.grant()

    private val context get() = AppTestHelper.context
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        AppTestHelper.resetState()
        manager.cancelAll()
    }

    @After
    fun tearDown() {
        manager.cancelAll()
    }

    private fun waitForNotification(): Notification {
        AppTestHelper.waitUntil(message = "No apareció la notificación") {
            manager.activeNotifications.any { it.id == 1001 }
        }
        return manager.activeNotifications.first { it.id == 1001 }.notification
    }

    @Test
    fun channel_isCreatedWithDefaultImportance() {
        NotificationHelper.createNotificationChannel(context)

        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun receiver_postsRealNotificationInEnglish() {
        DailyNotificationReceiver().onReceive(context, Intent())

        val n = waitForNotification()
        assertEquals("How's your day going?", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Take a moment to review your habits and track your progress.",
            n.extras.getString(Notification.EXTRA_TEXT)
        )
    }

    @Test
    fun receiver_usesLanguageSelectedInTheApp() {
        AppTestHelper.settingsPrefs().edit().putString("app_language", "es").commit()

        DailyNotificationReceiver().onReceive(context, Intent())

        assertEquals("¿Qué tal va tu día?", waitForNotification().extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun notification_isAutoCancelAndOpensTheApp() {
        DailyNotificationReceiver().onReceive(context, Intent())

        val n = waitForNotification()
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNotNull(n.contentIntent)
    }

    @Test
    fun postingTwice_replacesInsteadOfStacking() {
        DailyNotificationReceiver().onReceive(context, Intent())
        DailyNotificationReceiver().onReceive(context, Intent())
        waitForNotification()

        assertEquals(1, manager.activeNotifications.count { it.id == 1001 })
    }
}
