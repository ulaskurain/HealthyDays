package com.app.healthydays.ui.history

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.app.healthydays.R
import com.app.healthydays.data.dto.HabitItem
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HistoryAdapterTest {

    private lateinit var context: Context
    private lateinit var parent: FrameLayout

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_HealthyDays
        )
        parent = FrameLayout(context)
    }

    private fun habit(id: Int, name: String, category: String, order: Int = 0, done: Boolean = false) =
        HabitItem(id, name, done, category, order)

    private fun bind(adapter: HistoryAdapter, position: Int) =
        adapter.createViewHolder(parent, adapter.getItemViewType(position)).also {
            adapter.bindViewHolder(it, position)
        }

    @Test
    fun emptyList_hasNoItems() {
        assertEquals(0, HistoryAdapter(emptyList()).itemCount)
    }

    @Test
    fun itemCount_isHeadersPlusHabits() {
        val a = HistoryAdapter(
            listOf(habit(1, "A", "SOCIAL"), habit(2, "B", "SOCIAL"), habit(3, "C", "HOME"))
        )
        assertEquals(5, a.itemCount)
    }

    @Test
    fun habitsAreSortedBySortOrderThenId() {
        val a = HistoryAdapter(
            listOf(
                habit(3, "C", "SOCIAL", order = 1),
                habit(2, "B", "SOCIAL", order = 0),
                habit(1, "A", "SOCIAL", order = 0)
            )
        )
        val names = (1..3).map { (bind(a, it) as HistoryAdapter.HabitViewHolder).binding.textHabitName.text.toString() }
        assertEquals(listOf("A", "B", "C"), names)
    }

    @Test
    fun categoryOrderIsRespected() {
        val a = HistoryAdapter(
            habits = listOf(habit(1, "A", "PHYSICAL_HEALTH"), habit(2, "B", "HOBBIES")),
            categoryOrder = listOf("HOBBIES")
        )
        val header = bind(a, 0) as HistoryAdapter.CategoryViewHolder
        assertEquals(context.getString(R.string.category_hobbies), header.title.text.toString())
    }

    @Test
    fun updateCategoryOrder_changesHeaderOrder() {
        val a = HistoryAdapter(listOf(habit(1, "A", "PHYSICAL_HEALTH"), habit(2, "B", "HOBBIES")))
        a.updateCategoryOrder(listOf("HOBBIES"))

        val header = bind(a, 0) as HistoryAdapter.CategoryViewHolder
        assertEquals(context.getString(R.string.category_hobbies), header.title.text.toString())
    }

    @Test
    fun habitWithUnknownCategory_goesUnderOther() {
        val a = HistoryAdapter(listOf(habit(1, "Raro", "NO_EXISTE")))
        assertEquals(2, a.itemCount)
        val header = bind(a, 0) as HistoryAdapter.CategoryViewHolder
        assertEquals(context.getString(R.string.category_other), header.title.text.toString())
    }

    @Test
    fun clickingHeader_collapsesAndExpands() {
        val a = HistoryAdapter(listOf(habit(1, "A", "SOCIAL"), habit(2, "B", "SOCIAL")))
        assertEquals(3, a.itemCount)

        bind(a, 0).itemView.performClick()
        assertEquals(1, a.itemCount)

        val header = bind(a, 0) as HistoryAdapter.CategoryViewHolder
        assertEquals("+", header.arrow.text.toString())
        header.itemView.performClick()
        assertEquals(3, a.itemCount)
    }

    @Test
    fun updateData_replacesContent() {
        val a = HistoryAdapter(listOf(habit(1, "A", "SOCIAL")))
        a.updateData(emptyList())
        assertEquals(0, a.itemCount)
        a.updateData(listOf(habit(1, "A", "SOCIAL"), habit(2, "B", "HOME")))
        assertEquals(4, a.itemCount)
    }

    @Test
    fun bindHabit_completedAndUncompletedDoNotCrashAndShowName() {
        val a = HistoryAdapter(
            listOf(habit(1, "Hecho", "SOCIAL", 0, done = true), habit(2, "Pendiente", "SOCIAL", 1, done = false))
        )
        val done = bind(a, 1) as HistoryAdapter.HabitViewHolder
        val pending = bind(a, 2) as HistoryAdapter.HabitViewHolder

        assertEquals("Hecho", done.binding.textHabitName.text.toString())
        assertEquals("Pendiente", pending.binding.textHabitName.text.toString())
    }
}
