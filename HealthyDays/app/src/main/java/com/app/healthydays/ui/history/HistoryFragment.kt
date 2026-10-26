package com.app.healthydays.ui.history

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.healthydays.R
import com.app.healthydays.data.entity.Day
import com.app.healthydays.data.entity.HabitCategory
import com.app.healthydays.databinding.FragmentHistoryBinding
import com.prolificinteractive.materialcalendarview.CalendarDay
import kotlinx.coroutines.launch
import java.time.LocalDate

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()

    private lateinit var historyAdapter: HistoryAdapter

    private var selectedDay: CalendarDay? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHistoryBinding.inflate(
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

        setupRecyclerView()
        setupCalendar()
        observeViewModel()

        // Cargar todos los días registrados
        viewModel.loadDays()
    }

    private fun setupRecyclerView() {
        val prefs = requireContext().getSharedPreferences(
            "habit_order",
            android.content.Context.MODE_PRIVATE
        )

        val savedOrder = prefs.getString("category_order", null)

        val validCategoryIds = HabitCategory.entries.map { it.id }

        val categoryOrder = savedOrder
            ?.split(",")
            ?.filter { it in validCategoryIds }
            ?.let { saved ->
                (saved + validCategoryIds).distinct()
            }
            ?: validCategoryIds

        historyAdapter = HistoryAdapter(
            habits = emptyList(),
            categoryOrder = categoryOrder
        )

        binding.recyclerHistoryHabits.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("DefaultLocale")
    private fun setupCalendar() {
        val today = CalendarDay.today()
        selectedDay = today
        binding.calendarView.setSelectedDate(today)

        val locale = resources.configuration.locales[0]
        binding.calendarView.setTitleFormatter { day ->
            val month = java.time.YearMonth.of(day.year, day.month)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", locale)

            month.atDay(1).format(formatter).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
            }
        }

        binding.calendarView.setOnDateChangedListener { _, date, selected ->

            if (selected) {
                selectedDay = date

                val selectedDate = String.format(
                    "%04d-%02d-%02d",
                    date.year,
                    date.month,
                    date.day
                )

                viewModel.loadDay(selectedDate)
                updateCalendarDecorators(viewModel.days.value)
            }
        }

        // Cargar el día de hoy al entrar en History
        binding.calendarView.post {
            selectedDay = today

            binding.calendarView.clearSelection()
            binding.calendarView.setSelectedDate(today)

            val todayString = LocalDate.now().toString()
            viewModel.loadDay(todayString)
            updateCalendarDecorators(viewModel.days.value)
        }
    }

    private fun observeViewModel() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                // Hábitos del día seleccionado
                launch {
                    viewModel.habits.collect { habits ->
                        historyAdapter.updateData(habits)

                        binding.recyclerHistoryHabits.visibility =
                            if (habits.isEmpty()) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }

                        binding.noHabitsMessage.visibility =
                            if (habits.isEmpty()) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                    }
                }

                // Rating del día seleccionado
                launch {
                    viewModel.selectedDay.collect { day ->
                        if (day != null) {
                            updateRating(day.rating)
                        } else {
                            updateRating(-1)
                        }
                    }
                }

                // Días registrados y sus colores
                launch {
                    viewModel.days.collect { days ->
                        updateCalendarDecorators(days)
                    }
                }
            }
        }
    }

    private fun updateCalendarDecorators(days: List<Day>) {

        binding.calendarView.removeDecorators()

        for (day in days) {

            val parts = day.date.split("-")

            if (parts.size != 3) continue

            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val dayOfMonth = parts[2].toInt()

            val calendarDay = CalendarDay.from(
                year,
                month,
                dayOfMonth
            )

            val color = when {
                // 0 - 49 → rojo
                day.rating < 50 -> {
                    ContextCompat.getColor(requireContext(), R.color.sad_color)
                }

                // 50 - 74 → naranja
                day.rating < 75 -> {
                    ContextCompat.getColor(requireContext(), R.color.meh_color)
                }

                // 75 - 100 → verde
                else -> {
                    ContextCompat.getColor(requireContext(), R.color.happy_color)
                }
            }

            binding.calendarView.addDecorator(RatingDayDecorator(calendarDay, color))
        }

        // Círculo alrededor de HOY
        val today = CalendarDay.today()
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.today_circle)

        if (drawable != null) {
            binding.calendarView.addDecorator(
                TodayDecorator(today, drawable, selectedDay)
            )
        }

        binding.calendarView.invalidateDecorators()
    }

    private fun updateRating(rating: Int) {
        if (rating == -1) {
            binding.dailyRating.textRating.text = "-"
        }
        else {
            binding.dailyRating.textRating.text = rating.toString()
        }
        when {
            rating < 0 -> {
                binding.dailyRating.imageEmoji.setImageResource(R.drawable.meh_face)
                binding.dailyRating.imageEmoji.setColorFilter(
                    requireContext().getColor(R.color.gray))
                binding.dailyRating.textRating.setTextColor(
                    requireContext().getColor(R.color.gray))
            }

            rating <= 49 -> {
                binding.dailyRating.imageEmoji.setImageResource(R.drawable.sad_face)
                binding.dailyRating.imageEmoji.setColorFilter(
                    requireContext().getColor(R.color.sad_color))
                binding.dailyRating.textRating.setTextColor(
                    requireContext().getColor(R.color.sad_color))
            }

            rating <= 74 -> {
                binding.dailyRating.imageEmoji.setImageResource(R.drawable.meh_face)
                binding.dailyRating.imageEmoji.setColorFilter(
                    requireContext().getColor(R.color.meh_color))
                binding.dailyRating.textRating.setTextColor(
                    requireContext().getColor(R.color.meh_color))
            }

            else -> {
                binding.dailyRating.imageEmoji.setImageResource(R.drawable.happy_face)
                binding.dailyRating.imageEmoji.setColorFilter(
                    requireContext().getColor(R.color.happy_color))
                binding.dailyRating.textRating.setTextColor(
                    requireContext().getColor(R.color.happy_color))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}