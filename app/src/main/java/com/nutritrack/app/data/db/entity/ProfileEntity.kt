package com.nutritrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "User",
    val age: Int = 25,
    val height: Float = 170f,
    val weight: Float = 70f,
    val gender: String = "male",
    val goal: String = "maintain",   // "lose" / "gain" / "maintain" / "healthy"
    val targetWeight: Float = 0f,     // целевой вес, кг (0 = не задан)
    val activityLevel: String = "moderate",
    val photoUri: String? = null,
    val isActive: Boolean = false,
    val allergens: String = "",   // коды аллергенов через ; (см. AllergenData)
    val diets: String = ""        // ключи диет через ; (см. DietFilter)
)