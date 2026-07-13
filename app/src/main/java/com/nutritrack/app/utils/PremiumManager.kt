package com.nutritrack.app.utils

import android.content.Context
import android.widget.TextView
import com.nutritrack.app.R

/**
 * Центральная точка Premium-функций.
 *
 * Этап 1 (сейчас): ENFORCEMENT = false — всё бесплатно, в UI пометки «временно бесплатно».
 * Этап 2 (запуск монетизации): включить ENFORCEMENT = true и выставлять
 * PrefsManager.isPremium из биллинга (RuStore Billing). Гейты уже расставлены.
 */
object PremiumManager {

    enum class Feature {
        MENU_BUILDER,          // конструктор меню на неделю
        PDF_REPORTS,           // PDF-отчёты
        ALL_PETS_FRAMES,       // все питомцы и рамки без уровней
        MULTI_PROFILES,        // дополнительные профили (семья)
        MEASUREMENT_ANALYTICS  // расширенная аналитика замеров
    }

    /** Рубильник монетизации. Пока выключен — все функции доступны всем. */
    const val ENFORCEMENT = false

    fun isPremium(ctx: Context): Boolean =
        PremiumStore.isActive(ctx) && PremiumStore.signatureOk(ctx)

    fun hasAccess(ctx: Context, feature: Feature): Boolean =
        !ENFORCEMENT || isPremium(ctx)

    /**
     * Гейт перед Premium-функцией. Возвращает true, если доступ есть.
     * При включённом ENFORCEMENT без подписки показывает диалог и возвращает false.
     */
    fun gate(ctx: Context, feature: Feature): Boolean {
        if (hasAccess(ctx, feature)) return true
        android.app.AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.premium_locked_title))
            .setMessage(ctx.getString(R.string.premium_locked_msg))
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return false
    }

    /** Пилюля-бейдж «⭐ временно бесплатно» для программной вставки в UI. */
    fun freeBadge(ctx: Context): TextView = TextView(ctx).apply {
        text = ctx.getString(R.string.premium_free_badge)
        textSize = 11f
        setTextColor(0xFFFFD54F.toInt())
        val d = ctx.resources.displayMetrics.density
        setPadding((8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 12 * d
            setColor(0x33FFD54F)
        }
    }
}
