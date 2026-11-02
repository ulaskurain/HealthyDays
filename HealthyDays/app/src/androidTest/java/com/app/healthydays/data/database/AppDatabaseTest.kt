package com.app.healthydays.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.healthydays.data.dto.ExportData
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertDay(date: String) {
        database.dayDao().insertOrUpdateDay(
            Day(date = date, rating = 0)
        )
    }

    private suspend fun insertHabit(
        name: String,
        categoryId: String,
        sortOrder: Int = 0
    ): Int {
        return database.habitDao().insertHabit(
            Habit(
                name = name,
                categoryId = categoryId,
                sortOrder = sortOrder
            )
        ).toInt()
    }

    private fun createCompletion(
        habitId: Int,
        habitName: String,
        date: String,
        completed: Boolean = false,
        categoryId: String = "OTHER",
        sortOrder: Int = 0
    ) = HabitCompletion(
        habitId = habitId,
        habitName = habitName,
        date = date,
        isCompleted = completed,
        categoryId = categoryId,
        sortOrder = sortOrder
    )

    @Test
    fun importDataInsertsNewHabits() = runBlocking {
        val backup = ExportData(
            habits = listOf(
                Habit(
                    name = "Beber agua",
                    categoryId = "NUTRITION",
                    sortOrder = 1
                )
            ),
            days = emptyList(),
            completions = emptyList()
        )

        database.importData(backup)

        val habits = database.habitDao().getAllHabits()

        assertEquals(1, habits.size)
        assertEquals("Beber agua", habits.first().name)
        assertEquals("NUTRITION", habits.first().categoryId)
        assertEquals(1, habits.first().sortOrder)
    }

    @Test
    fun importDataUpdatesExistingHabitKeepingItsId() = runBlocking {
        val existingId = insertHabit(
            name = "Leer",
            categoryId = "LEARNING",
            sortOrder = 0
        )

        val backup = ExportData(
            habits = listOf(
                Habit(
                    habitId = 100,
                    name = "Leer 20 minutos",
                    categoryId = "LEARNING",
                    sortOrder = 2
                )
            ),
            days = emptyList(),
            completions = emptyList()
        )

        // Mismo nombre y categoría para que se reconozca como el mismo hábito.
        val matchingBackup = backup.copy(
            habits = listOf(
                backup.habits.first().copy(name = "Leer")
            )
        )

        database.importData(matchingBackup)

        val result = database.habitDao().getHabitById(existingId)

        assertNotNull(result)
        assertEquals(existingId, result?.habitId)
        assertEquals(2, result?.sortOrder)
    }

    @Test
    fun importDataDoesNotDuplicateExistingDays() = runBlocking {
        insertDay("2026-10-28")

        val backup = ExportData(
            habits = emptyList(),
            days = listOf(
                Day(date = "2026-10-28", rating = 5),
                Day(date = "2026-10-29", rating = 3)
            ),
            completions = emptyList()
        )

        database.importData(backup)

        val days = database.dayDao().getAllDays()

        assertEquals(2, days.size)
        assertEquals(
            0,
            database.dayDao().getDayByDate("2026-10-28")?.rating
        )
        assertEquals(
            3,
            database.dayDao().getDayByDate("2026-10-29")?.rating
        )
    }

    @Test
    fun importDataRestoresCompletionsWithCorrectHabitId() = runBlocking {
        val currentId = insertHabit(
            name = "Beber agua",
            categoryId = "NUTRITION"
        )

        val backup = ExportData(
            habits = listOf(
                Habit(
                    habitId = 100,
                    name = "Beber agua",
                    categoryId = "NUTRITION"
                )
            ),
            days = listOf(
                Day(date = "2026-10-28", rating = 4)
            ),
            completions = listOf(
                createCompletion(
                    habitId = 100,
                    habitName = "Beber agua",
                    date = "2026-10-28",
                    completed = true,
                    categoryId = "NUTRITION"
                )
            )
        )

        database.importData(backup)

        val result = database.habitCompletionDao()
            .getCompletion(currentId, "2026-10-28")

        assertNotNull(result)
        assertEquals(currentId, result?.habitId)
        assertTrue(result!!.isCompleted)
    }

    @Test
    fun importDataPreservesExistingCompletions() = runBlocking {
        val habitId = insertHabit(
            name = "Leer",
            categoryId = "LEARNING"
        )
        insertDay("2026-10-28")

        database.habitCompletionDao().insertOrUpdateCompletion(
            createCompletion(
                habitId = habitId,
                habitName = "Leer",
                date = "2026-10-28",
                completed = false,
                categoryId = "LEARNING"
            )
        )

        val backup = ExportData(
            habits = listOf(
                Habit(
                    name = "Leer",
                    categoryId = "LEARNING"
                )
            ),
            days = listOf(
                Day(date = "2026-10-28", rating = 5)
            ),
            completions = listOf(
                createCompletion(
                    habitId = 100,
                    habitName = "Leer",
                    date = "2026-10-28",
                    completed = true,
                    categoryId = "LEARNING"
                )
            )
        )

        database.importData(backup)

        val result = database.habitCompletionDao()
            .getCompletion(habitId, "2026-10-28")

        assertNotNull(result)
        assertFalse(result!!.isCompleted)
    }

    @Test
    fun importDataRestoresCompletionOfDeletedHabit() = runBlocking {
        insertDay("2026-10-28")

        val backup = ExportData(
            habits = emptyList(),
            days = emptyList(),
            completions = listOf(
                createCompletion(
                    habitId = 50,
                    habitName = "Hábito antiguo",
                    date = "2026-10-28",
                    completed = true
                )
            )
        )

        database.importData(backup)

        val result = database.habitCompletionDao()
            .getCompletion(50, "2026-10-28")

        assertNotNull(result)
        assertEquals("Hábito antiguo", result?.habitName)
        assertTrue(result!!.isCompleted)
    }

    @Test
    fun deleteAllDataRemovesHabitsDaysAndCompletions() = runBlocking {
        val habitId = insertHabit(
            name = "Meditar",
            categoryId = "MENTAL_WELLBEING"
        )
        insertDay("2026-10-28")

        database.habitCompletionDao().insertOrUpdateCompletion(
            createCompletion(
                habitId = habitId,
                habitName = "Meditar",
                date = "2026-10-28",
                categoryId = "MENTAL_WELLBEING"
            )
        )

        database.deleteAllData()

        assertTrue(database.habitDao().getAllHabits().isEmpty())
        assertTrue(database.dayDao().getAllDays().isEmpty())
        assertTrue(database.habitCompletionDao().getAllCompletions().isEmpty())
    }
}
