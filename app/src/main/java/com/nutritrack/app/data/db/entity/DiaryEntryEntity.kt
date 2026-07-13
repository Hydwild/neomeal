package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries", indices = [Index(value = ["profileId", "date"])])
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val grams: Float,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val date: String,                // "yyyy-MM-dd"
    val mealType: String = "other",
    val profileId: Long = 1,
    val timestamp: Long = System.currentTimeMillis(),  // момент добавления — для night_owl / early_bird
    val photoUri: String? = null                        // 📷 фото блюда (опционально)
)
