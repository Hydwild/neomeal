package com.nutritrack.app.ui.dashboard

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentDashboardBinding
import com.nutritrack.app.utils.ActiveProfile
import com.nutritrack.app.utils.CalorieCalculator
import com.nutritrack.app.utils.DialogHelper
import com.nutritrack.app.utils.HapticHelper
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.SmartCoach
import com.nutritrack.app.utils.StreakCalculator
import com.nutritrack.app.utils.StreakFreeze
import com.nutritrack.app.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * «Умные» блоки главного экрана: серия дней (streak) с заморозкой, умное
 * приветствие по вчерашним данным, периодическая подсказка Умного тренера.
 * Вынесено из DashboardFragment.
 *
 * [bindingOf] возвращает актуальный binding или null (view уничтожена) —
 * async-колбэки безопасно завершаются, если экран уже закрыт.
 */
class SmartInsightsController(
    private val fragment: Fragment,
    private val bindingOf: () -> FragmentDashboardBinding?,
    private val vm: DashboardViewModel,
) {
    private val ctx get() = fragment.requireContext()
    private val prefs = PrefsManager(fragment.requireContext())
    private val db get() = (fragment.requireActivity().application as NutriApp).database
    private val scope get() = fragment.viewLifecycleOwner.lifecycleScope
    private fun s(id: Int, vararg a: Any) = ctx.getString(id, *a)
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Серия дней в норме калорий

    fun loadStreak() {
        val goal = vm.calorieGoal.value ?: 2000
        scope.launch(Dispatchers.IO) {
            val profileId = ActiveProfile.id(ctx, db)
            val end = LocalDate.now()
            val start = end.minusDays(60)
            val entries = db.diaryDao().getEntriesForRange(start.format(fmt), end.format(fmt), profileId)
            val byDay = entries.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { e -> e.calories.toDouble() } }
            val shown = StreakCalculator.calculate(
                byDay = byDay, goal = goal, end = end,
                frozen = prefs.frozenDates, vacationOn = prefs.vacationMode, vacationStart = prefs.vacationStart
            )
            withContext(Dispatchers.Main) {
                val b = bindingOf() ?: return@withContext
                if ("streak" in prefs.hiddenDashboardCards) { b.tvStreak.visibility = View.GONE; return@withContext }
                val frostMark = if (StreakFreeze.isYesterdayFrozen(prefs)) " ❄️" else ""
                when {
                    prefs.vacationMode -> {
                        b.tvStreak.visibility = View.VISIBLE
                        b.tvStreak.text = s(R.string.ds_streak_vac)
                    }
                    shown >= 2 -> {
                        b.tvStreak.visibility = View.VISIBLE
                        b.tvStreak.text = s(R.string.ds_streak_fmt, shown, daysWord(shown)) + frostMark
                    }
                    else -> b.tvStreak.visibility = View.GONE
                }
                b.tvStreak.setOnClickListener {
                    if (prefs.vacationMode) return@setOnClickListener
                    StreakFreeze.requestFreeze(ctx, prefs, it) { loadStreak() }
                }
            }
        }
    }

    private fun daysWord(n: Int): String {
        val m10 = n % 10; val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> s(R.string.ds_day1)
            m10 in 2..4 && m100 !in 12..14 -> s(R.string.ds_day24)
            else -> s(R.string.ds_day5)
        }
    }

    // Умное приветствие по вчерашним данным

    fun loadSmartGreeting() {
        val calGoal = vm.calorieGoal.value ?: 2000
        val waterGoal = vm.waterGoal.value ?: 2000
        scope.launch(Dispatchers.IO) {
            val profileId = ActiveProfile.id(ctx, db)
            val yesterday = LocalDate.now().minusDays(1).format(fmt)
            val entries = db.diaryDao().getEntriesForDateAndProfile(yesterday, profileId)
            val yCal = entries.sumOf { it.calories.toDouble() }
            val yWater = try {
                db.waterDao().getWaterForDateSync(yesterday, profileId).sumOf { it.amountMl }
            } catch (_: Exception) { -1 }
            val insight: String? = when {
                entries.isEmpty() -> null
                yWater in 1 until (waterGoal * 0.6).toInt() -> s(R.string.ds_y_water_low)
                yCal > 0 && (yCal / calGoal) in 0.85..1.10 -> s(R.string.ds_y_ok)
                yCal > calGoal * 1.25 -> s(R.string.ds_y_much)
                yCal in 1.0..(calGoal * 0.6) -> s(R.string.ds_y_low)
                else -> null
            }
            withContext(Dispatchers.Main) {
                val b = bindingOf() ?: return@withContext
                if (insight != null) b.tvGreetingSub.text = insight
            }
        }
    }

    // Умный тренер (раз в 14 дней)

    fun checkSmartCoach() {
        if (!prefs.smartCoachEnabled) return
        val today = LocalDate.now()
        val last = prefs.lastCoachCheck
        if (last.isNotEmpty() && ChronoUnit.DAYS.between(LocalDate.parse(last, fmt), today) < 14) return
        val profile = vm.activeProfile.value ?: return
        scope.launch(Dispatchers.IO) {
            val history = db.weightDao().getAllWeightSync(profile.id)
            val suggestion = SmartCoach.suggest(ctx, profile.goal, history)
            withContext(Dispatchers.Main) {
                if (bindingOf() == null) return@withContext
                if (suggestion == null) { prefs.lastCoachCheck = today.format(fmt); return@withContext }
                val sign = if (suggestion.deltaKcal > 0) "+" else ""
                val d = AlertDialog.Builder(ctx)
                    .setTitle(s(R.string.ds_coach_title))
                    .setMessage(suggestion.message + s(R.string.ds_coach_new_norm_fmt,
                        (vm.calorieGoal.value ?: 2000) + suggestion.deltaKcal))
                    .setPositiveButton(s(R.string.ds_coach_apply_fmt, "$sign${suggestion.deltaKcal}")) { _, _ ->
                        prefs.calorieAdjustment += suggestion.deltaKcal
                        CalorieCalculator.adjustment = prefs.calorieAdjustment
                        prefs.lastCoachCheck = today.format(fmt)
                        HapticHelper.success(fragment.view)
                        Toast.makeText(ctx, s(R.string.ds_coach_updated), Toast.LENGTH_SHORT).show()
                        fragment.requireActivity().recreate()
                    }
                    .setNegativeButton(s(R.string.ds_coach_later)) { _, _ -> prefs.lastCoachCheck = today.format(fmt) }
                    .create()
                d.setOnShowListener { DialogHelper.tint(d, ctx) }
                d.show()
            }
        }
    }
}
