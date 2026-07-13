package com.nutritrack.app.data.db.dao

import androidx.room.*
import com.nutritrack.app.data.db.entity.MeasurementEntity

@Dao
interface MeasurementDao {

 /** Последнее значение каждой части (для отображения актуальных размеров). */
    @Query("""
        SELECT m.* FROM measurements m
        INNER JOIN (
            SELECT bodyPart, MAX(id) AS maxId
            FROM measurements
            WHERE profileId = :profileId
            GROUP BY bodyPart
        ) last ON m.id = last.maxId
    """)
    suspend fun getLatestForProfile(profileId: Long): List<MeasurementEntity>

 /** История одной части по времени (для графика динамики). */
    @Query("SELECT * FROM measurements WHERE bodyPart = :bodyPart AND profileId = :profileId ORDER BY id ASC")
    suspend fun getHistory(bodyPart: String, profileId: Long): List<MeasurementEntity>

    @Insert
    suspend fun insert(measurement: MeasurementEntity)

    @Query("DELETE FROM measurements WHERE bodyPart = :bodyPart AND profileId = :profileId")
    suspend fun deletePart(bodyPart: String, profileId: Long)

    // Для бэкапа
    @Query("SELECT * FROM measurements")
    suspend fun dumpAll(): List<MeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<MeasurementEntity>)
}
