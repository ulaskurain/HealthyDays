package com.app.healthydays.ui.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.TestUtils
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.HabitCompletion
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
class HistoryViewModelTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        TestUtils.resetDatabaseSingleton()
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(app)
        runBlocking { db.deleteAllData() }
        viewModel = HistoryViewModel(app)
    }

    @After
    fun tearDown() {
        TestUtils.resetDatabaseSingleton()
    }

    @Test
    fun initialState_isEmpty() {
        assertNull(viewModel.selectedDay.value)
        assertTrue(viewModel.habits.value.isEmpty())
        assertTrue(viewModel.days.value.isEmpty())
    }

    @Test
    fun loadDays_exposesAllRegisteredDays() {
        runBlocking {
            db.dayDao().insertOrUpdateDay(Day("2026-11-01", 10))
            db.dayDao().insertOrUpdateDay(Day("2026-11-02", 90))
        }

        viewModel.loadDays()

        TestUtils.waitUntil { viewModel.days.value.size == 2 }
        assertEquals(setOf("2026-11-01", "2026-11-02"), viewModel.days.value.map { it.date }.toSet())
    }

    @Test
    fun loadDay_exposesRatingAndHabitsOfThatDay() {
        runBlocking {
            db.dayDao().insertOrUpdateDay(Day("2026-11-01", 50))
            db.habitCompletionDao().insertOrUpdateCompletion(
                HabitCompletion(1, "A", "2026-11-01", true, "SOCIAL", 0)
            )
            db.habitCompletionDao().insertOrUpdateCompletion(
                HabitCompletion(2, "B", "2026-11-01", false, "SOCIAL", 1)
            )
        }

        viewModel.loadDay("2026-11-01")

        TestUtils.waitUntil { viewModel.habits.value.size == 2 }
        assertEquals(50, viewModel.selectedDay.value?.rating)
        assertEquals(listOf("A", "B"), viewModel.habits.value.map { it.name })
        assertEquals(listOf(true, false), viewModel.habits.value.map { it.isCompleted })
    }

    @Test
    fun loadDay_withoutData_clearsSelection() {
        runBlocking {
            db.dayDao().insertOrUpdateDay(Day("2026-11-01", 50))
            db.habitCompletionDao().insertOrUpdateCompletion(HabitCompletion(1, "A", "2026-11-01", true))
        }
        viewModel.loadDay("2026-11-01")
        TestUtils.waitUntil { viewModel.habits.value.isNotEmpty() }

        viewModel.loadDay("2000-01-01")

        TestUtils.waitUntil { viewModel.habits.value.isEmpty() }
        assertNull(viewModel.selectedDay.value)
    }
}
