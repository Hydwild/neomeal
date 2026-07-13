package com.nutritrack.app.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nutritrack.app.data.db.entity.WeightEntryEntity

@Dao
interface WeightDao {

    // Используется ProfileRepository

    // getWeightHistory(profileId) weightDao.getAllWeightEntries(profileId)
    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY date DESC")
    fun getAllWeightEntries(profileId: Long): LiveData<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestWeight(profileId: Long): WeightEntryEntity?

    @Query("SELECT * FROM weight_entries WHERE date = :date AND profileId = :profileId LIMIT 1")
    suspend fun getWeightForDate(date: String, profileId: Long): WeightEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(entry: WeightEntryEntity)

    @Delete
    suspend fun deleteWeight(entry: WeightEntryEntity)

    // Используется AchievementsViewModel

    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY date DESC")
    suspend fun getAllWeightSync(profileId: Long): List<WeightEntryEntity>

    // Прочее

    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY date DESC")
    fun getWeightHistory(profileId: Long): LiveData<List<WeightEntryEntity>>

    @Query("DELETE FROM weight_entries WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("SELECT * FROM weight_entries")
    suspend fun dumpAll(): List<WeightEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<WeightEntryEntity>)
}
