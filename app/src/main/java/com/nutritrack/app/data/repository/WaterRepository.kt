package com.nutritrack.app.data.repository

import com.nutritrack.app.data.db.dao.WaterDao
import com.nutritrack.app.data.db.entity.WaterEntryEntity

class WaterRepository(private val dao: WaterDao) {
    fun getWaterForDate(date: String, profileId: Long) =
        dao.getWaterForDate(date, profileId)

    fun getTotalWaterForDate(date: String, profileId: Long) =
        dao.getTotalWaterForDate(date, profileId)

    suspend fun getTotalSync(date: String, profileId: Long) =
        dao.getTotalWaterSync(date, profileId)

    suspend fun insert(entry: WaterEntryEntity) = dao.insertWater(entry)
    suspend fun delete(entry: WaterEntryEntity) = dao.deleteWater(entry)
    suspend fun deleteAll() = dao.deleteAll()
}