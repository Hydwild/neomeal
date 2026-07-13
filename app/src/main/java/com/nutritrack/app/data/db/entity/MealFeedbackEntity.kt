package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Оценка самочувствия после употребления продукта.
 * rating: "great" / "ok" / "discomfort" / "bad".
 * Привязка к productId — для агрегации проблемных продуктов.
 */
@Entity(tableName = "meal_feedback")
data class MealFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val rating: String,
    val date: String,                // "yyyy-MM-dd"
    val profileId: Long = 1,
    val timestamp: Long = System.currentTimeMillis()
)
