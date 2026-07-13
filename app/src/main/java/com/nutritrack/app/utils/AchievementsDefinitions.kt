package com.nutritrack.app.utils

import com.nutritrack.app.utils.AchievementDef
import com.nutritrack.app.utils.AchievementsDefinitions
data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val maxProgress: Int = 1,
    val category: String = "general",
    val rarity: String = "common"
)

object AchievementsDefinitions {
    val all: List<AchievementDef> = listOf(
        AchievementDef("first_meal",        "Первый шаг",           "Добавь первый продукт в дневник",             "🍽️", 1, "start",    "common"),
        AchievementDef("profile_complete",  "Знакомство",           "Заполни все данные профиля",                  "👤", 1, "start",    "common"),
        AchievementDef("first_water",       "Первый глоток",        "Запиши первый стакан воды",                   "💧", 1, "start",    "common"),
        AchievementDef("first_weight",      "Взвешивание",          "Внеси свой вес впервые",                      "⚖️", 1, "start",    "common"),
        AchievementDef("calorie_goal_1",    "В цель!",              "Выполни норму калорий за 1 день",             "🎯", 1, "calories", "common"),
        AchievementDef("calorie_streak_3",  "Три дня подряд",       "Выполняй норму калорий 3 дня подряд",        "🔥", 3, "calories", "common"),
        AchievementDef("calorie_streak_7",  "Неделя дисциплины",    "Выполняй норму калорий 7 дней подряд",       "📆", 7, "calories", "rare"),
        AchievementDef("calorie_streak_30", "Месяц на страже",      "Выполняй норму калорий 30 дней подряд",      "🏅", 30,"calories", "epic"),
        AchievementDef("calorie_streak_100","Сто дней чистоты",     "Выполняй норму калорий 100 дней подряд",     "👑",100,"calories", "legendary"),
        AchievementDef("calorie_total_10000","10 000 ккал в дневнике","Суммарно запиши 10 000 ккал",             "📊",10000,"calories","common"),
        AchievementDef("calorie_total_100000","100 000 ккал учтено","Суммарно запиши 100 000 ккал",             "💫",100000,"calories","epic"),
        AchievementDef("water_goal_1",      "Водопой",              "Выпей дневную норму воды за 1 день",         "💦", 1, "water",    "common"),
        AchievementDef("water_streak_7",    "Неделя гидратации",    "Выполняй норму воды 7 дней подряд",          "🌊", 7, "water",    "rare"),
        AchievementDef("water_streak_30",   "Месяц без жажды",      "Выполняй норму воды 30 дней подряд",         "🏖️",30, "water",    "epic"),
        AchievementDef("water_total_100l",  "100 литров",           "Суммарно выпей 100 000 мл воды",             "🚿",100000,"water", "common"),
        AchievementDef("water_total_1000l", "Бассейн!",             "Суммарно выпей 1 000 000 мл воды",           "🏊",1000000,"water","rare"),
        AchievementDef("water_tower",       "Водонапорная башня",   "Суммарно выпей 5 000 000 мл воды",           "🗼",5000000,"water","legendary"),
        AchievementDef("protein_hero",      "Белковый герой",       "Набери 150г белка за 1 день",                "💪", 1, "protein",  "common"),
        AchievementDef("protein_total_1kg", "Килограмм белка",      "Суммарно потреби 1 000г белка",              "🥩",1000,"protein", "common"),
        AchievementDef("protein_total_10kg","10 кг белка",          "Суммарно потреби 10 000г белка",             "🦁",10000,"protein","rare"),
        AchievementDef("carb_king",         "Главный по углеводам", "Набери 400г углеводов за 1 день",            "🍞", 1, "carbs",    "common"),
        AchievementDef("carb_total_50kg",   "50 кг углеводов",      "Суммарно потреби 50 000г углеводов",         "🍕",50000,"carbs",  "epic"),
        AchievementDef("weight_log_30",     "Взвешиваюсь регулярно","Внеси вес 30 раз",                           "🏋️",30, "weight",   "common"),
        AchievementDef("diary_days_30",     "Дневниковед",          "Делай записи в дневник 30 дней",             "📓",30, "diary",    "common"),
        AchievementDef("diary_days_365",    "Год с дневником",      "Делай записи в дневник 365 дней",            "🗓️",365,"diary",    "legendary"),
        AchievementDef("perfect_day",       "Идеальный день",       "Выполни норму калорий И воды в 1 день",      "⭐", 1, "special",  "rare"),
        AchievementDef("perfect_week",      "Идеальная неделя",     "Выполни норму калорий И воды 7 дней подряд", "🌟", 7, "special",  "epic"),
        AchievementDef("night_owl",         "Поздний ужин",         "Добавь запись в дневник после 22:00",         "🦉", 1, "special",  "common"),
        AchievementDef("early_bird",        "Ранняя птичка",        "Добавь запись в дневник до 7:00",             "🐦", 1, "special",  "common"),
        AchievementDef("fasting_16h",       "Первое голодание",     "Завершии интервальное голодание 16:8",        "⏱️", 1, "fasting",  "common"),
        AchievementDef("fasting_streak_7",  "Фастинг-марафон",      "Завершии голодание 7 дней подряд",            "🧘", 7, "fasting",  "rare"),
        AchievementDef("first_measure",      "Первый замер",         "Запиши первый замер тела",                    "📐", 1, "body",     "common"),
        AchievementDef("measure_log_10",     "Контроль формы",       "Сделай 10 замеров тела",                      "📊", 10,"body",     "common"),
        AchievementDef("waist_minus_5",      "Минус 5 см талии",     "Уменьши талию на 5 см от первого замера",     "📉", 5, "body",     "rare"),
        AchievementDef("biceps_plus_2",      "Рост силы",            "Увеличь бицепс на 2 см от первого замера",    "💪", 2, "body",     "rare"),
        AchievementDef("secret_pivca",      "Любимая Пивца💝",       "Секретное достижение… ты его нашёл!",         "💝", 1, "secret",   "legendary")
    )

    fun getById(id: String) = all.firstOrNull { it.id == id }
}