
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
            hc.isCompleted AS isCompleted,
            h.categoryId AS categoryId,
            h.sortOrder AS sortOrder
        FROM habit_completion hc
        INNER JOIN habit h ON h.habitId = hc.habitId
        WHERE hc.date = :date
        ORDER BY h.sortOrder, h.habitId
    """)
    fun getHabitsWithCompletionByDateFlow(date: String): Flow<List<HabitItem>>

    @Query("""
        SELECT
            hc.habitId AS habitId,
            hc.habitName AS name,
            hc.isCompleted AS isCompleted,
            hc.categoryId AS categoryId,
            hc.sortOrder AS sortOrder
        FROM habit_completion hc
        WHERE hc.date = :date
        ORDER BY hc.sortOrder, hc.habitId
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

    @Query("""
        UPDATE habit_completion
        SET sortOrder = :sortOrder,
            categoryId = :categoryId
        WHERE habitId = :habitId
          AND date = :today
    """)
    suspend fun updateTodayHabitOrder(
        habitId: Int,
        sortOrder: Int,
        categoryId: String,
        today: String
    )

    @Query("DELETE FROM habit_completion")
    suspend fun deleteAllCompletions()
}
