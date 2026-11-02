
package com.app.healthydays.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
import com.app.healthydays.data.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitCompletionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var completionDao: HabitCompletionDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        completionDao = database.habitCompletionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Helpers para crear los registros necesarios antes de insertar completions.

    private suspend fun insertDay(date: String) {
        database.dayDao().insertOrUpdateDay(
            Day(date = date, rating = 0)
        )
    }

    private suspend fun insertHabit(
        id: Int,
        name: String = "Beber agua",
        categoryId: String = "NUTRITION",
        sortOrder: Int = 0
    ) {
        database.habitDao().insertHabit(
            Habit(
                habitId = id,
                name = name,
                categoryId = categoryId,
                sortOrder = sortOrder
            )
        )
    }

    private fun createCompletion(
        habitId: Int,
        date: String,
        name: String = "Beber agua",
        completed: Boolean = false,
        categoryId: String = "NUTRITION",
        sortOrder: Int = 0
    ) = HabitCompletion(
        habitId = habitId,
        habitName = name,
        date = date,
        isCompleted = completed,
        categoryId = categoryId,
        sortOrder = sortOrder
    )

    @Test
    fun insertCompletionAndRetrieveIt() = runBlocking {
        insertDay("2026-10-28")

        val completion = createCompletion(
            habitId = 1,
            date = "2026-10-28",
            completed = true
        )

        completionDao.insertOrUpdateCompletion(completion)

        val result = completionDao.getCompletion(1, "2026-10-28")

        assertNotNull(result)
        assertEquals(completion, result)
    }

    @Test
    fun insertOrUpdateCompletionReplacesExistingCompletion() = runBlocking {
        insertDay("2026-10-28")

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-28", completed = false)
        )

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-28", completed = true)
        )

        val result = completionDao.getCompletion(1, "2026-10-28")

        assertEquals(true, result?.isCompleted)
        assertEquals(1, completionDao.getAllCompletions().size)
    }

    @Test
    fun insertCompletionsIgnoresDuplicates() = runBlocking {
        insertDay("2026-10-28")

        val completion = createCompletion(1, "2026-10-28")

        completionDao.insertCompletions(listOf(completion))
        completionDao.insertCompletions(listOf(completion))

        assertEquals(1, completionDao.getAllCompletions().size)
    }

    @Test
    fun getCompletionReturnsNullWhenItDoesNotExist() = runBlocking {
        val result = completionDao.getCompletion(1, "2026-10-28")

        assertNull(result)
    }

    @Test
    fun getAllCompletionsReturnsEveryCompletion() = runBlocking {
        insertDay("2026-10-28")
        insertDay("2026-10-29")

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-28")
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-29")
        )

        val result = completionDao.getAllCompletions()

        assertEquals(2, result.size)
    }

    @Test
    fun getCompletionsFromReturnsOnlyCompletionsFromDateOnwards() = runBlocking {
        insertDay("2026-10-27")
        insertDay("2026-10-28")
        insertDay("2026-10-29")

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-27")
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-28")
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-29")
        )

        val result = completionDao.getCompletionsFrom("2026-10-28")

        assertEquals(2, result.size)
        assertTrue(result.all { it.date >= "2026-10-28" })
    }

    @Test
    fun getHistoricalHabitIdReturnsMostRecentHabitId() = runBlocking {
        insertDay("2026-10-27")
        insertDay("2026-10-29")

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-27")
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(2, "2026-10-29")
        )

        val result = completionDao.getHistoricalHabitId(
            "Beber agua",
            "NUTRITION"
        )

        assertEquals(2, result)
    }

    @Test
    fun deleteFutureCompletionsRemovesTodayAndFutureButKeepsPast() = runBlocking {
        insertDay("2026-10-27")
        insertDay("2026-10-28")
        insertDay("2026-10-29")

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-27")
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-28")
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-29")
        )

        completionDao.deleteFutureCompletions(1, "2026-10-28")

        val result = completionDao.getAllCompletions()

        assertEquals(1, result.size)
        assertEquals("2026-10-27", result.first().date)
    }

    @Test
    fun updateTodayHabitOrderChangesOrderAndCategory() = runBlocking {
        insertDay("2026-10-28")

        completionDao.insertOrUpdateCompletion(
            createCompletion(
                habitId = 1,
                date = "2026-10-28",
                sortOrder = 0
            )
        )

        completionDao.updateTodayHabitOrder(
            habitId = 1,
            sortOrder = 3,
            categoryId = "HOBBIES",
            today = "2026-10-28"
        )

        val result = completionDao.getCompletion(1, "2026-10-28")

        assertEquals(3, result?.sortOrder)
        assertEquals("HOBBIES", result?.categoryId)
    }

    @Test
    fun deleteAllCompletionsRemovesEveryCompletion() = runBlocking {
        insertDay("2026-10-28")
        insertDay("2026-10-29")

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-28")
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(2, "2026-10-29")
        )

        completionDao.deleteAllCompletions()

        assertTrue(completionDao.getAllCompletions().isEmpty())
    }

    @Test
    fun getHabitsWithCompletionByDateFlowReturnsHabitsForDate() = runBlocking {
        insertDay("2026-10-28")
        insertDay("2026-10-29")

        insertHabit(1, "Beber agua", "NUTRITION", 0)
        insertHabit(2, "Leer", "LEARNING", 1)

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-28", completed = true)
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(2, "2026-10-29", name = "Leer")
        )

        val result = completionDao
            .getHabitsWithCompletionByDateFlow("2026-10-28")
            .first()

        assertEquals(1, result.size)
        assertEquals("Beber agua", result.first().name)
        assertTrue(result.first().isCompleted)
    }

    @Test
    fun getHabitCompletionsByDateReturnsCompletionsForDate() = runBlocking {
        insertDay("2026-10-28")
        insertDay("2026-10-29")

        completionDao.insertOrUpdateCompletion(
            createCompletion(1, "2026-10-28", completed = true)
        )
        completionDao.insertOrUpdateCompletion(
            createCompletion(2, "2026-10-29")
        )

        val result = completionDao.getHabitCompletionsByDate("2026-10-28")

        assertEquals(1, result.size)
        assertEquals(1, result.first().habitId)
        assertTrue(result.first().isCompleted)
    }
}
