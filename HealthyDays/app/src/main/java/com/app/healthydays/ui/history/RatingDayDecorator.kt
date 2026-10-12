package com.app.healthydays.ui.history

import android.text.style.ForegroundColorSpan
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade

class RatingDayDecorator(private val date: CalendarDay, private val color: Int) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return day == date
    }

    override fun decorate(view: DayViewFacade) {
        view.addSpan(ForegroundColorSpan(color))
    }
}