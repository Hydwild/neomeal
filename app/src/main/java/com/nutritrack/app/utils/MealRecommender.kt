package com.nutritrack.app.utils

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
        goalCalories: Int,
        eatenCalories: Int,
        eatenProtein: Float,
        goal: String,
        allProducts: List<ProductEntity>
    ): Recommendation {

        val remaining = goalCalories - eatenCalories
        val percent   = if (goalCalories > 0)
            ((eatenCalories.toFloat() / goalCalories) * 100f).toInt() else 0
        val progressLine = "$eatenCalories / $goalCalories ккал ($percent%)"

        if (remaining <= -50) {
            return Recommendation(
                goalCalories, eatenCalories, remaining, percent, progressLine,
                headline = "⚠️ Норма превышена на ${-remaining} ккал",
                detail = when (goal) {
                    "lose" -> "Сегодня лучше остановиться. Завтра вернись в рамки — один день не критичен."
                    "gain" -> "Для набора профицит полезен, но не переусердствуй."
                    else   -> "Немного превысил норму — это нормально, учти на завтра."
                },
                suggestions = emptyList()
            )
        }

        if (remaining in -49..120) {
            return Recommendation(
                goalCalories, eatenCalories, remaining, percent, progressLine,
                headline = "✅ Норма почти выполнена!",
                detail = "Осталось $remaining ккал. Можно лёгкий перекус или просто воду.",
                suggestions = pickProducts(remaining.coerceAtLeast(60), goal, allProducts, light = true)
            )
        }

        val proteinTarget   = goalCalories * 0.30f / 4f
        val needMoreProtein = eatenProtein < proteinTarget * 0.6f

        val detail = buildString {
            when (goal) {
                "lose"    -> append("Для похудения упор на белок и овощи — насыщают надолго. ")
                "gain"    -> append("Для набора добавь сытные продукты с белком и крупами. ")
                "healthy" -> append("Выбирай мягкую, нераздражающую пищу. ")
                else      -> append("Распредели остаток между приёмами пищи. ")
            }
            if (needMoreProtein) append("Белка сегодня не хватает — добавь белковые продукты.")
            else append("Белка достаточно — можно добрать гарниром или фруктами.")
        }

        return Recommendation(
            goalCalories, eatenCalories, remaining, percent, progressLine,
            headline = "🍽️ Осталось $remaining ккал на сегодня",
            detail = detail,
            suggestions = pickProducts(remaining, goal, allProducts, preferProtein = needMoreProtein)
        )
    }

    private fun pickProducts(
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

            val reason = when {
                product.protein >= 15f -> "богат белком"
                product.category == "Овощи" -> "мало калорий, много клетчатки"
                product.category == "Крупы" -> "медленные углеводы — энергия надолго"
                product.category in listOf("Фрукты", "Ягоды") -> "витамины и клетчатка"
                product.category == "Орехи" -> "полезные жиры (немного)"
                product.category == "Молочные" -> "белок и кальций"
                else -> "хороший выбор для твоей цели"
            }

            result.add(Suggestion(product, roundedGrams, realCal, reason))
            usedCategories.add(product.category)
        }
        return result
    }
}
