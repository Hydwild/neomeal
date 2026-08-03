package com.nutritrack.app

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Страж «единственного источника правды».
 *
 * Три подряд найденных бага (фантомные уведомления о еде, чужой профиль в
 * половине экранов, разъезжающийся ключ даты) — один и тот же дефект: у сущности
 * заводится второй источник правды, синхронизация отсутствует, расхождение
 * замечает только пользователь.
 *
 * Тест дёшев и гоняется на JVM: он проверяет инварианты по исходникам, а не
 * поведение на устройстве, поэтому ловит регресс в момент коммита.
 */
class SingleSourceOfTruthTest {

    private fun sourceRoot(): File? {
        // Тесты запускаются из каталога модуля (app/), но подстрахуемся
        val candidates = listOf(
            File("src/main/java/com/nutritrack/app"),
            File("app/src/main/java/com/nutritrack/app")
        )
        return candidates.firstOrNull { it.isDirectory }
    }

    private fun kotlinFiles(): List<File> {
        val root = sourceRoot()
        Assume.assumeTrue("Исходники не найдены — тест пропущен", root != null)
        return root!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun `кэш активного профиля читают только через ActiveProfile`() {
        val offenders = kotlinFiles()
            .filter { it.name != "ActiveProfile.kt" && it.name != "PrefsManager.kt" }
            .filter { f ->
                f.readLines().any { line ->
                    val code = line.substringBefore("//")
                    code.contains("activeProfileIdCache")
                }
            }
            .map { it.name }

        assertTrue(
            "Кэш активного профиля читают напрямую: $offenders. " +
                "Источник правды — флаг isActive в БД, обращаться только через ActiveProfile.",
            offenders.isEmpty()
        )
    }

    @Test fun `ключ даты в БД не зависит от локали устройства`() {
        val dateUtils = kotlinFiles().firstOrNull { it.name == "DateUtils.kt" }
        Assume.assumeTrue("DateUtils.kt не найден", dateUtils != null)

        val dbLocaleLine = dateUtils!!.readLines()
            .map { it.substringBefore("//") }
            .firstOrNull { it.contains("DB_LOCALE") && it.contains("=") }

        assertTrue("Не найдено объявление DB_LOCALE в DateUtils", dbLocaleLine != null)
        assertTrue(
            "DB_LOCALE не должен браться из локали устройства: для th-TH это буддийский " +
                "календарь (год +543), а часть кода пишет даты через java.time. " +
                "Строка: ${dbLocaleLine?.trim()}",
            !dbLocaleLine!!.contains("Locale.getDefault()")
        )
    }

    @Test fun `напоминания о еде читают только ключи meal_notif`() {
        // Раньше существовали параллельные reminder_breakfast/lunch/dinner
        // с default=true, которые не выключались ни из одного экрана.
        val offenders = kotlinFiles()
            .filter { f ->
                f.readLines().any { line ->
                    val code = line.substringBefore("//")
                    code.contains("\"reminder_breakfast\"") ||
                        code.contains("\"reminder_lunch\"") ||
                        code.contains("\"reminder_dinner\"") ||
                        code.contains("\"reminder_snack\"")
                }
            }
            .map { it.name }

        assertTrue(
            "Вернулись параллельные ключи напоминаний о еде: $offenders. " +
                "Источник правды — meal_notif_<слот> (ползунки в «Плане питания»).",
            offenders.isEmpty()
        )
    }
}
