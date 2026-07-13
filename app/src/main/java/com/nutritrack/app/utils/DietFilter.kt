package com.nutritrack.app.utils

import com.nutritrack.app.data.Recipe

/**
 * Диетические фильтры для конструктора меню.
 * Подходящесть рецепта определяется автоматически по ингредиентам (ключевые слова).
 *
 * ВАЖНО: для медицинских групп (беременные, кормящие, диабет, аллергия) это
 * НЕ медицинская рекомендация, а лишь подбор блюд по общим принципам.
 * Везде, где diet.medical == true, показывается дисклеймер о консультации с врачом.
 */
object DietFilter {

    data class Diet(
        val key: String,
        val emoji: String,
        val medical: Boolean   // требуется ли дисклеймер о враче
    )

    val DIETS = listOf(
        Diet("none",       "🍽️", false),
        Diet("vegan",      "🌱", false),
        Diet("vegetarian", "🥗", false),
        Diet("no_pork",    "🍖", false),
        Diet("no_lactose", "🥛", false),
        Diet("no_gluten",  "🌾", false),
        Diet("pregnant",   "🤰", true),
        Diet("nursing",    "🤱", true),
        Diet("diabetic",   "🩺", true),
        Diet("allergic",   "⚠️", true),
    )

    fun byKey(key: String): Diet = DIETS.firstOrNull { it.key == key } ?: DIETS.first()

    // Ключевые слова по группам ингредиентов (по русским названиям)
    private val MEAT = listOf("говядин","свинин","баранин","курин","куриц","индейк","телятин","фарш","мясо","бекон","ветчин","колбас","сосиск","котлет","стейк","грудк","филе куриное","печёнк","печень курин","печень говяж","печень телячь","печень индейк","субпродукт","язык говяж","сердце курин","потрох")
    private val PORK = listOf("свинин","бекон","ветчин","сало","карбонад")
    private val FISH = listOf("рыб","лосось","тунец","треск","сёмг","форель","сельдь","скумбри","креветк","кальмар","морепродукт","икра")
    private val EGG  = listOf("яйц","яичн","омлет","меланж")
    private val DAIRY = listOf("молок","творог","сыр","кефир","сметан","йогурт","сливк","масло сливочн","ряженк","брынз","моцарелл")
    private val GLUTEN = listOf("пшениц","хлеб","мук","макарон","паста","булгур","манк","овсян","перлов","ячмен","сухар","панировк","печень","лаваш","батон","спагетти")
    private val HONEY = listOf("мёд","мед ")
    private val HIGH_SUGAR = listOf("сахар","мёд","мед ","сироп","варень","джем","сгущ","шоколад","конфет","зефир","мармелад","печень","торт","пирожн","банан","финик","изюм")
    private val COMMON_ALLERGENS = listOf("орех","арахис","миндал","фундук","кешью","креветк","рак","краб","моллюск","яйц","молок","сыр","соя","сельдер","горчиц","кунжут")

    private fun ingredientsText(r: Recipe): String =
        (r.name + " " + r.ingredients.joinToString(" ") { it.name }).lowercase()

    private fun containsAny(text: String, words: List<String>) = words.any { text.contains(it) }

 /** Подходит ли рецепт под выбранную диету. */
    fun matches(recipe: Recipe, dietKey: String): Boolean {
        val t = ingredientsText(recipe)
        return when (dietKey) {
            "none"       -> true
            // веган: постное (lenten) уже без животного — самый надёжный признак
            "vegan"      -> recipe.lenten ||
                    !(containsAny(t, MEAT) || containsAny(t, FISH) || containsAny(t, EGG) ||
                            containsAny(t, DAIRY) || containsAny(t, HONEY))
            "vegetarian" -> !(containsAny(t, MEAT) || containsAny(t, FISH))
            "no_pork"    -> !containsAny(t, PORK)
            "no_lactose" -> !containsAny(t, DAIRY)
            "no_gluten"  -> !containsAny(t, GLUTEN)
            // медицинские: подбор по общим принципам (не строгая гарантия)
            "pregnant"   -> !containsAny(t, listOf("алкогол","вино","пиво","сырая рыб","суши","ролл","непастеризов")) &&
                    !containsAny(t, FISH.filter { it in listOf("тунец") })   // тунец — много ртути
            "nursing"    -> !containsAny(t, listOf("алкогол","вино","пиво")) &&
                    !containsAny(t, COMMON_ALLERGENS.take(5))                // меньше острых аллергенов
            "diabetic"   -> !containsAny(t, HIGH_SUGAR)                              // ограничиваем быстрые углеводы
            "allergic"   -> !containsAny(t, COMMON_ALLERGENS)                        // убираем топ-аллергены
            else         -> true
        }
    }

    fun filter(recipes: List<Recipe>, dietKey: String): List<Recipe> =
        recipes.filter { matches(it, dietKey) }

    /**
     * Мясной ли продукт по названию. Исключение: «яйцо куриное» и подобные —
     * если из мясных корней совпали только «курин»/«куриц», а название яичное,
     * это яйцо, а не мясо.
     */
    private fun isMeatName(t: String): Boolean {
        val hits = MEAT.filter { t.contains(it) }
        if (hits.isEmpty()) return false
        val onlyChickenWord = hits.all { it == "курин" || it == "куриц" }
        val eggish = t.contains("яйц") || t.contains("яичн")
        return !(onlyChickenWord && eggish)
    }

    /** Подходит ли продукт (по названию) под выбранную диету. */
    fun matchesProductName(productName: String, dietKey: String): Boolean {
        val t = productName.lowercase()
        return when (dietKey) {
            "none"       -> true
            "vegan"      -> !(isMeatName(t) || containsAny(t, FISH) || containsAny(t, EGG) ||
                    containsAny(t, DAIRY) || containsAny(t, HONEY))
            "vegetarian" -> !(isMeatName(t) || containsAny(t, FISH))
            "no_pork"    -> !containsAny(t, PORK)
            "no_lactose" -> !containsAny(t, DAIRY)
            "no_gluten"  -> !containsAny(t, GLUTEN)
            "pregnant"   -> !containsAny(t, listOf("алкогол","вино","пиво","сырая рыб","суши","ролл","непастеризов")) &&
                    !containsAny(t, listOf("тунец"))
            "nursing"    -> !containsAny(t, listOf("алкогол","вино","пиво")) &&
                    !containsAny(t, COMMON_ALLERGENS.take(5))
            "diabetic"   -> !containsAny(t, HIGH_SUGAR)
            "allergic"   -> !containsAny(t, COMMON_ALLERGENS)
            else         -> true
        }
    }

    /** Проходит ли продукт ВСЕ диеты пользователя (ключи через ;). */
    fun productPassesDiets(productName: String, dietsStored: String?): Boolean {
        if (dietsStored.isNullOrBlank()) return true
        val keys = dietsStored.split(";").map { it.trim() }.filter { it.isNotEmpty() && it != "none" }
        return keys.all { matchesProductName(productName, it) }
    }
}
