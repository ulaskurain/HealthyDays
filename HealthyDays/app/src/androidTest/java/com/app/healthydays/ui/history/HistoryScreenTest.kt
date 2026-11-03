package com.app.healthydays.ui.history

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.Visibility
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppTestHelper.resetState()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun checkboxOf(name: String): Matcher<View> =
        allOf(withId(R.id.checkboxCompleted), hasSibling(withText(name)))

    private fun launchAndOpenHistory(vararg habits: Habit) {
        AppTestHelper.seedHabits(*habits)
        scenario = AppTestHelper.launchMain()
        // Home crea primero el día y DESPUÉS las finalizaciones de cada hábito. Historial solo lee
        // los datos una vez al entrar, así que hay que esperar a que estén todas escritas.
        waitUntil {
            runBlocking { AppTestHelper.db.dayDao().getDayByDate(today) } != null &&
                runBlocking { AppTestHelper.db.habitCompletionDao().getHabitCompletionsByDate(today) }.size == habits.size
        }
        AppTestHelper.goToDrawerItem(R.id.nav_history)
    }

    @Test
    fun withoutHabits_showsEmptyMessageAndZeroRating() {
        launchAndOpenHistory()

        eventually {
            onView(withId(R.id.noHabitsMessage)).check(matches(isDisplayed()))
            onView(withText("No habits registered for that day")).check(matches(isDisplayed()))
            onView(withId(R.id.textRating)).check(matches(withText("0")))
        }
        onView(withId(R.id.recyclerHistoryHabits)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun todayIsSelectedAndShowsItsHabitsGroupedByCategory() {
        launchAndOpenHistory(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"),
            Habit(name = "Leer", categoryId = "LEARNING")
        )

        eventually {
            onView(withText("Physical health")).check(matches(isDisplayed()))
            onView(withText("Correr")).check(matches(isDisplayed()))
            onView(withText("Learning & Personal growth")).check(matches(isDisplayed()))
            onView(withText("Leer")).check(matches(isDisplayed()))
            onView(withId(R.id.noHabitsMessage)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun historyReflectsTheRatingSavedFromHome() {
        AppTestHelper.seedHabits(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 0),
            Habit(name = "Nadar", categoryId = "PHYSICAL_HEALTH", sortOrder = 1)
        )
        scenario = AppTestHelper.launchMain()
        eventually { onView(checkboxOf("Correr")).check(matches(isDisplayed())) }
        onView(checkboxOf("Correr")).perform(click())
        waitUntil { runBlocking { AppTestHelper.db.dayDao().getDayByDate(today) }?.rating == 50 }

        AppTestHelper.goToDrawerItem(R.id.nav_history)

        eventually { onView(withId(R.id.textRating)).check(matches(withText("50"))) }
        onView(withText("Correr")).check(matches(isDisplayed()))
        onView(withText("Nadar")).check(matches(isDisplayed()))
    }

    @Test
    fun clickingCategoryHeader_collapsesItsHabits() {
        launchAndOpenHistory(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }

        onView(withText("Physical health")).perform(click())
        eventually { onView(withText("Correr")).check(doesNotExist()) }

        onView(withText("Physical health")).perform(click())
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }
    }

    @Test
    fun selectingAnotherDay_showsEmptyStateAndDash() {
        launchAndOpenHistory(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }

        // Elegimos un día del mes distinto de hoy (el calendario solo muestra los del mes actual).
        val dayToClick = if (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) != 1) "1" else "2"
        onView(allOf(withText(dayToClick), isDisplayed())).perform(click())

        eventually {
            onView(withId(R.id.noHabitsMessage)).check(matches(isDisplayed()))
            onView(withId(R.id.textRating)).check(matches(withText("-")))
        }
        onView(withText("Correr")).check(doesNotExist())
    }

    @Test
    fun returningToToday_showsTodaysHabitsAgain() {
        launchAndOpenHistory(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }

        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val other = if (today != 1) "1" else "2"
        onView(allOf(withText(other), isDisplayed())).perform(click())
        eventually { onView(withId(R.id.noHabitsMessage)).check(matches(isDisplayed())) }

        onView(allOf(withText(today.toString()), isDisplayed())).perform(click())
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }
    }
}
