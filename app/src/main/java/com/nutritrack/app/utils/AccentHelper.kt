package com.nutritrack.app.utils

import android.content.Context
import android.graphics.Color

/** Акцентный цвет приложения, выбранный пользователем. */
object AccentHelper {

    // ключ (название, основной цвет, мягкий цвет-пилюля)
    val palette = listOf(
        Triple("purple", "🟣 Фиолетовый", "#B388FF"),
        Triple("green",  "🟢 Зелёный",    "#69F0AE"),
        Triple("blue",   "🔵 Голубой",    "#40C4FF"),
        Triple("orange", "🟠 Оранжевый",  "#FFB74D"),
        Triple("pink",   "🩷 Розовый",    "#FF80AB"),
        Triple("teal",   "🩵 Бирюзовый",  "#64FFDA"),
    )

    fun colorHex(context: Context): String {
        val key = PrefsManager(context).accentColor
        return palette.firstOrNull { it.first == key }?.third ?: "#B388FF"
    }

    fun color(context: Context): Int = Color.parseColor(colorHex(context))

 /** Полупрозрачная версия для пилюли/подсветок (alpha 0x3A). */
    fun pillColor(context: Context): Int {
        val c = color(context)
        return Color.argb(0x3A, Color.red(c), Color.green(c), Color.blue(c))
    }

    fun label(context: Context): String {
        val key = PrefsManager(context).accentColor
        return labelOf(context, key)
    }

 /** Локализованное название акцента по ключу. */
    fun labelOf(context: Context, key: String): String {
        val resId = when (key) {
            "purple" -> com.nutritrack.app.R.string.acc_purple
            "green"  -> com.nutritrack.app.R.string.acc_green
            "blue"   -> com.nutritrack.app.R.string.acc_blue
            "orange" -> com.nutritrack.app.R.string.acc_orange
            "pink"   -> com.nutritrack.app.R.string.acc_pink
            "teal"   -> com.nutritrack.app.R.string.acc_teal
            else     -> com.nutritrack.app.R.string.acc_purple
        }
        return context.getString(resId)
    }
}
