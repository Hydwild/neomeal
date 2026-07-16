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

 /**
 * Сгенерировать план на 7 дней под цель.
 * [favorites] — id избранных рецептов: они ставятся в начало ротации,
 * поэтому попадают в первые дни недели.
 */
    fun generate(goal: String, dietKey: String = "none", favorites: Set<String> = emptySet()): WeekPlan {
        fun pick(meal: String): List<com.nutritrack.app.data.Recipe> {
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
        val breakfasts = pick("breakfast")
        val lunches    = pick("lunch")
        val dinners    = pick("dinner")
        val snacks     = pick("snack")

        val days = (0 until 7).map { i ->
            DayPlan(
                dayName   = DAY_NAMES[i],
                breakfast = breakfasts.getOrNull(i % breakfasts.size.coerceAtLeast(1)),
                lunch     = lunches.getOrNull(i % lunches.size.coerceAtLeast(1)),
                dinner    = dinners.getOrNull((i + 1) % dinners.size.coerceAtLeast(1)),  // сдвиг чтобы обед≠ужин
                snack     = if (snacks.isNotEmpty()) snacks[i % snacks.size] else null
            )
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
        "pregnant" -> com.nutritrack.app.R.string.diet_pregnant
        "nursing" -> com.nutritrack.app.R.string.diet_nursing
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
            "pregnant"   -> com.nutritrack.app.R.string.pbg_dietrec_pregnant
            "nursing"    -> com.nutritrack.app.R.string.pbg_dietrec_nursing
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
