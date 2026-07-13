package com.nutritrack.app.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Расчёт «серии дней» (streak): сколько дней подряд калории в коридоре 85–110% нормы.
 * Замороженные дни и режим отпуска серию не рвут, но и не увеличивают.
 * Чистая логика без UI/БД — покрыта юнит-тестами (StreakCalculatorTest).
 */
object StreakCalculator {

    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * @param byDay        калории по датам "yyyy-MM-dd"
     * @param goal         дневная норма калорий
     * @param end          сегодняшняя дата
     * @param frozen       вручную замороженные даты
     * @param vacationOn   включён ли режим отпуска
     * @param vacationStart дата начала отпуска "yyyy-MM-dd" (может быть пустой)
     * @return длина серии с учётом сегодняшнего дня (если сегодня уже в коридоре)
     */
    fun calculate(
        byDay: Map<String, Double>,
        goal: Int,
        end: LocalDate,
        frozen: Set<String>,
        vacationOn: Boolean,
        vacationStart: String
    ): Int {
        fun isFrozen(dStr: String): Boolean =
            dStr in frozen || (vacationOn && vacationStart.isNotEmpty() && dStr >= vacationStart)

        var streak = 0
        var day = end.minusDays(1)
        var guard = 0
        while (guard < 90) {
            guard++
            val dStr = day.format(FMT)
            val ratio = if (goal > 0) (byDay[dStr] ?: 0.0) / goal else 0.0
            when {
                ratio in 0.85..1.10 -> { streak++; day = day.minusDays(1) }
                isFrozen(dStr)      -> { day = day.minusDays(1) } // заморожен — пропускаем, серия не рвётся
                else -> break
            }
        }
        val todayRatio = if (goal > 0) (byDay[end.format(FMT)] ?: 0.0) / goal else 0.0
        return if (todayRatio in 0.85..1.10) streak + 1 else streak
    }
}
