package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R

/**
 * Справочник пищевых аллергенов (на основе 14 официальных аллергенов ЕС + частые).
 *
 * ВАЖНО: определение аллергена по названию продукта — это ПОДСКАЗКА, а не
 * медицинская гарантия. Аллерген может быть в составе, которого нет в названии.
 * Пользователь с аллергией обязан проверять состав сам. Везде в UI это
 * сопровождается предупреждением.
 *
 * code   — стабильный код для хранения в БД (через ;)
 * titleRes — название для UI (RU/EN)
 * keywords — ключевые слова в русских названиях для авто-определения
 */
object AllergenData {

    data class Allergen(
        val code: String,
        val emoji: String,
        val titleRes: Int,
        val keywords: List<String>
    )

    val ALL: List<Allergen> = listOf(
        Allergen("gluten", "🌾", R.string.allg_gluten,
            listOf("пшениц","хлеб","мук","макарон","паста","булгур","манк","ячмен","рож","сухар","панировк","лаваш","батон","спагетти","печенье","булк","блин","оладь")),
        Allergen("crustaceans", "🦐", R.string.allg_crustaceans,
            listOf("креветк","краб","рак","лобстер","омар","лангуст")),
        Allergen("eggs", "🥚", R.string.allg_eggs,
            listOf("яйц","яичн","омлет","меланж","майонез")),
        Allergen("fish", "🐟", R.string.allg_fish,
            listOf("рыб","лосось","тунец","треск","сёмг","форель","сельдь","скумбри","икра")),
        Allergen("peanuts", "🥜", R.string.allg_peanuts,
            listOf("арахис")),
        Allergen("soy", "🫛", R.string.allg_soy,
            listOf("соя","соев","тофу","эдамаме")),
        Allergen("milk", "🥛", R.string.allg_milk,
            listOf("молок","творог","сыр","кефир","сметан","йогурт","сливк","масло сливочн","ряженк","брынз","моцарелл","лактоз")),
        Allergen("nuts", "🌰", R.string.allg_nuts,
            listOf("орех","миндал","фундук","кешью","грецк","пекан","фисташк","макадами")),
        Allergen("celery", "🥬", R.string.allg_celery,
            listOf("сельдер")),
        Allergen("mustard", "🟡", R.string.allg_mustard,
            listOf("горчиц")),
        Allergen("sesame", "⚪", R.string.allg_sesame,
            listOf("кунжут","тахин","сезам")),
        Allergen("sulfites", "🍷", R.string.allg_sulfites,
            listOf("вино","сухофрукт","сульфит")),
        Allergen("lupin", "🌱", R.string.allg_lupin,
            listOf("люпин")),
        Allergen("molluscs", "🦪", R.string.allg_molluscs,
            listOf("мидии","устриц","кальмар","осьминог","моллюск","гребешок")),
    )

    fun byCode(code: String): Allergen? = ALL.firstOrNull { it.code == code }

    /** Список кодов из строки БД (коды через ;). */
    fun parse(stored: String?): List<String> =
        stored?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun join(codes: Collection<String>): String = codes.distinct().joinToString(";")

    /** Авто-определение аллергенов по названию продукта (подсказка). */
    fun detect(productName: String): List<String> {
        val t = productName.lowercase()
        return ALL.filter { a -> a.keywords.any { t.contains(it) } }.map { it.code }
    }

    /**
     * Безопасен ли продукт для пользователя с аллергенами [userAllergens] (строка кодов).
     * Учитывает как явные коды продукта [productAllergens], так и авто-определение по имени.
     */
    fun productIsSafe(productName: String, productAllergens: String?, userAllergens: String?): Boolean {
        val user = parse(userAllergens)
        if (user.isEmpty()) return true
        val inProduct = (parse(productAllergens) + detect(productName)).toSet()
        // небезопасен, если есть пересечение
        return user.none { it in inProduct }
    }

    fun title(ctx: Context, code: String): String =
        byCode(code)?.let { ctx.getString(it.titleRes) } ?: code
}
