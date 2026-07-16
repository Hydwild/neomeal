package com.nutritrack.app.utils

import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.ProductEntity

/**
 * Генератор рекомендаций по продуктам для выполнения дневной нормы калорий.
 * Подбирает РЕАЛИСТИЧНЫЕ порции обычных продуктов под остаток калорий.
 */
object MealRecommender {

    data class Suggestion(
        val product: ProductEntity,
        val grams: Int,
        val calories: Int,
        val reason: String
    )

    data class Recommendation(
        val goalCalories: Int,
        val eatenCalories: Int,
        val remaining: Int,
        val progressPercent: Int,
        val progressLine: String,
        val headline: String,
        val detail: String,
        val suggestions: List<Suggestion>
    )

    // Реалистичные диапазоны порций (граммы) по категориям: min..max
    private val portionRange: Map<String, IntRange> = mapOf(
        "Мясо"         to 80..200,
        "Птица"        to 80..200,
        "Рыба"         to 80..200,
        "Морепродукты" to 60..150,
        "Яйца"         to 50..150,   // 1-3 яйца
        "Молочные"     to 100..250,
        "Крупы"        to 50..200,   // в сухом виде меньше, но усредняем
        "Хлеб"         to 30..100,
        "Овощи"        to 100..300,
        "Фрукты"       to 100..250,
        "Ягоды"        to 50..150,
        "Орехи"        to 20..50,    // орехи калорийные — порция маленькая
        "Бобовые"      to 80..200,
        "Масла"        to 5..20,     // масло — чайная-столовая ложка
        "Сладкое"      to 20..60,
        "Напитки"      to 100..300,
        "Соусы"        to 10..40,
        "Спортпит"     to 30..60
    )

    // Продукты-деликатесы / неподходящие для "обычной" рекомендации (по ключевым словам)
    private val skipKeywords = listOf(
        "икра", "трюфел", "фуа", "устриц", "омар", "лобстер",
        "хамон", "пармезан", "моцарелла буффало", "вагю", "мраморн"
    )

    fun generate(
        ctx: android.content.Context,
        goalCalories: Int,
        eatenCalories: Int,
        eatenProtein: Float,
        goal: String,
        allProducts: List<ProductEntity>
    ): Recommendation {
        val remaining = goalCalories - eatenCalories
        val percent   = if (goalCalories > 0)
            ((eatenCalories.toFloat() / goalCalories) * 100f).toInt() else 0
        val progressLine = ctx.getString(R.string.mr_progress_fmt, eatenCalories, goalCalories, percent)

        if (remaining <= -50) {
            return Recommendation(
                goalCalories, eatenCalories, remaining, percent, progressLine,
                headline = ctx.getString(R.string.mr_over_fmt, -remaining),
                detail = ctx.getString(when (goal) {
                    "lose" -> R.string.mr_over_lose
                    "gain" -> R.string.mr_over_gain
                    else   -> R.string.mr_over_default
                }),
                suggestions = emptyList()
            )
        }

        if (remaining in -49..120) {
            return Recommendation(
                goalCalories, eatenCalories, remaining, percent, progressLine,
                headline = ctx.getString(R.string.mr_done),
                detail = ctx.getString(R.string.mr_done_detail_fmt, remaining),
                suggestions = pickProducts(ctx, remaining.coerceAtLeast(60), goal, allProducts, light = true)
            )
        }

        val proteinTarget   = goalCalories * 0.30f / 4f
        val needMoreProtein = eatenProtein < proteinTarget * 0.6f

        val detail = buildString {
            append(ctx.getString(when (goal) {
                "lose"    -> R.string.mr_goal_lose
                "gain"    -> R.string.mr_goal_gain
                "healthy" -> R.string.mr_goal_healthy
                else      -> R.string.mr_goal_default
            }))
            append(' ')
            append(ctx.getString(if (needMoreProtein) R.string.mr_protein_low else R.string.mr_protein_ok))
        }

        return Recommendation(
            goalCalories, eatenCalories, remaining, percent, progressLine,
            headline = ctx.getString(R.string.mr_left_fmt, remaining),
            detail = detail,
            suggestions = pickProducts(ctx, remaining, goal, allProducts, preferProtein = needMoreProtein)
        )
    }

    private fun pickProducts(
        ctx: android.content.Context,
        remaining: Int,
        goal: String,
        allProducts: List<ProductEntity>,
        preferProtein: Boolean = false,
        light: Boolean = false
    ): List<Suggestion> {
        if (allProducts.isEmpty() || remaining < 40) return emptyList()

        val preferredCategories = when {
            light -> listOf("Овощи", "Фрукты", "Ягоды", "Молочные")
            goal == "lose" -> listOf("Птица", "Рыба", "Овощи", "Яйца", "Молочные", "Бобовые")
            goal == "gain" -> listOf("Птица", "Мясо", "Крупы", "Молочные", "Бобовые", "Орехи")
            goal == "healthy" -> listOf("Крупы", "Овощи", "Молочные", "Рыба", "Птица", "Фрукты")
            else -> listOf("Птица", "Рыба", "Крупы", "Овощи", "Фрукты", "Молочные", "Яйца")
        }

        var candidates = allProducts.filter { p ->
            p.category in preferredCategories &&
                    p.calories in 20f..400f &&                       // отсекаем слишком калорийные/пустые
                    skipKeywords.none { p.name.lowercase().contains(it) }  // без деликатесов
        }

        candidates = if (preferProtein) candidates.sortedByDescending { it.protein }
        else candidates.shuffled()

        val result = mutableListOf<Suggestion>()
        val usedCategories = mutableSetOf<String>()
        val perPortion = (remaining / 3).coerceIn(80, 500)

        for (product in candidates) {
            if (result.size >= 3) break
            if (product.category in usedCategories) continue

            // Реалистичный диапазон порции для категории
            val range = portionRange[product.category] ?: 50..200

            // Сколько грамм нужно чтобы дать ~perPortion ккал
            val idealGrams = ((perPortion / product.calories) * 100f).toInt()
            // Зажимаем в реалистичный диапазон + округляем до 10
            val grams = idealGrams.coerceIn(range.first, range.last)
            val roundedGrams = (grams / 10) * 10
            if (roundedGrams < range.first) continue

            val realCal = (product.calories * roundedGrams / 100f).toInt()

            val reason = ctx.getString(when {
                product.protein >= 15f -> com.nutritrack.app.R.string.mr_reason_protein
                product.category == "Овощи" -> com.nutritrack.app.R.string.mr_reason_veg
                product.category == "Крупы" -> com.nutritrack.app.R.string.mr_reason_grains
                product.category in listOf("Фрукты", "Ягоды") -> com.nutritrack.app.R.string.mr_reason_fruit
                product.category == "Орехи" -> com.nutritrack.app.R.string.mr_reason_nuts
                product.category == "Молочные" -> com.nutritrack.app.R.string.mr_reason_dairy
                else -> com.nutritrack.app.R.string.mr_reason_default
            })

            result.add(Suggestion(product, roundedGrams, realCal, reason))
            usedCategories.add(product.category)
        }
        return result
    }
}
