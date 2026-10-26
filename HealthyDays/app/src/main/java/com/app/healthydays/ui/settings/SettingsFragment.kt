package com.app.healthydays.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.healthydays.R
import com.app.healthydays.data.database.AppDatabase
import com.app.healthydays.data.dto.ExportData
import com.app.healthydays.data.dto.ExportDataJson
import com.app.healthydays.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.app.healthydays.notifications.NotificationHelper
import com.app.healthydays.notifications.NotificationScheduler
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val database by lazy {
        AppDatabase.getDatabase(requireContext())
    }

    private val languages = arrayOf(
        "English",
        "Español",
        "Euskara",
        "Français"
    )

    private val languageCodes = arrayOf(
        "en",
        "es",
        "eu",
        "fr"
    )

    private val exportFileLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                exportDataToFile(uri)
            }
        }

    private val importFileLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                importDataFromFile(uri)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                showNotificationTimeDialog()
            } else {
                binding.switchNotifications.isChecked = false
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSettingsBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        updateCurrentLanguage()

        binding.optionLanguage.setOnClickListener {
            showLanguageDialog()
        }

        setupNotifications()
        setupAppearance()

        binding.optionExport.setOnClickListener {
            exportFileLauncher.launch("healthy_days_backup.json")
        }

        binding.optionImport.setOnClickListener {
            showImportConfirmation()
        }

        binding.optionDeleteData.setOnClickListener {
            showDeleteDataConfirmation()
        }

        binding.optionAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun updateCurrentLanguage() {
        val currentLanguage = getSavedLanguage()

        val languageIndex = languageCodes.indexOf(currentLanguage)

        if (languageIndex >= 0) {
            binding.textCurrentLanguage.text = languages[languageIndex]
        }
    }

    private fun showLanguageDialog() {

        val currentLanguage = getSavedLanguage()

        var checkedItem =
            languageCodes.indexOf(currentLanguage)

        if (checkedItem < 0) {
            checkedItem = 0
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_HealthyDays_AlertDialog)
            .setTitle(getString(R.string.language))
            .setSingleChoiceItems(
                languages,
                checkedItem
            ) { _, which ->
                checkedItem = which
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->

                val selectedCode = languageCodes[checkedItem]

                saveLanguage(selectedCode)

                requireActivity().recreate()

                dialog.dismiss()
            }
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .show()
    }

    private fun saveLanguage(language: String) {

        val prefs = requireContext()
            .getSharedPreferences(
                "settings",
                android.content.Context.MODE_PRIVATE
            )

        prefs.edit()
            .putString("app_language", language)
            .apply()
    }

    private fun getSavedLanguage(): String {

        val prefs = requireContext()
            .getSharedPreferences(
                "settings",
                android.content.Context.MODE_PRIVATE
            )

        return prefs.getString(
            "app_language",
            "en"
        ) ?: "en"
    }

    private fun setupNotifications() {
        val prefs = requireContext()
            .getSharedPreferences(
                "settings",
                Context.MODE_PRIVATE
            )

        val enabled = prefs.getBoolean(
            "notifications_enabled",
            false
        )

        binding.switchNotifications.isChecked = enabled

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (
                    android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.TIRAMISU
                ) {
                    val permissionGranted =
                        ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

                    if (permissionGranted) {
                        showNotificationTimeDialog()
                    } else {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                } else {
                    showNotificationTimeDialog()
                }
            } else {
                disableNotifications()
            }
        }
        updateNotificationTimeText()
    }

    private fun showNotificationTimeDialog() {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentHour = prefs.getInt("notification_hour", 20)
        val currentMinute = prefs.getInt("notification_minute", 0)

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                enableNotifications(hourOfDay, minute)
            },
            currentHour,
            currentMinute,
            true
        ).apply {
            setOnCancelListener {
                binding.switchNotifications.isChecked = false
            }

            show()
        }
    }

    private fun enableNotifications(hour: Int, minute: Int) {
        val context = requireContext()

        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notifications_enabled", true)
            .putInt("notification_hour", hour)
            .putInt("notification_minute", minute)
            .apply()

        NotificationHelper.createNotificationChannel(context)
        NotificationScheduler.scheduleDailyNotification(context, hour, minute)
        updateNotificationTimeText()
    }

    private fun disableNotifications() {
        val context = requireContext()

        context.getSharedPreferences(
            "settings",
            Context.MODE_PRIVATE
        )
            .edit()
            .putBoolean("notifications_enabled", false)
            .apply()

        NotificationScheduler.cancelDailyNotification(context)
        updateNotificationTimeText()
    }

    private fun updateNotificationTimeText() {
        val prefs = requireContext().getSharedPreferences(
            "settings",
            Context.MODE_PRIVATE
        )

        val enabled = prefs.getBoolean("notifications_enabled", false)

        if (enabled) {
            val hour = prefs.getInt("notification_hour", 20)
            val minute = prefs.getInt("notification_minute", 0)

            binding.textNotificationTime.text = String.format(
                Locale.getDefault(), "%02d:%02d", hour, minute)

            binding.textNotificationTime.visibility = View.VISIBLE
        } else {
            binding.textNotificationTime.visibility = View.GONE
        }
    }

    private fun setupAppearance() {
        val prefs = requireContext()
            .getSharedPreferences(
                "settings",
                android.content.Context.MODE_PRIVATE
            )

        val currentAppearance = prefs.getString(
            "appearance",
            "system"
        ) ?: "system"

        updateAppearanceText(currentAppearance)

        binding.optionAppearance.setOnClickListener {
            showAppearanceDialog()
        }
    }

    private fun updateAppearanceText(appearance: String) {
        binding.textCurrentAppearance.text = when (appearance) {
            "light" -> getString(R.string.light)
            "dark" -> getString(R.string.dark)
            else -> getString(R.string.system_default)
        }
    }

    private fun showAppearanceDialog() {
        val prefs = requireContext()
            .getSharedPreferences(
                "settings",
                android.content.Context.MODE_PRIVATE
            )

        val options = arrayOf(
            getString(R.string.system_default),
            getString(R.string.light),
            getString(R.string.dark)
        )

        val values = arrayOf("system", "light", "dark")
        val currentAppearance = prefs.getString("appearance", "system") ?: "system"
        var checkedItem = values.indexOf(currentAppearance)

        if (checkedItem < 0) {
            checkedItem = 0
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_HealthyDays_AlertDialog)
            .setTitle(getString(R.string.appearance))
            .setSingleChoiceItems(options, checkedItem)
            { _, which ->
                checkedItem = which
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val selectedAppearance = values[checkedItem]
                prefs.edit().putString("appearance", selectedAppearance).apply()
                requireActivity().recreate()
                updateAppearanceText(selectedAppearance)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun exportDataToFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                val habits = database.habitDao().getAllHabits()
                val days = database.dayDao().getAllDays()
                val completions = database.habitCompletionDao().getAllCompletions()
                val exportData = ExportData(
                    habits = habits,
                    days = days,
                    completions = completions
                )
                ExportDataJson.toJson(exportData)
            }

            try {
                requireContext().contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.export_success),
                    android.widget.Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.export_error),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showImportConfirmation() {
        AlertDialog.Builder(
            requireContext(),
            R.style.Theme_HealthyDays_AlertDialog
        )
            .setTitle(getString(R.string.import_data))
            .setMessage(getString(R.string.import_data_warning))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importFileLauncher.launch(
                    arrayOf(
                        "application/json",
                        "text/json"
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importDataFromFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    requireContext()
                        .contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("No se pudo leer el archivo")
                }

                val exportData = withContext(Dispatchers.Default) {
                    Gson().fromJson(
                        json,
                        ExportData::class.java
                    )
                }

                if (exportData == null) {
                    throw IllegalStateException("Archivo vacío")
                }

                withContext(Dispatchers.IO) {
                    database.importData(exportData)
                }

                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_success),
                    Toast.LENGTH_SHORT
                ).show()
                requireActivity().recreate()

            } catch (e: JsonSyntaxException) {
                android.util.Log.e(
                    "HealthyDaysImport",
                    "Error al importar la copia de seguridad",
                    e
                )
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_error),
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                android.util.Log.e(
                    "HealthyDaysImport",
                    "Error al importar la copia de seguridad",
                    e
                )
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showDeleteDataConfirmation() {
        AlertDialog.Builder(
            requireContext(),
            R.style.Theme_HealthyDays_AlertDialog
        )
            .setTitle(getString(R.string.delete_data))
            .setMessage(getString(R.string.delete_data_warning))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteAllData()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteAllData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.deleteAllData()
                }

                Toast.makeText(
                    requireContext(),
                    getString(R.string.delete_success),
                    Toast.LENGTH_SHORT
                ).show()

                requireActivity().recreate()

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.delete_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun showAboutDialog() {
        val versionName = try {
            requireContext()
                .packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName
        } catch (e: Exception) {
            "1.0"
        }

        AlertDialog.Builder(
            requireContext(),
            R.style.Theme_HealthyDays_AlertDialog
        )
            .setIcon(R.mipmap.ic_launcher)
            .setTitle(getString(R.string.about_healthy_days))
            .setMessage(
                getString(
                    R.string.about_healthy_days_message,
                    versionName
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}