package com.app.healthydays.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var habitDao: HabitDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        habitDao = database.habitDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertHabitAndRetrieveIt() = runBlocking {
        val habit = Habit(
            name = "Beber agua",
            categoryId = "NUTRITION",
            sortOrder = 0
        )

        val insertedId = habitDao.insertHabit(habit)

        val result = habitDao.getHabitById(insertedId.toInt())

        assertNotEquals(-1L, insertedId)
        assertNotNull(result)
        assertEquals(habit.name, result?.name)
        assertEquals(habit.categoryId, result?.categoryId)
        assertEquals(habit.sortOrder, result?.sortOrder)
    }

    @Test
    fun insertDuplicateHabitWithSameNameAndCategoryIsIgnored() = runBlocking {
        val habit = Habit(
            name = "Beber agua",
            categoryId = "NUTRITION"
        )

        habitDao.insertHabit(habit)
        val secondId = habitDao.insertHabit(habit)

        val habits = habitDao.getAllHabits()

        assertEquals(-1L, secondId)
        assertEquals(1, habits.size)
    }

    @Test
    fun sameHabitNameInDifferentCategoriesIsAllowed() = runBlocking {
        val firstHabit = Habit(
            name = "Leer",
            categoryId = "LEARNING"
        )

        val secondHabit = Habit(
            name = "Leer",
            categoryId = "HOBBIES"
        )

        habitDao.insertHabit(firstHabit)
        habitDao.insertHabit(secondHabit)

        val habits = habitDao.getAllHabits()

        assertEquals(2, habits.size)
    }

    @Test
    fun getHabitByNameAndCategoryReturnsCorrectHabit() = runBlocking {
        val habit = Habit(
            name = "Caminar",
            categoryId = "PHYSICAL_HEALTH"
        )

        habitDao.insertHabit(habit)

        val result = habitDao.getHabitByNameAndCategory(
            "Caminar",
            "PHYSICAL_HEALTH"
        )

        assertNotNull(result)
        assertEquals("Caminar", result?.name)
    }

    @Test
    fun updateHabitChangesItsInformation() = runBlocking {
        val habit = Habit(
            name = "Leer",
            categoryId = "LEARNING"
        )

        val id = habitDao.insertHabit(habit).toInt()

        val updatedHabit = habit.copy(
            habitId = id,
            name = "Leer 20 minutos",
            sortOrder = 2
        )

        habitDao.updateHabit(updatedHabit)

        val result = habitDao.getHabitById(id)

        assertEquals("Leer 20 minutos", result?.name)
        assertEquals(2, result?.sortOrder)
    }

    @Test
    fun deleteHabitRemovesItFromDatabase() = runBlocking {
        val habit = Habit(
            name = "Meditar",
            categoryId = "MENTAL_WELLBEING"
        )

        val id = habitDao.insertHabit(habit).toInt()
        val insertedHabit = habitDao.getHabitById(id)

        assertNotNull(insertedHabit)

        habitDao.deleteHabit(insertedHabit!!)

        val result = habitDao.getHabitById(id)

        assertNull(result)
    }

    @Test
    fun updateHabitSortOrderChangesItsOrder() = runBlocking {
        val habit = Habit(
            name = "Hacer ejercicio",
            categoryId = "PHYSICAL_HEALTH",
            sortOrder = 0
        )

        val id = habitDao.insertHabit(habit).toInt()

        habitDao.updateHabitSortOrder(id, 3)

        val result = habitDao.getHabitById(id)

        assertEquals(3, result?.sortOrder)
    }

    @Test
    fun deleteAllHabitsRemovesEveryHabit() = runBlocking {
        habitDao.insertHabit(Habit(name = "Leer", categoryId = "LEARNING"))
        habitDao.insertHabit(Habit(name = "Meditar", categoryId = "MENTAL_WELLBEING"))

        habitDao.deleteAllHabits()

        val habits = habitDao.getAllHabits()

        assertEquals(0, habits.size)
    }
}
