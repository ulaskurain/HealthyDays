package com.app.healthydays.ui.stats

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.healthydays.R
import com.app.healthydays.data.dto.StatsRange
import com.app.healthydays.data.dto.StatsResult
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}