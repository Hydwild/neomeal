package com.nutritrack.app.utils

import com.nutritrack.app.R

import android.content.Context

/** Единицы измерения: метрические / имперские. Конвертация только при отображении — в БД всегда метрика. */
object UnitsHelper {

    fun isImperial(context: Context) = PrefsManager(context).units == "imperial"

    // Вес (кг lb)
    fun formatWeight(context: Context, kg: Float, digits: Int = 1): String =
        if (isImperial(context)) "%.${digits}f lb".format(kg * 2.20462f)
        else context.getString(R.string.uh_kg_fmt, "%.${digits}f".format(kg))

    // Вода (мл fl oz)
    fun formatWater(context: Context, ml: Int): String =
        if (isImperial(context)) "%.1f oz".format(ml / 29.5735f)
        else context.getString(R.string.uh_ml_fmt, ml)

    // Рост/объёмы (см in)
    fun formatCm(context: Context, cm: Float, digits: Int = 1): String =
        if (isImperial(context)) "%.${digits}f in".format(cm / 2.54f)
        else context.getString(R.string.uh_cm_fmt, "%.${digits}f".format(cm))

    fun unitsLabel(context: Context): String =
        if (isImperial(context)) context.getString(R.string.uh_imperial) else context.getString(R.string.uh_metric)
}
