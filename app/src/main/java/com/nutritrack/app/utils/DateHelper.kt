package com.nutritrack.app.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Форматирование дат с учётом текущей локали приложения.
 * LocaleHelper.wrap() уже выставляет Locale.setDefault(выбранный язык),
 * поэтому Locale.getDefault() здесь = язык интерфейса.
 *
 * Русский требует родительного падежа месяца («12 мая», не «12 май») —
 * это обеспечивает паттерн "d MMMM" с русской локалью.
 */
object DateHelper {

    private fun locale(): Locale = Locale.getDefault()

 /** "12 мая" / "May 12" — день и месяц без года. */
    fun dayMonth(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMMM", locale()))

 /** "12 мая 2026" / "May 12, 2026" — с годом. */
    fun dayMonthYear(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale()))

 /** "12.05" / "05/12" — короткая дата по локали. */
    fun shortDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("dd.MM", locale()))

 /** Безопасный парс "yyyy-MM-dd" LocalDate или null. */
    fun parse(iso: String): LocalDate? = try {
        LocalDate.parse(iso)
    } catch (_: Exception) {
        // запасной разбор "yyyy-MM-dd" вручную
        try {
            val p = iso.split("-")
            LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
        } catch (_: Exception) { null }
    }

 /** "Январь 2026" / "January 2026" — заголовок месяца (именительный падеж). */
    fun monthYear(year: Int, month: Int): String {
        val date = LocalDate.of(year, month, 1)
        // LLLL = standalone-форма месяца (именительный падеж для русского)
        val name = date.format(DateTimeFormatter.ofPattern("LLLL", locale()))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale()) else it.toString() }
        return "$name $year"
    }
}
