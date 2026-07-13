package com.nutritrack.app.utils

import com.nutritrack.app.data.db.entity.ProductEntity

/**
 * Определяет единицу измерения продукта: миллилитры для напитков, граммы для остального.
 * Напитки определяются по категории — без новых полей БД.
 * Не путать с [UnitsHelper] — тот отвечает за метрику/империал при отображении.
 *
 * Для напитков КБЖУ считается как «на 100 мл» (≈ на 100 г, погрешность плотности 3-5%
 * несущественна для подсчёта калорий — напитки никто не взвешивает на весах).
 */
object ProductUnits {

    // Категории, которые считаем жидкими
    private val LIQUID_CATEGORIES = setOf("Напитки")

    // Ключевые слова в названии, по которым продукт тоже считается напитком
    // (на случай кастомных продуктов из сканера или добавленных вручную)
    private val LIQUID_KEYWORDS = listOf(
        "сок", "вода", "кофе", "чай", "молоко", "кефир", "морс",
        "компот", "лимонад", "кола", "напиток", "смузи", "коктейль",
        "пиво", "вино", "квас", "какао", "латте", "капучино", "айран",
        "ряженка", "йогурт питьевой", "тан "
    )

 /** true если продукт измеряется в мл. */
    fun isLiquid(product: ProductEntity): Boolean {
        if (product.category in LIQUID_CATEGORIES) return true
        val name = product.name.lowercase()
        return LIQUID_KEYWORDS.any { name.contains(it) }
    }

 /** Единица: "мл" или "г". */
    fun unit(product: ProductEntity): String = if (isLiquid(product)) "мл" else "г"

 /** Быстрые порции для +/- и стартовое значение. */
    fun defaultAmount(product: ProductEntity): Int = if (isLiquid(product)) 250 else 100

 /** Шаг изменения через кнопки +/-. */
    fun step(product: ProductEntity, current: Int): Int {
        return if (isLiquid(product)) {
            if (current >= 250) 50 else 25
        } else {
            if (current >= 100) 50 else 10
        }
    }
}
