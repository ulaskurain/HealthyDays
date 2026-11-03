package com.app.healthydays.ui.settings

import android.Manifest
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.app.healthydays.AppTestHelper
import com.app.healthydays.AppTestHelper.eventually
import com.app.healthydays.AppTestHelper.waitUntil
import com.app.healthydays.MainActivity
import com.app.healthydays.R
import com.app.healthydays.data.entity.Habit
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        else GrantPermissionRule.grant()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppTestHelper.resetState()
    }

    @After
    fun tearDown() {
        scenario?.close()
        AppTestHelper.resetState()
    }

    private fun openSettings(vararg habits: Habit) {
        AppTestHelper.seedHabits(*habits)
        scenario = AppTestHelper.launchMain()
        onView(withId(R.id.action_settings)).perform(click())
    }

    private fun dialogOk() = onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    private fun dialogCancel() = onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())

    // ---------- Pantalla ----------

    @Test
    fun showsAllSectionsAndOptions() {
        openSettings()

        onView(withId(R.id.optionLanguage)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.optionNotifications)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.optionAppearance)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.optionExport)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.optionImport)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.optionDeleteData)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.optionAbout)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun showsCurrentValues_englishAndSystemDefault() {
        AppTestHelper.settingsPrefs().edit().remove("appearance").commit()
        openSettings()

        onView(withId(R.id.textCurrentLanguage)).check(matches(withText("English")))
        onView(withId(R.id.textCurrentAppearance)).perform(scrollTo()).check(matches(withText("System default")))
        onView(withId(R.id.textNotificationTime)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    // ---------- Idioma ----------

    @Test
    fun language_dialogListsFourLanguagesAndCancelChangesNothing() {
        openSettings()
        onView(withId(R.id.optionLanguage)).perform(click())

        listOf("English", "Español", "Euskara", "Français").forEach {
            onView(withText(it)).inRoot(isDialog()).check(matches(isDisplayed()))
        }
        dialogCancel()

        assertEquals("en", AppTestHelper.settingsPrefs().getString("app_language", null))
    }

    @Test
    fun language_changeToSpanish_appliesImmediatelyAndIsSaved() {
        openSettings()
        onView(withId(R.id.optionLanguage)).perform(click())
        onView(withText("Español")).inRoot(isDialog()).perform(click())
        dialogOk()

        assertEquals("es", AppTestHelper.settingsPrefs().getString("app_language", null))
        eventually {
            onView(withId(R.id.textCurrentLanguage)).check(matches(withText("Español")))
            onView(withText("Preferencias")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun language_changeToBasqueAndFrench() {
        openSettings()

        onView(withId(R.id.optionLanguage)).perform(click())
        onView(withText("Euskara")).inRoot(isDialog()).perform(click())
        dialogOk()
        eventually { onView(withText("Hobespenak")).check(matches(isDisplayed())) }

        onView(withId(R.id.optionLanguage)).perform(click())
        onView(withText("Français")).inRoot(isDialog()).perform(click())
        dialogOk()
        eventually { onView(withText("Préférences")).check(matches(isDisplayed())) }
        assertEquals("fr", AppTestHelper.settingsPrefs().getString("app_language", null))
    }

    // ---------- Apariencia ----------

    @Test
    fun appearance_changeToDark_isAppliedAndSaved() {
        openSettings()
        onView(withId(R.id.optionAppearance)).perform(scrollTo(), click())
        onView(withText("Dark")).inRoot(isDialog()).perform(click())
        dialogOk()

        assertEquals("dark", AppTestHelper.settingsPrefs().getString("appearance", null))
        eventually {
            onView(withId(R.id.textCurrentAppearance)).perform(scrollTo()).check(matches(withText("Dark")))
        }
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun appearance_cancelKeepsCurrentValue() {
        openSettings()
        onView(withId(R.id.optionAppearance)).perform(scrollTo(), click())
        onView(withText("Dark")).inRoot(isDialog()).perform(click())
        dialogCancel()

        assertEquals("light", AppTestHelper.settingsPrefs().getString("appearance", null))
    }

    // ---------- Notificaciones ----------

    @Test
    fun notifications_enable_savesTimeAndShowsIt() {
        openSettings()
        onView(withId(R.id.switchNotifications)).perform(scrollTo(), click())
        dialogOk() // hora por defecto 20:00

        waitUntil { AppTestHelper.settingsPrefs().getBoolean("notifications_enabled", false) }
        assertEquals(20, AppTestHelper.settingsPrefs().getInt("notification_hour", -1))
        assertEquals(0, AppTestHelper.settingsPrefs().getInt("notification_minute", -1))
        onView(withId(R.id.switchNotifications)).check(matches(isChecked()))
        onView(withId(R.id.textNotificationTime)).check(matches(allOf(isDisplayed(), withText("20:00"))))
    }

    @Test
    fun notifications_cancellingTheTimePicker_leavesThemDisabled() {
        openSettings()
        onView(withId(R.id.switchNotifications)).perform(scrollTo(), click())
        pressBack() // cierra el selector de hora

        eventually { onView(withId(R.id.switchNotifications)).check(matches(isNotChecked())) }
        assertFalse(AppTestHelper.settingsPrefs().getBoolean("notifications_enabled", false))
    }

    @Test
    fun notifications_disableAfterEnabling_hidesTimeAndSavesState() {
        openSettings()
        onView(withId(R.id.switchNotifications)).perform(scrollTo(), click())
        dialogOk()
        waitUntil { AppTestHelper.settingsPrefs().getBoolean("notifications_enabled", false) }

        onView(withId(R.id.switchNotifications)).perform(click())

        waitUntil { !AppTestHelper.settingsPrefs().getBoolean("notifications_enabled", true) }
        onView(withId(R.id.textNotificationTime)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    // ---------- Datos ----------

    @Test
    fun import_showsWarningAndCancelClosesWithoutOpeningFilePicker() {
        openSettings()
        onView(withId(R.id.optionImport)).perform(scrollTo(), click())

        onView(withText(containsString("will be added to the current data"))).inRoot(isDialog())
            .check(matches(isDisplayed()))
        dialogCancel()

        onView(withId(R.id.optionImport)).check(matches(isDisplayed()))
    }

    @Test
    fun deleteData_cancelKeepsEverything() {
        openSettings(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        onView(withId(R.id.optionDeleteData)).perform(scrollTo(), click())

        onView(withText(containsString("cannot be undone"))).inRoot(isDialog()).check(matches(isDisplayed()))
        dialogCancel()

        assertEquals(1, runBlocking { AppTestHelper.db.habitDao().getAllHabits() }.size)
    }

    @Test
    fun deleteData_confirmRemovesHabitsDaysAndCompletions() {
        openSettings(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        waitUntil { runBlocking { AppTestHelper.db.dayDao().getAllDays() }.isNotEmpty() }

        onView(withId(R.id.optionDeleteData)).perform(scrollTo(), click())
        dialogOk()

        waitUntil { runBlocking { AppTestHelper.db.habitDao().getAllHabits() }.isEmpty() }
        assertTrue(runBlocking { AppTestHelper.db.dayDao().getAllDays() }.isEmpty())
        assertTrue(runBlocking { AppTestHelper.db.habitCompletionDao().getAllCompletions() }.isEmpty())
    }

    // ---------- Información ----------

    @Test
    fun about_showsTitleMessageAndVersion() {
        openSettings()
        onView(withId(R.id.optionAbout)).perform(scrollTo(), click())

        onView(withText("About Healthy Days")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(containsString("Healthy Days was created"))).inRoot(isDialog())
            .check(matches(isDisplayed()))
        dialogOk()

        onView(withId(R.id.optionAbout)).check(matches(isDisplayed()))
    }
}
