package com.app.healthydays.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.dto.ExportData
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room sobre SQLite real del dispositivo (no simulado): persistencia en fichero,
 * restricciones, claves foráneas y transacciones.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseInstrumentedTest {

    private val dbName = "instrumented_test_db"
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun data_persistsAfterClosingAndReopeningTheDatabase() = runBlocking {
        val id = db.habitDao().insertHabit(Habit(name = "Correr", categoryId = "PHYSICAL_HEALTH")).toInt()
        db.dayDao().insertOrUpdateDay(Day("2026-11-02", 75))
        db.habitCompletionDao().insertOrUpdateCompletion(
            HabitCompletion(id, "Correr", "2026-11-02", true, "PHYSICAL_HEALTH", 0)
        )
        db.close()

        db = open()

        assertEquals("Correr", db.habitDao().getHabitById(id)?.name)
        assertEquals(75, db.dayDao().getDayByDate("2026-11-02")?.rating)
        assertTrue(db.habitCompletionDao().getCompletion(id, "2026-11-02")!!.isCompleted)
    }

    @Test
    fun habit_uniqueOnNameAndCategory() = runBlocking {
        assertTrue(db.habitDao().insertHabit(Habit(name = "Leer", categoryId = "LEARNING")) > 0)
        assertEquals(-1L, db.habitDao().insertHabit(Habit(name = "Leer", categoryId = "LEARNING")))
        assertTrue(db.habitDao().insertHabit(Habit(name = "Leer", categoryId = "HOBBIES")) > 0)
        assertEquals(2, db.habitDao().getAllHabits().size)
    }

    @Test
    fun completion_withoutExistingDay_violatesForeignKey() = runBlocking {
        try {
            db.habitCompletionDao().insertOrUpdateCompletion(
                HabitCompletion(1, "A", "2000-01-01", false)
            )
            fail("Debería lanzar SQLiteConstraintException")
        } catch (e: SQLiteConstraintException) {
            // esperado
        }
    }

    @Test
    fun deletingAllDays_cascadesToCompletions() = runBlocking {
        db.dayDao().insertOrUpdateDay(Day("2026-11-02", 0))
        db.habitCompletionDao().insertOrUpdateCompletion(HabitCompletion(1, "A", "2026-11-02", true))

        db.dayDao().deleteAllDays()

        assertTrue(db.habitCompletionDao().getAllCompletions().isEmpty())
    }

    @Test
    fun importData_isAtomic_failureRollsBackEverything() = runBlocking {
        // La finalización apunta a un día que no está en el backup -> viola la FK a mitad de la transacción.
        val broken = ExportData(
            habits = listOf(Habit(1, "A", "HOME", 0), Habit(2, "B", "HOME", 1)),
            days = listOf(Day("2026-11-01", 10)),
            completions = listOf(HabitCompletion(1, "A", "2030-01-01", true, "HOME", 0))
        )

        var failed = false
        try {
            db.importData(broken)
        } catch (e: Exception) {
            failed = true
        }

        assertTrue("La importación inválida debería fallar", failed)
        assertTrue("No debe quedar ningún hábito a medias", db.habitDao().getAllHabits().isEmpty())
        assertTrue("No debe quedar ningún día a medias", db.dayDao().getAllDays().isEmpty())
    }

    @Test
    fun importData_validBackup_isFullyApplied() = runBlocking {
        val data = ExportData(
            habits = listOf(Habit(1, "A", "HOME", 0)),
            days = listOf(Day("2026-11-01", 100)),
            completions = listOf(HabitCompletion(1, "A", "2026-11-01", true, "HOME", 0))
        )

        db.importData(data)

        val habit = db.habitDao().getAllHabits().single()
        assertNotNull(db.habitCompletionDao().getCompletion(habit.habitId, "2026-11-01"))
    }

    @Test
    fun deleteAllData_thenDatabaseIsUsableAgain() = runBlocking {
        db.habitDao().insertHabit(Habit(name = "A"))
        db.dayDao().insertOrUpdateDay(Day("2026-11-01", 10))

        db.deleteAllData()
        assertTrue(db.habitDao().getAllHabits().isEmpty())
        assertTrue(db.dayDao().getAllDays().isEmpty())

        assertTrue(db.habitDao().insertHabit(Habit(name = "A")) > 0)
    }

    @Test
    fun habitsFlow_emitsAgainWhenDataChanges() = runBlocking {
        db.habitDao().insertHabit(Habit(name = "A"))

        // El Flow se recoge en otro hilo y las emisiones llegan por un canal, de forma que
        // el test espera de verdad a la primera emisión antes de modificar los datos.
        val emissions = Channel<List<Habit>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) {
            db.habitDao().getAllHabitsFlow().collect { emissions.send(it) }
        }
        try {
            val first = withTimeout(5000) { emissions.receive() }
            assertEquals(listOf("A"), first.map { it.name })

            db.habitDao().insertHabit(Habit(name = "B"))

            val second = withTimeout(5000) { emissions.receive() }
            assertEquals(listOf("A", "B"), second.map { it.name })
        } finally {
            collector.cancel()
        }
    }
}
