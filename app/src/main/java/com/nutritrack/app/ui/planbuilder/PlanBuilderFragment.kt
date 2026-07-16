package com.nutritrack.app.ui.planbuilder

import com.nutritrack.app.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nutritrack.app.data.Recipe
import com.nutritrack.app.databinding.FragmentPlanBuilderBinding
import com.nutritrack.app.utils.MealPlanGenerator
import androidx.navigation.fragment.NavHostFragment
import com.nutritrack.app.utils.navigateAnim

/**
 * Конструктор плана питания.
 * Выбор цели рекомендация недельное меню с раскрытием рецептов.
 * Безопасная версия: без лечебных диет, с дисклеймером о враче для "лёгкого питания".
 */
class PlanBuilderFragment : Fragment() {

    private var _binding: FragmentPlanBuilderBinding? = null
    private val binding get() = _binding!!

    private var selectedGoal: String? = null
    private var selectedDiet: String = "none"
    private var lastPlan: MealPlanGenerator.WeekPlan? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlanBuilderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.goalMass.setOnClickListener  { selectGoal("mass") }
        binding.goalFit.setOnClickListener   { selectGoal("fit") }
        binding.goalLose.setOnClickListener  { selectGoal("lose") }
        binding.goalLight.setOnClickListener { selectGoal("light") }
        binding.goalDry.setOnClickListener   { selectGoal("dry") }

        binding.dietNone.setOnClickListener       { selectDiet("none") }
        binding.dietVegan.setOnClickListener      { selectDiet("vegan") }
        binding.dietVegetarian.setOnClickListener { selectDiet("vegetarian") }
        binding.dietNoPork.setOnClickListener     { selectDiet("no_pork") }
        binding.dietNoLactose.setOnClickListener  { selectDiet("no_lactose") }
        binding.dietNoGluten.setOnClickListener   { selectDiet("no_gluten") }
        binding.dietPregnant.setOnClickListener   { selectDiet("pregnant") }
        binding.dietNursing.setOnClickListener    { selectDiet("nursing") }
        binding.dietDiabetic.setOnClickListener   { selectDiet("diabetic") }
        binding.dietAllergic.setOnClickListener   { selectDiet("allergic") }
        selectDiet("none")

        binding.btnGenerate.setOnClickListener {
            selectedGoal?.let { generateWeek(it) }
        }
        binding.btnReshuffle.setOnClickListener {
            selectedGoal?.let {
                generateWeek(it)
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.pb_new_menu), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnVitaminSet.setOnClickListener {
            try {
                NavHostFragment.findNavController(this).navigateAnim(R.id.nutrientGoalFragment)
            } catch (_: Exception) {}
        }
    }

