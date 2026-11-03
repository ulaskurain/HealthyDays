package com.app.healthydays.ui.home

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.TestUtils
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeViewModelTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var viewModel: HomeViewModel
    private val today = LocalDate.now().toString()

    @Before
    fun setUp() {
        TestUtils.resetDatabaseSingleton()
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("habit_order", Context.MODE_PRIVATE).edit().clear().commit()
        db = AppDatabase.getDatabase(app)
        runBlocking { db.deleteAllData() }
        viewModel = HomeViewModel(app)
    }

    @After
    fun tearDown() {
        TestUtils.resetDatabaseSingleton()
    }

    // ---------- Orden de categorías (SharedPreferences) ----------

    @Test
    fun getCategoryOrder_defaultsToEnumOrder() {
        assertEquals(HabitCategory.entries.map { it.id }, viewModel.getCategoryOrder())
    }

    @Test
    fun saveCategoryOrder_isPersistedAndCompleted() {
        viewModel.saveCategoryOrder(listOf("SOCIAL", "HOME"))

        val order = viewModel.getCategoryOrder()
        assertEquals(listOf("SOCIAL", "HOME"), order.take(2))
        assertEquals(HabitCategory.entries.size, order.size)
        assertEquals(order.size, order.toSet().size)
    }

    @Test
    fun getCategoryOrder_ignoresUnknownIds() {
        viewModel.saveCategoryOrder(listOf("INVENTADA", "SOCIAL"))

        val order = viewModel.getCategoryOrder()
        assertFalse(order.contains("INVENTADA"))
        assertEquals("SOCIAL", order.first())
    }

    @Test
    fun getCategoryOrder_isSharedBetweenViewModels() {
        viewModel.saveCategoryOrder(listOf("HOBBIES"))
        assertEquals("HOBBIES", HomeViewModel(app).getCategoryOrder().first())
    }

    // ---------- Días y hábitos ----------

    @Test
    fun initializeDay_createsDayWithZeroRating() {
        viewModel.initializeDay(today)

        TestUtils.waitUntil { runBlocking { db.dayDao().getDayByDate(today) } != null }
        assertEquals(0, runBlocking { db.dayDao().getDayByDate(today) }!!.rating)
    }

    @Test
    fun initializeDay_doesNotOverwriteExistingRating() {
        runBlocking { db.dayDao().insertOrUpdateDay(Day(today, 80)) }

        viewModel.initializeDay(today)
        Thread.sleep(300)

        assertEquals(80, runBlocking { db.dayDao().getDayByDate(today) }!!.rating)
    }

    @Test
    fun initializeDay_createsPendingCompletionForEveryHabit() {
        runBlocking {
            db.habitDao().insertHabit(Habit(name = "A", categoryId = "SOCIAL", sortOrder = 0))
            db.habitDao().insertHabit(Habit(name = "B", categoryId = "HOME", sortOrder = 0))
        }

        viewModel.initializeDay(today)

        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getHabitCompletionsByDate(today) }.size == 2 }
        val items = runBlocking { db.habitCompletionDao().getHabitCompletionsByDate(today) }
        assertTrue(items.none { it.isCompleted })
        assertEquals(setOf("SOCIAL", "HOME"), items.map { it.categoryId }.toSet())
    }

    @Test
    fun initializeDay_doesNotResetAlreadyCompletedHabit() {
        val id = runBlocking { db.habitDao().insertHabit(Habit(name = "A")).toInt() }
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getCompletion(id, today) } != null }

        viewModel.updateHabitChecked(id, true)
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getCompletion(id, today) }!!.isCompleted }

        viewModel.initializeDay(today)
        Thread.sleep(300)

        assertTrue(runBlocking { db.habitCompletionDao().getCompletion(id, today) }!!.isCompleted)
    }

    @Test
    fun addHabit_insertsHabitAndTodaysCompletion() {
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.dayDao().getDayByDate(today) } != null }

        viewModel.addHabit("Meditar", "MENTAL_WELLBEING")

        TestUtils.waitUntil { runBlocking { db.habitDao().getHabitByName("Meditar") } != null }
        val habit = runBlocking { db.habitDao().getHabitByName("Meditar") }!!
        assertEquals("MENTAL_WELLBEING", habit.categoryId)
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getCompletion(habit.habitId, today) } != null }
        assertFalse(runBlocking { db.habitCompletionDao().getCompletion(habit.habitId, today) }!!.isCompleted)
    }

    @Test
    fun addHabit_assignsNextSortOrderInsideCategory() {
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.dayDao().getDayByDate(today) } != null }

        viewModel.addHabit("Uno", "LEARNING")
        TestUtils.waitUntil { runBlocking { db.habitDao().getHabitByName("Uno") } != null }
        viewModel.addHabit("Dos", "LEARNING")
        TestUtils.waitUntil { runBlocking { db.habitDao().getHabitByName("Dos") } != null }

        assertEquals(0, runBlocking { db.habitDao().getHabitByName("Uno") }!!.sortOrder)
        assertEquals(1, runBlocking { db.habitDao().getHabitByName("Dos") }!!.sortOrder)
    }

    @Test
    fun addHabit_sameNameAndSameCategory_isIgnored() {
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.dayDao().getDayByDate(today) } != null }

        viewModel.addHabit("Correr", "PHYSICAL_HEALTH")
        TestUtils.waitUntil { runBlocking { db.habitDao().getAllHabits() }.size == 1 }
        viewModel.addHabit("Correr", "PHYSICAL_HEALTH")
        Thread.sleep(300)

        assertEquals(1, runBlocking { db.habitDao().getAllHabits() }.size)
    }

    @Test
    fun addHabit_sameNameInDifferentCategory_isAllowed() {
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.dayDao().getDayByDate(today) } != null }

        viewModel.addHabit("Leer", "LEARNING")
        TestUtils.waitUntil { runBlocking { db.habitDao().getAllHabits() }.size == 1 }
        viewModel.addHabit("Leer", "HOBBIES")

        TestUtils.waitUntil { runBlocking { db.habitDao().getAllHabits() }.size == 2 }
        val habits = runBlocking { db.habitDao().getAllHabits() }
        assertEquals(setOf("LEARNING", "HOBBIES"), habits.map { it.categoryId }.toSet())
        // Cada uno tiene su finalización de hoy.
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getHabitCompletionsByDate(today) }.size == 2 }
    }

    @Test
    fun updateHabitChecked_persistsState() {
        val id = runBlocking { db.habitDao().insertHabit(Habit(name = "A")).toInt() }
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getCompletion(id, today) } != null }

        viewModel.updateHabitChecked(id, true)
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getCompletion(id, today) }!!.isCompleted }

        viewModel.updateHabitChecked(id, false)
        TestUtils.waitUntil { !runBlocking { db.habitCompletionDao().getCompletion(id, today) }!!.isCompleted }
    }

    @Test
    fun updateHabitChecked_unknownHabit_doesNothing() {
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.dayDao().getDayByDate(today) } != null }

        viewModel.updateHabitChecked(999, true)
        Thread.sleep(300)

        assertNull(runBlocking { db.habitCompletionDao().getCompletion(999, today) })
    }

    @Test
    fun updateDailyRating_updatesExistingDay() {
        runBlocking { db.dayDao().insertOrUpdateDay(Day(today, 0)) }

        viewModel.updateDailyRating(65)

        TestUtils.waitUntil { runBlocking { db.dayDao().getDayByDate(today) }!!.rating == 65 }
    }

    @Test
    fun updateDailyRating_withoutDay_doesNotCreateOne() {
        viewModel.updateDailyRating(65)
        Thread.sleep(300)
        assertNull(runBlocking { db.dayDao().getDayByDate(today) })
    }

    @Test
    fun reorderHabits_savesSortOrderInHabitAndTodaysCompletion() {
        val a = runBlocking { db.habitDao().insertHabit(Habit(name = "A", categoryId = "SOCIAL", sortOrder = 0)).toInt() }
        val b = runBlocking { db.habitDao().insertHabit(Habit(name = "B", categoryId = "SOCIAL", sortOrder = 1)).toInt() }
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getHabitCompletionsByDate(today) }.size == 2 }

        viewModel.reorderHabits(
            listOf(
                Habit(habitId = b, name = "B", categoryId = "SOCIAL", sortOrder = 0),
                Habit(habitId = a, name = "A", categoryId = "SOCIAL", sortOrder = 1)
            )
        )

        TestUtils.waitUntil { runBlocking { db.habitDao().getHabitById(a) }!!.sortOrder == 1 }
        assertEquals(0, runBlocking { db.habitDao().getHabitById(b) }!!.sortOrder)
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getCompletion(a, today) }!!.sortOrder == 1 }
        assertEquals(0, runBlocking { db.habitCompletionDao().getCompletion(b, today) }!!.sortOrder)
    }

    @Test
    fun deleteHabit_removesHabitAndTodaysCompletionButKeepsPast() {
        val past = "2000-01-01"
        val id = runBlocking { db.habitDao().insertHabit(Habit(name = "A", categoryId = "SOCIAL")).toInt() }
        runBlocking { db.dayDao().insertOrUpdateDay(Day(past, 50)) }
        runBlocking {
            db.habitCompletionDao().insertOrUpdateCompletion(
                com.app.healthydays.data.entity.HabitCompletion(id, "A", past, true, "SOCIAL", 0)
            )
        }
        viewModel.initializeDay(today)
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getCompletion(id, today) } != null }

        viewModel.deleteHabit(runBlocking { db.habitDao().getHabitById(id)!! })

        TestUtils.waitUntil { runBlocking { db.habitDao().getHabitById(id) } == null }
        TestUtils.waitUntil { runBlocking { db.habitCompletionDao().getCompletion(id, today) } == null }
        assertNotNull("El historial pasado debe conservarse", runBlocking { db.habitCompletionDao().getCompletion(id, past) })
    }

    @Test
    fun deleteHabit_compactsSortOrderOfRemainingHabitsInCategory() {
        val ids = runBlocking {
            listOf("A", "B", "C").mapIndexed { index, name ->
                db.habitDao().insertHabit(Habit(name = name, categoryId = "HOME", sortOrder = index)).toInt()
            }
        }

        viewModel.deleteHabit(runBlocking { db.habitDao().getHabitById(ids[0])!! })

        TestUtils.waitUntil { runBlocking { db.habitDao().getHabitById(ids[1]) }!!.sortOrder == 0 }
        assertEquals(1, runBlocking { db.habitDao().getHabitById(ids[2]) }!!.sortOrder)
    }
}
