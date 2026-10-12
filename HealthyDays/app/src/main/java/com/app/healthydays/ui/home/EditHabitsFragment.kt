package com.app.healthydays.ui.home

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.healthydays.R
import com.app.healthydays.databinding.FragmentEditHabitsBinding
import kotlinx.coroutines.launch

class EditHabitsFragment : Fragment() {

    private var _binding: FragmentEditHabitsBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var adapter: EditHabitAdapter

    companion object {
        private const val MAX_HABIT_NAME_LENGTH = 50
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditHabitsBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        homeViewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        setupRecyclerView()
        observeHabits()

        binding.buttonAddHabit.setOnClickListener {
            showAddHabitDialog()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRecyclerView() {
        adapter = EditHabitAdapter(emptyList()) { habit ->
            homeViewModel.deleteHabit(habit)
        }
        binding.recyclerEditHabits.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recyclerEditHabits.adapter = adapter
    }

    private fun observeHabits() {
        viewLifecycleOwner.lifecycleScope.launch {
            homeViewModel.getAllHabits().collect { habits ->
                adapter.updateData(habits)
            }
        }
    }

    private fun showAddHabitDialog() {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.habit_name)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            filters = arrayOf(
                InputFilter.LengthFilter(MAX_HABIT_NAME_LENGTH)
            )
        }

        val container = FrameLayout(requireContext()).apply {
            setPadding(48, 0, 48, 0)
            addView(editText)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_habit)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(requireContext(), R.color.healthy_day_4)
            )

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                ContextCompat.getColor(requireContext(), R.color.healthy_day_4)
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = editText.text.toString().trim()
                if (name.isEmpty()) {
                    editText.error = getString(R.string.habit_name_required)
                    return@setOnClickListener
                }
                homeViewModel.addHabit(name)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}