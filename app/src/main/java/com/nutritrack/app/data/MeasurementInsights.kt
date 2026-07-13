package com.nutritrack.app.data

/**
 * Логика аналитики замеров: категории телосложения, нормы, предметные сравнения.
 * Цифры — ориентировочные, основаны на общедоступной антропометрии.
 * БЕЗ имён реальных людей (юридическая чистота + достоверность).
 */
object MeasurementInsights {

 /** Категория значения части тела (диапазоны для мужчин, ориентировочно). */
    data class Category(val label: String, val emoji: String)

 /**
 * Уровень развития части (обобщённо, без имён).
 * Возвращает подпись вида "уровень атлета".
 */
    fun bicepsLevel(cm: Float): Category = when {
        cm < 30f -> Category("Начальный уровень", "🌱")
        cm < 35f -> Category("Средний уровень", "💪")
        cm < 40f -> Category("Хорошая форма", "🔥")
        cm < 45f -> Category("Уровень атлета", "🏋️")
        else     -> Category("Уровень профи-бодибилдера", "🏆")
    }

    fun waistAssessment(cm: Float, isMale: Boolean): Category {
        // ВОЗ: риск для здоровья — талия >94 (муж) / >80 (жен)
        val threshold = if (isMale) 94f else 80f
        val high = if (isMale) 102f else 88f
        return when {
            cm < threshold -> Category("Здоровый диапазон", "✅")
            cm < high      -> Category("Повышенный — стоит следить", "⚠️")
            else           -> Category("Высокий — риск для здоровья", "🔴")
        }
    }

 /** Предметное сравнение по обхвату (наглядно, не врёт). */
    fun objectComparison(cm: Float): String? {
        return when {
            cm <= 0f -> null
            cm < 16f  -> "тоньше горлышка бутылки 🍾"
            cm < 22f  -> "как обхват банки колы 🥤"
            cm < 28f  -> "как обхват кружки пива 🍺"
            cm < 34f  -> "как обхват грейпфрута 🍊"
            cm < 40f  -> "как обхват небольшой дыни 🍈"
            cm < 50f  -> "как обхват баскетбольного мяча 🏀"
            cm < 70f  -> "как обхват большого арбуза 🍉"
            cm < 95f  -> "как обхват автомобильной шины 🛞"
            else      -> "внушительный объём 💪"
        }
    }

 /** Соотношение талия/бёдра (WHR) — индикатор здоровья. */
    fun whrAssessment(waist: Float, hips: Float, isMale: Boolean): Pair<Float, Category>? {
        if (waist <= 0f || hips <= 0f) return null
        val whr = waist / hips
        val cat = if (isMale) {
            when {
                whr < 0.90f -> Category("Низкий риск", "✅")
                whr < 1.0f  -> Category("Умеренный риск", "⚠️")
                else        -> Category("Повышенный риск", "🔴")
            }
        } else {
            when {
                whr < 0.80f -> Category("Низкий риск", "✅")
                whr < 0.85f -> Category("Умеренный риск", "⚠️")
                else        -> Category("Повышенный риск", "🔴")
            }
        }
        return whr to cat
    }

 /** Соотношение плечи/талия — "V-тейпер" (атлетичность силуэта). */
    fun shoulderToWaist(shoulders: Float, waist: Float): Pair<Float, Category>? {
        if (shoulders <= 0f || waist <= 0f) return null
        val ratio = shoulders / waist
        val cat = when {
            ratio < 1.2f -> Category("Прямой силуэт", "📏")
            ratio < 1.4f -> Category("Спортивный силуэт", "💪")
            ratio < 1.6f -> Category("Атлетический V-силуэт", "🔺")
            else         -> Category("Выраженный V-силуэт", "🏆")
        }
        return ratio to cat
    }
}
