package com.nutritrack.app.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutritrack.app.data.db.entity.DiaryEntryEntity

/**
 * Именованные шаблоны приёмов пищи («Мой завтрак»): набор позиций дневника,
 * добавляемый одним тапом из шторки добавления еды.
 *
 * Хранение — JSON в prefs (шаблонов немного, миграция БД не нужна).
 * Ключ включён в резервную копию (BackupManager, settings).
 */
object MealTemplates {

    const val PREFS_KEY = "meal_templates_json"
    private const val MAX_TEMPLATES = 12

    data class Item(
        val productId: Long,
        val productName: String,
        val grams: Float,
        val calories: Float,
        val protein: Float,
        val fat: Float,
        val carbs: Float,
        val fiber: Float? = null,
        val sugar: Float? = null,
    )

    data class Template(val name: String, val items: List<Item>) {
        val totalCalories: Int get() = items.sumOf { it.calories.toDouble() }.toInt()
    }

    private val gson = Gson()

    fun load(context: Context): List<Template> = try {
        val json = PrefsManager(context).getString(PREFS_KEY)
        if (json.isEmpty()) emptyList()
        else gson.fromJson(json, object : TypeToken<List<Template>>() {}.type) ?: emptyList()
    } catch (e: Exception) {
        AppLog.e("meal templates load failed", e); emptyList()
    }

    fun save(context: Context, templates: List<Template>) {
        PrefsManager(context).setString(PREFS_KEY, gson.toJson(templates.take(MAX_TEMPLATES)))
    }

 /** Сохранить записи (например, весь сегодняшний день) как шаблон. */
    fun addFromEntries(context: Context, name: String, entries: List<DiaryEntryEntity>) {
        if (entries.isEmpty()) return
        val tpl = Template(
            name = name.trim(),
            items = entries.map {
                Item(it.productId, it.productName, it.grams, it.calories,
                    it.protein, it.fat, it.carbs, it.fiber, it.sugar)
            }
        )
        // Шаблон с тем же именем заменяется
        save(context, load(context).filter { it.name != tpl.name } + tpl)
    }

    fun remove(context: Context, name: String) {
        save(context, load(context).filter { it.name != name })
    }

 /** Позиции шаблона как записи дневника на дату [date]. */
    fun toEntries(tpl: Template, date: String, profileId: Long): List<DiaryEntryEntity> =
        tpl.items.map {
            DiaryEntryEntity(
                productId = it.productId,
                productName = it.productName,
                grams = it.grams,
                calories = it.calories,
                protein = it.protein,
                fat = it.fat,
                carbs = it.carbs,
                fiber = it.fiber,
                sugar = it.sugar,
                date = date,
                profileId = profileId,
                timestamp = System.currentTimeMillis()
            )
        }
}
