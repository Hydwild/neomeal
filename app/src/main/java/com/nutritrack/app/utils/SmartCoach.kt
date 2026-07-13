package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.WeightEntryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** «Умный тренер»: анализ динамики веса предложение коррекции нормы. */
object SmartCoach {

    data class Suggestion(val deltaKcal: Int, val message: String)

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun suggest(context: Context, goal: String, history: List<WeightEntryEntity>): Suggestion? {
        if (history.size < 2) return null
        val latest = history.first()
        val latestDate = LocalDate.parse(latest.date, fmt)
        val past = history.firstOrNull {
            val d = ChronoUnit.DAYS.between(LocalDate.parse(it.date, fmt), latestDate)
            d in 10..21
        } ?: return null
        val days = ChronoUnit.DAYS.between(LocalDate.parse(past.date, fmt), latestDate).toInt()
        if (days <= 0) return null
        val deltaKg = latest.weight - past.weight
        val perWeek = deltaKg / days * 7f

        return when (goal) {
            "lose", "healthy" -> when {
                perWeek > -0.1f -> Suggestion(-150,
                    context.getString(R.string.sc_lose_slow_fmt, days, fmtKg(context, deltaKg)))
                perWeek < -1.2f -> Suggestion(+100,
                    context.getString(R.string.sc_lose_fast_fmt, fmtKg(context, deltaKg), days))
                else -> null
            }
            "gain" -> when {
                perWeek < 0.1f -> Suggestion(+150,
                    context.getString(R.string.sc_gain_slow_fmt, days, fmtKg(context, deltaKg)))
                perWeek > 0.8f -> Suggestion(-100,
                    context.getString(R.string.sc_gain_fast_fmt, fmtKg(context, deltaKg), days))
                else -> null
            }
            else -> when {
                deltaKg > 1.0f -> Suggestion(-150,
                    context.getString(R.string.sc_maintain_up_fmt, fmtKg(context, deltaKg), days))
                deltaKg < -1.0f -> Suggestion(+150,
                    context.getString(R.string.sc_maintain_down_fmt, fmtKg(context, -deltaKg), days))
                else -> null
            }
        }
    }

    private fun fmtKg(context: Context, v: Float) = context.getString(R.string.sc_kg_fmt, v)
}
