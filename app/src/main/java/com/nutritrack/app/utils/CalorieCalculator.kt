package com.nutritrack.app.utils

import com.nutritrack.app.data.db.entity.ProfileEntity
import kotlin.math.roundToInt

object CalorieCalculator {
    fun calculateDailyGoal(profile: ProfileEntity): Int =
        // Ручная цель пользователя (настройки) перекрывает расчёт
        if (manualCalories > 0) manualCalories
        else calculateDailyGoal(profile, adjustment)

    /**
     * Чистый расчёт дневной нормы калорий (Mifflin-St Jeor).
     * [adjust] — ручная корректировка пользователя. Вынесен параметром,
     * чтобы функцию можно было тестировать без глобального состояния.
     */
    fun calculateDailyGoal(profile: ProfileEntity, adjust: Int): Int {
        // Mifflin-St Jeor
        val bmr = if (profile.gender == "male") {
            (10 * profile.weight + 6.25 * profile.height - 5 * profile.age + 5)
        } else {
            (10 * profile.weight + 6.25 * profile.height - 5 * profile.age - 161)
        }
        val activityMultiplier = when (profile.activityLevel) {
            "sedentary"  -> 1.2
            "light"      -> 1.375
            "moderate"   -> 1.55
            "active"     -> 1.725
            "very_active"-> 1.9
            else         -> 1.55
        }
        val tdee = bmr * activityMultiplier
        val base = when (profile.goal) {
            "lose"    -> (tdee - 500).roundToInt()
            "gain"    -> (tdee + 500).roundToInt()
            "healthy" -> (tdee - 200).roundToInt()
            else      -> tdee.roundToInt()
        }
        return (base + adjust).coerceAtLeast(1200)
    }

    @Volatile var adjustment: Int = 0

    // Ручные цели из настроек (0 = авто). Инициализируются в NutriApp,
    // обновляются из SettingsFragment.
    @Volatile var manualCalories: Int = 0
    @Volatile var manualWater: Int = 0

    // Пользовательский сплит БЖУ, % (сумма 100). Дефолт 30/30/40.
    @Volatile var proteinPct: Int = 30
    @Volatile var fatPct: Int = 30
    @Volatile var carbsPct: Int = 40

 /** Дневная цель белка в граммах (4 ккал/г). */
    fun proteinGoalGrams(calorieGoal: Int): Float =
        calorieGoal * (proteinPct / 100f) / 4f

    fun calculateWaterGoal(profile: ProfileEntity): Int {
        if (manualWater > 0) return manualWater
        // \~35 мл на кг
        return (profile.weight * 35).toInt()
    }
}