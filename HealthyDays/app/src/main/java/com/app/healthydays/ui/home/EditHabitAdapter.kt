package com.app.healthydays.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.healthydays.R
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCategory
import com.app.healthydays.databinding.ItemEditCategoryBinding
import com.app.healthydays.databinding.ItemEditHabitBinding

class EditHabitAdapter(
    private var habits: List<Habit>,
    private val onDeleteClick: (Habit) -> Unit,
    categoryOrder: List<String> = HabitCategory.entries.map { it.id }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var orderedCategoryIds = categoryOrder.toMutableList()

    private sealed class ListItem {
        data class CategoryHeader(val category: HabitCategory) : ListItem()
        data class HabitRow(val habit: Habit) : ListItem()
    }

    private var items: MutableList<ListItem> = mutableListOf()

    companion object {
        private const val TYPE_CATEGORY = 0
        private const val TYPE_HABIT = 1
    }

    init {
        rebuildItems()
    }

    class CategoryViewHolder(val binding: ItemEditCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    class HabitViewHolder(val binding: ItemEditHabitBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is ListItem.CategoryHeader -> TYPE_CATEGORY
            is ListItem.HabitRow -> TYPE_HABIT
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CATEGORY) {
            CategoryViewHolder(ItemEditCategoryBinding.inflate(inflater, parent, false))
        } else {
            HabitViewHolder(ItemEditHabitBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.CategoryHeader -> {
                val binding = (holder as CategoryViewHolder).binding
                binding.textCategoryTitle.setText(item.category.nameResId)
                binding.imageCategoryIcon.setImageResource(categoryIcon(item.category))
            }

            is ListItem.HabitRow -> {
                val binding = (holder as HabitViewHolder).binding
                val habit = item.habit

                binding.textHabitName.text = habit.name

                binding.buttonDelete.setOnClickListener {
                    onDeleteClick(habit)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newHabits: List<Habit>) {
        habits = newHabits
        rebuildItems()
        notifyDataSetChanged()
    }

    fun getCurrentHabits(): List<Habit> = habits

    fun getCategoryOrder(): List<String> = orderedCategoryIds.toList()

    @SuppressLint("NotifyDataSetChanged")
    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in items.indices || toPosition !in items.indices) {
            return false
        }
        if (fromPosition == toPosition) return false

        val fromItem = items[fromPosition]
        val toItem = items[toPosition]


        // Mover una categoría completa.
        if (fromItem is ListItem.CategoryHeader) {
            val fromCategoryId = fromItem.category.id
            val targetCategoryId = when (toItem) {
                is ListItem.CategoryHeader -> toItem.category.id
                is ListItem.HabitRow -> toItem.habit.categoryId
            }

            val fromCategoryIndex = orderedCategoryIds.indexOf(fromCategoryId)
            val toCategoryIndex = orderedCategoryIds.indexOf(targetCategoryId)

            if (fromCategoryIndex < 0 ||
                toCategoryIndex < 0 ||
                fromCategoryIndex == toCategoryIndex
            ) return false

            // Actualizar el orden lógico.
            val movedCategory = orderedCategoryIds.removeAt(fromCategoryIndex)
            orderedCategoryIds.add(toCategoryIndex, movedCategory)

            // Encontrar las posiciones reales del bloque.
            val block = items.filter { item ->
                when (item) {
                    is ListItem.CategoryHeader -> item.category.id == fromCategoryId
                    is ListItem.HabitRow -> item.habit.categoryId == fromCategoryId
                }
            }

            if (block.isEmpty()) return false

            val oldStart = items.indexOf(block.first())
            val blockSize = block.size

            // Retirar el bloque.
            repeat(blockSize) {
                items.removeAt(oldStart)
            }

            // Insertarlo junto a la categoría destino.
            val targetStart = items.indexOfFirst { item ->
                when (item) {
                    is ListItem.CategoryHeader -> item.category.id == targetCategoryId
                    is ListItem.HabitRow -> item.habit.categoryId == targetCategoryId
                }
            }

            if (targetStart < 0) {
                items.addAll(oldStart.coerceAtMost(items.size), block)
                return false
            }

            val insertionIndex = if (fromCategoryIndex < toCategoryIndex) {
                var index = targetStart
                while (index < items.size) {
                    val belongsToTarget = when (val item = items[index]) {
                        is ListItem.CategoryHeader -> item.category.id == targetCategoryId
                        is ListItem.HabitRow -> item.habit.categoryId == targetCategoryId
                    }
                    if (!belongsToTarget) break
                    index++
                }
                index
            } else {
                targetStart
            }

            items.addAll(insertionIndex, block)

            // Notificar cada movimiento de fila individualmente.
            if (insertionIndex < oldStart) {
                for (offset in 0 until blockSize) {
                    notifyItemMoved(oldStart + offset, insertionIndex + offset)
                }
            } else {
                for (offset in blockSize - 1 downTo 0) {
                    notifyItemMoved(oldStart + offset, insertionIndex + offset)
                }
            }

            return true
        }

        // Reordenar hábitos dentro de la misma categoría.
        if (fromItem is ListItem.HabitRow && toItem is ListItem.HabitRow) {
            val fromHabit = fromItem.habit
            val toHabit = toItem.habit

            if (fromHabit.categoryId != toHabit.categoryId) return false

            val categoryHabits = habits
                .filter { it.categoryId == fromHabit.categoryId }
                .sortedBy { it.sortOrder }
                .toMutableList()

            val fromIndex = categoryHabits.indexOfFirst {
                it.habitId == fromHabit.habitId
            }
            val toIndex = categoryHabits.indexOfFirst {
                it.habitId == toHabit.habitId
            }

            if (fromIndex < 0 || toIndex < 0) return false

            val movedHabit = categoryHabits.removeAt(fromIndex)
            categoryHabits.add(toIndex, movedHabit)

            val reorderedById = categoryHabits
                .mapIndexed { index, habit ->
                    habit.copy(sortOrder = index)
                }
                .associateBy { it.habitId }

            habits = habits.map { habit ->
                reorderedById[habit.habitId] ?: habit
            }

            // Actualizar la lista visible sin reconstruirla.
            val fromItemIndex = items.indexOfFirst {
                it is ListItem.HabitRow &&
                        it.habit.habitId == fromHabit.habitId
            }
            val toItemIndex = items.indexOfFirst {
                it is ListItem.HabitRow &&
                        it.habit.habitId == toHabit.habitId
            }

            if (fromItemIndex < 0 || toItemIndex < 0) return false

            items.removeAt(fromItemIndex)
            items.add(
                toItemIndex, ListItem.HabitRow(
                    movedHabit.copy(
                        sortOrder = toIndex
                    )
                )
            )

            // Sin notifyDataSetChanged durante el gesto.
            notifyItemMoved(fromItemIndex, toItemIndex)

            // Actualizar los datos sin interrumpir el arrastre.
            items.indices.forEach { index ->
                val item = items[index]
                if (item is ListItem.HabitRow) {
                    val updated = reorderedById[item.habit.habitId]
                    if (updated != null) {
                        items[index] = ListItem.HabitRow(updated)
                    }
                }
            }

            return true
        }

        return false
    }

    fun getOrderedHabitsForSaving(): List<Habit> {
        val orderedHabits = mutableListOf<Habit>()
        val categoryIds = orderedCategoryIds

        categoryIds.forEach { categoryId ->
            items.filterIsInstance<ListItem.HabitRow>()
                .map { it.habit }
                .filter { it.categoryId == categoryId }
                .forEachIndexed { index, habit ->
                    orderedHabits.add(habit.copy(sortOrder = index))
                }
        }

        return orderedHabits
    }

    private fun rebuildItems() {
        val result = mutableListOf<ListItem>()
        val categoriesById = HabitCategory.entries.associateBy { it.id }
        val allCategoryIds =
            (orderedCategoryIds + HabitCategory.entries.map { it.id }).distinct()

        orderedCategoryIds = allCategoryIds.toMutableList()

        allCategoryIds.forEach { categoryId ->
            val category = categoriesById[categoryId] ?: return@forEach
            result.add(ListItem.CategoryHeader(category))

            habits.filter { it.categoryId == category.id }
                .sortedBy { it.sortOrder }
                .forEach { result.add(ListItem.HabitRow(it)) }
        }

        items = result
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
}