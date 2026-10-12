package com.app.healthydays.data.dao

import androidx.room.*
import com.app.healthydays.data.dto.HabitItem
import com.app.healthydays.data.entity.HabitCompletion
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitCompletionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCompletion(completion: HabitCompletion)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletions(completions: List<HabitCompletion>)

    @Query("""
        SELECT 
            hc.habitId AS habitId,
            hc.habitName AS name,
            hc.isCompleted AS isCompleted
        FROM habit_completion hc
        INNER JOIN habit h
            ON h.habitId = hc.habitId
        WHERE hc.date = :date
        ORDER BY h.habitId
    """)
    fun getHabitsWithCompletionByDateFlow(date: String): Flow<List<HabitItem>>

    // -------------------------
    // HISTORY
    // -------------------------
    @Query("""
        SELECT
            habitId,
            habitName AS name,
            isCompleted
        FROM habit_completion
        WHERE date = :date
        ORDER BY habitId
    """)
    suspend fun getHabitCompletionsByDate(date: String): List<HabitItem>

    @Query("""
        DELETE FROM habit_completion
        WHERE habitId = :habitId
        AND date >= :today
    """)
    suspend fun deleteFutureCompletions(
        habitId: Int,
        today: String
    )

    @Query("SELECT * FROM habit_completion")
    suspend fun getAllCompletions(): List<HabitCompletion>

    @Query("""
        SELECT * FROM habit_completion
        WHERE habitId = :habitId
        AND date = :date
        LIMIT 1
    """)
    suspend fun getCompletion(habitId: Int, date: String): HabitCompletion?

    @Query("DELETE FROM habit_completion")
    suspend fun deleteAllCompletions()
}

