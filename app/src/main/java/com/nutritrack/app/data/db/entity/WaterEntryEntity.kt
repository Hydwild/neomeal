package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "water_entries", indices = [Index(value = ["profileId", "date"])])
  data class WaterEntryEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val amountMl:  Int,
      val date:      String,
      val profileId: Long = 1,
      val timestamp: Long = System.currentTimeMillis()
   )
