
package com.app.healthydays.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.app.healthydays.R
import com.app.healthydays.databinding.FragmentSettingsBinding
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    private val languages = arrayOf("English", "Español", "Euskara", "Français")
    private val languageCodes = arrayOf("en", "es", "eu", "fr")

    // ----------------------------------------------------
    // FILE SELECTOR
    // ----------------------------------------------------

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

    // ----------------------------------------------------
    // NOTIFICATIONS PERMISSION
    // ----------------------------------------------------

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

    // ----------------------------------------------------
    // CREATE VIEW
    // ----------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateCurrentLanguage()

        setupNotifications()
        setupAppearance()

        binding.optionLanguage.setOnClickListener {
            showLanguageDialog()
        }

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

    // ----------------------------------------------------
    // LANGUAGE
    // ----------------------------------------------------

    private fun updateCurrentLanguage() {
        val currentLanguage = viewModel.getSavedLanguage()
        val languageIndex = languageCodes.indexOf(currentLanguage)

        if (languageIndex >= 0) {
            binding.textCurrentLanguage.text = languages[languageIndex]
        }
    }

    private fun showLanguageDialog() {
        val currentLanguage = viewModel.getSavedLanguage()
        var checkedItem = languageCodes.indexOf(currentLanguage)

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
                viewModel.saveLanguage(selectedCode)
                requireActivity().recreate()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ----------------------------------------------------
    // NOTIFICATIONS
    // ----------------------------------------------------

    private fun setupNotifications() {
        binding.switchNotifications.isChecked = viewModel.areNotificationsEnabled()

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

        val currentHour = viewModel.getNotificationHour()
        val currentMinute = viewModel.getNotificationMinute()

        TimePickerDialog(
            ContextThemeWrapper(requireContext(), R.style.HealthyDaysTimePickerTheme),
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

            getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.healthy_day_4))

            getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.healthy_day_4))
        }
    }

    private fun enableNotifications(hour: Int, minute: Int) {
        viewModel.enableNotifications(hour, minute)
        updateNotificationTimeText()
    }

    private fun disableNotifications() {
        viewModel.disableNotifications()
        updateNotificationTimeText()
    }

    private fun updateNotificationTimeText() {
        if (viewModel.areNotificationsEnabled()) {
            val hour = viewModel.getNotificationHour()
            val minute = viewModel.getNotificationMinute()

            binding.textNotificationTime.text = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                hour,
                minute
            )

            binding.textNotificationTime.visibility = View.VISIBLE

        } else {
            binding.textNotificationTime.visibility = View.GONE
        }
    }

    // ----------------------------------------------------
    // APPEARANCE
    // ----------------------------------------------------

    private fun setupAppearance() {
        updateAppearanceText(viewModel.getSavedAppearance())

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
        val options = arrayOf(
            getString(R.string.system_default),
            getString(R.string.light),
            getString(R.string.dark)
        )

        val values = arrayOf("system", "light", "dark")
        val currentAppearance = viewModel.getSavedAppearance()
        var checkedItem = values.indexOf(currentAppearance)

        if (checkedItem < 0) {
            checkedItem = 0
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_HealthyDays_AlertDialog)
            .setTitle(getString(R.string.appearance))
            .setSingleChoiceItems(
                options,
                checkedItem
            ) { _, which ->
                checkedItem = which
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val selectedAppearance = values[checkedItem]
                viewModel.saveAppearance(selectedAppearance)
                requireActivity().recreate()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ----------------------------------------------------
    // EXPORT DATA
    // ----------------------------------------------------

    private fun exportDataToFile(uri: Uri) {
        viewModel.exportData { result ->
            if (!isAdded) return@exportData

            result.onSuccess { json ->
                try {
                    requireContext()
                        .contentResolver
                        .openOutputStream(uri, "wt")
                        ?.use { outputStream ->
                            outputStream.write(json.toByteArray(Charsets.UTF_8))
                        }
                        ?: throw IllegalStateException("No se pudo abrir el archivo")

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_success),
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ----------------------------------------------------
    // IMPORT DATA
    // ----------------------------------------------------

    private fun showImportConfirmation() {
        AlertDialog.Builder(requireContext(), R.style.Theme_HealthyDays_AlertDialog)
            .setTitle(getString(R.string.import_data))
            .setMessage(getString(R.string.import_data_warning))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importFileLauncher.launch(arrayOf("application/json", "text/json"))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importDataFromFile(uri: Uri) {
        try {
            val json = requireContext()
                .contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalStateException("Couldn't read the file")

            viewModel.importData(json) { result ->
                if (!isAdded) return@importData

                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_success),
                        Toast.LENGTH_SHORT
                    ).show()

                    requireActivity().recreate()

                }.onFailure { error ->
                    android.util.Log.e(
                        "HealthyDaysImport",
                        "Error importing the security copy",
                        error
                    )

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.import_error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ----------------------------------------------------
    // DELETE DATA
    // ----------------------------------------------------

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
        viewModel.deleteAllData { result ->
            if (!isAdded) return@deleteAllData

            result.onSuccess {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.delete_success),
                    Toast.LENGTH_SHORT
                ).show()

                requireActivity().recreate()

            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.delete_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ----------------------------------------------------
    // ABOUT US
    // ----------------------------------------------------

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

        AlertDialog.Builder(requireContext(), R.style.Theme_HealthyDays_AlertDialog)
            .setIcon(R.mipmap.ic_launcher)
            .setTitle(getString(R.string.about_healthy_days))
            .setMessage(
                getString(R.string.about_healthy_days_message, versionName)
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ----------------------------------------------------
    // DESTROY VIEW
    // ----------------------------------------------------

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
