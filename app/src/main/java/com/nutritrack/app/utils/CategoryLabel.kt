package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R

/**
 * Перевод категории продукта для отображения.
 * Логика приложения работает по русскому ключу (как в БД),
 * а на экране показывается строка по текущей локали.
 */
object CategoryLabel {
    private val map = mapOf(
        "Мясо" to R.string.cat_meat,
        "Птица" to R.string.cat_poultry,
        "Рыба" to R.string.cat_fish,
        "Морепродукты" to R.string.cat_seafood,
        "Яйца" to R.string.cat_eggs,
        "Молочные" to R.string.cat_dairy,
        "Крупы" to R.string.cat_grains,
        "Хлеб" to R.string.cat_bread,
        "Овощи" to R.string.cat_veg,
        "Фрукты" to R.string.cat_fruits,
        "Ягоды" to R.string.cat_berries,
        "Орехи" to R.string.cat_nuts,
        "Бобовые" to R.string.cat_legumes,
        "Напитки" to R.string.cat_drinks,
        "Спортпит" to R.string.cat_sportpit,
        "Спортивное" to R.string.cat_sports,
        "Масла" to R.string.cat_oils,
        "Соусы" to R.string.cat_sauces,
        "Сладкое" to R.string.cat_sweets,
        "Фастфуд" to R.string.cat_fastfood,
        "Готовые блюда" to R.string.cat_ready,
        "Мировая кухня" to R.string.cat_world,
        "ПП и диета" to R.string.cat_diet,
    )

 /** Возвращает переведённую подпись категории; для неизвестных — исходный текст. */
    fun of(context: Context, category: String): String {
        val res = map[category] ?: return category
        return context.getString(res)
    }
}
