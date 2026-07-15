package com.nutritrack.app.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Модели ответа Open Food Facts

data class OffProductResponse(
    val status: Int = 0,                    // 1 = найдено, 0 = нет
    val product: OffProduct? = null
)

data class OffSearchResponse(
    val products: List<OffProduct> = emptyList()
)

data class OffProduct(
    @SerializedName("product_name")    val productName: String? = null,
    @SerializedName("product_name_ru") val productNameRu: String? = null,
    @SerializedName("brands")          val brands: String? = null,
    @SerializedName("nutriments")      val nutriments: OffNutriments? = null,
    @SerializedName("image_url")       val imageUrl: String? = null,
    @SerializedName("serving_size")        val servingSize: String? = null,
    @SerializedName("serving_quantity")    val servingQuantity: String? = null,
    @SerializedName("code")            val code: String? = null
) {
 /** Лучшее доступное название (рус общее бренд). */
    fun bestName(): String {
        val n = productNameRu?.takeIf { it.isNotBlank() }
            ?: productName?.takeIf { it.isNotBlank() }
            ?: brands?.takeIf { it.isNotBlank() }
        return n?.trim() ?: ""
    }

    fun hasName(): Boolean = bestName().isNotBlank()

    /**
     * Вес одной порции в граммах из базы (serving_quantity или из serving_size).
     * Возвращает null, если в базе нет данных о порции.
     * Примеры serving_size: "81 g", "30 g (1 bar)", "250ml".
     */
    fun servingGrams(): Int? {
        // serving_quantity обычно уже число в граммах
        servingQuantity?.trim()?.toDoubleOrNull()?.let {
            if (it in 1.0..2000.0) return it.toInt()
        }
        // иначе вытаскиваем первое число из serving_size
        val s = servingSize ?: return null
        val match = Regex("""(\d+(?:[.,]\d+)?)""").find(s) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return if (value in 1.0..2000.0) value.toInt() else null
    }
}

data class OffNutriments(
    // Значения на 100 г
    @SerializedName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerializedName("proteins_100g")    val proteins100g: Double? = null,
    @SerializedName("fat_100g")         val fat100g: Double? = null,
    @SerializedName("carbohydrates_100g") val carbs100g: Double? = null,
    @SerializedName("fiber_100g")       val fiber100g: Double? = null,
    @SerializedName("sugars_100g")      val sugars100g: Double? = null
)

// Retrofit-интерфейс

interface OpenFoodFactsService {

    // Поиск продукта по штрихкоду
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String =
            "product_name,product_name_ru,brands,nutriments,image_url,code,serving_size,serving_quantity"
    ): OffProductResponse

    // Поиск по названию — полный URL (строим в коде, надёжнее для search_terms)
    @GET
    suspend fun searchByUrl(@retrofit2.http.Url url: String): OffSearchResponse
}

// Singleton-клиент

object OpenFoodFacts {
    private const val BASE_URL = "https://world.openfoodfacts.org/"

    // Клиент с увеличенными таймаутами — устойчивее к медленным сетям/VPN
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)   // ожидание соединения
            .readTimeout(20, TimeUnit.SECONDS)      // ожидание ответа
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                // Open Food Facts просит указывать User-Agent приложения
                val req = chain.request().newBuilder()
                    .header("User-Agent",
                        "NeoMeal/${com.nutritrack.app.BuildConfig.VERSION_NAME} (Android)")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    val service: OpenFoodFactsService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenFoodFactsService::class.java)
    }
}
