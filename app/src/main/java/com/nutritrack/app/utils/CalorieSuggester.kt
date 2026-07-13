package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R

/**
 * Подсказки для добора дневной нормы калорий и усиления частых блюд.
 *
 * Два вида:
 *  - fillGap: продукты/порции, которыми удобно «добить» остаток калорий.
 *  - boosters: добавки (соусы, авокадо, мясо и т.п.), повышающие калорийность
 *    и/или белок частого блюда.
 *
 * Подсказки помечаются флагом conflict, если содержат аллерген пользователя
 * или не вписываются в его диету. Конфликтные НЕ скрываются — показываются с
 * предупреждением (решение пользователя). Это помощник, не медицинский совет.
 */
object CalorieSuggester {

    /** Готовая подсказка для UI. */
    data class Suggestion(
        val title: String,        // напр. "Горсть миндаля (30 г)"
        val kcal: Int,
        val proteinG: Int,
        val emoji: String,
        val conflict: Boolean,    // конфликт с аллергеном/диетой
        val conflictReason: String? = null
    )

    /** Кандидат-добавка (внутренний справочник). */
    private data class Item(
        val nameRes: Int,
        val emoji: String,
        val kcal: Int,
        val protein: Int,
        val allergens: List<String>,   // коды AllergenData
        val nonVegan: Boolean = false,
        val nonVegetarian: Boolean = false,
        val highSugar: Boolean = false
    )

    // Продукты для добора остатка калорий (порции усреднённые)
    private val FILLERS = listOf(
        Item(R.string.sug_almonds, "🌰", 180, 7, listOf("nuts")),
        Item(R.string.sug_banana, "🍌", 105, 1, emptyList(), highSugar = true),
        Item(R.string.sug_greek_yogurt, "🥛", 130, 11, listOf("milk")),
        Item(R.string.sug_boiled_egg, "🥚", 78, 6, listOf("eggs"), nonVegan = true),
        Item(R.string.sug_cottage, "🧀", 160, 18, listOf("milk")),
        Item(R.string.sug_peanut_butter, "🥜", 190, 8, listOf("peanuts")),
        Item(R.string.sug_oatmeal, "🥣", 150, 5, listOf("gluten")),
        Item(R.string.sug_protein_shake, "🥤", 120, 24, listOf("milk")),
        Item(R.string.sug_dark_choc, "🍫", 170, 2, listOf("milk"), highSugar = true),
        Item(R.string.sug_avocado_half, "🥑", 160, 2, emptyList()),
        Item(R.string.sug_tuna_can, "🐟", 130, 28, listOf("fish"), nonVegan = true, nonVegetarian = true),
        Item(R.string.sug_hummus, "🫛", 170, 6, listOf("sesame")),
    )

    // Добавки-усилители для частых блюд
    private val BOOSTERS = listOf(
        Item(R.string.boost_avocado, "🥑", 160, 2, emptyList()),
        Item(R.string.boost_olive_oil, "🫒", 120, 0, emptyList()),
        Item(R.string.boost_cheese, "🧀", 110, 7, listOf("milk")),
        Item(R.string.boost_chicken, "🍗", 165, 31, emptyList(), nonVegan = true, nonVegetarian = true),
        Item(R.string.boost_nuts, "🌰", 175, 6, listOf("nuts")),
        Item(R.string.boost_seeds, "🌻", 150, 5, emptyList()),
        Item(R.string.boost_sour_cream, "🥛", 60, 1, listOf("milk")),
        Item(R.string.boost_tahini, "⚪", 90, 3, listOf("sesame")),
        Item(R.string.boost_egg, "🥚", 78, 6, listOf("eggs"), nonVegan = true),
        Item(R.string.boost_peanut_sauce, "🥜", 140, 5, listOf("peanuts")),
    )

    private fun toSuggestion(ctx: Context, it: Item, userAllergens: List<String>, diets: List<String>): Suggestion {
        val hitAllergen = it.allergens.firstOrNull { it in userAllergens }
        val dietConflict = when {
            "vegan" in diets && it.nonVegan -> ctx.getString(R.string.sug_conflict_vegan)
            "vegetarian" in diets && it.nonVegetarian -> ctx.getString(R.string.sug_conflict_vegetarian)
            "diabetic" in diets && it.highSugar -> ctx.getString(R.string.sug_conflict_sugar)
            else -> null
        }
        val reason = when {
            hitAllergen != null -> ctx.getString(R.string.sug_conflict_allergen, AllergenData.title(ctx, hitAllergen))
            dietConflict != null -> dietConflict
            else -> null
        }
        return Suggestion(ctx.getString(it.nameRes), it.kcal, it.protein, it.emoji, reason != null, reason)
    }

    /**
     * Подсказки «чем добить норму».
     * remainingKcal — сколько калорий осталось до цели.
     * Возвращает варианты, близкие к остатку (не сильно превышающие), сначала
     * совместимые с профилем, потом конфликтные (с пометкой).
     */
    fun fillGap(ctx: Context, remainingKcal: Int, userAllergens: List<String>, diets: List<String>): List<Suggestion> {
        if (remainingKcal <= 50) return emptyList()
        val all = FILLERS.map { toSuggestion(ctx, it, userAllergens, diets) }
        // ближе к остатку и не сильно больше него
        return all.sortedWith(compareBy({ it.conflict }, { kotlin.math.abs(it.kcal - remainingKcal) }))
            .take(6)
    }

    /** Усилители для блюда (повысить калории/белок). */
    fun boosters(ctx: Context, userAllergens: List<String>, diets: List<String>): List<Suggestion> {
        return BOOSTERS.map { toSuggestion(ctx, it, userAllergens, diets) }
            .sortedWith(compareBy({ it.conflict }, { -it.proteinG }))
            .take(6)
    }
}
