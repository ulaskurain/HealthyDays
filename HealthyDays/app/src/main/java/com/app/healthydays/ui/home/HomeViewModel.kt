
package com.app.healthydays.ui.home

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.dto.HabitItem
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCategory
import com.app.healthydays.data.entity.HabitCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val habitDao = database.habitDao()
    private val habitCompletionDao = database.habitCompletionDao()
    private val dayDao = database.dayDao()
    private val preferences = application.getSharedPreferences(
        "habit_order",
        Context.MODE_PRIVATE
    )

    fun observeHabits(date: String): StateFlow<List<HabitItem>> {
        return habitCompletionDao
            .getHabitsWithCompletionByDateFlow(date)
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                emptyList()
            )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun initializeDay(date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val day = dayDao.getDayByDate(date)

            if (day == null) {
                dayDao.insertOrUpdateDay(
                    Day(
                        date = date,
                        rating = 0
                    )
                )
            }

            val habits = habitDao.getAllHabits()

            val completions = habits.map { habit ->
                HabitCompletion(
                    habitId = habit.habitId,
                    habitName = habit.name,
                    date = date,
                    isCompleted = false,
                    categoryId = habit.categoryId,
                    sortOrder = habit.sortOrder
                )
            }

            habitCompletionDao.insertCompletions(completions)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateHabitChecked(habitId: Int, isChecked: Boolean) {
        val date = LocalDate.now().toString()

        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitDao.getHabitById(habitId)

            if (habit != null) {
                habitCompletionDao.insertOrUpdateCompletion(
                    HabitCompletion(
                        habitId = habitId,
                        habitName = habit.name,
                        date = date,
                        isCompleted = isChecked,
                        categoryId = habit.categoryId,
                        sortOrder = habit.sortOrder
                    )
                )
            }
        }
    }

    fun getAllHabits(): StateFlow<List<Habit>> {
        return habitDao
            .getAllHabitsFlow()
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                emptyList()
            )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun addHabit(name: String, categoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingHabit = habitDao.getHabitByName(name)
            if (existingHabit != null) return@launch

            val categoryHabits = habitDao.getAllHabits()
                .filter { it.categoryId == categoryId }

            val habitId = habitDao.insertHabit(
                Habit(
                    name = name,
                    categoryId = categoryId,
                    sortOrder = categoryHabits.size
                )
            )

            if (habitId != -1L) {
                habitCompletionDao.insertOrUpdateCompletion(
                    HabitCompletion(
                        habitId = habitId.toInt(),
                        habitName = name,
                        date = LocalDate.now().toString(),
                        isCompleted = false,
                        categoryId = categoryId,
                        sortOrder = categoryHabits.size
                    )
                )
            }
        }
    }

    /**
     * Guarda el nuevo orden de los hábitos de una categoría.
     * La lista recibida debe contener los hábitos en el orden deseado.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun reorderHabits(orderedHabits: List<Habit>) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now().toString()

            orderedHabits.forEach { habit ->
                habitDao.updateHabitSortOrder(
                    habitId = habit.habitId,
                    sortOrder = habit.sortOrder
                )

                habitCompletionDao.updateTodayHabitOrder(
                    habitId = habit.habitId,
                    sortOrder = habit.sortOrder,
                    categoryId = habit.categoryId,
                    today = today
                )
            }
        }
    }

    fun getCategoryOrder(): List<String> {
        val savedOrder = preferences.getString("category_order", null)

        val allCategories = HabitCategory.entries.map { it.id }

        val orderedCategories = savedOrder
            ?.split(",")
            ?.filter { it in allCategories }
            .orEmpty()

        return (orderedCategories + allCategories)
            .distinct()
    }

    fun saveCategoryOrder(categoryIds: List<String>) {
        preferences.edit()
            .putString("category_order", categoryIds.joinToString(","))
            .apply()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateDailyRating(rating: Int) {
        val date = LocalDate.now().toString()

        viewModelScope.launch(Dispatchers.IO) {
            val currentDay = dayDao.getDayByDate(date)

            if (currentDay != null) {
                dayDao.updateDay(currentDay.copy(rating = rating))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun deleteHabit(habit: Habit) {
        val today = LocalDate.now().toString()

        viewModelScope.launch(Dispatchers.IO) {
            habitCompletionDao.deleteFutureCompletions(
                habitId = habit.habitId,
                today = today
            )

            habitDao.deleteHabit(habit)

            val remainingHabits = habitDao.getAllHabits()
                .filter { it.categoryId == habit.categoryId }
                .sortedBy { it.sortOrder }

            remainingHabits.forEachIndexed { index, remainingHabit ->
                habitDao.updateHabitSortOrder(
                    habitId = remainingHabit.habitId,
                    sortOrder = index
                )
            }
        }
    }
}
