package com.app.healthydays.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day")
data class Day(
    @PrimaryKey val date: String,  // yyyy-MM-dd
    val rating: Int                // 0-100
)

