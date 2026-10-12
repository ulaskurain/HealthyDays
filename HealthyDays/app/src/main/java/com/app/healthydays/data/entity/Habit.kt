package com.app.healthydays.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit",
    indices = [
        Index(value = ["name"], unique = true)
    ])

data class Habit(
    @PrimaryKey(autoGenerate = true)
    val habitId: Int = 0,

    val name: String
)