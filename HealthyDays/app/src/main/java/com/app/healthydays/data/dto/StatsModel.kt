package com.app.healthydays.data.dto

enum class StatsRange { LAST_7_DAYS, LAST_30_DAYS, ALL }

/** One day in the range. If planned == 0, the day has no data. */
data class DayStat(
    val date: String,      // yyyy-MM-dd
    val planned: Int,      // habits expected that day
    val completed: Int     // habits completed that day
) {
    val hasData: Boolean get() = planned > 0
    val percentage: Float? get() =
        if (planned > 0) completed * 100f / planned else null
}

/** Regularity of a habit, identified by habitId (not by name). */
data class HabitStat(
    val habitId: Int,
    val name: String,      // last known name
    val plannedDays: Int,
    val completedDays: Int,
    val categoryId: String = "OTHER"
) {
    val percentage: Float get() =
        if (plannedDays > 0) completedDays * 100f / plannedDays else 0f
}

data class StatsSummary(
    val averagePercentage: Float?,  // null if there is no day with data
    val perfectDays: Int,
    val daysWithData: Int
)

data class StatsResult(
    val range: StatsRange,
    val days: List<DayStat>,        // all the days in the range, including those without data
    val habits: List<HabitStat>,    // only the active habits
    val summary: StatsSummary,
    val availableHabits: List<FilterableHabit> = emptyList(), // all, active or not
    val activeHabitCount: Int = 0
)

/** Filter entrance: groups the habitId that share name and category. */
data class FilterableHabit(
    val habitIds: List<Int>,
    val name: String,       // last known name
    val categoryId: String, // id of HabitCategory (for example "NUTRITION")
    val sortOrder: Int      // order inside the category, to show in Home
)