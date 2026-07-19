package com.nutritrack.app.utils

import com.nutritrack.app.data.Recipe
import com.nutritrack.app.data.RecipeData

/**
 * Генератор недельного плана питания под выбранную цель.
 * Безопасная версия: НЕ лечебные диеты, НЕ медицинские назначения.
 * "Лёгкое питание" — просто мягкие, нейтральные блюда + дисклеймер о враче.
 */
object MealPlanGenerator {

    data class DayPlan(
        val dayName: String,
        val breakfast: Recipe?,
        val lunch: Recipe?,
        val dinner: Recipe?,
        val snack: Recipe?
    ) {
        val totalCalories: Int
            get() = listOfNotNull(breakfast, lunch, dinner, snack)
                .sumOf { it.caloriesPerServing }
        val totalProtein: Int
            get() = listOfNotNull(breakfast, lunch, dinner, snack)
                .sumOf { it.proteinPerServing }
    }

    data class WeekPlan(
        val goal: String,
        val days: List<DayPlan>
    )

    // Ключи дней — как в dayLabel() у PlanBuilderFragment
    private val DAY_NAMES = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

 /** Подпись цели по текущей локали. */
    fun goalLabel(ctx: android.content.Context, goal: String): String = ctx.getString(when (goal) {
        "mass"  -> com.nutritrack.app.R.string.pbg_goal_mass
        "fit"   -> com.nutritrack.app.R.string.pbg_goal_fit
        "lose"  -> com.nutritrack.app.R.string.pbg_goal_lose
        "dry"   -> com.nutritrack.app.R.string.pbg_goal_dry
        "light" -> com.nutritrack.app.R.string.pbg_goal_light
        else    -> com.nutritrack.app.R.string.pbg_goal_default
    })

    // Доли дневной нормы калорий по слотам (используются, если задан [dailyCalorieTarget]).
    private const val SHARE_BREAKFAST = 0.25
    private const val SHARE_LUNCH = 0.35
    private const val SHARE_DINNER = 0.30
    private const val SHARE_SNACK = 0.10

 /**
 * Сгенерировать план на 7 дней под цель.
 *
 * [favorites] — id избранных рецептов: они ставятся в начало ротации,
 * поэтому попадают в первые дни недели.
 * [dailyCalorieTarget] — если задан, блюда в каждом слоте подбираются так,
 * чтобы день был ближе к дневной норме (по долям слотов). Если null —
 * прежняя ротация без калорийной подгонки.
 *
 * Разнообразие: блюдо не повторяется в течение недели, пока в пуле есть
 * неиспользованные варианты; ужин не совпадает с обедом того же дня.
 */
    fun generate(
        goal: String,
        dietKey: String = "none",
        favorites: Set<String> = emptySet(),
        dailyCalorieTarget: Int? = null
    ): WeekPlan {
        fun pool(meal: String): List<com.nutritrack.app.data.Recipe> {
            val base = RecipeData.forGoalAndMeal(goal, meal).ifEmpty { RecipeData.forGoal(goal) }
            val filtered = DietFilter.filter(base, dietKey)
            // если под диету ничего не нашлось — берём из всех рецептов, подходящих под диету
            return filtered.ifEmpty {
                DietFilter.filter(RecipeData.forMeal(meal), dietKey)
            }.ifEmpty { base }
                .shuffled()
                // стабильная сортировка: избранные вперёд, порядок внутри групп сохраняется
                .sortedByDescending { it.id in favorites }
        }
        val breakfasts = pool("breakfast")
        val lunches    = pool("lunch")
        val dinners    = pool("dinner")
        val snacks     = pool("snack")

        // Использованные за неделю id — отдельно по каждому приёму, чтобы блюда,
        // помеченные несколькими типами, не «съедали» свежесть чужого слота.
        val usedBreakfast = mutableSetOf<String>()
        val usedLunch = mutableSetOf<String>()
        val usedDinner = mutableSetOf<String>()
        val usedSnack = mutableSetOf<String>()

        fun choose(
            pool: List<com.nutritrack.app.data.Recipe>,
            used: MutableSet<String>,
            slotTarget: Int?,
            dayIndex: Int,
            avoid: com.nutritrack.app.data.Recipe? = null
        ): com.nutritrack.app.data.Recipe? {
            if (pool.isEmpty()) return null
            // Избранное — в первые дни: на день 0 отдаём ещё не использованное избранное из пула.
            if (dayIndex == 0) {
                pool.firstOrNull { it.id in favorites && it.id !in used }?.let {
                    used.add(it.id); return it
                }
            }
            // Свежие (не повторявшиеся) кандидаты; если исчерпаны — весь пул (кроме avoid).
            val fresh = pool.filter { it.id !in used && it.id != avoid?.id }
            val candidates = fresh.ifEmpty {
                pool.filter { it.id != avoid?.id }.ifEmpty { pool }
            }
            val chosen = if (slotTarget != null) {
                candidates.minByOrNull { kotlin.math.abs(it.caloriesPerServing - slotTarget) }!!
            } else {
                candidates[dayIndex % candidates.size]
            }
            used.add(chosen.id)
            return chosen
        }

        fun slot(share: Double) = dailyCalorieTarget?.let { (it * share).toInt() }

        val days = (0 until 7).map { i ->
            val breakfast = choose(breakfasts, usedBreakfast, slot(SHARE_BREAKFAST), i)
            val lunch     = choose(lunches, usedLunch, slot(SHARE_LUNCH), i)
            // ужин ≠ обед того же дня
            val dinner    = choose(dinners, usedDinner, slot(SHARE_DINNER), i, avoid = lunch)
            val snack     = choose(snacks, usedSnack, slot(SHARE_SNACK), i)
            DayPlan(DAY_NAMES[i], breakfast, lunch, dinner, snack)
        }

        return WeekPlan(goal, days)
    }

