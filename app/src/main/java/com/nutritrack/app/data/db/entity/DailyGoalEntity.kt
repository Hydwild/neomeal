package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Факт выполнения дневных норм за конкретный день.
 * Нужна для подсчёта серий (streak) достижений.
 *
 * Первичный ключ — комбинация profileId + date (одна запись на день на профиль).
 */
@Entity(tableName = "daily_goals", primaryKeys = ["profileId", "date"])
data class DailyGoalEntity(
    val profileId: Long,
    val date: String,            // "yyyy-MM-dd"
    val calorieGoalMet: Boolean, // выполнена ли норма калорий
    val waterGoalMet: Boolean    // выполнена ли норма воды
)
