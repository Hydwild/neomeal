package com.nutritrack.app.data.db.entity
import androidx.room.*
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val profileId: Long = 1,
    val unlockedAt: String? = null,
    val isUnlocked: Boolean = false,
    val progress: Int = 0
)