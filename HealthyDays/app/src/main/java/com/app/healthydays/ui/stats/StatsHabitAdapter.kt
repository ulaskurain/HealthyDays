package com.app.healthydays.ui.stats

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.healthydays.R
import com.app.healthydays.data.dto.HabitStat
import com.app.healthydays.data.entity.HabitCategory
import com.app.healthydays.databinding.ItemHabitStatBinding
import java.util.Locale
import kotlin.math.roundToInt

class StatsHabitAdapter(
    private var habits: List<HabitStat> = emptyList()
) : RecyclerView.Adapter<StatsHabitAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHabitStatBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHabitStatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val habit = habits[position]
        val binding = holder.binding
        val context = holder.itemView.context
        val locale = Locale.getDefault()
        val percentage = habit.percentage.roundToInt()

        binding.textHabitName.text = habit.name
        val category = HabitCategory.entries.firstOrNull { it.id == habit.categoryId }
            ?: HabitCategory.OTHER
        binding.imageHabitCategory.setImageResource(categoryIcon(category))
        binding.textHabitPercentage.text = String.format(locale, "%d%%", percentage)
        binding.textHabitDays.text = context.getString(
            R.string.stats_habit_days,
            habit.completedDays,
            habit.plannedDays
        )

        binding.progressHabit.progress = percentage
        binding.progressHabit.progressTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.stats_bar_fill)
        )
        binding.progressHabit.progressBackgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.stats_bar_background)
        )
        binding.progressHabit.progressBackgroundTintMode = PorterDuff.Mode.SRC_IN
    }

    override fun getItemCount(): Int = habits.size

    private fun categoryIcon(category: HabitCategory): Int =
        when (category) {
            HabitCategory.PHYSICAL_HEALTH -> R.drawable.ic_health
            HabitCategory.NUTRITION -> R.drawable.ic_nutrition
            HabitCategory.MENTAL_WELLBEING -> R.drawable.ic_mental
            HabitCategory.LEARNING -> R.drawable.ic_learning
            HabitCategory.SOCIAL -> R.drawable.ic_social
            HabitCategory.PRODUCTIVITY -> R.drawable.ic_work
            HabitCategory.HOME -> R.drawable.ic_home
            HabitCategory.HOBBIES -> R.drawable.ic_hobbies
            HabitCategory.OTHER -> R.drawable.ic_others
        }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newHabits: List<HabitStat>) {
        habits = newHabits
        notifyDataSetChanged()
    }
}