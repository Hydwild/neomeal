package com.nutritrack.app.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nutritrack.app.data.db.entity.MealItemEntity
import com.nutritrack.app.data.db.entity.MealPlanEntity

@Dao
interface MealPlanDao {

    // Получение приёмов пищи

    @Query("SELECT * FROM meal_plans WHERE date = :date AND profileId = :profileId ORDER BY time ASC")
    fun getMealPlansForDate(date: String, profileId: Long): LiveData<List<MealPlanEntity>>

    // Получить фиксированный слот по ключу (breakfast/lunch/dinner/snack)
    @Query("""
        SELECT * FROM meal_plans 
        WHERE mealKey = :mealKey AND date = :date AND profileId = :profileId 
        LIMIT 1
    """)
    suspend fun getMealPlanByKey(mealKey: String, date: String, profileId: Long): MealPlanEntity?

    @Query("SELECT * FROM meal_plans WHERE id = :id LIMIT 1")
    suspend fun getMealPlanById(id: Long): MealPlanEntity?

    // CRUD для плана

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(plan: MealPlanEntity): Long

    @Update
    suspend fun updateMealPlan(plan: MealPlanEntity)

    @Delete
    suspend fun deleteMealPlan(plan: MealPlanEntity)

    // Продукты в приёме пищи

    @Query("SELECT * FROM meal_items WHERE mealPlanId = :mealPlanId ORDER BY id ASC")
    fun getItemsForMeal(mealPlanId: Long): LiveData<List<MealItemEntity>>

    // Синхронный вариант — используется в ReminderReceiver при подтверждении "Поел"
    @Query("SELECT * FROM meal_items WHERE mealPlanId = :mealPlanId ORDER BY id ASC")
    suspend fun getItemsForMealSync(mealPlanId: Long): List<MealItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealItem(item: MealItemEntity)

    @Delete
    suspend fun deleteMealItem(item: MealItemEntity)

    @Query("DELETE FROM meal_items WHERE mealPlanId = :mealPlanId")
    suspend fun deleteItemsForMeal(mealPlanId: Long)

    @Query("SELECT * FROM meal_plans")
    suspend fun dumpAllPlans(): List<MealPlanEntity>

    @Query("SELECT * FROM meal_items")
    suspend fun dumpAllItems(): List<MealItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlans(list: List<MealPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(list: List<MealItemEntity>)
}
