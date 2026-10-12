package com.app.healthydays.ui.history

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.healthydays.R
import com.app.healthydays.data.dto.HabitItem
import com.app.healthydays.databinding.ItemHistoryHabitBinding

class HistoryAdapter(private var habits: List<HabitItem>):
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(
        val binding: ItemHistoryHabitBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryHabitBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val habit = habits[position]
        holder.binding.textHabitName.text = habit.name

        if (habit.isCompleted) {
            holder.binding.imageCompletionStatus.setImageResource(
                R.drawable.icon_completed
            )
            holder.binding.imageCompletionStatus.setColorFilter(
                ContextCompat.getColor(
                    holder.itemView.context,
                    android.R.color.holo_green_dark
                )
            )
        } else {
            holder.binding.imageCompletionStatus.setImageResource(
                R.drawable.icon_uncompleted
            )
            holder.binding.imageCompletionStatus.setColorFilter(
                ContextCompat.getColor(
                    holder.itemView.context,
                    android.R.color.holo_red_dark
                )
            )
        }
    }

    override fun getItemCount(): Int = habits.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newHabits: List<HabitItem>) {
        habits = newHabits
        notifyDataSetChanged()
    }
}