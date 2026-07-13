package com.nutritrack.app.ui.micronutrients

import androidx.lifecycle.*
import com.nutritrack.app.data.db.AppDatabase
import com.nutritrack.app.data.db.entity.ProductEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Подбор продуктов под выбранные пользователем целевые нутриенты. */
class NutrientGoalViewModel(
    private val db: AppDatabase
) : ViewModel() {

    private val _result = MutableLiveData<List<DeficitSuggestion>>()
    val result: LiveData<List<DeficitSuggestion>> = _result

    private var cached: List<ProductEntity>? = null

    /** keys — выбранные ключи нутриентов (vitA, iron, ...). */
    fun build(keys: List<String>) {
        viewModelScope.launch {
            val allProducts = withContext(Dispatchers.IO) {
                cached ?: db.productDao().getAllProductsSync().also { cached = it }
            }
            // Профиль пользователя: его диеты и аллергены
            val profile = withContext(Dispatchers.IO) { db.profileDao().getActiveProfileSync() }
            val userDiets = profile?.diets
            val userAllergens = profile?.allergens

            // Отсеиваем то, что противоречит диете или содержит аллергены пользователя
            val products = allProducts.filter { p ->
                com.nutritrack.app.utils.DietFilter.productPassesDiets(p.name, userDiets) &&
                com.nutritrack.app.utils.AllergenData.productIsSafe(p.name, p.allergens, userAllergens)
            }
            val out = keys.mapNotNull { key ->
                val n = com.nutritrack.app.data.MicronutrientNorms.byKey(key) ?: return@mapNotNull null
                val rich = products
                    .mapNotNull { p -> valueOf(p, key)?.let { p.name to it } }
                    .filter { it.second > 0f }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .distinct()
                    .take(6)
                if (rich.isEmpty()) null
                else DeficitSuggestion(key, n.emoji, 0, rich)
            }
            _result.postValue(out)
        }
    }

    private fun valueOf(p: ProductEntity, key: String): Float? = when (key) {
        "vitA" -> p.vitA; "vitC" -> p.vitC; "vitD" -> p.vitD; "vitE" -> p.vitE
        "vitK" -> p.vitK; "vitB6" -> p.vitB6; "vitB12" -> p.vitB12; "folate" -> p.folate
        "calcium" -> p.calcium; "iron" -> p.iron; "magnesium" -> p.magnesium
        "zinc" -> p.zinc; "potassium" -> p.potassium; "sodium" -> p.sodium
        "selenium" -> p.selenium
        else -> null
    }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NutrientGoalViewModel(db) as T
    }
}
