package com.nutritrack.app.data.db.dao

import androidx.room.*
import com.nutritrack.app.data.db.entity.ProgressPhotoEntity

@Dao
interface ProgressPhotoDao {

    @Query("SELECT * FROM progress_photos WHERE profileId = :profileId ORDER BY date DESC, id DESC")
    suspend fun getAllForProfile(profileId: Long): List<ProgressPhotoEntity>

    @Insert
    suspend fun insert(photo: ProgressPhotoEntity)

    @Delete
    suspend fun delete(photo: ProgressPhotoEntity)

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Бэкап
    @Query("SELECT * FROM progress_photos")
    suspend fun dumpAll(): List<ProgressPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ProgressPhotoEntity>)
}
