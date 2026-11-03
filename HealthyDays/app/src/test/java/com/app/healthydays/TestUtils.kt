package com.app.healthydays

import android.content.Context
import androidx.room.Room
import com.app.healthydays.data.database.AppDatabase
import org.junit.Assert.fail

/** Utilidades compartidas por los tests. */
object TestUtils {

    /** Base de datos en memoria, independiente del singleton de la app. */
    fun inMemoryDb(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** Espera (con timeout) a que se cumpla una condición. Útil con corrutinas lanzadas en Dispatchers.IO. */
    fun waitUntil(timeoutMs: Long = 5000, message: String = "Timeout esperando condición", condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) fail(message)
            Thread.sleep(25)
        }
    }

    /** Cierra y reinicia el singleton AppDatabase.INSTANCE para aislar los tests de ViewModel. */
    fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        (field.get(null) as? AppDatabase)?.close()
        field.set(null, null)
    }
}
