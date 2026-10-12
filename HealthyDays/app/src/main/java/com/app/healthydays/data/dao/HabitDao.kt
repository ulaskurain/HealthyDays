package com.app.healthydays.data.dao

import androidx.room.*
import com.app.healthydays.data.entity.Habit
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.internal.Intrinsics.Kotlin

@Dao
interface HabitDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabit(habit: Habit): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabits(habits: List<Habit>)

    @Query("SELECT * FROM habit")
    fun getAllHabitsFlow(): Flow<List<Habit>>

    @Query("SELECT * FROM habit")
    suspend fun getAllHabits(): List<Habit>

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @Query("SELECT name FROM habit WHERE habitId = :habitId")
    suspend fun getHabitNameById(habitId: Int): String

    @Query("SELECT * FROM habit WHERE habitId = :habitId")
    suspend fun getHabitById(habitId: Int): Habit?

    @Query("SELECT * FROM habit WHERE name = :name LIMIT 1")
    suspend fun getHabitByName(name: String): Habit?

    @Query("DELETE FROM habit")
    suspend fun deleteAllHabits()
}
