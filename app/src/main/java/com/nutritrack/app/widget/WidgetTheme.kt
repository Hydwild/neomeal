package com.nutritrack.app.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import com.nutritrack.app.R
import com.nutritrack.app.utils.PrefsManager

/**
 * Material You для виджетов: при включённой опции «Цвета из обоев»
 * (Android 12+) фон подменяется на системный нейтральный тон
 * (drawable-v31/widget_bg_dynamic). RemoteViews не умеют theme-атрибуты,
 * поэтому подмена — в рантайме при сборке виджета.
 */
object WidgetTheme {

    fun apply(context: Context, views: RemoteViews) {
        if (Build.VERSION.SDK_INT >= 31 && PrefsManager(context).dynamicColors) {
            views.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_bg_dynamic)
        }
    }
}
