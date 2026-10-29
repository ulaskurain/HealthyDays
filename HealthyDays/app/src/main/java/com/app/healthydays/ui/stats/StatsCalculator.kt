package com.app.healthydays.ui.stats

import com.app.healthydays.data.dto.DayStat
import com.app.healthydays.data.dto.FilterableHabit
import com.app.healthydays.data.dto.HabitStat
import com.app.healthydays.data.dto.StatsRange
import com.app.healthydays.data.dto.StatsResult
import com.app.healthydays.data.dto.StatsSummary
import com.app.healthydays.data.entity.HabitCompletion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object StatsCalculator {

    private const val DATE_PATTERN = "yyyy-MM-dd"

    /** Hoy en formato yyyy-MM-dd, igual que LocalDate.now().toString(). */
    fun today(): String = format(Calendar.getInstance())

    /** Primer día de los rangos fijos; null para ALL (depende de los datos). */
    fun fixedRangeStart(range: StatsRange, today: String): String? =
        when (range) {
            StatsRange.LAST_7_DAYS -> addDays(today, -6)
            StatsRange.LAST_30_DAYS -> addDays(today, -29)
            StatsRange.ALL -> null
        }

    fun calculate(
        completions: List<HabitCompletion>,
        range: StatsRange,
        today: String,
        excludedHabitIds: Set<Int> = emptySet()
    ): StatsResult {
        // El inicio de "Todo" se calcula con todos los datos, sin filtrar,
        // para que el rango no cambie al activar o desactivar hábitos.
        val start = fixedRangeStart(range, today)
            ?: completions.map { it.date }.filter { it <= today }.minOrNull()
            ?: today

        val inRangeAll = completions.filter { it.date in start..today }

        // Entradas del filtro: se agrupan por categoría y nombre, así un hábito
        // borrado y recreado muchas veces sale una sola vez.
        val availableHabits = inRangeAll
            .groupBy { it.habitId }
            .map { (habitId, rows) -> habitId to rows.maxByOrNull { it.date }!! }
            .groupBy { (_, latest) ->
                latest.categoryId to latest.habitName.trim().lowercase()
            }
            .map { (_, entries) ->
                val newest = entries.maxByOrNull { it.second.date }!!.second
                FilterableHabit(
                    habitIds = entries.map { it.first }.sorted(),
                    name = newest.habitName,
                    categoryId = newest.categoryId,
                    sortOrder = newest.sortOrder
                )
            }
            .sortedWith(
                compareBy<FilterableHabit> { it.sortOrder }
                    .thenBy { it.name.lowercase() }
            )

        // A partir de aquí todo sale de las filas de los hábitos activos.
        val inRange = inRangeAll.filter { it.habitId !in excludedHabitIds }
        val rowsByDate = inRange.groupBy { it.date }

        // Un DayStat por cada día del rango. Sin filas => planned = 0 => sin datos.
        val days = datesBetween(start, today).map { date ->
            val rows = rowsByDate[date].orEmpty()
            DayStat(
                date = date,
                planned = rows.size,
                completed = rows.count { it.isCompleted }
            )
        }

        val percentages = days.mapNotNull { it.percentage }
        val summary = StatsSummary(
            averagePercentage =
            if (percentages.isEmpty()) null else percentages.average().toFloat(),
            perfectDays = days.count { it.hasData && it.completed == it.planned },
            daysWithData = percentages.size
        )

        // Agrupado por habitId: un hábito borrado y recreado con el mismo
        // nombre tiene otro habitId, así que sale como hábito distinto.
        val habits = inRange
            .groupBy { it.habitId }
            .map { (habitId, rows) ->
                val latest = rows.maxByOrNull { it.date }
                HabitStat(
                    habitId = habitId,
                    name = latest?.habitName ?: "",
                    plannedDays = rows.size,
                    completedDays = rows.count { it.isCompleted },
                    categoryId = latest?.categoryId ?: "OTHER"
                )
            }
            .sortedWith(
                compareByDescending<HabitStat> { it.percentage }
                    .thenBy { it.name.lowercase() }
            )

        return StatsResult(
            range = range,
            days = days,
            habits = habits,
            summary = summary,
            availableHabits = availableHabits,
            activeHabitCount = availableHabits.count { habit ->
                habit.habitIds.any { it !in excludedHabitIds }
            }
        )
    }

    // ---- Fechas ----

    private fun newFormat() = SimpleDateFormat(DATE_PATTERN, Locale.US)

    private fun format(calendar: Calendar): String =
        newFormat().format(calendar.time)

    private fun parse(date: String): Calendar {
        val calendar = Calendar.getInstance()
        calendar.time = newFormat().parse(date) ?: calendar.time
        calendar.set(Calendar.HOUR_OF_DAY, 12) // evita problemas con cambios de hora
        return calendar
    }

    private fun addDays(date: String, days: Int): String {
        val calendar = parse(date)
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return format(calendar)
    }

    private fun datesBetween(start: String, end: String): List<String> {
        val result = mutableListOf<String>()
        val calendar = parse(start)
        var current = format(calendar)
        while (current <= end) {
            result.add(current)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            current = format(calendar)
        }
        return result
    }
}