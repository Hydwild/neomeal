package com.nutritrack.app.data.repository

import com.nutritrack.app.data.api.OffProduct
import com.nutritrack.app.data.api.OpenFoodFacts
import com.nutritrack.app.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Онлайн-поиск продуктов в Open Food Facts.
 * Чистый data-слой: без Context и UI — маппинг в ProductEntity делает вызывающий.
 */
object FoodSearchRepository {

    private const val SEARCH_FIELDS =
        "product_name,product_name_ru,brands,nutriments,image_url,code"

    private fun searchUrl(host: String, encQuery: String) =
        "https://$host/cgi/search.pl?search_terms=$encQuery" +
            "&search_simple=1&action=process&json=1&page_size=30" +
            "&sort_by=unique_scans_n&fields=$SEARCH_FIELDS"

    /**
     * Поиск по названию: сначала русская база, затем мировая.
     * Результаты объединяются без дублей (по штрихкоду/имени), с именем и калориями.
     * Сетевые ошибки каждой базы глотаются отдельно — вернётся то, что удалось получить.
     */
    suspend fun searchByName(query: String): List<OffProduct> = withContext(Dispatchers.IO) {
        val enc = URLEncoder.encode(query, "UTF-8")
        val acc = LinkedHashMap<String, OffProduct>()
        for (host in listOf("ru.openfoodfacts.org", "world.openfoodfacts.org")) {
            try {
                OpenFoodFacts.service.searchByUrl(searchUrl(host, enc)).products.forEach { p ->
                    val key = p.code ?: p.bestName()
                    if (p.hasName()) acc.putIfAbsent(key, p)
                }
            } catch (e: Exception) {
                AppLog.e("OFF search failed: $host", e)
            }
        }
        acc.values.toList()
    }

    /**
     * Поиск по штрихкоду. null — продукт не найден в базе.
     * Сетевые ошибки пробрасываются — вызывающий различает «нет в базе» и «нет сети».
     */
    suspend fun byBarcode(barcode: String): OffProduct? = withContext(Dispatchers.IO) {
        val resp = OpenFoodFacts.service.getProductByBarcode(barcode)
        if (resp.status == 1) resp.product else null
    }
}
