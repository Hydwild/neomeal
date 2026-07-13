package com.nutritrack.app.data.db.entity
import androidx.room.*
@Entity(tableName = "fitness_data", indices = [Index(value = ["profileId", "date"])])
data class FitnessDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, val profileId: Long = 1,
    val steps: Int = 0, val caloriesBurned: Float = 0f,
    val heartRateAvg: Int = 0, val heartRateMax: Int = 0,
    val heartRateMin: Int = 0, val activeMinutes: Int = 0,
    val distanceMeters: Float = 0f, val source: String = "manual"
)