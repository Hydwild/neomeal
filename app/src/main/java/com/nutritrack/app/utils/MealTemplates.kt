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

    // Раскладка ключей, которую писали релизные сборки до 1.4.4: классы не были
    // под keep, и R8 переименовывал поля (снято с mapping.txt релизной сборки).
    private val LEGACY_TEMPLATE_KEYS = mapOf("a" to "name", "b" to "items")
    private val LEGACY_ITEM_KEYS = mapOf(
        "a" to "productId", "b" to "productName", "c" to "grams", "d" to "calories",
        "e" to "protein", "f" to "fat", "g" to "carbs", "h" to "fiber", "i" to "sugar"
    )

    private fun renameKeys(obj: com.google.gson.JsonObject, table: Map<String, String>) =
        com.google.gson.JsonObject().also { out ->
            obj.entrySet().forEach { (k, v) -> out.add(table[k] ?: k, v) }
        }

 /**
 * Разбор сохранённого JSON. Понимает обе раскладки: нормальную и старую
 * буквенную. Выбрасывает исключение на нечитаемых данных — решение, что с
 * ними делать, принимает [load].
 */
    internal fun parse(json: String): List<Template> {
        if (json.isBlank()) return emptyList()
        val root = com.google.gson.JsonParser.parseString(json)
        val normalized = if (!root.isJsonArray) root else com.google.gson.JsonArray().also { arr ->
            root.asJsonArray.forEach { el ->
                if (!el.isJsonObject || el.asJsonObject.has("name")) { arr.add(el); return@forEach }
                val tpl = renameKeys(el.asJsonObject, LEGACY_TEMPLATE_KEYS)
                val items = tpl.get("items")
                if (items != null && items.isJsonArray) {
                    tpl.add("items", com.google.gson.JsonArray().also { fixed ->
                        items.asJsonArray.forEach { item ->
                            fixed.add(if (item.isJsonObject && !item.asJsonObject.has("calories"))
                                renameKeys(item.asJsonObject, LEGACY_ITEM_KEYS) else item)
                        }
                    })
                }
                arr.add(tpl)
            }
        }
        return gson.fromJson<List<Template>>(
            normalized, object : TypeToken<List<Template>>() {}.type) ?: emptyList()
    }

    fun load(context: Context): List<Template> {
        val prefs = PrefsManager(context)
        val json = prefs.getString(PREFS_KEY)
        return try {
            parse(json)
        } catch (e: Exception) {
            AppLog.e("meal templates load failed", e)
            // Страховка: следующее сохранение пишет «прочитанное + новое» и
            // затёрло бы эти данные. Откладываем исходник в отдельный ключ.
            if (prefs.getString(UNREADABLE_KEY).isEmpty()) prefs.setString(UNREADABLE_KEY, json)
            emptyList()
        }
    }

    private const val UNREADABLE_KEY = "meal_templates_json_unreadable"

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
