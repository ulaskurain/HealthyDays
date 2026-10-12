package com.app.healthydays.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.healthydays.R
import com.app.healthydays.data.dto.HabitItem
import com.app.healthydays.data.entity.HabitCompletion

class HabitAdapter(
    private var habits: List<HabitItem>,
    private val onCheckChanged: (habitId: Int, isChecked: Boolean) -> Unit
) : RecyclerView.Adapter<HabitAdapter.HabitViewHolder>() {

    inner class HabitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.checkboxCompleted)
        val textHabitName: TextView = view.findViewById(R.id.textHabitName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_habit, parent, false)
        return HabitViewHolder(view)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        val habit = habits[position]

        holder.textHabitName.text = habit.name

        // Quitar el listener anterior
        holder.checkBox.setOnCheckedChangeListener(null)

        // Actualizar el estado visual
        holder.checkBox.isChecked = habit.isCompleted

        // Volver a poner el listener
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            onCheckChanged(habit.habitId, isChecked)
        }
    }

    override fun getItemCount() = habits.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newHabits: List<HabitItem>) {
        habits = newHabits
        notifyDataSetChanged()
    }
}

