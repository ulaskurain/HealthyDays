
package com.app.healthydays.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.dto.ExportData
import com.app.healthydays.data.dto.ExportDataJson
import com.app.healthydays.notifications.NotificationHelper
import com.app.healthydays.notifications.NotificationScheduler
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) :
    AndroidViewModel(application) {

    private val context = application.applicationContext

    private val database = AppDatabase.getDatabase(context)

    private val prefs = context.getSharedPreferences(
        "settings",
        android.content.Context.MODE_PRIVATE
    )

    // ----------------------------------------------------
    // LANGUAGE
    // ----------------------------------------------------

    fun getSavedLanguage(): String {
        return prefs.getString("app_language", "en") ?: "en"
    }

    fun saveLanguage(language: String) {
        prefs.edit()
            .putString("app_language", language)
            .apply()
    }

    // ----------------------------------------------------
    // APPEARANCE
    // ----------------------------------------------------

    fun getSavedAppearance(): String {
        return prefs.getString("appearance", "system") ?: "system"
    }

    fun saveAppearance(appearance: String) {
        prefs.edit().putString("appearance", appearance).apply()
    }

    // ----------------------------------------------------
    // NOTIFICATIONS
    // ----------------------------------------------------

    fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", false)
    }

    fun getNotificationHour(): Int {
        return prefs.getInt("notification_hour", 20)
    }

    fun getNotificationMinute(): Int {
        return prefs.getInt("notification_minute", 0)
    }

    fun enableNotifications(hour: Int, minute: Int) {
        prefs.edit()
            .putBoolean("notifications_enabled", true)
            .putInt("notification_hour", hour)
            .putInt("notification_minute", minute)
            .apply()

        NotificationHelper.createNotificationChannel(context)

        NotificationScheduler.scheduleDailyNotification(context, hour, minute)
    }

    fun disableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", false).apply()
        NotificationScheduler.cancelDailyNotification(context)
    }

    // ----------------------------------------------------
    // EXPORT DATA
    // ----------------------------------------------------

    fun exportData(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val habits = database.habitDao().getAllHabits()
                    val days = database.dayDao().getAllDays()
                    val completions =
                        database.habitCompletionDao().getAllCompletions()

                    val exportData = ExportData(
                        habits = habits,
                        days = days,
                        completions = completions
                    )

                    ExportDataJson.toJson(exportData)
                }

                onResult(Result.success(json))

            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    // ----------------------------------------------------
    // IMPORT DATA
    // ----------------------------------------------------

    fun importData(json: String, onResult: (Result<Unit>) -> Unit) {

        viewModelScope.launch {

            try {
                val exportData = withContext(Dispatchers.Default) {

                    Gson().fromJson(
                        json,
                        ExportData::class.java
                    )
                } ?: throw IllegalStateException("Archivo vacío")

                withContext(Dispatchers.IO) {
                    database.importData(exportData)
                }

                onResult(Result.success(Unit))

            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    // ----------------------------------------------------
    // DELETE DATA
    // ----------------------------------------------------

    fun deleteAllData(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.deleteAllData()
                }

                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
}
