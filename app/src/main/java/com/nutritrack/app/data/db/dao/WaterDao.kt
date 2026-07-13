package com.nutritrack.app.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nutritrack.app.data.db.entity.WaterEntryEntity

@Dao
interface WaterDao {

    // Используется WaterRepository

    // getWaterForDate(date, profileId)
    @Query("SELECT * FROM water_entries WHERE date = :date AND profileId = :profileId ORDER BY timestamp ASC")
    fun getWaterForDate(date: String, profileId: Long): LiveData<List<WaterEntryEntity>>

    @Query("SELECT * FROM water_entries WHERE date = :date AND profileId = :profileId")
    suspend fun getWaterForDateSync(date: String, profileId: Long): List<WaterEntryEntity>

    // getTotalWaterForDate(date, profileId) — LiveData
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE date = :date AND profileId = :profileId")
    fun getTotalWaterForDate(date: String, profileId: Long): LiveData<Int>

    // Суммарно выпито воды за всё время (мл)
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE profileId = :profileId")
    suspend fun getTotalWaterAllTime(profileId: Long): Int

    // Кол-во записей воды всего
    @Query("SELECT COUNT(*) FROM water_entries WHERE profileId = :profileId")
    suspend fun getWaterEntriesCount(profileId: Long): Int


    // getTotalSync(date, profileId) getTotalWaterSync — suspend
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE date = :date AND profileId = :profileId")
    suspend fun getTotalWaterSync(date: String, profileId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWater(entry: WaterEntryEntity)

    @Delete
    suspend fun deleteWater(entry: WaterEntryEntity)

    @Query("DELETE FROM water_entries")
    suspend fun deleteAll()

    // Прочее (для ReminderReceiver / AchievementsViewModel)

    @Query("SELECT * FROM water_entries WHERE date = :date AND profileId = :profileId ORDER BY timestamp ASC")
    suspend fun getAllWaterSync(date: String, profileId: Long): List<WaterEntryEntity>

    // Синоним insert для ReminderReceiver (вызывает waterDao().insert(...))
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WaterEntryEntity)

    @Delete
    suspend fun delete(entry: WaterEntryEntity)

    @Query("SELECT * FROM water_entries WHERE profileId = :profileId AND date >= :startDate ORDER BY timestamp ASC")
    suspend fun getWaterSince(startDate: String, profileId: Long): List<WaterEntryEntity>

    @Query("SELECT * FROM water_entries")
    suspend fun dumpAll(): List<WaterEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<WaterEntryEntity>)
}
