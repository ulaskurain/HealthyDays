
package com.app.healthydays.ui.home

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.healthydays.R
import com.app.healthydays.data.entity.HabitCategory
import com.app.healthydays.databinding.FragmentEditHabitsBinding
import kotlinx.coroutines.launch
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

class EditHabitsFragment : Fragment() {

    private var _binding: FragmentEditHabitsBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var adapter: EditHabitAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

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

        homeViewModel = ViewModelProvider(
            requireActivity()
        )[HomeViewModel::class.java]

        setupRecyclerView()
        observeHabits()

        binding.buttonAddHabit.setOnClickListener {
            showAddHabitDialog()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRecyclerView() {
        adapter = EditHabitAdapter(
            habits = emptyList(),
            onDeleteClick = { habit: com.app.healthydays.data.entity.Habit ->
                homeViewModel.deleteHabit(habit)
            },
            categoryOrder = homeViewModel.getCategoryOrder()
        )


        binding.recyclerEditHabits.layoutManager =
            LinearLayoutManager(requireContext())

        binding.recyclerEditHabits.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                if (fromPosition == RecyclerView.NO_POSITION ||
                    toPosition == RecyclerView.NO_POSITION
                ) {
                    return false
                }

                return adapter.moveItem(fromPosition, toPosition)
            }

            override fun onSwiped(
                viewHolder: RecyclerView.ViewHolder,
                direction: Int
            ) = Unit

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                homeViewModel.saveCategoryOrder(adapter.getCategoryOrder())

                homeViewModel.reorderHabits(
                    adapter.getOrderedHabitsForSaving()
                )
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerEditHabits)
    }

    private fun observeHabits() {
        viewLifecycleOwner.lifecycleScope.launch {
            homeViewModel.getAllHabits().collect { habits ->
                adapter.updateData(habits)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun showAddHabitDialog() {
        val categories = HabitCategory.entries.toList()
        val categoryOrder = homeViewModel.getCategoryOrder()
        val orderedCategories = categories.sortedBy { category ->
            val index = categoryOrder.indexOf(category.id)
            if (index == -1) Int.MAX_VALUE else index
        }

        var selectedCategory = HabitCategory.OTHER

        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.habit_name)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(MAX_HABIT_NAME_LENGTH))
        }

        val categoryButton = Button(requireContext()).apply {
            isAllCaps = false
            setBackgroundTintList(
                ContextCompat.getColorStateList(
                    requireContext(),
                    R.color.category_button_background
                )
            )
            gravity = Gravity.START or Gravity.CENTER_VERTICAL

            fun updateCategory(category: HabitCategory) {
                text = getString(category.nameResId)

                val icon = ContextCompat.getDrawable(
                    requireContext(),
                    categoryIcon(category)
                )?.mutate()

                icon?.setTint(ContextCompat.getColor(requireContext(), R.color.icon_background))
                icon?.setBounds(0, 0, dp(24), dp(24))
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = dp(12)
            }

            updateCategory(selectedCategory)

            setOnClickListener {
                showCategoryPicker(orderedCategories, selectedCategory) { category ->
                    selectedCategory = category
                    updateCategory(category)
                }
            }
        }

        val categoryTitle = TextView(requireContext()).apply {
            text = getString(R.string.category)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(8))
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            addView(
                editText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(categoryTitle)
            addView(
                categoryButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_habit)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()

        dialog.setOnShowListener {
            val accent = ContextCompat.getColor(
                requireContext(),
                R.color.healthy_day_4
            )

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setTextColor(accent)
                setOnClickListener {
                    val name = editText.text.toString().trim()

                    if (name.isEmpty()) {
                        editText.error = getString(R.string.habit_name_required)
                        return@setOnClickListener
                    }

                    homeViewModel.addHabit(
                        name = name,
                        categoryId = selectedCategory.id
                    )
                    dialog.dismiss()
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent)
        }

        dialog.show()
    }

    private fun showCategoryPicker(
        orderedCategories: List<HabitCategory>,
        currentCategory: HabitCategory,
        onSelected: (HabitCategory) -> Unit
    ) {
        val context = requireContext()
        val accent = ContextCompat.getColor(context, R.color.healthy_day_4)

        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(
            android.R.attr.colorBackground,
            typedValue,
            true
        )

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val picker = AlertDialog.Builder(context)
            .setTitle(R.string.category)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .create()

        orderedCategories.forEach { category ->
            val isSelected = category == currentCategory

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isClickable = true
                isFocusable = true

                background = android.util.TypedValue().let {
                    val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                    val typedArray = context.obtainStyledAttributes(attrs)
                    val drawable = typedArray.getDrawable(0)
                    typedArray.recycle()
                    drawable
                }

                if (isSelected) {
                    setBackgroundColor(
                        ContextCompat.getColor(context, R.color.selected_habit)
                    )
                }
            }

            val icon = ImageView(context).apply {
                setImageResource(categoryIcon(category))
                scaleType = ImageView.ScaleType.FIT_CENTER
                imageTintList = ContextCompat.getColorStateList(
                    context,
                    R.color.icon_background
                )
                adjustViewBounds = false
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            }

            val name = TextView(context).apply {
                setText(category.nameResId)
                textSize = 16f
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            row.addView(icon)
            row.addView(name)

            row.setOnClickListener {
                onSelected(category)
                picker.dismiss()
            }

            container.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }
            )
        }

        picker.show()
        picker.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent)
    }

    private fun categoryIcon(category: HabitCategory): Int =
        when (category) {
            HabitCategory.PHYSICAL_HEALTH -> R.drawable.ic_health
            HabitCategory.NUTRITION -> R.drawable.ic_nutrition
            HabitCategory.MENTAL_WELLBEING -> R.drawable.ic_mental
            HabitCategory.LEARNING -> R.drawable.ic_learning
            HabitCategory.SOCIAL -> R.drawable.ic_social
            HabitCategory.PRODUCTIVITY -> R.drawable.ic_work
            HabitCategory.HOME -> R.drawable.ic_home
            HabitCategory.HOBBIES -> R.drawable.ic_hobbies
            HabitCategory.OTHER -> R.drawable.ic_others
        }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        itemTouchHelper. attachToRecyclerView(null)
        _binding = null
        super.onDestroyView()
    }
}
