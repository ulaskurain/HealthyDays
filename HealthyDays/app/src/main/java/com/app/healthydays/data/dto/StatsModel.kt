package com.app.healthydays.data.dto

enum class StatsRange { LAST_7_DAYS, LAST_30_DAYS, ALL }

/** Un día del rango. Si planned == 0, el día no tiene datos. */
data class DayStat(
    val date: String,      // yyyy-MM-dd
    val planned: Int,      // hábitos previstos ese día
    val completed: Int     // hábitos completados ese día
) {
    val hasData: Boolean get() = planned > 0
    val percentage: Float? get() =
        if (planned > 0) completed * 100f / planned else null
}

/** Regularidad de un hábito, identificado por habitId (no por nombre). */
data class HabitStat(
    val habitId: Int,
    val name: String,      // último nombre conocido
    val plannedDays: Int,
    val completedDays: Int
) {
    val percentage: Float get() =
        if (plannedDays > 0) completedDays * 100f / plannedDays else 0f
}

data class StatsSummary(
    val averagePercentage: Float?,  // null si no hay ningún día con datos
    val perfectDays: Int,
    val daysWithData: Int
)

data class StatsResult(
    val range: StatsRange,
    val days: List<DayStat>,        // todos los días del rango, también los sin datos
    val habits: List<HabitStat>,
    val summary: StatsSummary
)