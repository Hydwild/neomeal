package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Фото прогресса (до/после). Хранится путь к файлу во внутренней памяти.
 */
@Entity(tableName = "progress_photos")
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoUri: String,
    val date: String,
    val note: String = "",
    val profileId: Long,
    val timestamp: Long = System.currentTimeMillis()
)
