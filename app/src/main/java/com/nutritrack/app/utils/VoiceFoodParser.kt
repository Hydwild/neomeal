package com.nutritrack.app.utils

/**
 * Разбирает распознанную фразу на "название продукта + граммовку".
 * Мультиязык: понимает числа цифрами + единицы (г/грамм/gram/g, мл/ml, шт).
 * Примеры: "творог 150 грамм", "chicken breast 200g", "молоко 250 мл".
 *
 * Возвращает Pair(чистое_название, граммы?). Если число не найдено — граммы = null.
 */
object VoiceFoodParser {

    data class Result(val query: String, val grams: Int?)

    // Слова-единицы, которые нужно убрать из названия (разные языки)
    private val unitWords = listOf(
        "грамм", "граммов", "грамма", "грам", "гр", "г",
        "миллилитров", "миллилитра", "миллилитр", "мл",
        "штук", "штуки", "штука", "шт",
        "grams", "gram", "g", "gr",
        "milliliters", "milliliter", "ml",
        "pieces", "piece", "pcs",
        "грамм.", "г."
    )

    // Слова-связки/мусор, убираем
    private val stopWords = listOf(
        "съел", "съела", "добавь", "добавить", "запиши", "записать",
        "это", "и", "был", "была", "около", "примерно", "штук",
        "ate", "add", "log", "record", "about", "approximately", "of", "a", "an"
    )

    fun parse(raw: String): Result {
        var text = raw.lowercase().trim()

        // 1. Найти число (граммовку). Берём первое число в строке.
        // Поддержка "200г", "200 г", "200gram"
        val numberRegex = Regex("""(\d+)\s*([а-яёa-z.]*)""")
        var grams: Int? = null
        val match = numberRegex.find(text)
        if (match != null) {
            grams = match.groupValues[1].toIntOrNull()
        }

        // 2. Убрать числа из текста
        text = text.replace(Regex("""\d+"""), " ")

        // 3. Разбить на слова, убрать единицы и стоп-слова
        val words = text.split(Regex("""[\s,.]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { w -> w !in unitWords }
            .filter { w -> w !in stopWords }

        val query = words.joinToString(" ").trim()

        return Result(query, grams)
    }
}
