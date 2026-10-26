
package com.app.healthydays.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.app.healthydays.data.dao.DayDao
import com.app.healthydays.data.dao.HabitCompletionDao
import com.app.healthydays.data.dao.HabitDao
import com.app.healthydays.data.dto.ExportData
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion

@Database(
    entities = [Habit::class, Day::class, HabitCompletion::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun dayDao(): DayDao
    abstract fun habitCompletionDao(): HabitCompletionDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "healthy_days_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun importData(data: ExportData) {
        withTransaction {
            val currentHabits = habitDao().getAllHabits()
            val habitIdMap = mutableMapOf<String, Int>()

            // Restaurar los hábitos por nombre, incluyendo categoría y orden.
            for (backupHabit in data.habits) {
                val existingHabit = currentHabits.firstOrNull {
                    it.name == backupHabit.name
                }

                if (existingHabit != null) {
                    val restoredHabit = backupHabit.copy(
                        habitId = existingHabit.habitId
                    )

                    habitDao().updateHabit(restoredHabit)
                    habitIdMap[backupHabit.name] = existingHabit.habitId
                } else {
                    val newId = habitDao().insertHabit(
                        backupHabit.copy(habitId = 0)
                    )

                    if (newId != -1L) {
                        habitIdMap[backupHabit.name] = newId.toInt()
                    }
                }
            }

            // Restaurar los días.
            for (backupDay in data.days) {
                val existingDay = dayDao().getDayByDate(backupDay.date)

                if (existingDay == null) {
                    dayDao().insertOrUpdateDay(backupDay)
                }
            }

            // Restaurar finalizaciones, incluidas las de hábitos eliminados.
            for (backupCompletion in data.completions) {
                val currentHabitId = habitIdMap[backupCompletion.habitName]

                val restoredCompletion = if (currentHabitId != null) {
                    backupCompletion.copy(habitId = currentHabitId)
                } else {
                    backupCompletion
                }

                val existingCompletion = habitCompletionDao().getCompletion(
                    habitId = restoredCompletion.habitId,
                    date = restoredCompletion.date
                )

                if (existingCompletion == null) {
                    habitCompletionDao().insertOrUpdateCompletion(
                        restoredCompletion
                    )
                }
            }
        }
    }

    suspend fun deleteAllData() {
        withTransaction {
            habitCompletionDao().deleteAllCompletions()
            dayDao().deleteAllDays()
            habitDao().deleteAllHabits()
        }
    }
}