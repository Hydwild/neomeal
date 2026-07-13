package com.nutritrack.app.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nutritrack.app.data.db.entity.FastingSessionEntity

@Dao
interface FastingDao {

    @Query("SELECT * FROM fasting_sessions WHERE profileId = :profileId AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getActiveSession(profileId: Long): LiveData<FastingSessionEntity?>

    @Query("SELECT * FROM fasting_sessions WHERE profileId = :profileId AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSessionSync(profileId: Long): FastingSessionEntity?

    @Query("SELECT * FROM fasting_sessions WHERE profileId = :profileId AND isCompleted = 1 ORDER BY startTime DESC")
    fun getCompletedSessions(profileId: Long): LiveData<List<FastingSessionEntity>>

    // LiveData-версия для FastingViewModel.switchMap
    @Query("SELECT COUNT(*) FROM fasting_sessions WHERE profileId = :profileId AND isCompleted = 1")
    fun getCompletedCountLive(profileId: Long): LiveData<Int>

    // suspend-версия для AchievementsViewModel.checkAchievements()
    @Query("SELECT COUNT(*) FROM fasting_sessions WHERE profileId = :profileId AND isCompleted = 1")
    suspend fun getCompletedCount(profileId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FastingSessionEntity): Long

    @Update
    suspend fun update(session: FastingSessionEntity)

    @Delete
    suspend fun delete(session: FastingSessionEntity)

    @Query("SELECT * FROM fasting_sessions")
    suspend fun dumpAll(): List<FastingSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<FastingSessionEntity>)
}
