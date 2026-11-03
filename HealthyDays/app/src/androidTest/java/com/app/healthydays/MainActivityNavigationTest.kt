package com.app.healthydays

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppTestHelper.resetState()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = AppTestHelper.launchMain()
    }

    private fun toolbarTitle(text: String) =
        onView(withId(R.id.toolbarTitle)).check(matches(withText(text)))

    @Test
    fun appStartsOnHome() {
        launch()
        toolbarTitle("Home")
        onView(withId(R.id.recyclerHabits)).check(matches(isDisplayed()))
    }

    @Test
    fun drawer_showsHomeAndHistoryEntriesAndTodaysDate() {
        launch()
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())

        // "Home" también es el título de la barra superior: hay que limitar la búsqueda al menú lateral.
        listOf("Home", "History", "Statistics").forEach { entry ->
            onView(allOf(withText(entry), isDescendantOfA(withId(R.id.nav_view))))
                .check(matches(isDisplayed()))
        }
        onView(withId(R.id.nav_date)).check(matches(withText(AppTestHelper.todayForDrawerHeader)))
    }

    @Test
    fun drawer_navigatesToStatistics() {
        launch()

        AppTestHelper.goToDrawerItem(R.id.nav_stats)

        toolbarTitle("Statistics")
    }

    @Test
    fun drawer_navigatesToHistoryAndBackToHome() {
        launch()

        AppTestHelper.goToDrawerItem(R.id.nav_history)
        toolbarTitle("History")
        onView(withId(R.id.calendarView)).check(matches(isDisplayed()))

        AppTestHelper.goToDrawerItem(R.id.nav_home)
        toolbarTitle("Home")
    }

    @Test
    fun settingsIcon_opensSettingsAndBackReturnsToHome() {
        launch()

        onView(withId(R.id.action_settings)).perform(click())
        onView(allOf(withText("Settings"), isDisplayed())).check(matches(isDisplayed()))
        onView(withId(R.id.optionLanguage)).check(matches(isDisplayed()))

        pressBack()
        toolbarTitle("Home")
    }

    @Test
    fun homeFab_opensEditHabitsAndBackReturnsToHome() {
        launch()

        onView(withId(R.id.editHabits)).perform(click())
        onView(allOf(withText("Edit habits"), isDisplayed())).check(matches(isDisplayed()))
        onView(withId(R.id.buttonAddHabit)).check(matches(isDisplayed()))

        pressBack()
        toolbarTitle("Home")
    }

    @Test
    fun savedAppearanceIsAppliedOnLaunch() {
        AppTestHelper.resetState(appearance = "dark")
        launch()
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())

        scenario?.close()
        AppTestHelper.resetState(appearance = "light")
        launch()
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun savedLanguageIsAppliedOnLaunch() {
        listOf("en" to "Home", "es" to "Inicio", "eu" to "Hasiera", "fr" to "Accueil").forEach { (code, title) ->
            scenario?.close()
            AppTestHelper.resetState(language = code)
            launch()
            toolbarTitle(title)
        }
    }
}
