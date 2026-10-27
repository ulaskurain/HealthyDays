package com.app.healthydays.ui.stats

import android.app.Application
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

    private var loadJob: Job? = null

    fun load(range: StatsRange) {
        selectedRange = range
        loadJob?.cancel() // si el usuario cambia de rango rápido, gana el último
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val today = StatsCalculator.today()
            val from = StatsCalculator.fixedRangeStart(range, today)

            val completions = if (from == null) {
                habitCompletionDao.getAllCompletions()
            } else {
                habitCompletionDao.getCompletionsFrom(from)
            }

            _result.value = StatsCalculator.calculate(completions, range, today)
        }
    }
}