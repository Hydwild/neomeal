package com.nutritrack.app.utils

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.nutritrack.app.R

/**
 * Навигация с плавной анимацией (slide + fade).
 * Используется вместо обычного navigate(id) для единообразных переходов.
 */
fun NavController.navigateAnim(destId: Int) {
    val options = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_in_right)
        .setExitAnim(R.anim.slide_out_left)
        .setPopEnterAnim(R.anim.slide_in_left)
        .setPopExitAnim(R.anim.slide_out_right)
        .build()
    try { navigate(destId, null, options) } catch (_: Exception) {}
}
