package com.app.healthydays.ui.home

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.healthydays.MainActivity
import com.app.healthydays.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun homeScreen_displaysMainElements() {
        onView(withId(R.id.recyclerHabits))
            .check(matches(isDisplayed()))

        onView(withId(R.id.dailyRating))
            .check(matches(isDisplayed()))

        onView(withId(R.id.editHabits))
            .check(matches(isDisplayed()))
    }

    @Test
    fun clickingEditHabitsButton_navigatesToEditHabits() {
        onView(withId(R.id.editHabits))
            .perform(click())

        onView(withId(R.id.toolbarTitle))
            .check(matches(withText("")))
    }
}