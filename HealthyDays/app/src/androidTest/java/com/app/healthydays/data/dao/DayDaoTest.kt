package com.app.healthydays.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.entity.Day
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dayDao: DayDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dayDao = database.dayDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertDayAndRetrieveIt() = runBlocking {
        val day = Day(
            date = "2026-10-28",
            rating = 4
        )

        dayDao.insertOrUpdateDay(day)

        val result = dayDao.getDayByDate("2026-10-28")

        assertNotNull(result)
        assertEquals(day, result)
    }

    @Test
    fun getDayByDateReturnsNullWhenDayDoesNotExist() = runBlocking {
        val result = dayDao.getDayByDate("2026-10-28")

        assertNull(result)
    }

    @Test
    fun updateDayChangesItsRating() = runBlocking {
        val day = Day(
            date = "2026-10-28",
            rating = 2
        )

        dayDao.insertOrUpdateDay(day)
        dayDao.updateDay(day.copy(rating = 5))

        val result = dayDao.getDayByDate("2026-10-28")

        assertNotNull(result)
        assertEquals(5, result?.rating)
    }
}
