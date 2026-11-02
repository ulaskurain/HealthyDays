package com.app.healthydays.ui.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.healthydays.data.database.AppDatabase

class HomeViewModelFactory(
    private val application: Application,
    private val database: AppDatabase = AppDatabase.getDatabase(application)
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(application, database) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}