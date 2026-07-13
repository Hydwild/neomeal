package com.nutritrack.app.utils

import com.nutritrack.app.data.db.entity.DailyGoalEntity

/**
 * Система уровней (геймификация). Чистая логика на числах.
 *
 * XP начисляется из двух источников:
 * • достижения — фиксированно за каждое разблокированное;
 * • дни выполнения норм — за калории, за воду и бонус за «идеальный» день.
 *
 * Уровень считается из суммарного XP по растущим порогам.
 * Рамка аватара открывается в зависимости от уровня.
 */
object LevelSystem {

    // Начисление XP
    const val XP_PER_ACHIEVEMENT = 75
    const val XP_CALORIE_DAY = 10
    const val XP_WATER_DAY   = 10
    const val XP_PERFECT_BONUS = 15   // дополнительно, если выполнены обе нормы

 /** Полный пересчёт XP по фактам. */
    fun totalXp(unlockedAchievements: Int, days: List<DailyGoalEntity>): Int {
        var xp = unlockedAchievements * XP_PER_ACHIEVEMENT
        for (d in days) {
            if (d.calorieGoalMet) xp += XP_CALORIE_DAY
            if (d.waterGoalMet)   xp += XP_WATER_DAY
            if (d.calorieGoalMet && d.waterGoalMet) xp += XP_PERFECT_BONUS
        }
        return xp
    }

    // Уровни
 /**
 * Порог суммарного XP для достижения уровня [level] (1-based).
 * Уровень 1 — 0 XP. Дальше растёт квадратично, чтобы прогресс замедлялся.
 * Уровень N требует 100 * (N-1)^2 XP суммарно.
 */
    fun xpForLevel(level: Int): Int {
        if (level <= 1) return 0
        val n = level - 1
        return 100 * n * n
    }

    data class LevelInfo(
        val level: Int,
        val xpIntoLevel: Int,   // XP, набранные внутри текущего уровня
        val xpForNext: Int,     // сколько XP нужно от начала уровня до следующего
        val totalXp: Int
    )

    fun levelInfo(totalXp: Int): LevelInfo {
        var level = 1
        while (xpForLevel(level + 1) <= totalXp && level < 999) level++
        val base = xpForLevel(level)
        val next = xpForLevel(level + 1)
        return LevelInfo(
            level = level,
            xpIntoLevel = totalXp - base,
            xpForNext = (next - base).coerceAtLeast(1),
            totalXp = totalXp
        )
    }

    // Рамки аватара (награды за уровень)
 /**
 * style: "none" / "neon" (пульсация свечения) / "rainbow" (вращающийся градиент) /
 * "shine" (бегущий блик). color — базовый цвет (rainbow игнорирует, рисует радугу).
 */
    data class Frame(val id: String, val minLevel: Int, val color: Long, val titleRes: Int, val style: String = "none")

 /** Рамки по возрастанию требуемого уровня. titleRes — ключ строки названия. */
    val frames: List<Frame> = listOf(
        Frame("none",         1,  0xFF9E9EB0, com.nutritrack.app.R.string.frame_none,        "none"),
        Frame("neon_cyan",    3,  0xFF18FFFF, com.nutritrack.app.R.string.frame_neon_cyan,   "neon"),
        Frame("neon_pink",    6,  0xFFFF4DD2, com.nutritrack.app.R.string.frame_neon_pink,   "neon"),
        Frame("shine_gold",   10, 0xFFFFD740, com.nutritrack.app.R.string.frame_shine_gold,  "shine"),
        Frame("rainbow",      15, 0xFFB388FF, com.nutritrack.app.R.string.frame_rainbow,     "rainbow"),
        Frame("neon_lime",    20, 0xFF76FF03, com.nutritrack.app.R.string.frame_neon_lime,   "neon"),
        Frame("shine_diamond",30, 0xFF40C4FF, com.nutritrack.app.R.string.frame_shine_diamond,"shine")
    )

    fun framesForLevel(level: Int): List<Frame> = frames.filter { it.minLevel <= level }
    fun frameById(id: String): Frame = frames.firstOrNull { it.id == id } ?: frames.first()

    // Талисманы-компаньоны = питомцы (награды за уровень).
    // id совпадает с PetCatalog.id — выбранный компаньон становится питомцем у воды.
    data class Companion(val id: String, val minLevel: Int, val emoji: String)

    /** Питомцы, открываемые по уровням. Лиса и кот — стартовые. */
    val companions: List<Companion> = listOf(
        Companion("fox",     1,  "🦊"),
        Companion("cat",     1,  "🐱"),
        Companion("otter",   4,  "🦦"),
        Companion("penguin", 8,  "🐧"),
        Companion("rabbit",  14, "🐰"),
        Companion("panda",   20, "🐼")
    )

    fun companionsForLevel(level: Int): List<Companion> = companions.filter { it.minLevel <= level }
}
