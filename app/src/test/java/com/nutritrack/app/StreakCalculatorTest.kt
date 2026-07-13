package com.nutritrack.app

import com.nutritrack.app.utils.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 7, 3)
    private fun d(minus: Long) = today.minusDays(minus).toString() // yyyy-MM-dd

    @Test
    fun no_data_zero_streak() {
        assertEquals(0, StreakCalculator.calculate(emptyMap(), 2000, today, emptySet(), false, ""))
    }

    @Test
    fun three_days_in_corridor() {
        val byDay = mapOf(d(1) to 2000.0, d(2) to 1900.0, d(3) to 2100.0)
        assertEquals(3, StreakCalculator.calculate(byDay, 2000, today, emptySet(), false, ""))
    }

    @Test
    fun today_in_corridor_adds_one() {
        val byDay = mapOf(d(0) to 2000.0, d(1) to 2000.0)
        assertEquals(2, StreakCalculator.calculate(byDay, 2000, today, emptySet(), false, ""))
    }

    @Test
    fun below_85_percent_breaks_streak() {
        // вчера норм, позавчера 50% -> серия 1
        val byDay = mapOf(d(1) to 2000.0, d(2) to 1000.0, d(3) to 2000.0)
        assertEquals(1, StreakCalculator.calculate(byDay, 2000, today, emptySet(), false, ""))
    }

    @Test
    fun above_110_percent_breaks_streak() {
        val byDay = mapOf(d(1) to 2000.0, d(2) to 2500.0)
        assertEquals(1, StreakCalculator.calculate(byDay, 2000, today, emptySet(), false, ""))
    }

    @Test
    fun frozen_day_does_not_break() {
        // d2 пустой, но заморожен: d1 и d3 в норме -> серия 2
        val byDay = mapOf(d(1) to 2000.0, d(3) to 2000.0)
        assertEquals(2, StreakCalculator.calculate(byDay, 2000, today, setOf(d(2)), false, ""))
    }

    @Test
    fun frozen_day_does_not_count() {
        // только заморозки без еды -> 0
        assertEquals(0, StreakCalculator.calculate(emptyMap(), 2000, today, setOf(d(1), d(2)), false, ""))
    }

    @Test
    fun vacation_freezes_from_start_date() {
        // отпуск с d2: d2 и d3 пустые, но >= vacStart... d3 < d2 -> d3 не заморожен
        // d1 в норме, d2 в отпуске (пропуск), d3 пустой и НЕ в отпуске -> серия 1
        val byDay = mapOf(d(1) to 2000.0)
        assertEquals(1, StreakCalculator.calculate(byDay, 2000, today, emptySet(), true, d(2)))
    }

    @Test
    fun zero_goal_no_crash() {
        assertEquals(0, StreakCalculator.calculate(mapOf(d(1) to 2000.0), 0, today, emptySet(), false, ""))
    }

    @Test
    fun boundary_85_and_110_inclusive() {
        val byDay = mapOf(d(1) to 1700.0, d(2) to 2200.0) // ровно 0.85 и 1.10
        assertEquals(2, StreakCalculator.calculate(byDay, 2000, today, emptySet(), false, ""))
    }
}
