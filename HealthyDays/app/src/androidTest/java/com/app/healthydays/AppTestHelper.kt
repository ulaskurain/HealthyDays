package com.app.healthydays

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.entity.Habit
import com.app.healthydays.notifications.NotificationScheduler
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Utilidades comunes de los tests instrumentados. */
object AppTestHelper {

    val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Misma instancia (singleton) que usa la app dentro del proceso de test. */
    val db: AppDatabase get() = AppDatabase.getDatabase(context)

    /** Fecha de hoy con el mismo formato yyyy-MM-dd que usa la app. */
    val today: String get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Fecha de hoy con el formato de la cabecera del menú lateral. */
    val todayForDrawerHeader: String get() = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())

    /**
     * Deja la app en un estado conocido: base de datos vacía, preferencias limpias,
     * idioma/tema fijados y sin alarmas de notificación. Llamar SIEMPRE antes de lanzar la actividad.
     */
    fun resetState(language: String = "en", appearance: String = "light") {
        NotificationScheduler.cancelDailyNotification(context)
        runBlocking { db.deleteAllData() }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .clear()
            .putString("app_language", language)
            .putString("appearance", appearance)
            .commit()
        context.getSharedPreferences("habit_order", Context.MODE_PRIVATE).edit().clear().commit()
    }

    fun settingsPrefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun seedHabits(vararg habits: Habit) {
        runBlocking { db.habitDao().insertHabits(habits.toList()) }
    }

    fun launchMain(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

    /** Abre el menú lateral y navega a la opción indicada (R.id.nav_home / R.id.nav_history). */
    fun goToDrawerItem(itemId: Int) {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withId(R.id.nav_view)).perform(NavigationViewActions.navigateTo(itemId))
    }

    /** Espera a que se cumpla una condición sobre datos (p. ej. escrituras en Dispatchers.IO). */
    fun waitUntil(timeoutMs: Long = 5000, message: String = "Timeout esperando condición", condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError(message)
            Thread.sleep(50)
        }
    }

    /** Reintenta una comprobación de UI hasta que pase (las corrutinas de la app actualizan la pantalla de forma asíncrona). */
    fun eventually(timeoutMs: Long = 5000, block: () -> Unit) {
        val end = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < end) {
            try {
                block()
                return
            } catch (e: Throwable) {
                last = e
                Thread.sleep(100)
            }
        }
        throw AssertionError("La comprobación de UI no se cumplió a tiempo", last)
    }
}
