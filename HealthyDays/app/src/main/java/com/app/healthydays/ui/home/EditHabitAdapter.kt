package com.app.healthydays.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.databinding.ItemEditHabitBinding

class EditHabitAdapter(
    private var habits: List<Habit>,
    private val onDeleteClick: (Habit) -> Unit
) : RecyclerView.Adapter<EditHabitAdapter.EditHabitViewHolder>() {

    class EditHabitViewHolder(
        val binding: ItemEditHabitBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): EditHabitViewHolder {

        val binding = ItemEditHabitBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return EditHabitViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: EditHabitViewHolder,
        position: Int
    ) {
        val habit = habits[position]

        holder.binding.textHabitName.text = habit.name

        holder.binding.buttonDelete.setOnClickListener {
            onDeleteClick(habit)
        }
    }

    override fun getItemCount(): Int = habits.size

    fun updateData(newHabits: List<Habit>) {
        habits = newHabits
        notifyDataSetChanged()
    }
}