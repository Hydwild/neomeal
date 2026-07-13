package com.nutritrack.app.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    const val DB_FORMAT = "yyyy-MM-dd"
    const val DISPLAY_FORMAT = "dd MMM yyyy"

    fun today(): String = format(Date(), DB_FORMAT)

 /** Дата N дней назад в формате БД (yyyy-MM-dd). */
    fun daysAgo(n: Int): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -n)
        return format(cal.time, DB_FORMAT)
    }

    fun format(date: Date, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)

    fun parse(dateStr: String): Date =
        SimpleDateFormat(DB_FORMAT, Locale.getDefault()).parse(dateStr) ?: Date()

    fun getWeekDates(offsetWeeks: Int = 0): List<String> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.WEEK_OF_YEAR, offsetWeeks)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return (0..6).map { i ->
            val d = cal.clone() as Calendar
            d.add(Calendar.DAY_OF_WEEK, i)
            format(d.time, DB_FORMAT)
        }
    }
}