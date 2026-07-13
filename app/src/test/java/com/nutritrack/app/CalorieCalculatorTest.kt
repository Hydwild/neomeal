package com.nutritrack.app

import com.nutritrack.app.data.db.entity.ProfileEntity
import com.nutritrack.app.utils.CalorieCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты чистого расчёта нормы калорий (Mifflin-St Jeor) и воды.
 * Используют перегрузку с явным adjust, чтобы не зависеть от глобального состояния.
 */
class CalorieCalculatorTest {

    private fun profile(
        gender: String = "male",
        weight: Float = 70f,
        height: Float = 170f,
        age: Int = 25,
        activity: String = "moderate",
        goal: String = "maintain"
    ) = ProfileEntity(
        gender = gender, weight = weight, height = height,
        age = age, activityLevel = activity, goal = goal
    )

    @Test
    fun male_maintain_moderate() {
        // BMR = 10*70 + 6.25*170 - 5*25 + 5 = 1642.5
        // TDEE = 1642.5 * 1.55 = 2545.875 -> 2546
        val goal = CalorieCalculator.calculateDailyGoal(profile(), adjust = 0)
        assertEquals(2546, goal)
    }

    @Test
    fun female_maintain_moderate() {
        // BMR = 10*70 + 6.25*170 - 5*25 - 161 = 1476.5
        // TDEE = 1476.5 * 1.55 = 2288.575 -> 2289
        val goal = CalorieCalculator.calculateDailyGoal(profile(gender = "female"), adjust = 0)
        assertEquals(2289, goal)
    }

    @Test
    fun lose_goal_subtracts_500() {
        val maintain = CalorieCalculator.calculateDailyGoal(profile(goal = "maintain"), 0)
        val lose = CalorieCalculator.calculateDailyGoal(profile(goal = "lose"), 0)
        assertEquals(maintain - 500, lose)
    }

    @Test
    fun gain_goal_adds_500() {
        val maintain = CalorieCalculator.calculateDailyGoal(profile(goal = "maintain"), 0)
        val gain = CalorieCalculator.calculateDailyGoal(profile(goal = "gain"), 0)
        assertEquals(maintain + 500, gain)
    }

    @Test
    fun healthy_goal_subtracts_200() {
        val maintain = CalorieCalculator.calculateDailyGoal(profile(goal = "maintain"), 0)
        val healthy = CalorieCalculator.calculateDailyGoal(profile(goal = "healthy"), 0)
        assertEquals(maintain - 200, healthy)
    }

    @Test
    fun adjustment_is_applied() {
        val base = CalorieCalculator.calculateDailyGoal(profile(), 0)
        val adjusted = CalorieCalculator.calculateDailyGoal(profile(), 300)
        assertEquals(base + 300, adjusted)
    }

    @Test
    fun never_below_1200() {
        // очень маленький профиль + сильное урезание не должно опускать ниже 1200
        val goal = CalorieCalculator.calculateDailyGoal(
            profile(weight = 40f, height = 150f, age = 80, activity = "sedentary", goal = "lose"),
            adjust = -1000
        )
        assertTrue("норма не должна быть ниже 1200", goal >= 1200)
    }

    @Test
    fun activity_levels_increase_goal() {
        val sedentary = CalorieCalculator.calculateDailyGoal(profile(activity = "sedentary"), 0)
        val active = CalorieCalculator.calculateDailyGoal(profile(activity = "very_active"), 0)
        assertTrue("активный должен получать больше калорий", active > sedentary)
    }

    @Test
    fun unknown_activity_defaults_to_moderate() {
        val moderate = CalorieCalculator.calculateDailyGoal(profile(activity = "moderate"), 0)
        val unknown = CalorieCalculator.calculateDailyGoal(profile(activity = "чтотонетакое"), 0)
        assertEquals(moderate, unknown)
    }

    @Test
    fun water_goal_is_35ml_per_kg() {
        val p = profile(weight = 70f)
        assertEquals(2450, CalorieCalculator.calculateWaterGoal(p))
    }
}
