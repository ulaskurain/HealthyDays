package com.app.healthydays.ui.home

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.dto.HabitItem
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // Obtenemos una única instancia de la base de datos
    private val database = AppDatabase.getDatabase(application)

    private val habitDao = database.habitDao()
    private val habitCompletionDao = database.habitCompletionDao()
    private val dayDao = database.dayDao()


    fun observeHabits(date: String): StateFlow<List<HabitItem>> {
        return habitCompletionDao
            .getHabitsWithCompletionByDateFlow(date)
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                emptyList()
            )
    }


    /**
     * Inicializa los datos del día:
     *
     * 1. Crea el Day si todavía no existe.
     * 2. Crea un HabitCompletion para cada Habit.
     * 3. Todos empiezan con isCompleted = false.
     *
     * Si ya existen, no se duplican porque el DAO utiliza IGNORE.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun initializeDay(date: String) {
        viewModelScope.launch(Dispatchers.IO) {

            // 1. Comprobar si existe el día
            val day = dayDao.getDayByDate(date)

            if (day == null) {
                dayDao.insertOrUpdateDay(
                    Day(
                        date = date,
                        rating = 0
                    )
                )
            }

            // 2. Obtener todos los hábitos actuales
            val habits = habitDao.getAllHabits()

            // 3. Crear un HabitCompletion para cada hábito
            val completions = habits.map { habit ->
                HabitCompletion(
                    habitId = habit.habitId,
                    habitName = habit.name,
                    date = date,
                    isCompleted = false
                )
            }

            // 4. Insertarlos
            // Los que ya existen serán ignorados
            habitCompletionDao.insertCompletions(completions)
        }
    }


    /**
     * Cambia el estado de completado de un hábito para hoy.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun updateHabitChecked(
        habitId: Int,
        isChecked: Boolean
    ) {

        val date = LocalDate.now().toString()

        viewModelScope.launch(Dispatchers.IO) {

            val habit = habitDao.getHabitById(habitId)

            if (habit != null) {

                habitCompletionDao.insertOrUpdateCompletion(
                    HabitCompletion(
                        habitId = habitId,
                        habitName = habit.name,
                        date = date,
                        isCompleted = isChecked
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

    /**
     * Añade un nuevo hábito al catálogo.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun addHabit(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Insertar el hábito y obtener su nuevo ID
            val habitId = habitDao.insertHabit(
                Habit(name = name)
            )
            if (habitId != -1L) {
                // Crear su completion para hoy
                habitCompletionDao.insertOrUpdateCompletion(
                    HabitCompletion(
                        habitId = habitId.toInt(),
                        habitName = name,
                        date = LocalDate.now().toString(),
                        isCompleted = false
                    )
                )
            }
        }
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

    /**
     * Elimina un hábito.
     *
     * Antes de eliminarlo del catálogo, elimina únicamente
     * sus HabitCompletion desde hoy en adelante.
     *
     * Los registros históricos permanecen.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun deleteHabit(habit: Habit) {
        val today = LocalDate.now().toString()
        viewModelScope.launch(Dispatchers.IO) {
            habitCompletionDao.deleteFutureCompletions(
                habitId = habit.habitId,
                today = today
            )
            habitDao.deleteHabit(habit)
        }
    }
}