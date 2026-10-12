package com.app.healthydays.ui.history

import android.graphics.drawable.Drawable
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade

class TodayDecorator(
    private val today: CalendarDay,
    private val drawable: Drawable,
    private val selectedDay: CalendarDay?
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return day == today && day != selectedDay
    }

    override fun decorate(view: DayViewFacade) {
        view.setBackgroundDrawable(drawable)
    }
}