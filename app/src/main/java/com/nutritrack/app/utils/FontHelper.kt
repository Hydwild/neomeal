package com.nutritrack.app.utils

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.nutritrack.app.R

/**
 * Применяет выбранный пользователем шрифт к конкретным View,
 * которые не подхватывают fontFamily из темы (например, базовый Button с кастомным фоном).
 */
object FontHelper {

 /** Возвращает Typeface выбранного шрифта или null для системного. */
    fun current(context: Context): Typeface? {
        val resId = when (PrefsManager(context).fontFamily) {
            "inter"      -> R.font.inter
            "manrope"    -> R.font.manrope
            "montserrat" -> R.font.montserrat
            else         -> return null
        }
        return try {
            ResourcesCompat.getFont(context, resId)
        } catch (e: Exception) {
            null
        }
    }

 /** Применяет шрифт к одному или нескольким TextView (сохраняя стиль bold/normal). */
    fun apply(vararg views: TextView?) {
        for (v in views) {
            v ?: continue
            val tf = current(v.context) ?: continue
            val style = v.typeface?.style ?: Typeface.NORMAL
            v.setTypeface(tf, style)
        }
    }
}
