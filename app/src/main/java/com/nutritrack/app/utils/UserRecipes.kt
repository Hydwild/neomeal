package com.nutritrack.app.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutritrack.app.data.Recipe

/**
 * Пользовательские рецепты конструктора меню.
 * Хранятся JSON-ом в prefs, участвуют в генерации меню и списке покупок
 * наравне со встроенными (RecipeData подмешивает [cached]).
 * Кэш инициализируется в NutriApp и обновляется при изменениях.
 */
object UserRecipes {

    const val PREFS_KEY = "user_recipes_json"
    const val ID_PREFIX = "user_"
    private const val MAX = 50

    @Volatile
    var cached: List<Recipe> = emptyList()
        private set

    private val gson = Gson()

    fun load(context: Context) {
        val prefs = PrefsManager(context)
        val json = prefs.getString(PREFS_KEY)
        cached = try {
            if (json.isEmpty()) emptyList()
            else gson.fromJson<List<Recipe>>(
                json, object : TypeToken<List<Recipe>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            AppLog.e("user recipes load failed", e)
            // Страховка: add() пишет «прочитанное + новое» и при пустом чтении
            // затёр бы все прежние рецепты. Откладываем исходник в отдельный ключ.
            // (До 1.4.4 так и происходило в релизе — см. TypeToken в proguard-rules.)
            if (prefs.getString(UNREADABLE_KEY).isEmpty()) prefs.setString(UNREADABLE_KEY, json)
            emptyList()
        }
    }

    private const val UNREADABLE_KEY = "user_recipes_json_unreadable"

    fun isUserRecipe(id: String) = id.startsWith(ID_PREFIX)

    fun add(context: Context, recipe: Recipe) {
        val list = (cached.filter { it.id != recipe.id } + recipe).takeLast(MAX)
        PrefsManager(context).setString(PREFS_KEY, gson.toJson(list))
        cached = list
    }

    fun remove(context: Context, id: String) {
        val list = cached.filter { it.id != id }
        PrefsManager(context).setString(PREFS_KEY, gson.toJson(list))
        cached = list
    }
}
