package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val category: String = "Other",
    val photoUri: String? = null,
    val isCustom: Boolean = false,
    val profileId: Long = 1,
    val price: Float? = null,           // ← НОВОЕ: стоимость
    val autoTip: String? = null,        // ← НОВОЕ: автоподсказка
    val manualNote: String? = null,     // ← НОВОЕ: ручная пометка
    val isFavorite: Boolean = false,    // Избранный продукт
    val allergens: String = "",         // коды аллергенов через ; (AllergenData)
    // Микронутриенты на 100 г (источник: USDA FoodData Central, SR Legacy, CC0).
    // null = данных нет (добавки, составные блюда без аналога).
    val vitA: Float? = null,    // мкг RAE
    val vitC: Float? = null,    // мг
    val vitD: Float? = null,    // мкг
    val vitE: Float? = null,    // мг
    val vitK: Float? = null,    // мкг
    val vitB6: Float? = null,   // мг
    val vitB12: Float? = null,  // мкг
    val folate: Float? = null,  // мкг
    val calcium: Float? = null, // мг
    val iron: Float? = null,    // мг
    val magnesium: Float? = null, // мг
    val zinc: Float? = null,    // мг
    val potassium: Float? = null, // мг
    val sodium: Float? = null,  // мг
    val selenium: Float? = null, // мкг

    // Состав комбо: "имя:граммы;имя:граммы" (только для комбо-продуктов). nullable.
    val comboItems: String? = null,

    // Клетчатка и сахар на 100 г. null = данных нет (v20).
    val fiber: Float? = null,   // г
    val sugar: Float? = null    // г
)