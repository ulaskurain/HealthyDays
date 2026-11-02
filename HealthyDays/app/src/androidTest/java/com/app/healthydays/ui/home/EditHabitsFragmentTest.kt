package com.app.healthydays.ui.home

import androidx.navigation.findNavController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.healthydays.MainActivity
import com.app.healthydays.R
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditHabitsFragmentTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun addingHabit_createsHabitAndDisplaysItInHome() {

        // Entrar en edición de hábitos
        onView(withId(R.id.editHabits))
            .perform(click())

        // Abrir el diálogo para añadir un hábito
        onView(withId(R.id.buttonAddHabit))
            .perform(click())

        // Escribir el nombre del hábito
        onView(withHint(R.string.habit_name))
            .perform(typeText("Test habit"), closeSoftKeyboard())

        // Guardar
        onView(withText(R.string.add))
            .perform(click())

        onView(withId(R.id.recyclerEditHabits))
            .check(matches(hasDescendant(withText("Test habit"))))

        // Volver directamente a Home
        activityRule.scenario.onActivity { activity ->
            activity.findNavController(R.id.nav_host_fragment_content_main)
                .navigate(R.id.nav_home)
        }

        // Comprobar que aparece el hábito
        onView(withId(R.id.recyclerHabits))
            .check(matches(hasDescendant(withText("Test habit"))))
    }

    @Test
    fun deletingHabit_removesItFromHome() {

        // Entrar en edición de hábitos
        onView(withId(R.id.editHabits))
            .perform(click())

        // Añadir un hábito para poder eliminarlo
        onView(withId(R.id.buttonAddHabit))
            .perform(click())

        onView(withHint(R.string.habit_name))
            .perform(typeText("Habit to delete"), closeSoftKeyboard())

        onView(withText(R.string.add))
            .perform(click())

        // Comprobar que se ha añadido
        onView(withId(R.id.recyclerEditHabits))
            .check(matches(hasDescendant(withText("Habit to delete"))))

        // Volver a Home para terminar la navegación en el test
        activityRule.scenario.onActivity { activity ->
            activity.findNavController(R.id.nav_host_fragment_content_main)
                .navigate(R.id.nav_home)
        }

        // Volver a edición para eliminarlo
        onView(withId(R.id.editHabits))
            .perform(click())

        // Eliminar el hábito desde su fila
        onView(withId(R.id.recyclerEditHabits))
            .perform(
                RecyclerViewActions.actionOnItem<EditHabitAdapter.HabitViewHolder>(
                    hasDescendant(withText("Habit to delete")),
                    clickChildViewWithId(R.id.buttonDelete)
                )
            )

        // Volver a Home
        activityRule.scenario.onActivity { activity ->
            activity.findNavController(R.id.nav_host_fragment_content_main)
                .navigate(R.id.nav_home)
        }

        // Comprobar que ya no aparece
        onView(withId(R.id.recyclerHabits))
            .check(matches(not(hasDescendant(withText("Habit to delete")))))
    }


    private fun clickChildViewWithId(id: Int): androidx.test.espresso.ViewAction {
        return object : androidx.test.espresso.ViewAction {
            override fun getConstraints() =
                androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(
                    android.view.View::class.java
                )

            override fun getDescription() = "Click on a child view with id $id"

            override fun perform(
                uiController: androidx.test.espresso.UiController,
                view: android.view.View
            ) {
                view.findViewById<android.view.View>(id).performClick()
            }
        }
    }

}
