package com.app.healthydays.ui.history

import android.graphics.drawable.ColorDrawable
import com.prolificinteractive.materialcalendarview.CalendarDay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DecoratorsTest {

    private val today = CalendarDay.from(2026, 11, 2)
    private val other = CalendarDay.from(2026, 11, 3)

    @Test
    fun ratingDayDecorator_decoratesOnlyItsDate() {
        val decorator = RatingDayDecorator(today, 0xFF00FF05.toInt())
        assertTrue(decorator.shouldDecorate(today))
        assertFalse(decorator.shouldDecorate(other))
    }

    @Test
    fun todayDecorator_decoratesTodayWhenNotSelected() {
        val decorator = TodayDecorator(today, ColorDrawable(0), selectedDay = other)
        assertTrue(decorator.shouldDecorate(today))
        assertFalse(decorator.shouldDecorate(other))
    }

    @Test
    fun todayDecorator_doesNotDecorateTodayWhenSelected() {
        val decorator = TodayDecorator(today, ColorDrawable(0), selectedDay = today)
        assertFalse(decorator.shouldDecorate(today))
    }

    @Test
    fun todayDecorator_withoutSelection_decoratesToday() {
        val decorator = TodayDecorator(today, ColorDrawable(0), selectedDay = null)
        assertTrue(decorator.shouldDecorate(today))
    }
}
