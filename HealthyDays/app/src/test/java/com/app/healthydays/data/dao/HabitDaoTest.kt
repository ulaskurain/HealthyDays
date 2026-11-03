package com.app.healthydays.data.dao

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.TestUtils
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.entity.Habit
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HabitDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: HabitDao

    @Before
    fun setUp() {
        db = TestUtils.inMemoryDb(ApplicationProvider.getApplicationContext<Context>())
        dao = db.habitDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertHabit_returnsGeneratedId() = runBlocking {
        val id = dao.insertHabit(Habit(name = "Correr"))
        assertTrue(id > 0)
        assertEquals("Correr", dao.getHabitById(id.toInt())?.name)
    }

    @Test
    fun insertHabit_defaultsToOtherCategoryAndZeroOrder() = runBlocking {
        val id = dao.insertHabit(Habit(name = "Leer")).toInt()
        val habit = dao.getHabitById(id)!!
        assertEquals("OTHER", habit.categoryId)
        assertEquals(0, habit.sortOrder)
    }

    @Test
    fun insertHabit_sameNameAndSameCategory_isIgnoredAndReturnsMinusOne() = runBlocking {
        dao.insertHabit(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        val second = dao.insertHabit(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH", sortOrder = 5))

        assertEquals(-1L, second)
        assertEquals(1, dao.getAllHabits().size)
        assertEquals(0, dao.getAllHabits()[0].sortOrder)
    }

    @Test
    fun insertHabit_sameNameButDifferentCategory_isAllowed() = runBlocking {
        val first = dao.insertHabit(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH"))
        val second = dao.insertHabit(Habit(name = "Correr", categoryId = "HOBBIES"))

        assertTrue(first > 0)
        assertTrue(second > 0)
        assertEquals(setOf("PHYSICAL_HEALTH", "HOBBIES"), dao.getAllHabits().map { it.categoryId }.toSet())
    }

    @Test
    fun insertHabit_differentNameSameCategory_isAllowed() = runBlocking {
        assertTrue(dao.insertHabit(Habit(name = "Correr", categoryId = "HOME")) > 0)
        assertTrue(dao.insertHabit(Habit(name = "Nadar", categoryId = "HOME")) > 0)
    }

    @Test
    fun insertHabits_ignoresDuplicates() = runBlocking {
        dao.insertHabits(listOf(Habit(name = "A"), Habit(name = "B"), Habit(name = "A")))
        assertEquals(listOf("A", "B"), dao.getAllHabits().map { it.name })
    }

    @Test
    fun getAllHabits_isOrderedByCategorySortOrderThenId() = runBlocking {
        dao.insertHabit(Habit(name = "Z", categoryId = "SOCIAL", sortOrder = 0))
        dao.insertHabit(Habit(name = "B2", categoryId = "NUTRITION", sortOrder = 1))
        dao.insertHabit(Habit(name = "B1", categoryId = "NUTRITION", sortOrder = 0))
        dao.insertHabit(Habit(name = "B1bis", categoryId = "NUTRITION", sortOrder = 0))

        assertEquals(
            listOf("B1", "B1bis", "B2", "Z"),
            dao.getAllHabits().map { it.name }
        )
    }

    @Test
    fun getAllHabitsFlow_emitsCurrentHabits() = runBlocking {
        dao.insertHabit(Habit(name = "A"))
        dao.insertHabit(Habit(name = "B"))

        assertEquals(listOf("A", "B"), dao.getAllHabitsFlow().first().map { it.name })
    }

    @Test
    fun updateHabitSortOrder_onlyChangesThatHabit() = runBlocking {
        val a = dao.insertHabit(Habit(name = "A")).toInt()
        val b = dao.insertHabit(Habit(name = "B")).toInt()

        dao.updateHabitSortOrder(a, 7)

        assertEquals(7, dao.getHabitById(a)?.sortOrder)
        assertEquals(0, dao.getHabitById(b)?.sortOrder)
    }

    @Test
    fun updateHabit_persistsChanges() = runBlocking {
        val id = dao.insertHabit(Habit(name = "A")).toInt()
        dao.updateHabit(Habit(habitId = id, name = "A2", categoryId = "HOBBIES", sortOrder = 3))

        val habit = dao.getHabitById(id)!!
        assertEquals("A2", habit.name)
        assertEquals("HOBBIES", habit.categoryId)
        assertEquals(3, habit.sortOrder)
    }

    @Test
    fun deleteHabit_removesIt() = runBlocking {
        val id = dao.insertHabit(Habit(name = "A")).toInt()
        dao.deleteHabit(dao.getHabitById(id)!!)

        assertNull(dao.getHabitById(id))
        assertTrue(dao.getAllHabits().isEmpty())
    }

    @Test
    fun getHabitNameById_returnsName() = runBlocking {
        val id = dao.insertHabit(Habit(name = "Meditar")).toInt()
        assertEquals("Meditar", dao.getHabitNameById(id))
    }

    @Test
    fun getHabitById_unknown_returnsNull() = runBlocking {
        assertNull(dao.getHabitById(999))
    }

    @Test
    fun getHabitByName_findsExistingAndNullForUnknown() = runBlocking {
        dao.insertHabit(Habit(name = "Meditar"))
        assertNotNull(dao.getHabitByName("Meditar"))
        assertNull(dao.getHabitByName("meditar-inexistente"))
    }

    @Test
    fun deleteAllHabits_emptiesTable() = runBlocking {
        dao.insertHabits(listOf(Habit(name = "A"), Habit(name = "B")))
        dao.deleteAllHabits()
        assertTrue(dao.getAllHabits().isEmpty())
    }
}
