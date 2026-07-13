package com.nutritrack.app.ui.fasting

import com.nutritrack.app.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.nutritrack.app.data.Recipe
import com.nutritrack.app.data.RecipeData
import com.nutritrack.app.databinding.FragmentFastingModeBinding
import com.nutritrack.app.utils.PrefsManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Режим поста — нейтральный инструмент для постящихся.
 * Пользователь сам отмечает период (любая традиция/религия), приложение:
 * показывает постные (растительные) блюда, считает дни, напоминает.
 * НЕ устанавливает религиозные правила.
 */
class FastingModeFragment : Fragment() {

    private var _binding: FragmentFastingModeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager

    private val KEY_ENABLED  = "fasting_mode_enabled"
    private val KEY_START    = "fasting_start_date"
    private val KEY_END      = "fasting_end_date"
    private val KEY_REMINDER = "fasting_reminder_enabled"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFastingModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())

        val enabled = prefs.getBool(KEY_ENABLED, false)
        binding.switchFasting.isChecked = enabled
        binding.switchFastingReminder.isChecked = prefs.getBool(KEY_REMINDER, false)
        updateUi()

        binding.switchFasting.setOnCheckedChangeListener { _, checked ->
            prefs.setBool(KEY_ENABLED, checked)
            updateUi()
        }
        binding.switchFastingReminder.setOnCheckedChangeListener { _, checked ->
            prefs.setBool(KEY_REMINDER, checked)
        }
        binding.rowStartDate.setOnClickListener { pickDate(true) }
        binding.rowEndDate.setOnClickListener { pickDate(false) }

        renderRecipes()
    }

    private fun pickDate(isStart: Boolean) {
        val cal = Calendar.getInstance()
        val existing = prefs.getString(if (isStart) KEY_START else KEY_END, "")
        if (existing.isNotEmpty()) {
            try {
                val d = LocalDate.parse(existing)
                cal.set(d.year, d.monthValue - 1, d.dayOfMonth)
            } catch (_: Exception) {}
        }
        android.app.DatePickerDialog(
            requireContext(),
            { _, y, m, day ->
                val picked = String.format("%04d-%02d-%02d", y, m + 1, day)
                if (isStart) prefs.setString(KEY_START, picked)
                else prefs.setString(KEY_END, picked)
                updateUi()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateUi() {
        val enabled = prefs.getBool(KEY_ENABLED, false)
        binding.tvFastingState.text = if (enabled) getString(R.string.fm_enabled) else getString(R.string.fm_disabled)
        binding.datesBlock.visibility = if (enabled) View.VISIBLE else View.GONE

        val start = prefs.getString(KEY_START, "")
        val end   = prefs.getString(KEY_END, "")
        binding.tvStartDate.text = if (start.isNotEmpty()) humanize(start) else "—"
        binding.tvEndDate.text   = if (end.isNotEmpty()) humanize(end) else "—"

        // Счётчик дней
        if (enabled && start.isNotEmpty() && end.isNotEmpty()) {
            try {
                val s = LocalDate.parse(start)
                val e = LocalDate.parse(end)
                val today = LocalDate.now()
                val totalDays = (java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1).toInt()

                when {
                    today.isBefore(s) -> {
                        val daysToStart = java.time.temporal.ChronoUnit.DAYS.between(today, s).toInt()
                        binding.cardCounter.visibility = View.VISIBLE
                        binding.tvCounterBig.text = getString(R.string.fasting_starts_in, daysToStart, resources.getQuantityString(R.plurals.days_streak, daysToStart))
                        binding.tvCounterSub.text = getString(R.string.fm_not_started)
                    }
                    today.isAfter(e) -> {
                        binding.cardCounter.visibility = View.VISIBLE
                        binding.tvCounterBig.text = getString(R.string.fm_finished_big)
                        binding.tvCounterSub.text = getString(R.string.fm_finished_sub)
                    }
                    else -> {
                        val dayNum = (java.time.temporal.ChronoUnit.DAYS.between(s, today) + 1).toInt()
                        val left = (java.time.temporal.ChronoUnit.DAYS.between(today, e)).toInt()
                        binding.cardCounter.visibility = View.VISIBLE
                        binding.tvCounterBig.text = getString(R.string.fm_day_of_fmt, dayNum, totalDays)
                        binding.tvCounterSub.text = if (left > 0) getString(R.string.fasting_left, left, resources.getQuantityString(R.plurals.days_streak, left)) else getString(R.string.fasting_last_day)
                    }
                }
            } catch (_: Exception) {
                binding.cardCounter.visibility = View.GONE
            }
        } else {
            binding.cardCounter.visibility = View.GONE
        }
    }

    private fun renderRecipes() {
        binding.containerRecipes.removeAllViews()
        RecipeData.lentenRecipes().forEach { recipe ->
            binding.containerRecipes.addView(buildRecipeCard(recipe))
        }
    }

    private fun buildRecipeCard(recipe: Recipe): View {
        val card = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = dp(16).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(android.graphics.Color.parseColor("#22193C"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
            layoutParams = lp
            isClickable = true
            setOnClickListener { showRecipeDialog(recipe) }
        }
        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val title = TextView(requireContext()).apply {
            text = "${recipe.emoji} ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), recipe.name)}"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val info = TextView(requireContext()).apply {
            text = context.getString(R.string.fm_card_macro_fmt, recipe.caloriesPerServing, recipe.proteinPerServing, recipe.fatPerServing, recipe.carbsPerServing, recipe.timeMinutes)
            setTextColor(android.graphics.Color.parseColor("#B39DDB"))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        inner.addView(title); inner.addView(info)
        card.addView(inner)
        return card
    }

    private fun showRecipeDialog(recipe: Recipe) {
        val sb = StringBuilder()
        sb.append(getString(R.string.fm_time_serv_fmt, recipe.timeMinutes, recipe.servings))
        sb.append(getString(R.string.fm_kcal_prot_fmt, recipe.caloriesPerServing, recipe.proteinPerServing))
        sb.append(getString(R.string.fm_macro_serving_fmt, recipe.fatPerServing.toString(), recipe.carbsPerServing.toString()) + "\n\n")
        sb.append(getString(R.string.fm_ingredients))
        recipe.ingredients.forEach { sb.append(getString(R.string.fm_ingredient_line_fmt, com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), it.name), it.amount)) }
        sb.append(getString(R.string.fm_cooking))
        recipe.steps.forEachIndexed { i, step -> sb.append("  ${i + 1}. ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), step)}\n") }
        recipe.tip?.let { sb.append("\n💡 ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), it)}") }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${recipe.emoji} ${com.nutritrack.app.utils.RecipeLabel.tr(requireContext(), recipe.name)}")
            .setMessage(sb.toString())
            .setPositiveButton(getString(R.string.c_close2), null)
            .show()
    }

    private fun humanize(date: String): String {
        return try {
            val d = LocalDate.parse(date)
            val months = arrayOf("янв","фев","мар","апр","мая","июн","июл","авг","сен","окт","ноя","дек")
            "${d.dayOfMonth} ${months[d.monthValue - 1]}"
        } catch (_: Exception) { date }
    }


    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
