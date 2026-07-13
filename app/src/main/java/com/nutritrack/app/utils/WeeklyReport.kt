package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Локальный недельный отчёт по правилам (без сети и LLM).
 * Принимает посчитанные данные за неделю и выдаёт список инсайтов.
 * Вся логика — на числах; тексты берутся из strings (RU/EN).
 */
object WeeklyReport {

 /** Данные за один день недели. */
    data class DayData(
        val date: String,
        val calories: Float,
        val protein: Float,
        val waterMl: Int
    )

 /** Входные данные для отчёта. */
    data class Input(
        val days: List<DayData>,
        val calorieGoal: Int,
        val proteinGoalG: Float,   // дневная цель по белку, г
        val waterGoalMl: Int,
        val weightStart: Float?,   // вес в начале периода (старейшая запись)
        val weightEnd: Float?      // вес в конце периода (свежайшая запись)
    )

 /** Один инсайт: эмодзи-заголовок + (опционально) рекомендация. */
    data class Insight(val title: String, val advice: String?)

    fun build(ctx: Context, input: Input): List<Insight> {
        val out = mutableListOf<Insight>()
        val daysWithFood = input.days.filter { it.calories > 0f }

        // Вода
        if (input.waterGoalMl > 0 && input.days.isNotEmpty()) {
            val ratios = input.days.map { it.waterMl.toFloat() / input.waterGoalMl }
            val avgPct = (ratios.average() * 100).roundToInt()
            val daysHit = ratios.count { it >= 1f }
            when {
                avgPct >= 90 -> out += Insight(
                    ctx.getString(R.string.rep_water_good_fmt, avgPct), null)
                avgPct >= 60 -> out += Insight(
                    ctx.getString(R.string.rep_water_mid_fmt, avgPct),
                    ctx.getString(R.string.rep_water_mid_advice))
                else -> out += Insight(
                    ctx.getString(R.string.rep_water_low_fmt, avgPct, daysHit),
                    ctx.getString(R.string.rep_water_low_advice))
            }
        }

        // Вес
        val ws = input.weightStart
        val we = input.weightEnd
        if (ws != null && we != null) {
            val diff = we - ws
            val diffStr = (if (diff > 0f) "+" else "") + String.format("%.1f", diff)
            out += when {
                abs(diff) < 0.1f -> Insight(ctx.getString(R.string.rep_weight_stable), null)
                diff < 0f -> Insight(ctx.getString(R.string.rep_weight_down_fmt, diffStr), null)
                else -> Insight(ctx.getString(R.string.rep_weight_up_fmt, diffStr), null)
            }
        }

        // Белок: максимальная серия дней подряд с недобором
        if (input.proteinGoalG > 0f && daysWithFood.isNotEmpty()) {
            val threshold = input.proteinGoalG * 0.8f   // <80% цели = недобор
            var streak = 0
            var maxStreak = 0
            for (d in input.days) {
                if (d.calories > 0f && d.protein < threshold) {
                    streak++; maxStreak = maxOf(maxStreak, streak)
                } else streak = 0
            }
            if (maxStreak >= 3) {
                out += Insight(
                    ctx.getString(R.string.rep_protein_low_fmt, maxStreak),
                    ctx.getString(R.string.rep_protein_low_advice))
            } else {
                val avgProt = daysWithFood.map { it.protein }.average().roundToInt()
                if (avgProt >= input.proteinGoalG * 0.9f)
                    out += Insight(ctx.getString(R.string.rep_protein_good_fmt, avgProt), null)
            }
        }

        // Калории: стабильность относительно цели
        if (input.calorieGoal > 0 && daysWithFood.size >= 3) {
            val avgCal = daysWithFood.map { it.calories }.average().roundToInt()
            val deviation = abs(avgCal - input.calorieGoal).toFloat() / input.calorieGoal
            if (deviation <= 0.1f) {
                out += Insight(ctx.getString(R.string.rep_cal_stable_fmt, avgCal), null)
            } else if (avgCal > input.calorieGoal) {
                out += Insight(
                    ctx.getString(R.string.rep_cal_over_fmt, avgCal, input.calorieGoal),
                    ctx.getString(R.string.rep_cal_over_advice))
            } else {
                out += Insight(
                    ctx.getString(R.string.rep_cal_under_fmt, avgCal, input.calorieGoal),
                    ctx.getString(R.string.rep_cal_under_advice))
            }
        }

        // Регулярность ведения дневника
        if (input.days.isNotEmpty()) {
            val logged = daysWithFood.size
            val total = input.days.size
            if (logged < total - 1) {
                out += Insight(
                    ctx.getString(R.string.rep_logging_fmt, logged, total),
                    ctx.getString(R.string.rep_logging_advice))
            }
        }

        if (out.isEmpty()) {
            out += Insight(ctx.getString(R.string.rep_empty), ctx.getString(R.string.rep_empty_advice))
        }
        return out
    }
}
