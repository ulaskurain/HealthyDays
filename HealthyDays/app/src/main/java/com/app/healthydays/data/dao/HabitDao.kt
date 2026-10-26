
package com.app.healthydays.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.healthydays.data.entity.Habit
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabit(habit: Habit): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabits(habits: List<Habit>)

    @Query("""
        SELECT * FROM habit
        ORDER BY categoryId, sortOrder, habitId
    """)
    fun getAllHabitsFlow(): Flow<List<Habit>>

    @Query("""
        SELECT * FROM habit
        ORDER BY categoryId, sortOrder, habitId
    """)
    suspend fun getAllHabits(): List<Habit>

    @Query("""
        UPDATE habit
        SET sortOrder = :sortOrder
        WHERE habitId = :habitId
    """)
    suspend fun updateHabitSortOrder(habitId: Int, sortOrder: Int)

    @Update
    suspend fun updateHabit(habit: Habit)

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
