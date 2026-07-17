package com.nutritrack.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Смена иконки приложения через activity-alias (классика/тёмная/фиолетовая).
 * Включается выбранный алиас, остальные выключаются (DONT_KILL_APP).
 * Побочный эффект платформы: некоторые лаунчеры пересоздают ярлык.
 */
object AppIconHelper {

    val KEYS = listOf("classic", "dark", "purple")

    private fun alias(key: String) = when (key) {
        "dark"   -> ".IconDark"
        "purple" -> ".IconPurple"
        else     -> ".IconClassic"
    }

    fun apply(context: Context, key: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        KEYS.forEach { k ->
            val state = if (k == key)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + alias(k)),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
