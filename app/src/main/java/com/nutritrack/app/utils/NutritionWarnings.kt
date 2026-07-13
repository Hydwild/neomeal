package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.DiaryEntryEntity

/**
 * Мягкие предупреждения о продуктах, съеденных сверх разумной дневной нормы.
 * Ключевые слова — по русским названиям продуктов в базе (логика поиска).
 * Тексты предупреждений локализуются через ресурсы.
 */
object NutritionWarnings {

    private data class Rule(
        val keywords: List<String>,
        val dailyLimitGrams: Float,
        val messageRes: Int
    )

    private val rules = listOf(
        Rule(listOf("яйц", "яичн", "омлет"), 200f, R.string.warn_eggs),
        Rule(listOf("говядин", "свинин", "баранин", "стейк"), 300f, R.string.warn_red_meat),
        Rule(listOf("колбас", "сосиск", "сардельк", "ветчин", "бекон"), 150f, R.string.warn_processed),
        Rule(listOf("шоколад", "конфет", "торт", "пирожн", "сладк", "зефир", "мармелад"), 120f, R.string.warn_sweets),
        Rule(listOf("чипс", "сухарик", "фастфуд", "бургер", "картофель фри"), 80f, R.string.warn_snacks),
        Rule(listOf("кофе", "эспрессо", "американо", "капучино", "латте"), 500f, R.string.warn_coffee),
        Rule(listOf("жарен"), 300f, R.string.warn_fried),
        Rule(listOf("орех", "арахис", "миндал", "фундук", "кешью"), 70f, R.string.warn_nuts),
        Rule(listOf("сыр"), 150f, R.string.warn_cheese),
        Rule(listOf("майонез"), 60f, R.string.warn_mayo),
        Rule(listOf("пиво", "вино", "водк", "виски", "коньяк", "алкогол"), 200f, R.string.warn_alcohol),
        Rule(listOf("кола", "газировк", "лимонад", "энергетик"), 500f, R.string.warn_soda),
    )

    fun analyze(context: Context, entries: List<DiaryEntryEntity>): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        for (rule in rules) {
            val matched = entries
                .filter { e -> rule.keywords.any { e.productName.lowercase().contains(it) } }
                .sortedBy { it.id }
            if (matched.isEmpty()) continue
            val total = matched.sumOf { it.grams.toDouble() }.toFloat()
            if (total > rule.dailyLimitGrams) {
                result[matched.last().id] = context.getString(rule.messageRes)
            }
        }
        return result
    }
}
