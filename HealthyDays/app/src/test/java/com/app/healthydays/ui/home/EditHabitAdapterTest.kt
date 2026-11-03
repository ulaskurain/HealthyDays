package com.app.healthydays.ui.home

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.R
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.data.entity.HabitCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EditHabitAdapterTest {

    private val deleted = mutableListOf<Habit>()
    private lateinit var context: Context

    private val categoryCount = HabitCategory.entries.size

    @Before
    fun setUp() {
        deleted.clear()
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_HealthyDays
        )
    }

    private fun habit(id: Int, name: String, category: String, order: Int) =
        Habit(habitId = id, name = name, categoryId = category, sortOrder = order)

    private fun adapter(
        habits: List<Habit>,
        order: List<String> = HabitCategory.entries.map { it.id }
    ) = EditHabitAdapter(habits, { deleted.add(it) }, order)

    @Test
    fun itemCount_includesAHeaderForEveryCategory() {
        assertEquals(categoryCount, adapter(emptyList()).itemCount)
        assertEquals(
            categoryCount + 2,
            adapter(listOf(habit(1, "A", "SOCIAL", 0), habit(2, "B", "HOME", 0))).itemCount
        )
    }

    @Test
    fun categoryOrder_defaultsToAllCategories() {
        assertEquals(HabitCategory.entries.map { it.id }, adapter(emptyList()).getCategoryOrder())
    }

    @Test
    fun categoryOrder_incompleteOrderIsCompleted() {
        val a = adapter(emptyList(), order = listOf("SOCIAL", "HOME"))
        val order = a.getCategoryOrder()
        assertEquals(listOf("SOCIAL", "HOME"), order.take(2))
        assertEquals(categoryCount, order.size)
        assertEquals(categoryCount, order.toSet().size)
    }

    @Test
    fun updateData_replacesHabits() {
        val a = adapter(listOf(habit(1, "A", "SOCIAL", 0)))
        a.updateData(listOf(habit(1, "A", "SOCIAL", 0), habit(2, "B", "SOCIAL", 1)))

        assertEquals(2, a.getCurrentHabits().size)
        assertEquals(categoryCount + 2, a.itemCount)
    }

    @Test
    fun deleteButton_invokesCallbackWithThatHabit() {
        val target = habit(5, "A", "PHYSICAL_HEALTH", 0)
        val a = adapter(listOf(target))

        // posición 0 = cabecera PHYSICAL_HEALTH, 1 = hábito
        val parent = FrameLayout(context)
        val holder = a.createViewHolder(parent, a.getItemViewType(1)) as EditHabitAdapter.HabitViewHolder
        a.bindViewHolder(holder, 1)

        assertEquals("A", holder.binding.textHabitName.text.toString())
        holder.binding.buttonDelete.performClick()

        assertEquals(listOf(target), deleted)
    }

    @Test
    fun moveItem_habitsInSameCategory_reordersThem() {
        val a = adapter(
            listOf(
                habit(1, "A", "PHYSICAL_HEALTH", 0),
                habit(2, "B", "PHYSICAL_HEALTH", 1),
                habit(3, "C", "PHYSICAL_HEALTH", 2)
            )
        )

        // 0 = cabecera, 1..3 = A, B, C. Mover A (1) a la posición de C (3).
        assertTrue(a.moveItem(1, 3))

        val saved = a.getOrderedHabitsForSaving()
        assertEquals(listOf("B", "C", "A"), saved.map { it.name })
        assertEquals(listOf(0, 1, 2), saved.map { it.sortOrder })
    }

    @Test
    fun moveItem_upwardsWorksToo() {
        val a = adapter(
            listOf(
                habit(1, "A", "PHYSICAL_HEALTH", 0),
                habit(2, "B", "PHYSICAL_HEALTH", 1),
                habit(3, "C", "PHYSICAL_HEALTH", 2)
            )
        )

        assertTrue(a.moveItem(3, 1))

        assertEquals(listOf("C", "A", "B"), a.getOrderedHabitsForSaving().map { it.name })
    }

    @Test
    fun moveItem_habitsOfDifferentCategories_isRejected() {
        val a = adapter(
            listOf(habit(1, "A", "PHYSICAL_HEALTH", 0), habit(2, "B", "NUTRITION", 0))
        )
        // 0 hdr PHYSICAL, 1 A, 2 hdr NUTRITION, 3 B
        assertFalse(a.moveItem(1, 3))
    }

    @Test
    fun moveItem_habitOntoHeader_isRejected() {
        val a = adapter(listOf(habit(1, "A", "PHYSICAL_HEALTH", 0)))
        assertFalse(a.moveItem(1, 0))
    }

    @Test
    fun moveItem_samePositionOrOutOfRange_isRejected() {
        val a = adapter(listOf(habit(1, "A", "PHYSICAL_HEALTH", 0)))
        assertFalse(a.moveItem(1, 1))
        assertFalse(a.moveItem(-1, 1))
        assertFalse(a.moveItem(1, 999))
    }

    @Test
    fun moveItem_categoryHeader_reordersCategories() {
        val a = adapter(
            listOf(habit(1, "A", "PHYSICAL_HEALTH", 0), habit(2, "B", "NUTRITION", 0))
        )
        // 0 hdr PHYSICAL, 1 A, 2 hdr NUTRITION, 3 B
        assertTrue(a.moveItem(0, 2))

        val order = a.getCategoryOrder()
        assertEquals(listOf("NUTRITION", "PHYSICAL_HEALTH"), order.take(2))
        assertEquals(categoryCount, order.size)
        // Los hábitos viajan con su categoría.
        assertEquals(listOf("B", "A"), a.getOrderedHabitsForSaving().map { it.name })
    }

    @Test
    fun moveItem_categoryOntoItself_isRejected() {
        val a = adapter(listOf(habit(1, "A", "PHYSICAL_HEALTH", 0)))
        // La cabecera y su propio hábito pertenecen a la misma categoría.
        assertFalse(a.moveItem(0, 1))
    }

    @Test
    fun getOrderedHabitsForSaving_followsCategoryOrderAndResetsSortOrder() {
        val a = adapter(
            habits = listOf(
                habit(1, "A", "PHYSICAL_HEALTH", 5),
                habit(2, "B", "SOCIAL", 9),
                habit(3, "C", "SOCIAL", 2)
            ),
            order = listOf("SOCIAL", "PHYSICAL_HEALTH")
        )

        val saved = a.getOrderedHabitsForSaving()

        assertEquals(listOf("C", "B", "A"), saved.map { it.name })
        assertEquals(listOf(0, 1, 0), saved.map { it.sortOrder })
    }
}
