package com.nutritrack.app.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nutritrack.app.data.db.entity.FitnessDataEntity

@Dao
interface FitnessDao {
    @Query("SELECT * FROM fitness_data WHERE date = :date AND profileId = :profileId LIMIT 1")
    fun getForDate(date: String, profileId: Long): LiveData<FitnessDataEntity?>

    @Query("SELECT * FROM fitness_data WHERE date = :date AND profileId = :profileId LIMIT 1")
    suspend fun getForDateSync(date: String, profileId: Long): FitnessDataEntity?

    @Query("SELECT * FROM fitness_data WHERE date BETWEEN :startDate AND :endDate AND profileId = :profileId ORDER BY date DESC")
    suspend fun getForRange(startDate: String, endDate: String, profileId: Long): List<FitnessDataEntity>

    @Query("SELECT SUM(steps) FROM fitness_data WHERE profileId = :profileId")
    suspend fun getTotalSteps(profileId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: FitnessDataEntity)

    @Update
    suspend fun update(data: FitnessDataEntity)

 /**
 * Удаляет дубликаты за одну дату, оставляя только запись с наибольшим id (самую свежую).
 * Чинит ранее накопленные дубли, из-за которых недельный «Всего» был завышен.
 */
    @Query("""
        DELETE FROM fitness_data
        WHERE id NOT IN (
            SELECT MAX(id) FROM fitness_data GROUP BY date, profileId
        )
    """)
    suspend fun deleteDuplicates()

    @Query("SELECT * FROM fitness_data")
    suspend fun dumpAll(): List<FitnessDataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<FitnessDataEntity>)
}