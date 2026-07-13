package com.nutritrack.app.ui.micronutrients

import androidx.lifecycle.*
import com.nutritrack.app.data.MicronutrientNorms
import com.nutritrack.app.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** Строка результата: нутриент, съедено за день, норма, % покрытия. */
data class NutrientProgress(
    val key: String,
    val emoji: String,
    val unit: String,
    val consumed: Float,
    val rda: Float
) {
    val percent: Int get() = if (rda <= 0f) 0 else ((consumed / rda) * 100f).toInt()
    val isLow: Boolean get() = percent < 50
    val isOver: Boolean get() = percent > 150
}

/** Предложение восполнить дефицитный нутриент продуктами. */
data class DeficitSuggestion(
    val nutrientKey: String,
    val emoji: String,
    val percent: Int,
    val products: List<String>   // названия продуктов, богатых этим нутриентом
)

class MicronutrientViewModel(
    private val db: AppDatabase,
    private val profileId: Long
) : ViewModel() {

    private val _items = MutableLiveData<List<NutrientProgress>>()
    val items: LiveData<List<NutrientProgress>> = _items

    private val _hasData = MutableLiveData(true)
    val hasData: LiveData<Boolean> = _hasData

    private val _suggestions = MutableLiveData<List<DeficitSuggestion>>()
    val suggestions: LiveData<List<DeficitSuggestion>> = _suggestions

    fun load(date: String = today()) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { compute(date) }
            _items.postValue(result)
        }
    }

    private suspend fun compute(date: String): List<NutrientProgress> {
        val gender = db.profileDao().getActiveProfileSync()?.gender
        val entries = db.diaryDao().getEntriesForDateSync(date, profileId)
        val allProducts = db.productDao().getAllProductsSync()
        val products = allProducts.associateBy { it.id }

        // ключ нутриента -> сумма за день
        val totals = HashMap<String, Float>()
        var anyData = false

        for (e in entries) {
            val p = products[e.productId] ?: continue
            val factor = e.grams / 100f
            for (key in NUTRIENT_KEYS) {
                val v = nutrientValue(p, key)
                if (v != null) {
                    totals[key] = (totals[key] ?: 0f) + v * factor
                    anyData = true
                }
            }
        }

        _hasData.postValue(anyData)

        val progress = MicronutrientNorms.all.map { n ->
            NutrientProgress(
                key = n.key,
                emoji = n.emoji,
                unit = n.unit,
                consumed = totals[n.key] ?: 0f,
                rda = n.rda(gender)
            )
        }

        // Подбор продуктов под топ-3 дефицита (только если есть данные за день)
        _suggestions.postValue(if (anyData) buildSuggestions(progress, allProducts) else emptyList())

        return progress
    }

    /** Для самых дефицитных нутриентов находит продукты, богатые ими. */
    private fun buildSuggestions(
        progress: List<NutrientProgress>,
        allProducts: List<com.nutritrack.app.data.db.entity.ProductEntity>
    ): List<DeficitSuggestion> {
        val deficits = progress.filter { it.isLow }.sortedBy { it.percent }.take(3)
        return deficits.mapNotNull { def ->
            // продукты с наибольшим содержанием этого нутриента на 100 г
            val rich = allProducts
                .mapNotNull { p -> nutrientValue(p, def.key)?.let { p.name to it } }
                .filter { it.second > 0f }
                .sortedByDescending { it.second }
                .map { it.first }
                .distinct()
                .take(5)
            if (rich.isEmpty()) null
            else DeficitSuggestion(def.key, def.emoji, def.percent, rich)
        }
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private val NUTRIENT_KEYS = listOf(
            "vitA", "vitC", "vitD", "vitE", "vitK", "vitB6", "vitB12", "folate",
            "calcium", "iron", "magnesium", "zinc", "potassium", "sodium", "selenium"
        )

        /** Значение нутриента на 100 г у продукта по ключу. */
        private fun nutrientValue(
            p: com.nutritrack.app.data.db.entity.ProductEntity, key: String
        ): Float? = when (key) {
            "vitA" -> p.vitA; "vitC" -> p.vitC; "vitD" -> p.vitD; "vitE" -> p.vitE
            "vitK" -> p.vitK; "vitB6" -> p.vitB6; "vitB12" -> p.vitB12; "folate" -> p.folate
            "calcium" -> p.calcium; "iron" -> p.iron; "magnesium" -> p.magnesium
            "zinc" -> p.zinc; "potassium" -> p.potassium; "sodium" -> p.sodium
            "selenium" -> p.selenium
            else -> null
        }
    }

    class Factory(
        private val db: AppDatabase,
        private val profileId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MicronutrientViewModel(db, profileId) as T
    }
}
