package com.nutritrack.app.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import androidx.appcompat.app.AlertDialog

/**
 * Тонирование системных диалогов (выбор языка, шрифта и т.п.) под выбранный градиент.
 * Применяется ТОЛЬКО в светлой теме — там стандартный белый фон выбивается из оформления.
 * В тёмной теме диалоги остаются штатными (тёмный фон и так согласован).
 */
object DialogHelper {

 /**
 * Показать диалог с фоном под текущий градиент (светлая тема).
 * Вызывать сразу после dialog.show().
 */
    fun tint(dialog: Dialog, context: Context) {
        if (PrefsManager(context).isDarkTheme) return
        val window = dialog.window ?: return
        val density = context.resources.displayMetrics.density
        val radius = 20f * density
        val bg = BackgroundHelper.dialogBackground(context, radius)
        // Отступы по краям, чтобы фон-диалог не прилипал к границам экрана
        val inset = (16 * density).toInt()
        window.setBackgroundDrawable(InsetDrawable(bg, inset, inset, inset, inset))
    }

 /** Создать и сразу показать тонированный AlertDialog из готового билдера. */
    fun showTinted(builder: AlertDialog.Builder, context: Context): AlertDialog {
        val dialog = builder.create()
        dialog.show()
        tint(dialog, context)
        return dialog
    }
}
