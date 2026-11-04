package com.app.healthydays.ui.home

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var viewModel: HomeViewModel

    private val today = LocalDate.now().toString()

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(
            application,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        viewModel = HomeViewModel(application, database)

        application.getSharedPreferences(
            "habit_order",
            Application.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @After
    fun tearDown() {
        waitForViewModelWork()
        database.close()

        application.getSharedPreferences(
            "habit_order",
            Application.MODE_PRIVATE
        ).edit().clear().commit()
    }

    /**
     * Los métodos del ViewModel (deleteHabit, addHabit...) lanzan corrutinas en Dispatchers.IO y
     * siguen trabajando DESPUÉS de lo que el test comprueba (p. ej. deleteHabit aún lee y reordena
     * los hábitos restantes). Si el test acaba y se cierra la base de datos antes, esa corrutina
     * lanza "connection pool has been closed" y el fallo se atribuye al test siguiente.
     * Se espera a que terminen las tareas; las que recogen un Flow indefinidamente
     * (observeHabits, getAllHabits) nunca acaban, por eso hay un tiempo máximo y luego se cancelan.
     */
    private fun waitForViewModelWork() = runBlocking {
        val job = viewModel.viewModelScope.coroutineContext[Job] ?: return@runBlocking
        withTimeoutOrNull(1500) { job.children.toList().joinAll() }
        job.cancelChildren()
    }

    private suspend fun waitUntil(
        condition: suspend () -> Boolean
    ) {
        withTimeout(5000) {
            while (!condition()) {
                delay(50)
            }
        }
    }

    private suspend fun insertToday() {
        database.dayDao().insertOrUpdateDay(
            Day(date = today, rating = 0)
        )
    }

    private suspend fun insertHabit(
        name: String,
        categoryId: String = "OTHER",
        sortOrder: Int = 0
    ): Habit {
        val id = database.habitDao().insertHabit(
            Habit(
                name = name,
                categoryId = categoryId,
                sortOrder = sortOrder
            )
        )

        return database.habitDao().getHabitById(id.toInt())!!
    }

    // ---------------------------------------------------------
    // initializeDay()
    // ---------------------------------------------------------

    @Test
    fun initializeDayCreatesDayAndCompletions() = runBlocking {
        val habit = insertHabit("Hacer ejercicio")

        viewModel.initializeDay(today)

        waitUntil {
            database.habitCompletionDao()
                .getCompletion(habit.habitId, today) != null
        }

        val day = database.dayDao().getDayByDate(today)
        val completion = database.habitCompletionDao()
            .getCompletion(habit.habitId, today)

        assertNotNull(day)
        assertEquals(0, day?.rating)

        assertNotNull(completion)
        assertEquals(false, completion?.isCompleted)
        assertEquals("Hacer ejercicio", completion?.habitName)
    }

    @Test
    fun initializeDayDoesNotOverwriteExistingDay() = runBlocking {
        database.dayDao().insertOrUpdateDay(
            Day(date = today, rating = 4)
        )

        viewModel.initializeDay(today)

        waitUntil {
            database.dayDao().getDayByDate(today)?.rating == 4
        }

        assertEquals(
            4,
            database.dayDao().getDayByDate(today)?.rating
        )
    }

    // ---------------------------------------------------------
    // addHabit()
    // ---------------------------------------------------------

    @Test
    fun addHabitCreatesHabitAndTodayCompletion() = runBlocking {
        insertToday()

        viewModel.addHabit("Leer", "LEARNING")

        waitUntil {
            val created = database.habitDao()
                .getHabitByNameAndCategory("Leer", "LEARNING")
            // addHabit inserta primero el hábito y después su finalización de hoy:
            // hay que esperar a las dos cosas o el test puede leer la finalización antes de que exista.
            created != null &&
                database.habitCompletionDao().getCompletion(created.habitId, today) != null
        }

        val habit = database.habitDao()
            .getHabitByNameAndCategory("Leer", "LEARNING")!!

        val completion = database.habitCompletionDao()
            .getCompletion(habit.habitId, today)

        assertNotNull(completion)
        assertEquals("Leer", completion?.habitName)
        assertFalse(completion!!.isCompleted)
        assertEquals("LEARNING", completion.categoryId)
        assertEquals(0, habit.sortOrder)
    }

    @Test
    fun addHabitDoesNotCreateDuplicate() = runBlocking {
        insertToday()
        insertHabit("Leer", "LEARNING")

        viewModel.addHabit("Leer", "LEARNING")

        delay(300)

        val habits = database.habitDao().getAllHabits()

        assertEquals(1, habits.size)
    }

    @Test
    fun addHabitUsesNextSortOrderInCategory() = runBlocking {
        insertToday()

        insertHabit("Leer", "LEARNING", 0)
        insertHabit("Estudiar", "LEARNING", 1)

        viewModel.addHabit("Practicar", "LEARNING")

        waitUntil {
            database.habitDao()
                .getHabitByNameAndCategory("Practicar", "LEARNING") != null
        }

        val habit = database.habitDao()
            .getHabitByNameAndCategory("Practicar", "LEARNING")!!

        assertEquals(2, habit.sortOrder)
    }

    // ---------------------------------------------------------
    // updateHabitChecked()
    // ---------------------------------------------------------

    @Test
    fun updateHabitCheckedUpdatesTodayCompletion() = runBlocking {
        insertToday()
        val habit = insertHabit("Beber agua")

        viewModel.updateHabitChecked(habit.habitId, true)

        waitUntil {
            database.habitCompletionDao()
                .getCompletion(habit.habitId, today)?.isCompleted == true
        }

        val completion = database.habitCompletionDao()
            .getCompletion(habit.habitId, today)

        assertTrue(completion!!.isCompleted)
    }

    // ---------------------------------------------------------
    // updateDailyRating()
    // ---------------------------------------------------------

    @Test
    fun updateDailyRatingChangesTodayRating() = runBlocking {
        insertToday()

        viewModel.updateDailyRating(5)

        waitUntil {
            database.dayDao().getDayByDate(today)?.rating == 5
        }

        assertEquals(
            5,
            database.dayDao().getDayByDate(today)?.rating
        )
    }

    @Test
    fun updateDailyRatingDoesNothingIfDayDoesNotExist() = runBlocking {
        viewModel.updateDailyRating(5)

        delay(300)

        assertNull(database.dayDao().getDayByDate(today))
    }

    // ---------------------------------------------------------
    // reorderHabits()
    // ---------------------------------------------------------

    @Test
    fun reorderHabitsUpdatesHabitAndCompletionOrder() = runBlocking {
        insertToday()

        val habit1 = insertHabit("Leer", "LEARNING", 0)
        val habit2 = insertHabit("Estudiar", "LEARNING", 1)

        database.habitCompletionDao().insertCompletions(
            listOf(
                HabitCompletion(
                    habitId = habit1.habitId,
                    habitName = habit1.name,
                    date = today,
                    isCompleted = false,
                    categoryId = habit1.categoryId,
                    sortOrder = 0
                ),
                HabitCompletion(
                    habitId = habit2.habitId,
                    habitName = habit2.name,
                    date = today,
                    isCompleted = false,
                    categoryId = habit2.categoryId,
                    sortOrder = 1
                )
            )
        )

        viewModel.reorderHabits(
            listOf(
                habit2.copy(sortOrder = 0),
                habit1.copy(sortOrder = 1)
            )
        )

        waitUntil {
            database.habitDao().getHabitById(habit2.habitId)?.sortOrder == 0 &&
                    database.habitDao().getHabitById(habit1.habitId)?.sortOrder == 1
        }

        val completion1 = database.habitCompletionDao()
            .getCompletion(habit1.habitId, today)

        val completion2 = database.habitCompletionDao()
            .getCompletion(habit2.habitId, today)

        assertEquals(1, completion1?.sortOrder)
        assertEquals(0, completion2?.sortOrder)
    }

    // ---------------------------------------------------------
    // deleteHabit()
    // ---------------------------------------------------------

    @Test
    fun deleteHabitRemovesHabitAndFutureCompletions() = runBlocking {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val tomorrow = LocalDate.now().plusDays(1).toString()

        database.dayDao().insertOrUpdateDay(Day(yesterday, 0))
        database.dayDao().insertOrUpdateDay(Day(today, 0))
        database.dayDao().insertOrUpdateDay(Day(tomorrow, 0))

        val habit = insertHabit("Correr")

        database.habitCompletionDao().insertCompletions(
            listOf(
                HabitCompletion(
                    habit.habitId, habit.name, yesterday,
                    true, habit.categoryId, 0
                ),
                HabitCompletion(
                    habit.habitId, habit.name, today,
                    true, habit.categoryId, 0
                ),
                HabitCompletion(
                    habit.habitId, habit.name, tomorrow,
                    false, habit.categoryId, 0
                )
            )
        )

        viewModel.deleteHabit(habit)

        waitUntil {
            database.habitDao().getHabitById(habit.habitId) == null
        }

        assertNotNull(
            database.habitCompletionDao()
                .getCompletion(habit.habitId, yesterday)
        )

        assertNull(
            database.habitCompletionDao()
                .getCompletion(habit.habitId, today)
        )

        assertNull(
            database.habitCompletionDao()
                .getCompletion(habit.habitId, tomorrow)
        )
    }

    @Test
    fun deleteHabitCompactsRemainingSortOrder() = runBlocking {
        val habit1 = insertHabit("Leer", "LEARNING", 0)
        val habit2 = insertHabit("Estudiar", "LEARNING", 1)
        val habit3 = insertHabit("Practicar", "LEARNING", 2)

        viewModel.deleteHabit(habit2)

        waitUntil {
            database.habitDao().getHabitById(habit3.habitId)?.sortOrder == 1
        }

        assertEquals(
            0,
            database.habitDao().getHabitById(habit1.habitId)?.sortOrder
        )

        assertEquals(
            1,
            database.habitDao().getHabitById(habit3.habitId)?.sortOrder
        )
    }

    // ---------------------------------------------------------
    // getCategoryOrder() / saveCategoryOrder()
    // ---------------------------------------------------------

    @Test
    fun saveCategoryOrderPersistsOrder() {
        val order = listOf("SOCIAL", "HOME", "LEARNING")

        viewModel.saveCategoryOrder(order)

        assertEquals(order, viewModel.getCategoryOrder().take(3))
    }

    @Test
    fun getCategoryOrderIgnoresInvalidAndDuplicateCategories() {
        viewModel.saveCategoryOrder(
            listOf("SOCIAL", "INVALID", "HOME", "SOCIAL")
        )

        val order = viewModel.getCategoryOrder()

        assertEquals("SOCIAL", order[0])
        assertEquals("HOME", order[1])
        assertEquals(order.size, order.distinct().size)
        assertTrue(order.none { it == "INVALID" })
    }

    @Test
    fun addHabitReusesHistoricalHabitId() = runBlocking {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val historicalId = 99

        database.dayDao().insertOrUpdateDay(
            Day(date = yesterday, rating = 3)
        )
        insertToday()

        // Simulamos una completación histórica cuyo hábito ya no existe.
        database.habitCompletionDao().insertOrUpdateCompletion(
            HabitCompletion(
                habitId = historicalId,
                habitName = "Leer",
                date = yesterday,
                isCompleted = true,
                categoryId = "LEARNING",
                sortOrder = 0
            )
        )

        viewModel.addHabit("Leer", "LEARNING")

        waitUntil {
            database.habitDao().getHabitById(historicalId) != null &&
                    database.habitCompletionDao()
                        .getCompletion(historicalId, today) != null
        }

        val habit = database.habitDao().getHabitById(historicalId)!!
        val completion = database.habitCompletionDao()
            .getCompletion(historicalId, today)!!

        assertEquals("Leer", habit.name)
        assertEquals("LEARNING", habit.categoryId)
        assertEquals(historicalId, habit.habitId)

        assertFalse(completion.isCompleted)
    }

    @Test
    fun observeHabitsReturnsCompletionsForDate() = runBlocking {
        insertToday()
        val habit = insertHabit("Hacer ejercicio")

        database.habitCompletionDao().insertOrUpdateCompletion(
            HabitCompletion(
                habitId = habit.habitId,
                habitName = habit.name,
                date = today,
                isCompleted = true,
                categoryId = habit.categoryId,
                sortOrder = habit.sortOrder
            )
        )

        val habits = withTimeout(5000) {
            viewModel.observeHabits(today).first {
                it.any { item -> item.habitId == habit.habitId }
            }
        }

        assertEquals(1, habits.size)
        assertEquals(habit.habitId, habits[0].habitId)
        assertEquals(habit.name, habits[0].name)
        assertTrue(habits[0].isCompleted)
    }

    @Test
    fun getAllHabitsReturnsStoredHabits() = runBlocking {
        val habit1 = insertHabit("Leer", "LEARNING")
        val habit2 = insertHabit("Correr", "PHYSICAL_HEALTH")

        val habits = withTimeout(5000) {
            viewModel.getAllHabits().first {
                it.size == 2
            }
        }

        assertEquals(2, habits.size)
        assertTrue(habits.any { it.habitId == habit1.habitId })
        assertTrue(habits.any { it.habitId == habit2.habitId })
    }
}