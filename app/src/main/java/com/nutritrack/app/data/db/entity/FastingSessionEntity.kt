package com.nutritrack.app.data.db.entity
import androidx.room.*
@Entity(tableName = "fasting_sessions")
data class FastingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1, val startTime: Long,
    val endTime: Long? = null, val targetHours: Int,
    val protocolName: String, val isCompleted: Boolean = false,
    val durationMinutes: Int = 0, val notes: String? = null
)