 /** Спросить про уведомления и загрузить блюда дня в план питания. */
    private fun askAndLoadToPlan(day: MealPlanGenerator.DayPlan) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pb_load_title))
            .setMessage(getString(R.string.pb_add_day_q, dayLabel(day.dayName)))
            .setPositiveButton(getString(R.string.pb_yes_reminders)) { _, _ -> loadToPlan(day, withReminders = true) }
            .setNeutralButton(getString(R.string.pb_no_reminders)) { _, _ -> loadToPlan(day, withReminders = false) }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

    private fun loadToPlan(day: MealPlanGenerator.DayPlan, withReminders: Boolean) {
        val db = (requireActivity().application as com.nutritrack.app.NutriApp).database
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        val profileId = prefs.activeProfileId
        val today = com.nutritrack.app.utils.DateUtils.today()

        // Слот: ключ, дефолтное время, блюдо
        val mapping = listOf(
            Triple("breakfast", "08:00", day.breakfast),
            Triple("lunch",     "13:00", day.lunch),
            Triple("dinner",    "19:00", day.dinner),
            Triple("snack",     "16:00", day.snack)
        )

        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mapping.forEach { (key, defTime, recipe) ->
                if (recipe == null) return@forEach
                val existing = db.mealPlanDao().getMealPlanByKey(key, today, profileId)
                val time = existing?.time ?: defTime
                // id слота (создаём или обновляем)
                val mealId: Long = if (existing != null) {
                    db.mealPlanDao().updateMealPlan(existing.copy(name = recipe.name, time = time))
                    existing.id
                } else {
                    db.mealPlanDao().insertMealPlan(
                        com.nutritrack.app.data.db.entity.MealPlanEntity(
                            name = recipe.name, time = time,
                            date = today, profileId = profileId, mealKey = key
                        )
                    )
                }
                // Очищаем старые позиции слота и добавляем блюдо с КБЖУ (1 порция)
                db.mealPlanDao().deleteItemsForMeal(mealId)
                db.mealPlanDao().insertMealItem(
                    com.nutritrack.app.data.db.entity.MealItemEntity(
                        mealPlanId  = mealId,
                        productId   = -1L,                 // -1 = блюдо из конструктора (нет в базе продуктов)
                        productName = recipe.name,
                        grams       = 100f,                // условная порция «1 блюдо»
                        calories    = recipe.caloriesPerServing.toFloat(),
                        protein     = recipe.proteinPerServing.toFloat(),
                        fat         = recipe.fatPerServing.toFloat(),
                        carbs       = recipe.carbsPerServing.toFloat()
                    )
                )
                if (withReminders) {
                    com.nutritrack.app.utils.ReminderScheduler.schedule(
                        requireContext(), key, time, recipe.name, getString(R.string.pb_time_for, recipe.name)
                    )
                    // Сохраняем флаг — чтобы ползунок в плане питания отобразился включённым
                    prefs.setBool("meal_notif_$key", true)
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val msg = if (withReminders) getString(R.string.pb_loaded_notif)
                else getString(R.string.pb_loaded)
                android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectGoal(goal: String) {
        selectedGoal = goal
        val chips = mapOf(
            "mass" to binding.goalMass, "fit" to binding.goalFit,
            "lose" to binding.goalLose, "light" to binding.goalLight,
            "dry" to binding.goalDry
        )
        chips.forEach { (g, chip) ->
            chip.setBackgroundResource(
                if (g == goal) com.nutritrack.app.R.drawable.chip_selected
                else com.nutritrack.app.R.drawable.chip_bg
            )
        }

        updateRecommendationAndDisclaimer()

        binding.btnGenerate.visibility = View.VISIBLE
        binding.tvHint.visibility = View.GONE
    }

    private fun selectDiet(diet: String) {
        selectedDiet = diet
        val chips = mapOf(
            "none" to binding.dietNone, "vegan" to binding.dietVegan,
            "vegetarian" to binding.dietVegetarian, "no_pork" to binding.dietNoPork,
            "no_lactose" to binding.dietNoLactose, "no_gluten" to binding.dietNoGluten,
            "pregnant" to binding.dietPregnant, "nursing" to binding.dietNursing,
            "diabetic" to binding.dietDiabetic, "allergic" to binding.dietAllergic
        )
        chips.forEach { (d, chip) ->
            chip.setBackgroundResource(
                if (d == diet) com.nutritrack.app.R.drawable.chip_selected
                else com.nutritrack.app.R.drawable.chip_bg
            )
        }
        updateRecommendationAndDisclaimer()
    }

 /** Показывает рекомендацию (цель + диета) и медицинский дисклеймер при необходимости. */
    private fun updateRecommendationAndDisclaimer() {
        val goal = selectedGoal
        val parts = mutableListOf<String>()
        if (goal != null) parts.add(MealPlanGenerator.recommendation(requireContext(), goal))
        val dietRec = MealPlanGenerator.dietRecommendation(requireContext(), selectedDiet)
        if (dietRec.isNotBlank()) parts.add(dietRec)

        if (parts.isNotEmpty()) {
            binding.cardRecommendation.visibility = View.VISIBLE
            binding.tvRecommendation.text = parts.joinToString("\n\n")
        }

        val needsMed = (goal != null && MealPlanGenerator.needsMedicalDisclaimer(goal)) ||
                MealPlanGenerator.dietNeedsDisclaimer(selectedDiet)
        binding.cardDisclaimer.visibility = if (needsMed) View.VISIBLE else View.GONE
        if (needsMed && MealPlanGenerator.dietNeedsDisclaimer(selectedDiet)) {
            binding.tvDisclaimer.text = getString(R.string.pb_medical_warn)
        }
    }

    private fun generateWeek(goal: String) {
        val plan = MealPlanGenerator.generate(goal, selectedDiet)
        lastPlan = plan
        binding.containerWeek.removeAllViews()

        val header = TextView(requireContext()).apply {
            text = getString(R.string.pb_week_menu_fmt,
                MealPlanGenerator.goalLabel(requireContext(), plan.goal))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(12))
        }
        binding.containerWeek.addView(header)

        plan.days.forEach { day ->
            binding.containerWeek.addView(buildDayCard(day))
        }

        val note = TextView(requireContext()).apply {
            text = getString(R.string.pb_menu_hint) + " " + getString(R.string.pb_tap_dish)
            setTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            textSize = 12f
            setPadding(dp(4), dp(12), dp(4), dp(20))
        }
        binding.containerWeek.addView(note)
        binding.btnReshuffle.visibility = View.VISIBLE
    }

    private fun buildDayCard(day: MealPlanGenerator.DayPlan): View {
        val card = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = dp(16).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(android.graphics.Color.parseColor("#22193C"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
            layoutParams = lp
        }
        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val dayHeader = TextView(requireContext()).apply {
            text = "📅 ${dayLabel(day.dayName)}"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(2))
        }
        inner.addView(dayHeader)

        val daySummary = TextView(requireContext()).apply {
            text = getString(R.string.pb_day_totals_fmt, day.totalCalories, day.totalProtein)
            setTextColor(android.graphics.Color.parseColor("#B39DDB"))
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        }
        inner.addView(daySummary)

        addMealRow(inner, getString(R.string.meal_breakfast2), day.breakfast)
        addMealRow(inner, getString(R.string.meal_lunch2), day.lunch)
        addMealRow(inner, getString(R.string.meal_dinner2), day.dinner)
        if (day.snack != null) addMealRow(inner, getString(R.string.meal_snack2), day.snack)

        // Кнопка загрузки именно этого дня
        val loadBtn = TextView(requireContext()).apply {
            text = getString(R.string.pb_load_day)
            setTextColor(android.graphics.Color.parseColor("#B388FF"))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), android.graphics.Color.parseColor("#B388FF"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }
            layoutParams = lp
            setPadding(0, dp(11), 0, dp(11))
            isClickable = true
            setOnClickListener { askAndLoadToPlan(day) }
        }
        inner.addView(loadBtn)

        card.addView(inner)
        return card
    }

    private fun addMealRow(parent: LinearLayout, label: String, recipe: Recipe?) {
        if (recipe == null) return
        // Цвет акцента по типу приёма
        val accent = when (label) {
            getString(R.string.meal_breakfast2) -> "#FF9E80"
            getString(R.string.meal_lunch2)    -> "#69F0AE"
            getString(R.string.meal_dinner2)    -> "#B388FF"
            else      -> "#40C4FF"
        }

        // Внешняя строка-карточка
        val rowCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(android.graphics.Color.parseColor("#1AFFFFFF"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(3)) }
            layoutParams = lp
            setPadding(dp(2), dp(8), dp(12), dp(8))
            isClickable = true
            setOnClickListener { showRecipeDialog(recipe) }
        }

        // Цветная полоска слева
        val accentBar = android.view.View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), dp(38)).apply {
                setMargins(0, 0, dp(10), 0)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(android.graphics.Color.parseColor(accent))
            }
        }
        rowCard.addView(accentBar)

        // Текстовый блок (название + ккал)
        val textCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val labelTv = TextView(requireContext()).apply {
            text = "$label"
            setTextColor(android.graphics.Color.parseColor(accent))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val nameTv = TextView(requireContext()).apply {
            text = "${recipe.emoji} ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), recipe.name)}"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
        }
        val kcalTv = TextView(requireContext()).apply {
            text = getString(R.string.pb_meal_kcal_fmt, recipe.caloriesPerServing, recipe.proteinPerServing)
            setTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            textSize = 11f
        }
        textCol.addView(labelTv)
        textCol.addView(nameTv)
        textCol.addView(kcalTv)
        rowCard.addView(textCol)

        // Стрелка
        val arrow = TextView(requireContext()).apply {
            text = "›"
            setTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            textSize = 22f
        }
        rowCard.addView(arrow)

        parent.addView(rowCard)
    }

    private fun showRecipeDialog(recipe: Recipe) {
        val sb = StringBuilder()
        sb.append(getString(R.string.pb_dlg_time_fmt, recipe.timeMinutes, recipe.servings))
        sb.append(getString(R.string.pb_dlg_kcal_fmt, recipe.caloriesPerServing, recipe.proteinPerServing))
        sb.append(getString(R.string.pb_macro_serving, recipe.fatPerServing.toString(), recipe.carbsPerServing.toString()))

        sb.append(getString(R.string.pb_ingredients_hdr))
        recipe.ingredients.forEach { sb.append("  • ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), it.name)} — ${it.amount}\n") }

        sb.append(getString(R.string.pb_steps_hdr))
        recipe.steps.forEachIndexed { i, step -> sb.append("  ${i + 1}. ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), step)}\n") }

        recipe.tip?.let { sb.append("\n💡 ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), it)}") }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${recipe.emoji} ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), recipe.name)}")
            .setMessage(sb.toString())
            .setPositiveButton(getString(R.string.c_close), null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun dayLabel(name: String): String = when (name) {
        "Понедельник" -> getString(R.string.day_mon); "Вторник" -> getString(R.string.day_tue)
        "Среда" -> getString(R.string.day_wed); "Четверг" -> getString(R.string.day_thu)
        "Пятница" -> getString(R.string.day_fri); "Суббота" -> getString(R.string.day_sat)
        "Воскресенье" -> getString(R.string.day_sun); else -> name
    }

}
