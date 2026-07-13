package com.nutritrack.app.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nutritrack.app.data.db.entity.AchievementEntity

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE profileId = :profileId")
    fun getAll(profileId: Long): LiveData<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE profileId = :profileId")
    suspend fun getAllSync(profileId: Long): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AchievementEntity?

    @Query("SELECT COUNT(*) FROM achievements WHERE isUnlocked = 1 AND profileId = :profileId")
    suspend fun getUnlockedCount(profileId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(achievement: AchievementEntity)

    @Update
    suspend fun update(achievement: AchievementEntity)

    @Query("DELETE FROM achievements WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("SELECT * FROM achievements")
    suspend fun dumpAll(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<AchievementEntity>)
}
