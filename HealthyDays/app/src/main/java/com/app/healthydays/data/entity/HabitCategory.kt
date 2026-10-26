package com.app.healthydays.data.entity

import com.app.healthydays.R

enum class HabitCategory(val id: String, val nameResId: Int) {
    PHYSICAL_HEALTH("PHYSICAL_HEALTH", R.string.category_physical_health),
    NUTRITION("NUTRITION", R.string.category_nutrition),
    MENTAL_WELLBEING("MENTAL_WELLBEING", R.string.category_mental_wellbeing),
    LEARNING("LEARNING", R.string.category_learning),
    SOCIAL("SOCIAL", R.string.category_social),
    PRODUCTIVITY("PRODUCTIVITY", R.string.category_productivity),
    HOME("HOME", R.string.category_home),
    HOBBIES("HOBBIES", R.string.category_hobbies),
    OTHER("OTHER", R.string.category_other)
}