package com.nutritrack.app.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Применение выбранного языка ("auto" = системный). */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val lang = PrefsManager(base).language
        if (lang == "auto" || lang.isBlank()) return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
