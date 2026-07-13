package com.nutritrack.app.data.repository

import androidx.lifecycle.LiveData
import com.nutritrack.app.data.db.dao.ProductDao
import com.nutritrack.app.data.db.entity.ProductEntity

class ProductRepository(private val dao: ProductDao) {
    val allProducts: LiveData<List<ProductEntity>> = dao.getAllProducts()
    val allCategories: LiveData<List<String>> = dao.getAllCategories()

    fun searchProducts(query: String) = dao.searchProducts(query)
    fun getByCategory(category: String) = dao.getProductsByCategory(category)
    suspend fun getById(id: Long) = dao.getProductById(id)
    suspend fun getAllSync() = dao.getAllProductsSync()
    suspend fun insert(product: ProductEntity) = dao.insertProduct(product)
    suspend fun update(product: ProductEntity) = dao.updateProduct(product)
    suspend fun delete(product: ProductEntity) = dao.deleteProduct(product)
}