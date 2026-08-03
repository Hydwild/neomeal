package com.nutritrack.app

import com.nutritrack.app.utils.DateUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Страж единственности ключа даты.
 *
 * В проекте даты в БД пишут два разных механизма: DateUtils (SimpleDateFormat)
 * и java.time (LocalDate + DateTimeFormatter). Оба обязаны давать ОДНУ и ту же
 * строку для одного дня при любой локали устройства, иначе запись, сделанная
 * одним путём, не находится запросом другого.
 */
class DateKeyTest {

    private val javaTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val original: Locale = Locale.getDefault()

    @After fun restoreLocale() {
        Locale.setDefault(original)
    }

    private fun javaTimeToday(): String = LocalDate.now().format(javaTimeFormatter)

    @Test fun `оба механизма дают одинаковый ключ — локаль по умолчанию`() {
        assertEquals(javaTimeToday(), DateUtils.today())
    }

    @Test fun `ключ не зависит от локали устройства`() {
        // th-TH — буддийский календарь (год +543); ar — восточно-арабские цифры;
        // ru/ko — обычные, но проверяем и их для полноты.
        val locales = listOf(
            Locale("th", "TH"),
            Locale("ar", "EG"),
            Locale("fa", "IR"),
            Locale("ru", "RU"),
            Locale("ko", "KR"),
            Locale.US
        )
        for (loc in locales) {
            Locale.setDefault(loc)
            assertEquals(
                "Ключ даты разъехался при локали $loc",
                javaTimeToday(),
                DateUtils.today()
            )
        }
    }

    @Test fun `ключ всегда ASCII и григорианский`() {
        for (loc in listOf(Locale("th", "TH"), Locale("ar", "EG"), Locale("fa", "IR"))) {
            Locale.setDefault(loc)
            val key = DateUtils.today()
            assertTrue("Ключ не в формате yyyy-MM-dd при локали $loc: $key",
                key.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            val year = key.substring(0, 4).toInt()
            assertTrue("Не григорианский год при локали $loc: $year",
                year == LocalDate.now().year)
        }
    }

    @Test fun `parse обратим относительно today`() {
        Locale.setDefault(Locale("th", "TH"))
        val key = DateUtils.today()
        assertEquals(key, DateUtils.format(DateUtils.parse(key), DateUtils.DB_FORMAT))
    }

    @Test fun `неделя начинается с понедельника и содержит сегодня — в любой локали`() {
        // Воскресенье 2026-08-02: именно здесь ломалась старая реализация для
        // локалей с воскресеньем как первым днём (en-US, ko-KR) — она отдавала
        // следующую неделю, не содержащую этот день.
        val sunday = LocalDate.of(2026, 8, 2)
        for (loc in listOf(Locale.US, Locale("ko", "KR"), Locale("ru", "RU"))) {
            Locale.setDefault(loc)
            val week = DateUtils.getWeekDates(0, sunday)
            assertEquals("Должно быть 7 дней ($loc)", 7, week.size)
            assertEquals("Неделя должна начинаться с понедельника ($loc)", "2026-07-27", week.first())
            assertEquals("и заканчиваться воскресеньем ($loc)", "2026-08-02", week.last())
            assertTrue("Текущая неделя обязана содержать сегодня ($loc)",
                week.contains("2026-08-02"))
        }
    }

    @Test fun `сдвиг недель работает в обе стороны`() {
        val wednesday = LocalDate.of(2026, 8, 5)
        assertEquals("2026-07-27", DateUtils.getWeekDates(-1, wednesday).first())
        assertEquals("2026-08-03", DateUtils.getWeekDates(0, wednesday).first())
        assertEquals("2026-08-10", DateUtils.getWeekDates(1, wednesday).first())
    }

    @Test fun `daysAgo даёт тот же формат, что и java_time`() {
        Locale.setDefault(Locale("th", "TH"))
        assertEquals(
            LocalDate.now().minusDays(14).format(javaTimeFormatter),
            DateUtils.daysAgo(14)
        )
    }
}
