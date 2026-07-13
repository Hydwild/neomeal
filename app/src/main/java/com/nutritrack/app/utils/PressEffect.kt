package com.nutritrack.app.utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View

/**
 * Лёгкое «проседание» вью при нажатии (≈96%) — премиальный тактильный отклик.
 * Применение: PressEffect.apply(button) или PressEffect.applyAll(v1, v2, ...).
 */
object PressEffect {

    @SuppressLint("ClickableViewAccessibility")
    fun apply(view: View?) {
        view ?: return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false   // не перехватываем клики — onClick работает как обычно
        }
    }

    fun applyAll(vararg views: View?) { views.forEach { apply(it) } }
}
