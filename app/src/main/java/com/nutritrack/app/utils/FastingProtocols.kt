package com.nutritrack.app.utils

data class FastingProtocol(
    val id: String,
    val name: String,
    val fastingHours: Int,
    val eatingHours: Int,
    val icon: String,
    val difficulty: String,   // "Лёгкий", "Средний", "Сложный", "Экстремальный"
    val shortDescription: String,
    val benefits: List<FastingBenefit>,
    val tips: List<String>
)

data class FastingBenefit(
    val hoursFrom: Int,
    val hoursTo: Int,
    val icon: String,
    val title: String,
    val description: String
)

object FastingProtocols {

    val all = listOf(
        FastingProtocol(
            id = "12_12",
            name = "12:12",
            fastingHours = 12,
            eatingHours = 12,
            icon = "🌙",
            difficulty = "Очень лёгкий",
            shortDescription = "Идеально для новичков. Голодание во время сна.",
            benefits = emptyList(),
            tips = listOf(
                "Последний приём пищи в 20:00, первый — в 8:00",
                "Большую часть голодания приходится на сон — почти незаметно",
                "Разреши себе чёрный кофе и воду в окне голодания"
            )
        ),
        FastingProtocol(
            id = "16_8",
            name = "16:8",
            fastingHours = 16,
            eatingHours = 8,
            icon = "⏱️",
            difficulty = "Лёгкий",
            shortDescription = "Самый популярный протокол. Пропуск завтрака или ужина.",
            benefits = listOf(
                FastingBenefit(0, 4, "🍽️", "Переваривание",
                    "Организм переваривает последний приём пищи. Уровень инсулина начинает снижаться."),
                FastingBenefit(4, 8, "🔥", "Сжигание гликогена",
                    "Запасы гликогена в печени истощаются. Организм начинает переключаться на жировое топливо."),
                FastingBenefit(8, 12, "⚡", "Жировое топливо",
                    "Тело активно переходит на сжигание жирных кислот. Кетоновые тела начинают вырабатываться."),
                FastingBenefit(12, 16, "🧹", "Аутофагия начинается",
                    "Запускается процесс клеточного «самоочищения» (аутофагия). Клетки утилизируют повреждённые белки.")
            ),
            tips = listOf(
                "Начни с окна 12–14 ч и постепенно увеличивай",
                "Пей воду, чёрный кофе или зелёный чай в окне голодания",
                "Не считай калории — просто не ешь в период голодания",
                "Оптимальное окно питания: 12:00–20:00 или 13:00–21:00"
            )
        ),
        FastingProtocol(
            id = "18_6",
            name = "18:6",
            fastingHours = 18,
            eatingHours = 6,
            icon = "💪",
            difficulty = "Средний",
            shortDescription = "Более интенсивный протокол для активной аутофагии.",
            benefits = listOf(
                FastingBenefit(12, 18, "🧬", "Активная аутофагия",
                    "Аутофагия значительно усиливается. Организм интенсивно утилизирует старые и повреждённые клетки."),
                FastingBenefit(16, 18, "🧠", "Повышение BDNF",
                    "Увеличивается выработка нейротрофического фактора мозга (BDNF) — улучшается память и когнитивные функции.")
            ),
            tips = listOf(
                "Окно питания: 14:00–20:00 (пропусти завтрак и обед)",
                "Хорошо сочетается с тренировками в 12–14 ч (натощак)",
                "Электролиты (натрий, калий) помогут избежать слабости"
            )
        ),
        FastingProtocol(
            id = "20_4",
            name = "20:4 (Воин)",
            fastingHours = 20,
            eatingHours = 4,
            icon = "⚔️",
            difficulty = "Сложный",
            shortDescription = "Протокол «Воин». Один большой приём пищи и небольшой перекус.",
            benefits = listOf(
                FastingBenefit(16, 20, "🔬", "Глубокая аутофагия",
                    "Аутофагия на пике. Гормон роста повышается до 500% от нормы."),
                FastingBenefit(18, 20, "🛡️", "Противовоспалительный эффект",
                    "Значительное снижение маркеров воспаления. Укрепляется иммунная система.")
            ),
            tips = listOf(
                "Можно есть сырые овощи и фрукты в дневное время",
                "Основной приём пищи вечером (17:00–21:00)",
                "Не для новичков — переходи постепенно с 16:8",
                "Особенно эффективно для похудения"
            )
        ),
        FastingProtocol(
            id = "24h",
            name = "24 часа (OMAD)",
            fastingHours = 24,
            eatingHours = 1,
            icon = "🏆",
            difficulty = "Экстремальный",
            shortDescription = "One Meal A Day. Один приём пищи в сутки.",
            benefits = listOf(
                FastingBenefit(20, 24, "🌟", "Максимальная аутофагия",
                    "Наивысший уровень аутофагии. Организм полностью очищает старые клетки."),
                FastingBenefit(24, 24, "⚕️", "Метаболический сброс",
                    "Полный метаболический перезапуск. Инсулиновая чувствительность достигает максимума.")
            ),
            tips = listOf(
                "Только для опытных практиков интервального голодания",
                "Максимум 1–2 раза в неделю",
                "Обязательно поговори с врачом",
                "Ешь в одно и то же время каждый день"
            )
        ),
        FastingProtocol(
            id = "5_2",
            name = "5:2",
            fastingHours = 0,
            eatingHours = 0,
            icon = "📅",
            difficulty = "Средний",
            shortDescription = "5 обычных дней + 2 дня с ограничением до 500 ккал.",
            benefits = emptyList(),
            tips = listOf(
                "5 дней ешь нормально, 2 дня — до 500 ккал",
                "«Голодные» дни не должны быть подряд",
                "В голодные дни: бульон, овощи, 1–2 яйца",
                "Помогает при нежелании ежедневного голодания"
            )
        )
    )

    fun getById(id: String) = all.firstOrNull { it.id == id }

    // Текущие эффекты исходя из прошедшего времени
    fun getCurrentBenefits(protocol: FastingProtocol, elapsedHours: Int): List<FastingBenefit> {
        return protocol.benefits.filter { it.hoursFrom <= elapsedHours }
    }

    // Следующий эффект
    fun getNextBenefit(protocol: FastingProtocol, elapsedHours: Int): FastingBenefit? {
        return protocol.benefits.firstOrNull { it.hoursFrom > elapsedHours }
    }
}