
package com.app.healthydays.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.healthydays.R
import com.app.healthydays.data.dto.HabitItem
import com.app.healthydays.data.entity.HabitCategory

class HabitAdapter(
    private var habits: List<HabitItem>,
    private val onCheckChanged: (habitId: Int, isChecked: Boolean) -> Unit,
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

    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imageCategoryIcon)
        val title: TextView = view.findViewById(R.id.textCategoryTitle)
        val arrow: TextView = view.findViewById(R.id.textCategoryArrow)
    }

    inner class HabitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.checkboxCompleted)
        val textHabitName: TextView = view.findViewById(R.id.textHabitName)
    }

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
                inflater.inflate(R.layout.item_habit_category, parent, false)
            )
        } else {
            HabitViewHolder(
                inflater.inflate(R.layout.item_habit, parent, false)
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (val item = items[position]) {
            is ListItem.CategoryHeader -> {
                val category = item.category
                val collapsed = category.id in collapsedCategories

                (holder as CategoryViewHolder).apply {
                    title.setText(category.nameResId)
                    icon.setImageResource(categoryIcon(category))
                    arrow.text = if (collapsed) "+" else "−"

                    itemView.setOnClickListener {
                        if (collapsed) {
                            collapsedCategories.remove(category.id)
                        } else {
                            collapsedCategories.add(category.id)
                        }

                        rebuildItems()
                        notifyDataSetChanged()
                    }
                }
            }

            is ListItem.HabitRow -> {
                val habit = item.habit

                (holder as HabitViewHolder).apply {
                    textHabitName.text = habit.name

                    checkBox.setOnCheckedChangeListener(null)
                    checkBox.isChecked = habit.isCompleted

                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        onCheckChanged(habit.habitId, isChecked)
                    }
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

    @SuppressLint("NotifyDataSetChanged")
    fun updateCategoryOrder(newOrder: List<String>) {
        categoryOrder = newOrder
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

    private fun rebuildItems() {
        val result = mutableListOf<ListItem>()
        val categoriesById = HabitCategory.entries.associateBy { it.id }

        val orderedCategories = (
                categoryOrder + HabitCategory.entries.map { it.id }
                ).distinct().mapNotNull { categoriesById[it] }

        orderedCategories.forEach { category ->
            val categoryHabits = habits
                .filter { it.categoryId == category.id }
                .sortedBy { it.sortOrder }

            if (categoryHabits.isNotEmpty()) {
                result.add(ListItem.CategoryHeader(category))

                if (category.id !in collapsedCategories) {
                    result.addAll(
                        categoryHabits.map { ListItem.HabitRow(it) }
                    )
                }
            }
        }

        val knownCategoryIds = HabitCategory.entries.map { it.id }.toSet()
        val uncategorizedHabits = habits.filter {
            it.categoryId !in knownCategoryIds
        }

        if (uncategorizedHabits.isNotEmpty()) {
            result.add(ListItem.CategoryHeader(HabitCategory.OTHER))

            if (HabitCategory.OTHER.id !in collapsedCategories) {
                result.addAll(
                    uncategorizedHabits.map { ListItem.HabitRow(it) }
                )
            }
        }

        items = result
    }
}
