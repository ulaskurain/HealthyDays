package com.app.healthydays.ui.history

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.healthydays.R
import com.app.healthydays.data.dto.HabitItem
import com.app.healthydays.data.entity.HabitCategory
import com.app.healthydays.databinding.ItemHabitCategoryBinding
import com.app.healthydays.databinding.ItemHistoryHabitBinding

class HistoryAdapter(
    private var habits: List<HabitItem>,
    private var categoryOrder: List<String> = HabitCategory.entries.map { it.id }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class ListItem {
        data class CategoryHeader(val category: HabitCategory) : ListItem()
        data class HabitRow(val habit: HabitItem) : ListItem()
    }

    private val collapsedCategories = mutableSetOf<String>()
    private var items: List<ListItem> = emptyList()

    companion object {
        private const val TYPE_CATEGORY = 0
        private const val TYPE_HABIT = 1
    }

    init {
        rebuildItems()
    }

    inner class CategoryViewHolder(
        val binding: ItemHabitCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        val icon: ImageView = binding.imageCategoryIcon
        val title: TextView = binding.textCategoryTitle
        val arrow: TextView = binding.textCategoryArrow
    }

    inner class HabitViewHolder(
        val binding: ItemHistoryHabitBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is ListItem.CategoryHeader -> TYPE_CATEGORY
            is ListItem.HabitRow -> TYPE_HABIT
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == TYPE_CATEGORY) {
            CategoryViewHolder(
                ItemHabitCategoryBinding.inflate(
                    inflater,
                    parent,
                    false
                )
            )
        } else {
            HabitViewHolder(
                ItemHistoryHabitBinding.inflate(
                    inflater,
                    parent,
                    false
                )
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (val item = items[position]) {
            is ListItem.CategoryHeader -> {
                val category = item.category
                val collapsed = category.id in collapsedCategories
                val categoryHolder = holder as CategoryViewHolder

                categoryHolder.title.setText(category.nameResId)
                categoryHolder.icon.setImageResource(categoryIcon(category))
                categoryHolder.arrow.text = if (collapsed) "+" else "−"

                categoryHolder.itemView.setOnClickListener {
                    if (category.id in collapsedCategories) {
                        collapsedCategories.remove(category.id)
                    } else {
                        collapsedCategories.add(category.id)
                    }

                    rebuildItems()
                    notifyDataSetChanged()
                }
            }

            is ListItem.HabitRow -> {
                val habitHolder = holder as HabitViewHolder
                val habit = item.habit
                val binding = habitHolder.binding

                binding.textHabitName.text = habit.name

                if (habit.isCompleted) {
                    binding.imageCompletionStatus.setImageResource(
                        R.drawable.icon_completed
                    )
                    binding.imageCompletionStatus.setColorFilter(
                        ContextCompat.getColor(
                            holder.itemView.context,
                            android.R.color.holo_green_dark
                        )
                    )
                } else {
                    binding.imageCompletionStatus.setImageResource(
                        R.drawable.icon_uncompleted
                    )
                    binding.imageCompletionStatus.setColorFilter(
                        ContextCompat.getColor(
                            holder.itemView.context,
                            android.R.color.holo_red_dark
                        )
                    )
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newHabits: List<HabitItem>) {
        habits = newHabits
        rebuildItems()
        notifyDataSetChanged()
    }

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
    fun updateCategoryOrder(newOrder: List<String>) {
        categoryOrder = newOrder
        rebuildItems()
        notifyDataSetChanged()
    }

    private fun rebuildItems() {
        val result = mutableListOf<ListItem>()
        val categoriesById = HabitCategory.entries.associateBy { it.id }

        val orderedCategoryIds =
            (categoryOrder + HabitCategory.entries.map { it.id }).distinct()

        orderedCategoryIds.forEach { categoryId ->
            val category = categoriesById[categoryId] ?: return@forEach

            val categoryHabits = habits
                .filter { it.categoryId == categoryId }
                .sortedWith(compareBy<HabitItem> { it.sortOrder }.thenBy { it.habitId })

            if (categoryHabits.isNotEmpty()) {
                result.add(ListItem.CategoryHeader(category))

                if (category.id !in collapsedCategories) {
                    result.addAll(categoryHabits.map { ListItem.HabitRow(it) })
                }
            }
        }

        val knownCategoryIds = HabitCategory.entries.map { it.id }.toSet()
        val uncategorizedHabits = habits
            .filter { it.categoryId !in knownCategoryIds }
            .sortedWith(compareBy<HabitItem> { it.sortOrder }.thenBy { it.habitId })

        if (uncategorizedHabits.isNotEmpty()) {
            result.add(ListItem.CategoryHeader(HabitCategory.OTHER))

            if (HabitCategory.OTHER.id !in collapsedCategories) {
                result.addAll(uncategorizedHabits.map { ListItem.HabitRow(it) })
            }
        }

        items = result
    }
}