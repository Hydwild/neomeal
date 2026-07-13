package com.nutritrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nutritrack.app.data.db.entity.MealFeedbackEntity

@Dao
interface MealFeedbackDao {

    @Insert
    suspend fun insert(entry: MealFeedbackEntity)

 /** Все оценки профиля (для агрегации проблемных продуктов). */
    @Query("SELECT * FROM meal_feedback WHERE profileId = :profileId")
    suspend fun getAllSync(profileId: Long): List<MealFeedbackEntity>

 /** Сколько раз продукт оценён как дискомфорт/плохо. */
    @Query("SELECT COUNT(*) FROM meal_feedback WHERE productId = :productId AND profileId = :profileId AND rating IN ('discomfort','bad')")
    suspend fun negativeCountForProduct(productId: Long, profileId: Long): Int

 /** Оценки за период (для PDF). */
    @Query("SELECT * FROM meal_feedback WHERE profileId = :profileId AND date >= :start AND date <= :end")
    suspend fun getForRange(start: String, end: String, profileId: Long): List<MealFeedbackEntity>

    @Query("DELETE FROM meal_feedback")
    suspend fun deleteAll()
}
