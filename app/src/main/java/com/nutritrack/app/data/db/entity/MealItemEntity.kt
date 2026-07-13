package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_items")
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealPlanId: Long,
    val productId: Long,
    val productName: String,
    val grams: Float,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float
)