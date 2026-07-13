package com.nutritrack.app.utils

import com.nutritrack.app.data.db.entity.DailyGoalEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Подсчёт серий (streak) выполнения норм по дням на основе daily_goals.
 * Серию по коридору калорий (85–110%, с заморозками) считает [StreakCalculator].
 */
object DailyGoalStreaks {

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

 /**
 * Серия выполнения нормы КАЛОРИЙ подряд, считая от сегодня назад.
 * Прерывается на первом дне без выполнения или пропущенной дате.
 */
    fun calorieStreak(days: List<DailyGoalEntity>): Int =
        streak(days) { it.calorieGoalMet }

 /** Серия выполнения нормы ВОДЫ подряд. */
    fun waterStreak(days: List<DailyGoalEntity>): Int =
        streak(days) { it.waterGoalMet }

 /** Серия "идеальных" дней (калории И вода) подряд. */
    fun perfectStreak(days: List<DailyGoalEntity>): Int =
        streak(days) { it.calorieGoalMet && it.waterGoalMet }

 /**
 * Универсальный подсчёт: сколько подряд идущих дат (начиная с сегодня
 * или вчера) удовлетворяют условию [condition].
 *
 * days — список фактов по дням (порядок не важен, отсортируем сами).
 */
    private fun streak(
        days: List<DailyGoalEntity>,
        condition: (DailyGoalEntity) -> Boolean
    ): Int {
        if (days.isEmpty()) return 0

        // Мапа дата -> запись
        val byDate = days.associateBy { it.date }

        val cal = Calendar.getInstance()
        // Начинаем с сегодня
        var cursor = fmt.format(cal.time)

        // Если сегодня ещё нет записи или норма не выполнена,
        // допускаем старт со вчера (день мог быть не закрыт)
        if (byDate[cursor]?.let(condition) != true) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            cursor = fmt.format(cal.time)
            if (byDate[cursor]?.let(condition) != true) return 0
        }

        var count = 0
        while (true) {
            val entry = byDate[cursor]
            if (entry != null && condition(entry)) {
                count++
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cursor = fmt.format(cal.time)
            } else {
                break
            }
        }
        return count
    }
}
