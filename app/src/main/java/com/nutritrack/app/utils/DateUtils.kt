package com.nutritrack.app.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    const val DB_FORMAT = "yyyy-MM-dd"
    const val DISPLAY_FORMAT = "dd MMM yyyy"

 /**
 * Локаль для КЛЮЧЕЙ базы данных. Только Locale.US — григорианский календарь и
 * ASCII-цифры.
 *
 * С Locale.getDefault() ключ зависел бы от локали устройства: для th-TH
 * SimpleDateFormat берёт буддийский календарь (год +543), в ряде локалей —
 * не-ASCII цифры. Часть кода пишет даты через java.time (всегда григорианский
 * ASCII), поэтому на таком устройстве два пути дали бы разные строки для одного
 * дня, и записи одного не находились бы запросами другого.
 *
 * Менять на локаль устройства нельзя. Для показа пользователю — DISPLAY_FORMAT
 * с локализованным форматированием (formatDisplay).
 */
    private val DB_LOCALE: Locale = Locale.US

    fun today(): String = format(Date(), DB_FORMAT)

 /** Дата N дней назад в формате БД (yyyy-MM-dd). */
    fun daysAgo(n: Int): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -n)
        return format(cal.time, DB_FORMAT)
    }

 /** Форматирование для БД и внутренних ключей — независимо от локали устройства. */
    fun format(date: Date, pattern: String): String =
        SimpleDateFormat(pattern, DB_LOCALE).format(date)

 /** Форматирование для показа пользователю — по локали устройства. */
    fun formatDisplay(date: Date, pattern: String = DISPLAY_FORMAT): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)

    fun parse(dateStr: String): Date =
        SimpleDateFormat(DB_FORMAT, DB_LOCALE).parse(dateStr) ?: Date()

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