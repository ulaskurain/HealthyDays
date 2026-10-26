package com.app.healthydays.data.dto

data class HabitItem(
    val habitId: Int,
    val name: String,
    val isCompleted: Boolean,
    val categoryId: String,
    val sortOrder: Int = 0
)