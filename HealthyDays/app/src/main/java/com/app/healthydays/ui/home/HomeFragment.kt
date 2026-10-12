package com.app.healthydays.ui.home

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.healthydays.R
import com.app.healthydays.databinding.FragmentHomeBinding
import com.app.healthydays.databinding.ItemRatingBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var habitAdapter: HabitAdapter

    private lateinit var dailyBinding: ItemRatingBinding


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(
            inflater,
            container,
            false
        )

        // Binding del include del rating
        dailyBinding = ItemRatingBinding.bind(
            binding.dailyRating.root
        )


        // RecyclerView
        habitAdapter = HabitAdapter(emptyList()) { habitId, isChecked ->

            homeViewModel.updateHabitChecked(
                habitId,
                isChecked
            )
        }

        binding.recyclerHabits.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = habitAdapter
        }

        binding.editHabits.setOnClickListener {
            findNavController().navigate(
                R.id.action_nav_home_to_editHabitsFragment
            )
        }

        return binding.root
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val today = LocalDate.now().toString()

        // Primero preparamos el día
        homeViewModel.initializeDay(today)

        // Después observamos los hábitos del día
        viewLifecycleOwner.lifecycleScope.launch {
            homeViewModel.observeHabits(today).collectLatest { habits ->
                // Actualizar RecyclerView
                habitAdapter.updateData(habits)

                // Calcular rating
                val completed = habits.count { it.isCompleted }
                val total =habits.size
                val percentage =
                    if (total > 0) {
                        (completed * 100) / total
                    } else {
                        0
                    }


                // Actualizar porcentaje
                dailyBinding.textRating.text = "$percentage%"
                homeViewModel.updateDailyRating(percentage)


                // Actualizar emoji y color
                when (percentage) {
                    in 0..49 -> {
                        dailyBinding.imageEmoji.setImageResource(R.drawable.sad_face)
                        dailyBinding.imageEmoji.setColorFilter(
                                requireContext().getColor(R.color.sad_color))
                        dailyBinding.textRating.setTextColor(
                                requireContext().getColor(R.color.sad_color))
                    }

                    in 50..75 -> {

                        dailyBinding.imageEmoji.setImageResource(R.drawable.meh_face)
                        dailyBinding.imageEmoji.setColorFilter(
                                requireContext().getColor(R.color.meh_color))
                        dailyBinding.textRating.setTextColor(
                                requireContext().getColor(R.color.meh_color))
                    }

                    in 76..100 -> {
                        dailyBinding.imageEmoji.setImageResource(R.drawable.happy_face)
                        dailyBinding.imageEmoji.setColorFilter(
                                requireContext().getColor(R.color.happy_color))
                        dailyBinding.textRating.setTextColor(
                                requireContext().getColor(R.color.happy_color))
                    }
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}