package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "weight_entries", indices = [Index(value = ["profileId", "date"])])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weight: Float,
    val date: String,
    val profileId: Long = 1
)