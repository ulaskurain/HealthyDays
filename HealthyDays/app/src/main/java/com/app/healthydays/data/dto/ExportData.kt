package com.app.healthydays.data.dto

import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion

data class ExportData(
    val habits: List<Habit>,
    val days: List<Day>,
    val completions: List<HabitCompletion>
)