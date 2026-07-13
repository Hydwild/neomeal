package com.nutritrack.app.data

/**
 * Справочник частей тела для журнала замеров.
 * key — стабильный ключ в БД, title — отображаемое название, emoji — иконка.
 * group — для группировки в списке.
 */
data class BodyPart(
    val key: String,
    val title: String,
    val emoji: String,
    val group: String
)

object BodyParts {
    val all: List<BodyPart> = listOf(
        // Корпус
        BodyPart("neck",       "Шея",            "🧣", "Корпус"),
        BodyPart("shoulders",  "Плечи",          "📐", "Корпус"),
        BodyPart("chest",      "Грудь",          "👕", "Корпус"),
        BodyPart("back",       "Спина",          "🔙", "Корпус"),
        BodyPart("waist",      "Талия",          "📏", "Корпус"),
        BodyPart("belly",      "Живот",          "⭕", "Корпус"),
        BodyPart("hips",       "Ягодицы",        "🍑", "Корпус"),
        // Руки
        BodyPart("biceps_l",   "Бицепс (лев.)",  "💪", "Руки"),
        BodyPart("biceps_r",   "Бицепс (прав.)", "💪", "Руки"),
        BodyPart("forearm_l",  "Предплечье (лев.)",  "🦾", "Руки"),
        BodyPart("forearm_r",  "Предплечье (прав.)", "🦾", "Руки"),
        BodyPart("wrist_l",    "Запястье (лев.)",  "⌚", "Руки"),
        BodyPart("wrist_r",    "Запястье (прав.)", "⌚", "Руки"),
        // Ноги
        BodyPart("thigh_l",    "Бедро (лев.)",   "🦵", "Ноги"),
        BodyPart("thigh_r",    "Бедро (прав.)",  "🦵", "Ноги"),
        BodyPart("calf_l",     "Икра (лев.)",    "🦿", "Ноги"),
        BodyPart("calf_r",     "Икра (прав.)",   "🦿", "Ноги")
    )

    fun byKey(key: String): BodyPart? = all.firstOrNull { it.key == key }

 /** Группы в порядке отображения. */
    val groups = listOf("Корпус", "Руки", "Ноги")
}
