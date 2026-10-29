package com.app.healthydays.ui.stats

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.healthydays.R
import com.app.healthydays.data.dto.FilterableHabit
import com.app.healthydays.data.dto.StatsRange
import com.app.healthydays.data.dto.StatsResult
import com.app.healthydays.data.entity.HabitCategory
import com.app.healthydays.databinding.DialogStatsFilterBinding
import com.app.healthydays.databinding.FragmentStatsBinding
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels()

    private lateinit var habitAdapter: StatsHabitAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        habitAdapter = StatsHabitAdapter()
        binding.recyclerHabitStats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = habitAdapter
        }

        setupRangeSelector()
        binding.fabFilter.setOnClickListener { showFilterDialog() }
        observeViewModel()

        viewModel.load(viewModel.selectedRange)
    }

    private fun setupRangeSelector() {
        // Marcar el botón antes de añadir el listener, para no disparar una carga extra.
        binding.toggleRange.check(buttonIdFor(viewModel.selectedRange))
        updateRangeButtons(binding.toggleRange.checkedButtonId)

        binding.toggleRange.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) {
                // Evitar que quede el grupo sin ninguna opción seleccionada.
                if (group.checkedButtonId == View.NO_ID) group.check(checkedId)
                return@addOnButtonCheckedListener
            }
            updateRangeButtons(checkedId)
            viewModel.load(rangeFor(checkedId))
        }
    }

    private fun updateRangeButtons(checkedId: Int) {
        val context = requireContext()

        listOf(
            binding.buttonRange7,
            binding.buttonRange30,
            binding.buttonRangeAll
        ).forEach { button ->
            val selected = button.id == checkedId

            val background = ContextCompat.getColor(
                context,
                if (selected) R.color.stats_range_selected_bg
                else R.color.stats_range_unselected_bg
            )
            val text = ContextCompat.getColor(
                context,
                if (selected) R.color.stats_range_selected_text
                else R.color.stats_range_unselected_text
            )

            button.backgroundTintList = ColorStateList.valueOf(background)
            button.setTextColor(text)
        }
    }

    private fun buttonIdFor(range: StatsRange): Int = when (range) {
        StatsRange.LAST_7_DAYS -> R.id.buttonRange7
        StatsRange.LAST_30_DAYS -> R.id.buttonRange30
        StatsRange.ALL -> R.id.buttonRangeAll
    }

    private fun rangeFor(buttonId: Int): StatsRange = when (buttonId) {
        R.id.buttonRange30 -> StatsRange.LAST_30_DAYS
        R.id.buttonRangeAll -> StatsRange.ALL
        else -> StatsRange.LAST_7_DAYS
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.result.collect { result ->
                    if (result != null) {
                        showSummary(result)
                        showChart(result)
                        showHabits(result)
                        showFilterState(result)
                    }
                }
            }
        }
    }

    private fun showSummary(result: StatsResult) {
        val summary = result.summary
        val locale = Locale.getDefault()

        binding.textAverageValue.text = summary.averagePercentage
            ?.let { String.format(locale, "%d%%", it.roundToInt()) }
            ?: "-"

        binding.textPerfectDaysValue.text =
            String.format(locale, "%d", summary.perfectDays)

        binding.textDaysWithDataValue.text =
            String.format(locale, "%d", summary.daysWithData)
    }

    private fun showChart(result: StatsResult) {
        binding.dailyChart.setData(result.days)

        val hasData = result.summary.daysWithData > 0
        binding.textChartNoData.visibility = if (hasData) View.GONE else View.VISIBLE
    }

    private fun showHabits(result: StatsResult) {
        habitAdapter.updateData(result.habits)

        val hasHabits = result.habits.isNotEmpty()
        binding.recyclerHabitStats.visibility = if (hasHabits) View.VISIBLE else View.GONE
        binding.textHabitsNoData.visibility = if (hasHabits) View.GONE else View.VISIBLE
    }

    private fun showFilterState(result: StatsResult) {
        val total = result.availableHabits.size
        val active = result.activeHabitCount

        // Sin hábitos en el historial no hay nada que filtrar.
        binding.fabFilter.visibility = if (total > 0) View.VISIBLE else View.GONE

        // El indicador solo aparece si hay un filtro activo.
        val filtering = total > 0 && active < total
        binding.textFilterInfo.visibility = if (filtering) View.VISIBLE else View.GONE
        if (filtering) {
            binding.textFilterInfo.text =
                getString(R.string.stats_filter_showing, active, total)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showFilterDialog() {
        val available = viewModel.result.value?.availableHabits.orEmpty()
        if (available.isEmpty()) return

        val context = requireContext()
        val dialogBinding = DialogStatsFilterBinding.inflate(layoutInflater)
        val container = dialogBinding.filterContainer

        // Copia de trabajo: no cambia nada hasta pulsar Aceptar.
        val excluded = viewModel.excludedHabitIds.toMutableSet()

        val tint = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.stats_chart_line)
        )
        val textColor = ContextCompat.getColor(context, R.color.stats_range_unselected_text)
        val density = resources.displayMetrics.density

        fun isActive(habit: FilterableHabit) = habit.habitIds.any { it !in excluded }

        fun setActive(habit: FilterableHabit, active: Boolean) {
            if (active) excluded.removeAll(habit.habitIds.toSet())
            else excluded.addAll(habit.habitIds)
        }

        val habitBoxes = mutableMapOf<FilterableHabit, CheckBox>()
        val categoryUpdaters = mutableListOf<() -> Unit>()

        val knownIds = HabitCategory.entries.map { it.id }.toSet()
        val byCategory = available.groupBy {
            if (it.categoryId in knownIds) it.categoryId else HabitCategory.OTHER.id
        }

        // Mismo orden de categorías que en Home.
        val categoriesInOrder = viewModel.getCategoryOrder().mapNotNull { id ->
            HabitCategory.entries.firstOrNull { it.id == id }
        }

        categoriesInOrder.forEach { category ->
            val habits = byCategory[category.id] ?: return@forEach

            val categoryBox = CheckBox(context).apply {
                buttonTintList = tint
                setTextColor(textColor)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }

            val updateCategory = {
                val active = habits.count { isActive(it) }
                categoryBox.isChecked = active == habits.size
                categoryBox.text =
                    "${getString(category.nameResId)} ($active/${habits.size})"
            }
            categoryUpdaters.add(updateCategory)

            categoryBox.setOnClickListener {
                // Tras el toque, isChecked ya tiene el estado nuevo.
                val check = categoryBox.isChecked
                habits.forEach { habit ->
                    setActive(habit, check)
                    habitBoxes[habit]?.isChecked = check
                }
                updateCategory()
            }

            container.addView(categoryBox)

            // Los hábitos ya vienen ordenados como en Home (sortOrder).
            habits.forEach { habit ->
                val habitBox = CheckBox(context).apply {
                    text = habit.name
                    buttonTintList = tint
                    setTextColor(textColor)
                    textSize = 15f
                    setPadding((24 * density).toInt(), paddingTop, paddingRight, paddingBottom)
                    isChecked = isActive(habit)
                    setOnClickListener {
                        setActive(habit, isChecked)
                        updateCategory()
                    }
                }
                habitBoxes[habit] = habitBox
                container.addView(habitBox)
            }

            updateCategory()
        }

        fun setAll(active: Boolean) {
            available.forEach { habit ->
                setActive(habit, active)
                habitBoxes[habit]?.isChecked = active
            }
            categoryUpdaters.forEach { it() }
        }

        dialogBinding.buttonSelectAll.setOnClickListener { setAll(true) }
        dialogBinding.buttonSelectNone.setOnClickListener { setAll(false) }

        AlertDialog.Builder(context, R.style.Theme_HealthyDays_AlertDialog)
            .setTitle(R.string.stats_filter_title)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.setExcludedHabits(excluded)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}