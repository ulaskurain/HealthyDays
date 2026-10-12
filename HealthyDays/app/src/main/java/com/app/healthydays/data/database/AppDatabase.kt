package com.app.healthydays.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.healthydays.data.dao.DayDao
import com.app.healthydays.data.dao.HabitCompletionDao
import com.app.healthydays.data.dao.HabitDao
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
import androidx.room.withTransaction
import com.app.healthydays.data.dto.ExportData

@Database(
    entities = [Habit::class, Day::class, HabitCompletion::class],
    version = 1
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
            // Relacionamos cada nombre del backup con el ID real de esta instalación
            val habitIdMap = mutableMapOf<String, Int>()
            for (backupHabit in data.habits) {
                val existingHabit = currentHabits.firstOrNull {
                    it.name == backupHabit.name
                }

                if (existingHabit != null) {
                    habitIdMap[backupHabit.name] = existingHabit.habitId
                } else {
                    val newId = habitDao().insertHabit(
                        Habit(name = backupHabit.name)
                    )
                    if (newId != -1L) {
                        habitIdMap[backupHabit.name] = newId.toInt()
                    }
                }
            }

            // Importar días sin modificar los que ya existen
            for (backupDay in data.days) {
                val existingDay = dayDao().getDayByDate(backupDay.date)

                if (existingDay == null) {
                    dayDao().insertOrUpdateDay(backupDay)
                }
            }

            // Importar completions usando los IDs de esta instalación
            for (backupCompletion in data.completions) {
                val currentHabitId =
                    habitIdMap[backupCompletion.habitName]
                        ?: continue

                val existingCompletion =
                    habitCompletionDao().getCompletion(
                        habitId = currentHabitId,
                        date = backupCompletion.date
                    )

                if (existingCompletion == null) {
                    habitCompletionDao().insertOrUpdateCompletion(
                        backupCompletion.copy(
                            habitId = currentHabitId
                        )
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
