package com.app.healthydays.data.dao

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.TestUtils
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
import kotlinx.coroutines.flow.first
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
class HabitCompletionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: HabitCompletionDao

    private val today = "2026-11-02"
    private val yesterday = "2026-11-01"
    private val tomorrow = "2026-11-03"

    @Before
    fun setUp() {
        db = TestUtils.inMemoryDb(ApplicationProvider.getApplicationContext<Context>())
        dao = db.habitCompletionDao()
        runBlocking {
            listOf(yesterday, today, tomorrow).forEach { db.dayDao().insertOrUpdateDay(Day(it, 0)) }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun completion(
        habitId: Int,
        date: String = today,
        done: Boolean = false,
        name: String = "h$habitId",
        category: String = "OTHER",
        order: Int = 0
    ) = HabitCompletion(habitId, name, date, done, category, order)

    @Test
    fun insertOrUpdateCompletion_replacesExisting() = runBlocking {
        dao.insertOrUpdateCompletion(completion(1, done = false))
        dao.insertOrUpdateCompletion(completion(1, done = true))

        assertEquals(1, dao.getAllCompletions().size)
        assertTrue(dao.getCompletion(1, today)!!.isCompleted)
    }

    @Test
    fun insertCompletions_ignoresExistingRowsAndKeepsTheirState() = runBlocking {
        dao.insertOrUpdateCompletion(completion(1, done = true))

        dao.insertCompletions(listOf(completion(1, done = false), completion(2, done = false)))

        assertEquals(2, dao.getAllCompletions().size)
        assertTrue("No debe sobrescribir un hábito ya marcado", dao.getCompletion(1, today)!!.isCompleted)
    }

    @Test
    fun insertCompletion_withoutExistingDay_violatesForeignKey() {
        var thrown = false
        try {
            runBlocking { dao.insertOrUpdateCompletion(completion(1, date = "1999-01-01")) }
        } catch (e: SQLiteConstraintException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun deletingDays_cascadesToCompletions() = runBlocking {
        dao.insertOrUpdateCompletion(completion(1))
        dao.insertOrUpdateCompletion(completion(2, date = yesterday))

        db.dayDao().deleteAllDays()

        assertTrue(dao.getAllCompletions().isEmpty())
    }

    @Test
    fun getCompletion_returnsNullWhenMissing() = runBlocking {
        assertNull(dao.getCompletion(1, today))
        dao.insertOrUpdateCompletion(completion(1))
        assertNotNull(dao.getCompletion(1, today))
        assertNull(dao.getCompletion(1, yesterday))
        assertNull(dao.getCompletion(2, today))
    }

    @Test
    fun getHabitCompletionsByDate_usesSnapshotColumnsAndOrder() = runBlocking {
        // No hace falta que el hábito exista: el historial usa los datos guardados en la finalización.
        dao.insertOrUpdateCompletion(completion(2, name = "B", category = "SOCIAL", order = 1, done = true))
        dao.insertOrUpdateCompletion(completion(1, name = "A", category = "SOCIAL", order = 0))
        dao.insertOrUpdateCompletion(completion(3, date = yesterday, name = "Otro día"))

        val items = dao.getHabitCompletionsByDate(today)

        assertEquals(listOf("A", "B"), items.map { it.name })
        assertEquals(listOf(false, true), items.map { it.isCompleted })
        assertEquals(listOf("SOCIAL", "SOCIAL"), items.map { it.categoryId })
    }

    @Test
    fun getHabitCompletionsByDate_emptyDay_returnsEmptyList() = runBlocking {
        assertTrue(dao.getHabitCompletionsByDate(today).isEmpty())
    }

    @Test
    fun getHabitsWithCompletionByDateFlow_joinsWithHabitTable() = runBlocking {
        val a = db.habitDao().insertHabit(Habit(name = "A", categoryId = "LEARNING", sortOrder = 1)).toInt()
        val b = db.habitDao().insertHabit(Habit(name = "B", categoryId = "LEARNING", sortOrder = 0)).toInt()
        dao.insertOrUpdateCompletion(completion(a, name = "A", done = true))
        dao.insertOrUpdateCompletion(completion(b, name = "B"))
        // Finalización de un hábito que ya no existe: el INNER JOIN debe excluirla.
        dao.insertOrUpdateCompletion(completion(999, name = "Fantasma"))

        val items = dao.getHabitsWithCompletionByDateFlow(today).first()

        assertEquals(listOf("B", "A"), items.map { it.name })
        assertEquals("LEARNING", items[0].categoryId)
        assertFalse(items[0].isCompleted)
        assertTrue(items[1].isCompleted)
    }

    @Test
    fun deleteFutureCompletions_removesTodayAndLaterOfThatHabitOnly() = runBlocking {
        dao.insertOrUpdateCompletion(completion(1, date = yesterday))
        dao.insertOrUpdateCompletion(completion(1, date = today))
        dao.insertOrUpdateCompletion(completion(1, date = tomorrow))
        dao.insertOrUpdateCompletion(completion(2, date = today))

        dao.deleteFutureCompletions(habitId = 1, today = today)

        assertNotNull("El histórico pasado se conserva", dao.getCompletion(1, yesterday))
        assertNull(dao.getCompletion(1, today))
        assertNull(dao.getCompletion(1, tomorrow))
        assertNotNull("Otros hábitos no se tocan", dao.getCompletion(2, today))
    }

    @Test
    fun updateTodayHabitOrder_onlyAffectsGivenDate() = runBlocking {
        dao.insertOrUpdateCompletion(completion(1, date = yesterday, category = "OTHER", order = 0))
        dao.insertOrUpdateCompletion(completion(1, date = today, category = "OTHER", order = 0))

        dao.updateTodayHabitOrder(habitId = 1, sortOrder = 5, categoryId = "HOME", today = today)

        val updated = dao.getCompletion(1, today)!!
        assertEquals(5, updated.sortOrder)
        assertEquals("HOME", updated.categoryId)
        val old = dao.getCompletion(1, yesterday)!!
        assertEquals(0, old.sortOrder)
        assertEquals("OTHER", old.categoryId)
    }

    @Test
    fun deleteAllCompletions_emptiesTable() = runBlocking {
        dao.insertOrUpdateCompletion(completion(1))
        dao.insertOrUpdateCompletion(completion(2))
        dao.deleteAllCompletions()
        assertTrue(dao.getAllCompletions().isEmpty())
    }
}
