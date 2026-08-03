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

 /**
 * Семь дат недели (понедельник … воскресенье) в формате БД.
 * [offsetWeeks] сдвигает на N недель: 0 — текущая неделя, -1 — прошлая.
 *
 * Начало недели считается явно от понедельника. Прежняя реализация делала
 * `Calendar.set(DAY_OF_WEEK, MONDAY)`, а он резолвится относительно
 * locale-зависимого firstDayOfWeek: в en-US и ko-KR неделя начинается с
 * воскресенья, поэтому по воскресеньям возвращалась СЛЕДУЮЩАЯ (пустая) неделя
 * вместо завершившейся.
 */
    fun getWeekDates(offsetWeeks: Int = 0): List<String> =
        getWeekDates(offsetWeeks, java.time.LocalDate.now())

 /** Версия с явной датой — для тестов (иначе поведение зависит от дня запуска). */
    internal fun getWeekDates(offsetWeeks: Int, today: java.time.LocalDate): List<String> {
        val monday = today
            .plusWeeks(offsetWeeks.toLong())
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        // ISO_LOCAL_DATE — ровно yyyy-MM-dd, без влияния локали
        return (0..6).map {
            monday.plusDays(it.toLong()).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }
}