package com.nutritrack.app.utils

/**
 * Разбор количеств ингредиентов: «200 г» → 200 + «г», «2 шт» → 2 + «шт».
 * Чистая логика без Android — покрыта юнит-тестами (AmountParserTest).
 * Перевод единиц по локали — RecipeLabel.trUnit/trAmount.
 */
object AmountParser {

    private val RX = Regex("""^(\d+(?:[.,]\d+)?)\s*(.*)$""")

 /**
 * @return число + единица («г», «шт», «ст.л.»…); пустая единица = «шт».
 * null — количество без числа впереди («по вкусу», «щепотка»).
 */
    fun parse(amount: String): Pair<Float, String>? {
        val m = RX.find(amount.trim()) ?: return null
        val num = m.groupValues[1].replace(',', '.').toFloatOrNull() ?: return null
        val unit = m.groupValues[2].trim().ifEmpty { "шт" }
        return num to unit
    }
}
