package com.nutritrack.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.data.repository.ProductRepository
import kotlinx.coroutines.launch

class ProductViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProductRepository((app as NutriApp).database.productDao())

    val allProducts = repo.allProducts
    val allCategories = repo.allCategories

    private val _searchQuery = MutableLiveData("")
    private val _selectedCategory = MutableLiveData<String?>(null)

    val filteredProducts: LiveData<List<ProductEntity>> = MediatorLiveData<List<ProductEntity>>().apply {
        var latestList = listOf<ProductEntity>()
        var latestQuery = ""
        var latestCategory: String? = null

        fun update() {
            value = latestList.filter { product ->
                val matchesQuery = latestQuery.isEmpty() ||
                        product.name.contains(latestQuery, ignoreCase = true)
                val matchesCategory = latestCategory == null ||
                        product.category == latestCategory
                matchesQuery && matchesCategory
            }
        }

        addSource(allProducts) { latestList = it; update() }
        addSource(_searchQuery) { latestQuery = it; update() }
        addSource(_selectedCategory) { latestCategory = it; update() }
    }

    fun setQuery(q: String) { _searchQuery.value = q }
    fun setCategory(c: String?) { _selectedCategory.value = c }

    fun insert(product: ProductEntity) = viewModelScope.launch { repo.insert(product) }
    fun update(product: ProductEntity) = viewModelScope.launch { repo.update(product) }
    fun delete(product: ProductEntity) = viewModelScope.launch { repo.delete(product) }
}