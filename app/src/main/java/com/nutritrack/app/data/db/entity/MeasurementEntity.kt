package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Замер части тела.
 * bodyPart — ключ части (waist, chest, biceps_l и т.д.)
 * valueCm — значение в сантиметрах
 * date — дата замера (yyyy-MM-dd)
 */
@Entity(tableName = "measurements", indices = [Index(value = ["profileId", "date"])])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyPart: String,
    val valueCm: Float,
    val date: String,
    val profileId: Long,
    val timestamp: Long = System.currentTimeMillis()
)
