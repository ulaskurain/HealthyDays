package com.app.healthydays.ui.stats

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.dto.StatsRange
import com.app.healthydays.data.dto.StatsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val habitCompletionDao =
        AppDatabase.getDatabase(application).habitCompletionDao()

    private val _result = MutableStateFlow<StatsResult?>(null)
    val result: StateFlow<StatsResult?> = _result

    var selectedRange: StatsRange = StatsRange.LAST_7_DAYS
        private set

    /** habitId of the disabled habits. Empty = all active. */
    var excludedHabitIds: Set<Int> = emptySet()
        private set

    private var loadJob: Job? = null

    private val preferences = application.getSharedPreferences(
        "habit_order",
        Context.MODE_PRIVATE
    )

    fun load(range: StatsRange) {
        selectedRange = range
        val excluded = excludedHabitIds // copy of this charge
        loadJob?.cancel() // if the user changes the range, cancel the previous load
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val today = StatsCalculator.today()
            val from = StatsCalculator.fixedRangeStart(range, today)

            val completions = if (from == null) {
                habitCompletionDao.getAllCompletions()
            } else {
                habitCompletionDao.getCompletionsFrom(from)
            }

            _result.value = StatsCalculator.calculate(completions, range, today, excluded)
        }
    }

    /** Order of the categories defined in Edit Habits (the same as Home). */
    fun getCategoryOrder(): List<String> {
        val allCategories = com.app.healthydays.data.entity.HabitCategory.entries.map { it.id }

        val saved = preferences.getString("category_order", null)
            ?.split(",")
            ?.filter { it in allCategories }
            .orEmpty()

        return (saved + allCategories).distinct()
    }

    /** Save the filter and recalculate with the current range. */
    fun setExcludedHabits(ids: Set<Int>) {
        excludedHabitIds = ids
        load(selectedRange)
    }
}