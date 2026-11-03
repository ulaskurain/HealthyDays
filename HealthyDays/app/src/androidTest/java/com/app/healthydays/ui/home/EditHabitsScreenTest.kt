package com.app.healthydays.ui.home

import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
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
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditHabitsScreenTest {

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppTestHelper.resetState()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun openEditScreen(vararg habits: Habit) {
        AppTestHelper.seedHabits(*habits)
        scenario = AppTestHelper.launchMain()
        onView(withId(R.id.editHabits)).perform(click())
    }

    private fun habitsInDb() = runBlocking { AppTestHelper.db.habitDao().getAllHabits() }

    private fun openAddDialog() {
        onView(withId(R.id.buttonAddHabit)).perform(click())
        onView(withHint("Habit name")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    private fun typeName(name: String) {
        onView(withHint("Habit name")).inRoot(isDialog()).perform(replaceText(name))
    }

    private fun confirmAdd() {
        onView(withText("Add")).inRoot(isDialog()).perform(click())
    }

    @Test
    fun listShowsExistingHabits() {
        openEditScreen(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 0),
            Habit(name = "Nadar", categoryId = "PHYSICAL_HEALTH", sortOrder = 1)
        )
        eventually {
            onView(withText("Correr")).check(matches(isDisplayed()))
            onView(withText("Nadar")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun addHabit_withDefaultCategory_savesItAsOther() {
        openEditScreen()
        openAddDialog()
        typeName("Meditar")
        confirmAdd()

        waitUntil { habitsInDb().any { it.name == "Meditar" } }
        assertEquals("OTHER", habitsInDb().single { it.name == "Meditar" }.categoryId)
    }

    @Test
    fun addHabit_withChosenCategory_savesThatCategory() {
        openEditScreen()
        openAddDialog()

        onView(withText("Other")).inRoot(isDialog()).perform(click())
        onView(withText("Physical health")).inRoot(isDialog()).perform(click())
        onView(withText("Physical health")).inRoot(isDialog()).check(matches(isDisplayed()))

        typeName("Estirar")
        confirmAdd()

        waitUntil { habitsInDb().any { it.name == "Estirar" } }
        assertEquals("PHYSICAL_HEALTH", habitsInDb().single { it.name == "Estirar" }.categoryId)
    }

    @Test
    fun addHabit_newHabitAppearsInTheList() {
        openEditScreen()
        openAddDialog()
        typeName("Estirar")
        onView(withText("Other")).inRoot(isDialog()).perform(click())
        onView(withText("Physical health")).inRoot(isDialog()).perform(click())
        confirmAdd()

        eventually { onView(withText("Estirar")).check(matches(isDisplayed())) }
    }

    @Test
    fun addHabit_newHabitGetsATodayCompletion() {
        openEditScreen()
        openAddDialog()
        typeName("Meditar")
        confirmAdd()

        waitUntil {
            runBlocking { AppTestHelper.db.habitCompletionDao().getHabitCompletionsByDate(today) }
                .any { it.name == "Meditar" }
        }
    }

    @Test
    fun addHabit_emptyName_showsErrorAndKeepsDialogOpen() {
        openEditScreen()
        openAddDialog()
        confirmAdd()

        onView(withHint("Habit name")).inRoot(isDialog()).check(matches(hasErrorText("Enter a name")))
        assertTrue(habitsInDb().isEmpty())
    }

    @Test
    fun addHabit_blankNameWithSpaces_isRejected() {
        openEditScreen()
        openAddDialog()
        typeName("     ")
        confirmAdd()

        onView(withHint("Habit name")).inRoot(isDialog()).check(matches(hasErrorText("Enter a name")))
        assertTrue(habitsInDb().isEmpty())
    }

    @Test
    fun addHabit_nameIsLimitedTo50Characters() {
        openEditScreen()
        openAddDialog()
        typeName("a".repeat(80))

        onView(withHint("Habit name")).inRoot(isDialog()).check { view, noViewFound ->
            if (noViewFound != null) throw noViewFound
            assertEquals(50, (view as EditText).text.length)
        }
    }

    @Test
    fun addHabit_cancel_createsNothing() {
        openEditScreen()
        openAddDialog()
        typeName("No debe guardarse")
        onView(withText("Cancel")).inRoot(isDialog()).perform(click())

        Thread.sleep(300)
        assertTrue(habitsInDb().isEmpty())
    }

    @Test
    fun addHabit_sameNameAndCategory_isNotDuplicated() {
        openEditScreen(Habit(name = "Correr", categoryId = "OTHER"))
        openAddDialog()
        typeName("Correr")
        confirmAdd()

        Thread.sleep(400)
        assertEquals(1, habitsInDb().size)
    }

    @Test
    fun addHabit_sameNameInAnotherCategory_isAllowed() {
        openEditScreen(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        openAddDialog()
        typeName("Correr")
        confirmAdd()

        waitUntil { habitsInDb().size == 2 }
        assertEquals(setOf("PHYSICAL_HEALTH", "OTHER"), habitsInDb().map { it.categoryId }.toSet())
    }

    @Test
    fun deleteHabit_removesItFromListAndDatabaseAndTodaysCompletion() {
        openEditScreen(
            Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 0),
            Habit(name = "Nadar", categoryId = "PHYSICAL_HEALTH", sortOrder = 1)
        )
        val id = runBlocking { AppTestHelper.db.habitDao().getHabitByName("Correr") }!!.habitId
        waitUntil { runBlocking { AppTestHelper.db.habitCompletionDao().getCompletion(id, today) } != null }
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }

        onView(allOf(withId(R.id.buttonDelete), hasSibling(withText("Correr")))).perform(click())

        waitUntil { runBlocking { AppTestHelper.db.habitDao().getHabitById(id) } == null }
        eventually { onView(withText("Correr")).check(doesNotExist()) }
        onView(withText("Nadar")).check(matches(isDisplayed()))
        assertNull(runBlocking { AppTestHelper.db.habitCompletionDao().getCompletion(id, today) })
    }

    @Test
    fun deleteHabit_keepsPastHistory() {
        val past = "2000-01-01"
        AppTestHelper.seedHabits(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        val id = runBlocking { AppTestHelper.db.habitDao().getHabitByName("Correr") }!!.habitId
        runBlocking {
            AppTestHelper.db.dayDao().insertOrUpdateDay(com.app.healthydays.data.entity.Day(past, 100))
            AppTestHelper.db.habitCompletionDao().insertOrUpdateCompletion(
                com.app.healthydays.data.entity.HabitCompletion(id, "Correr", past, true, "PHYSICAL_HEALTH", 0)
            )
        }
        scenario = AppTestHelper.launchMain()
        onView(withId(R.id.editHabits)).perform(click())
        eventually { onView(withText("Correr")).check(matches(isDisplayed())) }

        onView(allOf(withId(R.id.buttonDelete), hasSibling(withText("Correr")))).perform(click())

        waitUntil { runBlocking { AppTestHelper.db.habitDao().getHabitById(id) } == null }
        assertNotNull(runBlocking { AppTestHelper.db.habitCompletionDao().getCompletion(id, past) })
    }

    @Test
    fun afterEditing_homeReflectsTheChanges() {
        openEditScreen(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        openAddDialog()
        typeName("Nadar")
        onView(withText("Other")).inRoot(isDialog()).perform(click())
        onView(withText("Physical health")).inRoot(isDialog()).perform(click())
        confirmAdd()
        waitUntil { habitsInDb().size == 2 }

        pressBack()

        eventually {
            onView(withText("Correr")).check(matches(isDisplayed()))
            onView(withText("Nadar")).check(matches(isDisplayed()))
        }
    }
}
