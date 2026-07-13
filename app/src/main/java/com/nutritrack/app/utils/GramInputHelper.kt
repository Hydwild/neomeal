package com.nutritrack.app.utils

import com.nutritrack.app.R

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog

/**
 * Вспомогательный объект для ручного ввода граммов.
 * Используется в DashboardFragment, ProductsFragment и MealPlanFragment.
 */
object GramInputHelper {

 /**
 * Показывает диалог ручного ввода граммов.
 * @param context контекст
 * @param current текущее значение граммов
 * @param onResult callback с новым значением (если пользователь подтвердил)
 */
    fun showInputDialog(
        context: Context,
        current: Int,
        onResult: (Int) -> Unit
    ) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint      = context.getString(R.string.gi_hint)
            setText(current.toString())
            selectAll()
            textSize = 20f
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(context, 20)
            setPadding(pad, dpToPx(context, 8), pad, 0)
            addView(input)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.gi_title))
            .setView(container)
            .setPositiveButton(context.getString(R.string.c_done)) { _, _ ->
                val value = input.text.toString().toIntOrNull()
                when {
                    value == null || value <= 0 -> {
                        android.widget.Toast.makeText(
                            context, "Введи корректное число", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    value > 5000 -> {
                        android.widget.Toast.makeText(
                            context, "Максимум 5000г", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> onResult(value)
                }
            }
            .setNegativeButton(context.getString(R.string.c_cancel), null)
            .show()

        // Авто-открытие клавиатуры
        input.requestFocus()
        input.postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
