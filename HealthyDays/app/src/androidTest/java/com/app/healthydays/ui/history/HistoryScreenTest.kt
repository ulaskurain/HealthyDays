package com.app.healthydays.ui.history

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.healthydays.AppTestHelper
import com.app.healthydays.AppTestHelper.calendarDay
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
import org.junit.Assert.assertFalse
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

        // La pantalla es un NestedScrollView: bajo el calendario y la tarjeta de rating,
        // las últimas filas pueden quedar fuera de pantalla, así que se hace scroll hasta cada una.
        eventually {
            assertShownAfterScrolling("Physical health")
            assertShownAfterScrolling("Correr")
            assertShownAfterScrolling("Learning & Personal growth")
            assertShownAfterScrolling("Leer")
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
        eventually {
            assertShownAfterScrolling("Correr")
            assertShownAfterScrolling("Nadar")
        }
    }

    @Test
    fun clickingCategoryHeader_collapsesItsHabits() {
        launchAndOpenHistory(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        eventually { assertShownAfterScrolling("Correr") }

        onView(withText("Physical health")).perform(scrollTo(), click())
        eventually { assertNotShown("Correr") }

        onView(withText("Physical health")).perform(scrollTo(), click())
        eventually { assertShownAfterScrolling("Correr") }
    }

    @Test
    fun selectingAnotherDay_showsEmptyStateAndDash() {
        launchAndOpenHistory(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        eventually { assertShownAfterScrolling("Correr") }

        onView(calendarDay(otherDayOfMonth())).perform(click())

        eventually {
            onView(withId(R.id.noHabitsMessage)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.textRating)).check(matches(withText("-")))
            // La lista queda en GONE pero la fila antigua puede seguir en la jerarquía: lo que importa es que no se vea.
            assertNotShown("Correr")
        }
    }

    @Test
    fun returningToToday_showsTodaysHabitsAgain() {
        launchAndOpenHistory(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        eventually { assertShownAfterScrolling("Correr") }

        onView(calendarDay(otherDayOfMonth())).perform(click())
        eventually { onView(withId(R.id.noHabitsMessage)).perform(scrollTo()).check(matches(isDisplayed())) }

        onView(calendarDay(Calendar.getInstance().get(Calendar.DAY_OF_MONTH))).perform(click())
        eventually { assertShownAfterScrolling("Correr") }
    }

    // ---------- Utilidades ----------

    /** Día 1 del mes, o el 2 si hoy es día 1 (siempre existe en la página actual del calendario). */
    private fun otherDayOfMonth(): Int =
        if (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) != 1) 1 else 2

    private fun assertShownAfterScrolling(text: String) {
        onView(withText(text)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    /** Pasa si la vista no existe o existe pero no es visible (p. ej. dentro de un RecyclerView en GONE). */
    private fun assertNotShown(text: String) {
        onView(withText(text)).check { view, _ ->
            if (view != null) assertFalse("'$text' no debería verse", view.isShown)
        }
    }
}
