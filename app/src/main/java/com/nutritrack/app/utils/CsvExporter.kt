package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R
import com.nutritrack.app.data.db.AppDatabase

/**
 * Экспорт данных в CSV (для врача/тренера, открывается в Excel).
 * Возвращает строку CSV; запись в файл — через SAF в SettingsFragment.
 * Заголовки колонок и названия — по локали приложения.
 */
object CsvExporter {

    private fun esc(s: String): String {
        // экранируем кавычки и оборачиваем, если есть запятая/перенос
        val needsQuote = s.contains(',') || s.contains('"') || s.contains('\n')
        val v = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$v\"" else v
    }

 /** Дневник питания за всё время. */
    suspend fun diaryCsv(ctx: Context, db: AppDatabase, profileId: Long): String {
        val entries = db.diaryDao().getEntriesForRange("0000-01-01", "9999-12-31", profileId)
            .sortedBy { it.date }
        val sb = StringBuilder()
        sb.append(listOf(
            ctx.getString(R.string.csv_h_date), ctx.getString(R.string.csv_h_product),
            ctx.getString(R.string.csv_h_grams), ctx.getString(R.string.csv_h_calories),
            ctx.getString(R.string.csv_h_protein), ctx.getString(R.string.csv_h_fat),
            ctx.getString(R.string.csv_h_carbs), ctx.getString(R.string.csv_h_meal)
        ).joinToString(",") { esc(it) }).append('\n')
        entries.forEach { e ->
            sb.append(esc(e.date)).append(',')
                .append(esc(ProductNameLabel.of(ctx, e.productName))).append(',')
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
    suspend fun measurementsCsv(ctx: Context, db: AppDatabase, profileId: Long): String {
        val all = db.measurementDao().dumpAll()
            .filter { it.profileId == profileId }
            .sortedWith(compareBy({ it.date }, { it.bodyPart }))
        val sb = StringBuilder()
        sb.append(listOf(
            ctx.getString(R.string.csv_h_date), ctx.getString(R.string.csv_h_bodypart),
            ctx.getString(R.string.csv_h_value_cm)
        ).joinToString(",") { esc(it) }).append('\n')
        all.forEach { m ->
            val title = BodyPartLabel.of(ctx, m.bodyPart)
            sb.append(esc(m.date)).append(',')
                .append(esc(title)).append(',')
                .append(m.valueCm).append('\n')
        }
        return sb.toString()
    }

 /** Вода по записям за всё время. */
    suspend fun waterCsv(ctx: Context, db: AppDatabase, profileId: Long): String {
        val all = db.waterDao().dumpAll()
            .filter { it.profileId == profileId }
            .sortedBy { it.date }
        val sb = StringBuilder()
        sb.append(listOf(
            ctx.getString(R.string.csv_h_date), ctx.getString(R.string.csv_h_water_ml)
        ).joinToString(",") { esc(it) }).append('\n')
        all.forEach { w ->
            sb.append(esc(w.date)).append(',').append(w.amountMl).append('\n')
        }
        return sb.toString()
    }

 /** История веса за всё время. */
    suspend fun weightCsv(ctx: Context, db: AppDatabase, profileId: Long): String {
        val all = db.weightDao().dumpAll()
            .filter { it.profileId == profileId }
            .sortedBy { it.date }
        val sb = StringBuilder()
        sb.append(listOf(
            ctx.getString(R.string.csv_h_date), ctx.getString(R.string.csv_h_weight_kg)
        ).joinToString(",") { esc(it) }).append('\n')
        all.forEach { w ->
            sb.append(esc(w.date)).append(',').append(w.weight).append('\n')
        }
        return sb.toString()
    }
}