 /** Рекомендация-подсказка под диетическую группу. */
    fun dietTitle(ctx: android.content.Context, dietKey: String): String = ctx.getString(when (dietKey) {
        "vegan" -> com.nutritrack.app.R.string.diet_vegan
        "vegetarian" -> com.nutritrack.app.R.string.diet_vegetarian
        "no_pork" -> com.nutritrack.app.R.string.diet_no_pork
        "no_lactose" -> com.nutritrack.app.R.string.diet_no_lactose
        "no_gluten" -> com.nutritrack.app.R.string.diet_no_gluten
        "diabetic" -> com.nutritrack.app.R.string.diet_diabetic
        "allergic" -> com.nutritrack.app.R.string.diet_allergic
        else -> com.nutritrack.app.R.string.diet_none
    })

    fun dietRecommendation(ctx: android.content.Context, dietKey: String): String {
        val res = when (dietKey) {
            "vegan"      -> com.nutritrack.app.R.string.pbg_dietrec_vegan
            "vegetarian" -> com.nutritrack.app.R.string.pbg_dietrec_vegetarian
            "no_pork"    -> com.nutritrack.app.R.string.pbg_dietrec_no_pork
            "no_lactose" -> com.nutritrack.app.R.string.pbg_dietrec_no_lactose
            "no_gluten"  -> com.nutritrack.app.R.string.pbg_dietrec_no_gluten
            "diabetic"   -> com.nutritrack.app.R.string.pbg_dietrec_diabetic
            "allergic"   -> com.nutritrack.app.R.string.pbg_dietrec_allergic
            else -> return ""
        }
        return ctx.getString(res)
    }

 /** Нужен ли усиленный дисклеймер о враче для диеты. */
    fun dietNeedsDisclaimer(dietKey: String): Boolean = DietFilter.byKey(dietKey).medical

 /** Короткая рекомендация под цель (образовательная, без медицины). */
    fun recommendation(ctx: android.content.Context, goal: String): String {
        val res = when (goal) {
            "mass"  -> com.nutritrack.app.R.string.pbg_rec_mass
            "fit"   -> com.nutritrack.app.R.string.pbg_rec_fit
            "lose"  -> com.nutritrack.app.R.string.pbg_rec_lose
            "dry"   -> com.nutritrack.app.R.string.pbg_rec_dry
            "light" -> com.nutritrack.app.R.string.pbg_rec_light
            else -> return ""
        }
        return ctx.getString(res)
    }

 /** Нужен ли усиленный дисклеймер о враче. */
    fun needsMedicalDisclaimer(goal: String): Boolean = goal == "light"
}
