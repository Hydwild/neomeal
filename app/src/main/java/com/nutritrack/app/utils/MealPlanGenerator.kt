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
        val goalLabel: String,
        val days: List<DayPlan>
    )

    private val DAY_NAMES = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    fun goalLabel(goal: String): String = when (goal) {
        "mass"  -> "Набор массы"
        "fit"   -> "Поддержание формы"
        "lose"  -> "Снижение веса"
        "dry"   -> "Сушка"
        "light" -> "Лёгкое питание"
        else    -> "План питания"
    }

 /** Сгенерировать план на 7 дней под цель. */
    fun generate(goal: String, dietKey: String = "none"): WeekPlan {
        fun pick(meal: String): List<com.nutritrack.app.data.Recipe> {
            val base = RecipeData.forGoalAndMeal(goal, meal).ifEmpty { RecipeData.forGoal(goal) }
            val filtered = DietFilter.filter(base, dietKey)
            // если под диету ничего не нашлось — берём из всех рецептов, подходящих под диету
            return filtered.ifEmpty {
                DietFilter.filter(RecipeData.forMeal(meal), dietKey)
            }.ifEmpty { base }.shuffled()
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

        return WeekPlan(goal, goalLabel(goal), days)
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

    fun dietRecommendation(dietKey: String): String = when (dietKey) {
        "vegan" -> "Веганское меню: только растительные продукты. Следи за белком (бобовые, тофу, орехи), " +
                "витамином B12 и железом — их стоит добирать дополнительно."
        "vegetarian" -> "Вегетарианское меню: без мяса и рыбы. Яйца и молочные оставлены — они дают белок и B12."
        "no_pork" -> "Меню без свинины и её производных."
        "no_lactose" -> "Меню без молочных продуктов — для тех, кто плохо переносит лактозу."
        "no_gluten" -> "Меню без глютена. Внимание: следы глютена возможны в готовых продуктах — проверяй этикетки."
        "pregnant" -> "Питание при беременности: исключены алкоголь, сырая рыба и потенциально опасные продукты. " +
                "Важны фолиевая кислота, железо, кальций. Это НЕ медицинская рекомендация — обязательно согласуй рацион с врачом."
        "nursing" -> "Питание при ГВ: исключён алкоголь, ограничены частые аллергены. " +
                "Потребности индивидуальны — это НЕ медицинский совет, проконсультируйся с врачом."
        "diabetic" -> "Меню с ограничением быстрых углеводов и сахара. " +
                "Это НЕ замена назначений врача — при диабете рацион и дозировки определяет только специалист."
        "allergic" -> "Из меню убраны самые частые аллергены (орехи, морепродукты, яйца, молоко, соя и др.). " +
                "Это НЕ гарантия безопасности — всегда проверяй состав сам и согласуй с аллергологом."
        else -> ""
    }

 /** Нужен ли усиленный дисклеймер о враче для диеты. */
    fun dietNeedsDisclaimer(dietKey: String): Boolean = DietFilter.byKey(dietKey).medical

 /** Короткая рекомендация под цель (образовательная, без медицины). */
    fun recommendation(goal: String): String = when (goal) {
        "mass" -> "Для набора массы важен профицит калорий и достаточно белка (≈1.6-2 г на кг веса). " +
                "Ешь регулярно, добавь перекусы. Силовые тренировки + питание = рост мышц."
        "fit" -> "Для поддержания формы держи баланс БЖУ и стабильный калораж. " +
                "Сочетай белок, сложные углеводы и полезные жиры в каждом приёме пищи."
        "lose" -> "Для снижения веса нужен умеренный дефицит калорий (≈10-20%). " +
                "Делай упор на белок и овощи — они хорошо насыщают при низкой калорийности. " +
                "Не голодай резко — это вредно и неэффективно."
        "dry" -> "Сушка — это снижение жира с сохранением мышц: высокий белок (≈2-2.5 г на кг), " +
                "мало быстрых углеводов, дефицит калорий. Подходит подготовленным людям с опытом тренировок. " +
                "Это серьёзный режим — при проблемах со здоровьем проконсультируйся со специалистом."
        "light" -> "Лёгкое питание — это мягкие, простые блюда, которые многим комфортны. " +
                "Это НЕ лечебная диета. Если у тебя боли, дискомфорт или хронические проблемы с ЖКТ — " +
                "обязательно обратись к врачу. Самолечение питанием может быть опасным."
        else -> ""
    }

 /** Нужен ли усиленный дисклеймер о враче. */
    fun needsMedicalDisclaimer(goal: String): Boolean = goal == "light"
}
