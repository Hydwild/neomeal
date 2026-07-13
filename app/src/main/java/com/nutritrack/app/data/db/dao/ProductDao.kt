package com.nutritrack.app.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nutritrack.app.data.db.entity.ProductEntity

@Dao
interface ProductDao {

    // Получение продуктов

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): LiveData<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE category = :category ORDER BY name ASC")
    fun getProductsByCategory(category: String): LiveData<List<ProductEntity>>

    // Поиск по имени — LiveData (используется в ProductRepository)
    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchProducts(query: String): LiveData<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isCustom = 1 ORDER BY name ASC")
    fun getCustomProducts(): LiveData<List<ProductEntity>>

    // Все уникальные категории — LiveData (используется в ProductRepository)
    @Query("SELECT DISTINCT category FROM products ORDER BY category ASC")
    fun getAllCategories(): LiveData<List<String>>

    // Количество / проверки

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    // Вставка / обновление / удаление

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    // Очистка / перезаполнение базы

    // Удалить только встроенные (не пользовательские) продукты
    @Query("DELETE FROM products WHERE isCustom = 0")
    suspend fun deleteNonCustomProducts()

    // Удалить дубликаты встроенных продуктов (оставить минимальный id для каждого имени)
    @Query("""
        DELETE FROM products 
        WHERE isCustom = 0 
        AND id NOT IN (
            SELECT MIN(id) FROM products WHERE isCustom = 0 GROUP BY name
        )
    """)
    suspend fun removeDuplicates()

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("SELECT * FROM products")
    suspend fun dumpAll(): List<ProductEntity>
}
