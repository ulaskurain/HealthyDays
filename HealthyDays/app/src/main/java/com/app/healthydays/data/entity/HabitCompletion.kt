package com.app.healthydays.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "habit_completion",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = Day::class,
            parentColumns = ["date"],
            childColumns = ["date"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("habitId"),
        Index("date")
    ]
)
data class HabitCompletion(
    val habitId: Int,
    val habitName: String,
    val date: String,
    val isCompleted: Boolean,

    @ColumnInfo(defaultValue = "'OTHER'")
    val categoryId: String = "OTHER",

    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0
)