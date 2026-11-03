package com.app.healthydays.ui.home

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.R
import com.app.healthydays.data.dto.HabitItem
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
class HabitAdapterTest {

    private lateinit var context: Context
    private lateinit var parent: FrameLayout
    private val checks = mutableListOf<Pair<Int, Boolean>>()

    private val typeCategory = 0
    private val typeHabit = 1

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_HealthyDays
        )
        parent = FrameLayout(context)
        checks.clear()
    }

    private fun habit(id: Int, name: String, category: String, order: Int = 0, done: Boolean = false) =
        HabitItem(id, name, done, category, order)

    private fun adapter(
        habits: List<HabitItem>,
        order: List<String> = com.app.healthydays.data.entity.HabitCategory.entries.map { it.id }
    ) = HabitAdapter(habits, { id, checked -> checks.add(id to checked) }, order)

    private fun bind(adapter: HabitAdapter, position: Int): RecyclerView.ViewHolder {
        val holder = adapter.createViewHolder(parent, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        return holder
    }

    @Test
    fun emptyList_hasNoItems() {
        assertEquals(0, adapter(emptyList()).itemCount)
    }

    @Test
    fun itemCount_isHeadersPlusHabits_skippingEmptyCategories() {
        val a = adapter(
            listOf(
                habit(1, "A", "PHYSICAL_HEALTH"),
                habit(2, "B", "PHYSICAL_HEALTH"),
                habit(3, "C", "SOCIAL")
            )
        )
        // 2 cabeceras + 3 hábitos
        assertEquals(5, a.itemCount)
    }

    @Test
    fun viewTypes_headerThenHabits() {
        val a = adapter(listOf(habit(1, "A", "PHYSICAL_HEALTH"), habit(2, "B", "PHYSICAL_HEALTH")))
        assertEquals(typeCategory, a.getItemViewType(0))
        assertEquals(typeHabit, a.getItemViewType(1))
        assertEquals(typeHabit, a.getItemViewType(2))
    }

    @Test
    fun habitsInsideCategory_areSortedBySortOrder() {
        val a = adapter(
            listOf(
                habit(1, "Segundo", "LEARNING", order = 1),
                habit(2, "Primero", "LEARNING", order = 0)
            )
        )
        val first = bind(a, 1) as HabitAdapter.HabitViewHolder
        val second = bind(a, 2) as HabitAdapter.HabitViewHolder
        assertEquals("Primero", first.textHabitName.text.toString())
        assertEquals("Segundo", second.textHabitName.text.toString())
    }

    @Test
    fun categoryOrder_isRespected() {
        val a = adapter(
            habits = listOf(habit(1, "A", "PHYSICAL_HEALTH"), habit(2, "B", "SOCIAL")),
            order = listOf("SOCIAL", "PHYSICAL_HEALTH")
        )
        val header = bind(a, 0) as HabitAdapter.CategoryViewHolder
        assertEquals(context.getString(R.string.category_social), header.title.text.toString())
    }

    @Test
    fun updateCategoryOrder_reordersHeaders() {
        val a = adapter(listOf(habit(1, "A", "PHYSICAL_HEALTH"), habit(2, "B", "SOCIAL")))
        a.updateCategoryOrder(listOf("SOCIAL"))

        val header = bind(a, 0) as HabitAdapter.CategoryViewHolder
        assertEquals(context.getString(R.string.category_social), header.title.text.toString())
    }

    @Test
    fun orderListMissingCategories_areAppendedAtTheEnd() {
        val a = adapter(
            habits = listOf(habit(1, "A", "HOME"), habit(2, "B", "OTHER")),
            order = listOf("OTHER")
        )
        val first = bind(a, 0) as HabitAdapter.CategoryViewHolder
        assertEquals(context.getString(R.string.category_other), first.title.text.toString())
        assertEquals(4, a.itemCount)
    }

    @Test
    fun habitWithUnknownCategory_goesUnderOther() {
        val a = adapter(listOf(habit(1, "Raro", "CATEGORIA_QUE_NO_EXISTE")))
        assertEquals(2, a.itemCount)
        val header = bind(a, 0) as HabitAdapter.CategoryViewHolder
        assertEquals(context.getString(R.string.category_other), header.title.text.toString())
    }

    @Test
    fun clickingHeader_collapsesAndExpandsItsHabits() {
        val a = adapter(listOf(habit(1, "A", "SOCIAL"), habit(2, "B", "SOCIAL")))
        assertEquals(3, a.itemCount)

        var header = bind(a, 0) as HabitAdapter.CategoryViewHolder
        assertEquals("−", header.arrow.text.toString())
        header.itemView.performClick()
        assertEquals(1, a.itemCount)

        header = bind(a, 0) as HabitAdapter.CategoryViewHolder
        assertEquals("+", header.arrow.text.toString())
        header.itemView.performClick()
        assertEquals(3, a.itemCount)
    }

    @Test
    fun collapsingOneCategory_doesNotAffectOthers() {
        val a = adapter(
            listOf(habit(1, "A", "PHYSICAL_HEALTH"), habit(2, "B", "SOCIAL"), habit(3, "C", "SOCIAL"))
        )
        bind(a, 0).itemView.performClick() // colapsa PHYSICAL_HEALTH
        // PHYSICAL header + SOCIAL header + 2 hábitos
        assertEquals(4, a.itemCount)
    }

    @Test
    fun bindHabit_showsCompletedStateWithoutTriggeringCallback() {
        val a = adapter(listOf(habit(1, "A", "SOCIAL", done = true)))
        val holder = bind(a, 1) as HabitAdapter.HabitViewHolder

        assertTrue(holder.checkBox.isChecked)
        assertTrue(checks.isEmpty())
    }

    @Test
    fun togglingCheckbox_invokesCallbackWithHabitId() {
        val a = adapter(listOf(habit(7, "A", "SOCIAL")))
        val holder = bind(a, 1) as HabitAdapter.HabitViewHolder

        holder.checkBox.isChecked = true
        holder.checkBox.isChecked = false

        assertEquals(listOf(7 to true, 7 to false), checks)
    }

    @Test
    fun recycledHolder_doesNotLeakPreviousListener() {
        val a = adapter(listOf(habit(1, "A", "SOCIAL", order = 0), habit(2, "B", "SOCIAL", order = 1, done = true)))
        val holder = a.createViewHolder(parent, typeHabit) as HabitAdapter.HabitViewHolder

        a.bindViewHolder(holder, 1) // A
        a.bindViewHolder(holder, 2) // B (checked)
        assertTrue(checks.isEmpty())
        assertTrue(holder.checkBox.isChecked)

        holder.checkBox.isChecked = false
        assertEquals(listOf(2 to false), checks)
    }

    @Test
    fun updateData_rebuildsList() {
        val a = adapter(listOf(habit(1, "A", "SOCIAL")))
        assertEquals(2, a.itemCount)

        a.updateData(emptyList())
        assertEquals(0, a.itemCount)

        a.updateData(listOf(habit(1, "A", "SOCIAL"), habit(2, "B", "HOME")))
        assertEquals(4, a.itemCount)
        assertFalse(a.itemCount == 2)
    }
}
