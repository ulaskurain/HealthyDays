package com.app.healthydays.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.dto.HabitItem
import com.app.healthydays.data.entity.Day
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dayDao = database.dayDao()
    private val habitCompletionDao = database.habitCompletionDao()

    private val _selectedDay = MutableStateFlow<Day?>(null)
    val selectedDay: StateFlow<Day?> = _selectedDay

    private val _habits = MutableStateFlow<List<HabitItem>>(emptyList())
    val habits: StateFlow<List<HabitItem>> = _habits

    private val _days = MutableStateFlow<List<Day>>(emptyList())
    val days: StateFlow<List<Day>> = _days

    fun loadDay(date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val day = dayDao.getDayByDate(date)

            val completions = habitCompletionDao.getHabitCompletionsByDate(date)
            _selectedDay.value = day
            _habits.value = completions
        }
    }

    fun loadDays() {
        viewModelScope.launch(Dispatchers.IO) {
            _days.value = dayDao.getAllDays()
        }
    }
}