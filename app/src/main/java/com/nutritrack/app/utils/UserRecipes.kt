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
        cached = try {
            val json = PrefsManager(context).getString(PREFS_KEY)
            if (json.isEmpty()) emptyList()
            else gson.fromJson<List<Recipe>>(
                json, object : TypeToken<List<Recipe>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            AppLog.e("user recipes load failed", e); emptyList()
        }
    }

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
