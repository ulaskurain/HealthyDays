package com.app.healthydays.data.dao

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.TestUtils
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.entity.Day
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DayDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DayDao

    @Before
    fun setUp() {
        db = TestUtils.inMemoryDb(ApplicationProvider.getApplicationContext<Context>())
        dao = db.dayDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getDayByDate_unknown_returnsNull() = runBlocking {
        assertNull(dao.getDayByDate("2026-01-01"))
    }

    @Test
    fun insertOrUpdateDay_insertsNewDay() = runBlocking {
        dao.insertOrUpdateDay(Day("2026-11-02", 40))
        assertEquals(Day("2026-11-02", 40), dao.getDayByDate("2026-11-02"))
    }

    @Test
    fun insertOrUpdateDay_replacesExistingDay() = runBlocking {
        dao.insertOrUpdateDay(Day("2026-11-02", 40))
        dao.insertOrUpdateDay(Day("2026-11-02", 90))

        assertEquals(1, dao.getAllDays().size)
        assertEquals(90, dao.getDayByDate("2026-11-02")?.rating)
    }

    @Test
    fun updateDay_changesRating() = runBlocking {
        dao.insertOrUpdateDay(Day("2026-11-02", 10))
        dao.updateDay(Day("2026-11-02", 55))
        assertEquals(55, dao.getDayByDate("2026-11-02")?.rating)
    }

    @Test
    fun updateDay_unknownDay_doesNotInsert() = runBlocking {
        dao.updateDay(Day("2026-11-02", 55))
        assertNull(dao.getDayByDate("2026-11-02"))
    }

    @Test
    fun getAllDays_returnsEveryDay() = runBlocking {
        dao.insertOrUpdateDay(Day("2026-11-01", 10))
        dao.insertOrUpdateDay(Day("2026-11-02", 20))
        dao.insertOrUpdateDay(Day("2026-11-03", 30))

        assertEquals(
            setOf("2026-11-01", "2026-11-02", "2026-11-03"),
            dao.getAllDays().map { it.date }.toSet()
        )
    }

    @Test
    fun deleteAllDays_emptiesTable() = runBlocking {
        dao.insertOrUpdateDay(Day("2026-11-01", 10))
        dao.insertOrUpdateDay(Day("2026-11-02", 20))
        dao.deleteAllDays()
        assertTrue(dao.getAllDays().isEmpty())
    }
}
