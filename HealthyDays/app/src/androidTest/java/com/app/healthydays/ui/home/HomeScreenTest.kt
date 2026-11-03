package com.app.healthydays.ui.home

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.healthydays.AppTestHelper
import com.app.healthydays.AppTestHelper.eventually
import com.app.healthydays.AppTestHelper.today
import com.app.healthydays.AppTestHelper.waitUntil
import com.app.healthydays.MainActivity
import com.app.healthydays.R
import com.app.healthydays.data.entity.Habit
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.view.View

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppTestHelper.resetState()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launchWith(vararg habits: Habit) {
        AppTestHelper.seedHabits(*habits)
        scenario = AppTestHelper.launchMain()
    }

    private fun checkboxOf(name: String): Matcher<View> =
        allOf(withId(R.id.checkboxCompleted), hasSibling(withText(name)))

    private fun rating() = onView(withId(R.id.textRating))

    @Test
    fun withoutHabits_showsZeroPercentRating() {
        launchWith()
        eventually { rating().check(matches(withText("0%"))) }
        onView(withId(R.id.editHabits)).check(matches(isDisplayed()))
    }

    @Test
    fun habits_areGroupedUnderTheirCategoryHeader() {
        launchWith(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"),
            Habit(name = "Leer", categoryId = "LEARNING")
        )

        eventually {
            onView(withText("Physical health")).check(matches(isDisplayed()))
            onView(withText("Correr")).check(matches(isDisplayed()))
            onView(withText("Learning & Personal growth")).check(matches(isDisplayed()))
            onView(withText("Leer")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun emptyCategories_showNoHeader() {
        launchWith(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))

        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }
        onView(withText("Nutrition & Hydration")).check(doesNotExist())
        onView(withText("Social relationships")).check(doesNotExist())
    }

    @Test
    fun habitsStartUnchecked() {
        launchWith(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        eventually { onView(checkboxOf("Correr")).check(matches(isNotChecked())) }
    }

    @Test
    fun checkingAllHabits_gives100PercentAndIsSaved() {
        launchWith(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 0),
            Habit(name = "Nadar", categoryId = "PHYSICAL_HEALTH", sortOrder = 1)
        )
        eventually { onView(checkboxOf("Correr")).check(matches(isDisplayed())) }

        onView(checkboxOf("Correr")).perform(click())
        eventually { rating().check(matches(withText("50%"))) }

        onView(checkboxOf("Nadar")).perform(click())
        eventually { rating().check(matches(withText("100%"))) }

        waitUntil { runBlocking { AppTestHelper.db.dayDao().getDayByDate(today) }?.rating == 100 }
        val completions = runBlocking { AppTestHelper.db.habitCompletionDao().getHabitCompletionsByDate(today) }
        assertEquals(2, completions.size)
        assertTrue(completions.all { it.isCompleted })
    }

    @Test
    fun uncheckingAHabit_lowersTheRating() {
        launchWith(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 0),
            Habit(name = "Nadar", categoryId = "PHYSICAL_HEALTH", sortOrder = 1)
        )
        eventually { onView(checkboxOf("Correr")).check(matches(isDisplayed())) }

        onView(checkboxOf("Correr")).perform(click())
        onView(checkboxOf("Nadar")).perform(click())
        eventually { rating().check(matches(withText("100%"))) }

        onView(checkboxOf("Correr")).perform(click())
        eventually { rating().check(matches(withText("50%"))) }
        onView(checkboxOf("Correr")).check(matches(isNotChecked()))
        onView(checkboxOf("Nadar")).check(matches(isChecked()))

        waitUntil { runBlocking { AppTestHelper.db.dayDao().getDayByDate(today) }?.rating == 50 }
    }

    @Test
    fun clickingCategoryHeader_collapsesAndExpandsItsHabits() {
        launchWith(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 0),
            Habit(name = "Nadar", categoryId = "PHYSICAL_HEALTH", sortOrder = 1)
        )
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }

        onView(withText("Physical health")).perform(click())
        eventually {
            onView(withText("Correr")).check(doesNotExist())
            onView(withText("Nadar")).check(doesNotExist())
        }

        onView(withText("Physical health")).perform(click())
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }
    }

    @Test
    fun collapsingDoesNotChangeTheRating() {
        launchWith(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 0),
            Habit(name = "Nadar", categoryId = "PHYSICAL_HEALTH", sortOrder = 1)
        )
        eventually { onView(checkboxOf("Correr")).check(matches(isDisplayed())) }
        onView(checkboxOf("Correr")).perform(click())
        eventually { rating().check(matches(withText("50%"))) }

        onView(withText("Physical health")).perform(click())

        rating().check(matches(withText("50%")))
    }

    @Test
    fun launchingTheAppCreatesTodaysDayAndCompletions() {
        launchWith(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"),
            Habit(name = "Leer", categoryId = "LEARNING")
        )

        waitUntil {
            runBlocking { AppTestHelper.db.habitCompletionDao().getHabitCompletionsByDate(today) }.size == 2
        }
        assertEquals(0, runBlocking { AppTestHelper.db.dayDao().getDayByDate(today) }!!.rating)
        assertFalse(
            runBlocking { AppTestHelper.db.habitCompletionDao().getHabitCompletionsByDate(today) }.any { it.isCompleted }
        )
    }
}
