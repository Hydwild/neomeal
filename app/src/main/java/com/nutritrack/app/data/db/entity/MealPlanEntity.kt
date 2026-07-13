package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name:      String,
    val time:      String,
    val date:      String,
    val profileId: Long   = 1,
    // ключ фиксированного слота: "breakfast" | "lunch" | "dinner" | "snack"
    // null = пользовательский приём пищи (старый функционал)
    val mealKey:   String? = null
)
