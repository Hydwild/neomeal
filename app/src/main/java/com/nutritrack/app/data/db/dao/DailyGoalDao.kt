package com.nutritrack.app.data.db.dao

import androidx.room.*
import com.nutritrack.app.data.db.entity.DailyGoalEntity

@Dao
interface DailyGoalDao {

    // Записать/обновить факт за день
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DailyGoalEntity)

    // Все дни профиля по убыванию даты (свежие сверху)
    @Query("SELECT * FROM daily_goals WHERE profileId = :profileId ORDER BY date DESC")
    suspend fun getAllForProfile(profileId: Long): List<DailyGoalEntity>

    @Query("DELETE FROM daily_goals WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("SELECT * FROM daily_goals")
    suspend fun dumpAll(): List<DailyGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<DailyGoalEntity>)
}
