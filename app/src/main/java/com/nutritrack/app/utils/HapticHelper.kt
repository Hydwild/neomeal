package com.nutritrack.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View

/** Тактильный отклик. Управляется настройкой «Вибро-отклик». */
object HapticHelper {
    private fun vibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    } catch (_: Exception) { null }

    private fun vibrate(context: Context, millis: Long, amplitude: Int) {
        if (!PrefsManager(context).hapticEnabled) return
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        try { v.vibrate(VibrationEffect.createOneShot(millis, amplitude)) } catch (_: Exception) { }
    }

    fun tap(view: View?) { view ?: return; vibrate(view.context, 18L, 120) }
    fun success(view: View?) { view ?: return; vibrate(view.context, 45L, VibrationEffect.DEFAULT_AMPLITUDE) }
}
