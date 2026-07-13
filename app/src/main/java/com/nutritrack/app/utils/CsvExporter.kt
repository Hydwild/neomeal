package com.nutritrack.app.utils

import com.nutritrack.app.data.db.AppDatabase

/**
 * Экспорт данных в CSV (для врача/тренера, открывается в Excel).
 * Возвращает строку CSV; запись в файл — через SAF в SettingsFragment.
 */
object CsvExporter {

    private fun esc(s: String): String {
        // экранируем кавычки и оборачиваем, если есть запятая/перенос
        val needsQuote = s.contains(',') || s.contains('"') || s.contains('\n')
        val v = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$v\"" else v
    }

 /** Дневник питания за всё время. */
    suspend fun diaryCsv(db: AppDatabase, profileId: Long): String {
        val entries = db.diaryDao().getEntriesForRange("0000-01-01", "9999-12-31", profileId)
            .sortedBy { it.date }
        val sb = StringBuilder()
        sb.append("Дата,Продукт,Граммы,Калории,Белки,Жиры,Углеводы,Приём\n")
        entries.forEach { e ->
            sb.append(esc(e.date)).append(',')
                .append(esc(e.productName)).append(',')
                .append(e.grams).append(',')
                .append(e.calories).append(',')
                .append(e.protein).append(',')
                .append(e.fat).append(',')
                .append(e.carbs).append(',')
                .append(esc(e.mealType)).append('\n')
        }
        return sb.toString()
    }

 /** Замеры тела за всё время. */
    suspend fun measurementsCsv(db: AppDatabase, profileId: Long): String {
        val all = db.measurementDao().dumpAll()
            .filter { it.profileId == profileId }
            .sortedWith(compareBy({ it.date }, { it.bodyPart }))
        val sb = StringBuilder()
        sb.append("Дата,Часть тела,Значение (см)\n")
        all.forEach { m ->
            val title = com.nutritrack.app.data.BodyParts.byKey(m.bodyPart)?.title ?: m.bodyPart
            sb.append(esc(m.date)).append(',')
                .append(esc(title)).append(',')
                .append(m.valueCm).append('\n')
        }
        return sb.toString()
    }
}
