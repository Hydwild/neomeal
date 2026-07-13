package com.nutritrack.app.data

/**
 * Суточные нормы микронутриентов для взрослых.
 * Источник: U.S. National Academies (RDA/AI) и FDA Daily Values.
 * Значения — общие референсные для здоровых взрослых. Не учитывают
 * беременность, болезни и индивидуальные особенности.
 *
 * Это справочный ориентир, а не медицинская рекомендация.
 */
object MicronutrientNorms {

    /** Один нутриент: ключ, отображаемое имя, единица, нормы муж/жен, эмодзи. */
    data class Nutrient(
        val key: String,
        val unit: String,
        val rdaMale: Float,
        val rdaFemale: Float,
        val emoji: String
    ) {
        /** Норма для пола: "male"/"female", иначе среднее. */
        fun rda(gender: String?): Float = when (gender?.lowercase()) {
            "male", "м", "мужской" -> rdaMale
            "female", "ж", "женский" -> rdaFemale
            else -> (rdaMale + rdaFemale) / 2f
        }
    }

    // Порядок = порядок отображения на экране
    val all: List<Nutrient> = listOf(
        Nutrient("vitA",      "мкг",  900f,  700f, "👁️"),
        Nutrient("vitC",      "мг",    90f,   75f, "🍊"),
        Nutrient("vitD",      "мкг",   20f,   20f, "☀️"),
        Nutrient("vitE",      "мг",    15f,   15f, "🌰"),
        Nutrient("vitK",      "мкг",  120f,   90f, "🥬"),
        Nutrient("vitB6",     "мг",   1.3f,  1.3f, "⚡"),
        Nutrient("vitB12",    "мкг",  2.4f,  2.4f, "🧬"),
        Nutrient("folate",    "мкг",  400f,  400f, "🌱"),
        Nutrient("calcium",   "мг",  1000f, 1000f, "🦴"),
        Nutrient("iron",      "мг",     8f,   18f, "🩸"),
        Nutrient("magnesium", "мг",   420f,  320f, "💪"),
        Nutrient("zinc",      "мг",    11f,    8f, "🛡️"),
        Nutrient("potassium", "мг",  3400f, 2600f, "🫀"),
        Nutrient("sodium",    "мг",  1500f, 1500f, "🧂"),
        Nutrient("selenium",  "мкг",   55f,   55f, "✨"),
    )

    fun byKey(key: String): Nutrient? = all.firstOrNull { it.key == key }
}
