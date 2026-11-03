package com.app.healthydays.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.TestUtils
import com.app.healthydays.data.dto.ExportData
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppDatabaseImportDeleteTest {

    private lateinit var db: AppDatabase

    private val date = "2026-11-01"

    @Before
    fun setUp() {
        db = TestUtils.inMemoryDb(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = db.close()

    private fun backup(
        habits: List<Habit> = emptyList(),
        days: List<Day> = emptyList(),
        completions: List<HabitCompletion> = emptyList()
    ) = ExportData(habits, days, completions)

    @Test
    fun importData_intoEmptyDatabase_restoresEverything() = runBlocking {
        val data = backup(
            habits = listOf(
                Habit(5, "Correr", "PHYSICAL_HEALTH", 0),
                Habit(6, "Leer", "LEARNING", 1)
            ),
            days = listOf(Day(date, 80)),
            completions = listOf(
                HabitCompletion(5, "Correr", date, true, "PHYSICAL_HEALTH", 0),
                HabitCompletion(6, "Leer", date, false, "LEARNING", 1)
            )
        )

        db.importData(data)

        assertEquals(2, db.habitDao().getAllHabits().size)
        assertEquals(80, db.dayDao().getDayByDate(date)?.rating)

        // Los ids autogenerados pueden diferir de los del backup: las finalizaciones deben seguir a los hábitos por nombre.
        val running = db.habitDao().getHabitByName("Correr")!!
        val reading = db.habitDao().getHabitByName("Leer")!!
        assertEquals("PHYSICAL_HEALTH", running.categoryId)
        assertEquals("LEARNING", reading.categoryId)
        assertTrue(db.habitCompletionDao().getCompletion(running.habitId, date)!!.isCompleted)
        assertFalse(db.habitCompletionDao().getCompletion(reading.habitId, date)!!.isCompleted)
    }

    @Test
    fun importData_existingHabitWithSameNameAndCategory_isUpdatedKeepingItsId() = runBlocking {
        val existingId = db.habitDao()
            .insertHabit(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 3)).toInt()

        db.importData(backup(habits = listOf(Habit(42, "Correr", "PHYSICAL_HEALTH", 0))))

        val habits = db.habitDao().getAllHabits()
        assertEquals(1, habits.size)
        assertEquals(existingId, habits[0].habitId)
        assertEquals(0, habits[0].sortOrder)
    }

    @Test
    fun importData_sameNameButDifferentCategory_isImportedAsNewHabit() = runBlocking {
        db.habitDao().insertHabit(Habit(name = "Correr", categoryId = "OTHER"))

        db.importData(backup(habits = listOf(Habit(42, "Correr", "PHYSICAL_HEALTH", 0))))

        val habits = db.habitDao().getAllHabits()
        assertEquals(2, habits.size)
        assertEquals(setOf("OTHER", "PHYSICAL_HEALTH"), habits.map { it.categoryId }.toSet())
    }

    @Test
    fun importData_sameNameInTwoCategories_keepsEachCompletionWithItsOwnHabit() = runBlocking {
        val data = backup(
            habits = listOf(
                Habit(1, "Leer", "LEARNING", 0),
                Habit(2, "Leer", "HOBBIES", 0)
            ),
            days = listOf(Day(date, 50)),
            completions = listOf(
                HabitCompletion(1, "Leer", date, true, "LEARNING", 0),
                HabitCompletion(2, "Leer", date, false, "HOBBIES", 0)
            )
        )

        db.importData(data)

        val habits = db.habitDao().getAllHabits()
        assertEquals(2, habits.size)
        val learning = habits.first { it.categoryId == "LEARNING" }
        val hobbies = habits.first { it.categoryId == "HOBBIES" }
        assertTrue(db.habitCompletionDao().getCompletion(learning.habitId, date)!!.isCompleted)
        assertFalse(db.habitCompletionDao().getCompletion(hobbies.habitId, date)!!.isCompleted)
    }

    @Test
    fun importData_doesNotOverwriteExistingDay() = runBlocking {
        db.dayDao().insertOrUpdateDay(Day(date, 10))

        db.importData(backup(days = listOf(Day(date, 90), Day("2026-11-02", 70))))

        assertEquals(10, db.dayDao().getDayByDate(date)?.rating)
        assertEquals(70, db.dayDao().getDayByDate("2026-11-02")?.rating)
    }

    @Test
    fun importData_doesNotOverwriteExistingCompletion() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(name = "Correr")).toInt()
        db.dayDao().insertOrUpdateDay(Day(date, 0))
        db.habitCompletionDao().insertOrUpdateCompletion(HabitCompletion(id, "Correr", date, false))

        db.importData(
            backup(
                habits = listOf(Habit(1, "Correr")),
                days = listOf(Day(date, 100)),
                completions = listOf(HabitCompletion(1, "Correr", date, true))
            )
        )

        assertFalse(db.habitCompletionDao().getCompletion(id, date)!!.isCompleted)
    }

    @Test
    fun importData_keepsCompletionsOfDeletedHabits() = runBlocking {
        // "Fantasma" ya no está en la lista de hábitos del backup, pero sí en el historial.
        db.importData(
            backup(
                days = listOf(Day(date, 50)),
                completions = listOf(HabitCompletion(77, "Fantasma", date, true, "HOME", 2))
            )
        )

        val restored = db.habitCompletionDao().getCompletion(77, date)
        assertNotNull(restored)
        assertEquals("Fantasma", restored!!.habitName)
        assertTrue(restored.isCompleted)
        assertNull(db.habitDao().getHabitByName("Fantasma"))
    }

    @Test
    fun importData_isIdempotent() = runBlocking {
        val data = backup(
            habits = listOf(Habit(1, "Correr", "PHYSICAL_HEALTH", 0)),
            days = listOf(Day(date, 60)),
            completions = listOf(HabitCompletion(1, "Correr", date, true, "PHYSICAL_HEALTH", 0))
        )

        db.importData(data)
        db.importData(data)

        assertEquals(1, db.habitDao().getAllHabits().size)
        assertEquals(1, db.dayDao().getAllDays().size)
        assertEquals(1, db.habitCompletionDao().getAllCompletions().size)
    }

    @Test
    fun importData_emptyBackup_changesNothing() = runBlocking {
        db.habitDao().insertHabit(Habit(name = "Correr"))

        db.importData(backup())

        assertEquals(1, db.habitDao().getAllHabits().size)
    }

    @Test
    fun deleteAllData_removesHabitsDaysAndCompletions() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(name = "Correr")).toInt()
        db.dayDao().insertOrUpdateDay(Day(date, 10))
        db.habitCompletionDao().insertOrUpdateCompletion(HabitCompletion(id, "Correr", date, true))

        db.deleteAllData()

        assertTrue(db.habitDao().getAllHabits().isEmpty())
        assertTrue(db.dayDao().getAllDays().isEmpty())
        assertTrue(db.habitCompletionDao().getAllCompletions().isEmpty())
    }

    @Test
    fun deleteAllData_onEmptyDatabase_doesNotFail() = runBlocking {
        db.deleteAllData()
        assertTrue(db.habitDao().getAllHabits().isEmpty())
    }
}
