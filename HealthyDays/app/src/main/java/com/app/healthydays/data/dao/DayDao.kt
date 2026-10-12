package com.app.healthydays.data.dao

import androidx.room.*
import com.app.healthydays.data.entity.Day

@Dao
interface DayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDay(day: Day)

    @Update
    suspend fun updateDay(day: Day)

    @Query("SELECT * FROM day WHERE date = :date")
    suspend fun getDayByDate(date: String): Day?

    @Query("SELECT * FROM day")
    suspend fun getAllDays(): List<Day>

    @Query("DELETE FROM day")
    suspend fun deleteAllDays()
}

