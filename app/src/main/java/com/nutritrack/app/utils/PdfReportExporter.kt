package com.nutritrack.app.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.nutritrack.app.R
import com.nutritrack.app.data.db.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * Генерация PDF-отчёта на нативном android.graphics.pdf.PdfDocument (без внешних
 * библиотек). Рисует на Canvas: шапку, уровень, КБЖУ, график калорий по дням,
 * график веса, воду и инсайты недельного отчёта. Возвращает готовый файл.
 */
object PdfReportExporter {

    private const val PAGE_W = 595   // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    // Цвета (тёмная палитра приложения)
    private const val C_BG = 0xFF12094D.toInt()
    private const val C_CARD = 0xFF22193C.toInt()
    private const val C_WHITE = Color.WHITE
    private const val C_SEC = 0xFF9E9EB0.toInt()
    private const val C_ACCENT = 0xFFB39DDB.toInt()
    private const val C_WATER = 0xFF40C4FF.toInt()
    private const val C_PROT = 0xFF69F0AE.toInt()
    private const val C_FAT = 0xFFFFD740.toInt()
    private const val C_CARB = 0xFFFF6E40.toInt()

    data class Result(val file: File?, val error: String?)

    /** Какие разделы включать в отчёт (галочки пользователя). */
    data class Sections(
        val nutrition: Boolean = true,
        val water: Boolean = true,
        val weight: Boolean = true,
        val cost: Boolean = true,
        val micro: Boolean = true,
        val measurements: Boolean = true   // замеры тела
    )

 /** periodDays: 7 или 30. Выполнять в фоне (suspend-вызовы DAO внутри). */
    suspend fun export(
        context: Context, periodDays: Int, sections: Sections = Sections()
    ): Result {
        return try {
            val db = AppDatabase.getInstance(context)
            val profile = db.profileDao().getActiveProfileSync()
                ?: return Result(null, context.getString(R.string.pdf_err_no_profile))
            val profileId = profile.id

            val dates = lastDates(periodDays)
            val start = dates.first()
            val end = dates.last()

            // Сбор данных по дням
            data class Day(val date: String, val cal: Float, val prot: Float, val fat: Float, val carb: Float, val water: Int)
            val days = dates.map { d ->
                val entries = db.diaryDao().getEntriesForDateSync(d, profileId)
                Day(
                    d,
                    entries.sumOf { it.calories.toDouble() }.toFloat(),
                    entries.sumOf { it.protein.toDouble() }.toFloat(),
                    entries.sumOf { it.fat.toDouble() }.toFloat(),
                    entries.sumOf { it.carbs.toDouble() }.toFloat(),
                    db.waterDao().getTotalWaterSync(d, profileId)
                )
            }
            val withFood = days.filter { it.cal > 0f }
            val avgCal = if (withFood.isNotEmpty()) withFood.map { it.cal }.average().toInt() else 0
            val sumProt = days.sumOf { it.prot.toDouble() }.toInt()
            val sumFat = days.sumOf { it.fat.toDouble() }.toInt()
            val sumCarb = days.sumOf { it.carb.toDouble() }.toInt()
            val avgWater = if (days.isNotEmpty()) days.map { it.water }.average().toInt() else 0

            // Все записи за период — для частых продуктов и интервалов
            val allEntries = db.diaryDao().getEntriesForRange(start, end, profileId)

            // Карта продуктов (для микронутриентов)
            val productMap = db.productDao().getAllProductsSync().associateBy { it.id }

            // --- Микронутриенты за период (среднесуточные vs норма) ---
            data class MicroRow(val key: String, val emoji: String, val unit: String,
                                val avgPerDay: Float, val rda: Float, val percent: Int)
            val microRows: List<MicroRow> = run {
                val totals = HashMap<String, Float>()
                var any = false
                for (e in allEntries) {
                    val pr = productMap[e.productId] ?: continue
                    val f = e.grams / 100f
                    for (n in com.nutritrack.app.data.MicronutrientNorms.all) {
                        val v = microValue(pr, n.key) ?: continue
                        totals[n.key] = (totals[n.key] ?: 0f) + v * f
                        any = true
                    }
                }
                val dayCount = if (withFood.isNotEmpty()) withFood.size else 1
                if (!any) emptyList()
                else com.nutritrack.app.data.MicronutrientNorms.all.map { n ->
                    val avg = (totals[n.key] ?: 0f) / dayCount
                    val rda = n.rda(profile.gender)
                    val pct = if (rda <= 0f) 0 else ((avg / rda) * 100f).toInt()
                    MicroRow(n.key, n.emoji, n.unit, avg, rda, pct)
                }
            }

            // Частые продукты (по числу записей)
            val topFrequent = allEntries
                .groupBy { it.productName }
                .map { (name, list) -> name to list.size }
                .sortedByDescending { it.second }

            // Интервалы между приёмами пищи (внутри каждого дня, по timestamp)
            val gapsHours = mutableListOf<Double>()
            val mealsPerDay = mutableListOf<Int>()
            allEntries.groupBy { it.date }.forEach { (_, dayEntries) ->
                val times = dayEntries.map { it.timestamp }.sorted()
                if (times.isNotEmpty()) mealsPerDay.add(times.size)
                for (i in 1 until times.size) {
                    val gapH = (times[i] - times[i - 1]) / (1000.0 * 60 * 60)
                    if (gapH in 0.05..16.0) gapsHours.add(gapH)  // отсеиваем выбросы
                }
            }
            val avgGapHours = if (gapsHours.isNotEmpty()) gapsHours.average() else 0.0
            val avgMealsPerDay = if (mealsPerDay.isNotEmpty()) mealsPerDay.average() else 0.0

            // Вес: статистика за период
            val history = db.weightDao().getAllWeightSync(profileId)
                .filter { it.date >= start && it.date <= end }
                .sortedBy { it.date }
            val weightCurrent = history.lastOrNull()?.weight ?: profile.weight
            val weightStart = history.firstOrNull()?.weight
            val weightDiff = if (weightStart != null) weightCurrent - weightStart else null
            val weightMin = history.minByOrNull { it.weight }?.weight
            val weightMax = history.maxByOrNull { it.weight }?.weight

            // --- Замеры тела за период (первое → последнее значение каждой части) ---
            data class MeasureRow(val key: String, val emoji: String,
                                  val first: Float, val last: Float)
            val measureRows: List<MeasureRow> = run {
                val all = db.measurementDao().dumpAll()
                    .filter { it.profileId == profileId && it.date >= start && it.date <= end }
                if (all.isEmpty()) return@run emptyList()
                all.groupBy { it.bodyPart }.mapNotNull { (part, list) ->
                    val sorted = list.sortedBy { it.date }
                    val f = sorted.first().valueCm
                    val l = sorted.last().valueCm
                    val bp = com.nutritrack.app.data.BodyParts.byKey(part)
                    MeasureRow(part, bp?.emoji ?: "📏", f, l)
                }.sortedBy { it.key }
            }

            // Траты (только если у продуктов заданы цены)
            val totalSpent = db.diaryDao().getTotalSpentInRange(start, end, profileId)
            val topSpending = db.diaryDao().getTopSpendingInRange(start, end, profileId)

            // Инсайты
            val calGoal = CalorieCalculator.calculateDailyGoal(profile)
            val waterGoal = CalorieCalculator.calculateWaterGoal(profile)
            val reportDays = days.map {
                WeeklyReport.DayData(it.date, it.cal, it.prot, it.water)
            }
            val insights = WeeklyReport.build(context, WeeklyReport.Input(
                days = reportDays,
                calorieGoal = calGoal,
                proteinGoalG = calGoal * 0.30f / 4f,
                waterGoalMl = waterGoal,
                weightStart = weightStart,
                weightEnd = weightCurrent
            ))

            // Проблемные продукты (негативные оценки за период)
            val feedback = db.mealFeedbackDao().getForRange(start, end, profileId)
            val problemProducts = feedback
                .filter { it.rating == "discomfort" || it.rating == "bad" }
                .groupBy { it.productName }
                .map { (name, list) -> name to list.size }
                .sortedByDescending { it.second }

            // Рисуем PDF
            val doc = PdfDocument()
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            var c = page.canvas
            c.drawColor(C_BG)
            var y = MARGIN

            val genTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date())
            fun drawFooter() {
                p.color = C_SEC; p.textSize = 9f; p.isFakeBoldText = false
                c.drawText(context.getString(R.string.pdf_footer_fmt, genTime), MARGIN, PAGE_H - 24f, p)
                c.drawText(context.getString(R.string.pdf_page_fmt, pageNum), PAGE_W - MARGIN - 60f, PAGE_H - 24f, p)
            }

            // Шапка
            p.color = C_WHITE; p.textSize = 22f; p.isFakeBoldText = true
            c.drawText(context.getString(R.string.pdf_title), MARGIN, y + 18f, p)
            y += 30f
            p.color = C_SEC; p.textSize = 11f; p.isFakeBoldText = false
            c.drawText(context.getString(R.string.pdf_period_fmt, start, end), MARGIN, y + 12f, p)
            c.drawText(profile.name, PAGE_W - MARGIN - 120f, y + 12f, p)
            y += 28f

            // Профиль (возраст, рост, пол)
            val allergenCodes = com.nutritrack.app.utils.AllergenData.parse(profile.allergens)
            val dietKeys = profile.diets.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val hasMarks = allergenCodes.isNotEmpty() || dietKeys.isNotEmpty()
            y = drawCard(c, p, y, if (hasMarks) 66f else 48f) { top ->
                p.color = C_WHITE; p.textSize = 11f; p.isFakeBoldText = false
                val genderText = when (profile.gender) {
                    "female" -> context.getString(R.string.pdf_gender_female)
                    else -> context.getString(R.string.pdf_gender_male)
                }
                c.drawText(context.getString(R.string.pdf_profile_age_fmt, profile.age), MARGIN + 14f, top + 20f, p)
                c.drawText(context.getString(R.string.pdf_profile_height_fmt, profile.height.toInt()), MARGIN + 150f, top + 20f, p)
                c.drawText(context.getString(R.string.pdf_profile_gender_fmt, genderText), MARGIN + 290f, top + 20f, p)
                // Режим питания
                val mealsText = if (avgMealsPerDay > 0)
                    context.getString(R.string.pdf_meals_per_day_fmt, String.format("%.1f", avgMealsPerDay)) else "—"
                val gapText = if (avgGapHours > 0)
                    context.getString(R.string.pdf_meal_gap_fmt, String.format("%.1f", avgGapHours)) else "—"
                p.color = C_SEC
                c.drawText(mealsText, MARGIN + 14f, top + 38f, p)
                c.drawText(gapText, MARGIN + 200f, top + 38f, p)
                // Аллергены и диеты
                if (hasMarks) {
                    val diets = dietKeys.joinToString(", ") {
                        com.nutritrack.app.utils.MealPlanGenerator.dietTitle(context, it)
                    }
                    val allergens = allergenCodes.joinToString(", ") {
                        com.nutritrack.app.utils.AllergenData.title(context, it)
                    }
                    val parts = mutableListOf<String>()
                    if (diets.isNotEmpty()) parts.add(context.getString(R.string.pdf_diets_fmt, diets))
                    if (allergens.isNotEmpty()) parts.add(context.getString(R.string.pdf_allergens_fmt, allergens))
                    p.color = C_ACCENT
                    c.drawText(parts.joinToString("   ·   "), MARGIN + 14f, top + 56f, p)
                }
            }
            y += 12f

            // Сводка (карточка)
            y = drawCard(c, p, y, 92f) { top ->
                p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
                c.drawText(context.getString(R.string.pdf_summary), MARGIN + 14f, top + 20f, p)
                p.isFakeBoldText = false; p.textSize = 11f
                val col1 = MARGIN + 14f
                p.color = C_ACCENT; c.drawText(context.getString(R.string.pdf_avg_cal_fmt, avgCal), col1, top + 44f, p)
                p.color = C_PROT;   c.drawText(context.getString(R.string.pdf_protein_fmt, sumProt), col1, top + 62f, p)
                val col2 = MARGIN + 200f
                p.color = C_FAT;    c.drawText(context.getString(R.string.pdf_fat_fmt, sumFat), col2, top + 44f, p)
                p.color = C_CARB;   c.drawText(context.getString(R.string.pdf_carb_fmt, sumCarb), col2, top + 62f, p)
                val col3 = MARGIN + 360f
                p.color = C_WATER;  c.drawText(context.getString(R.string.pdf_avg_water_fmt, avgWater), col3, top + 44f, p)
            }
            y += 14f

            // График калорий по дням (с подписью значения над столбиком)
            p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
            c.drawText(context.getString(R.string.pdf_chart_cal), MARGIN, y + 14f, p)
            p.isFakeBoldText = false
            y += 24f
            y = drawBars(c, p, y, days.map { it.cal }, dates, calGoal.toFloat(), C_ACCENT)
            y += 16f

            // График воды по дням (мл)
            if (sections.water) {
                p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
                c.drawText(context.getString(R.string.pdf_chart_water), MARGIN, y + 14f, p)
                p.isFakeBoldText = false
                y += 24f
                y = drawBars(c, p, y, days.map { it.water.toFloat() }, dates, waterGoal.toFloat(), C_WATER)
                y += 16f
            }

            // Переход на вторую страницу
            drawFooter()
            doc.finishPage(page)
            pageNum = 2
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            c = page.canvas
            c.drawColor(C_BG)
            y = MARGIN
            p.color = C_WHITE; p.textSize = 16f; p.isFakeBoldText = true
            c.drawText(context.getString(R.string.pdf_page2_title), MARGIN, y + 14f, p)
            p.isFakeBoldText = false
            y += 30f

            // Статистика веса
            if (sections.weight && history.isNotEmpty()) {
                y = drawCard(c, p, y, if (weightDiff != null) 74f else 52f) { top ->
                    p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
                    c.drawText("⚖ " + context.getString(R.string.pdf_chart_weight), MARGIN + 14f, top + 20f, p)
                    p.isFakeBoldText = false; p.textSize = 11f
                    p.color = C_SEC
                    c.drawText(context.getString(R.string.pdf_weight_current_fmt, String.format("%.1f", weightCurrent)), MARGIN + 14f, top + 42f, p)
                    if (weightDiff != null) {
                        val sign = if (weightDiff > 0) "+" else ""
                        p.color = if (weightDiff > 0) C_CARB else C_PROT
                        c.drawText(context.getString(R.string.pdf_weight_change_fmt, "$sign${String.format("%.1f", weightDiff)}"), MARGIN + 14f, top + 60f, p)
                        if (weightMin != null && weightMax != null) {
                            p.color = C_SEC
                            c.drawText(context.getString(R.string.pdf_weight_range_fmt,
                                String.format("%.1f", weightMin), String.format("%.1f", weightMax)), MARGIN + 220f, top + 60f, p)
                        }
                    }
                }
                y += 14f
            }

            // Статистика цен (если есть)
            if (sections.cost && totalSpent > 0f) {
                val curSym = CurrencyHelper.symbol(PrefsManager(context))
                val rows = topSpending.size.coerceAtMost(5)
                y = drawCard(c, p, y, 40f + rows * 16f + 14f) { top ->
                    p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
                    c.drawText("💰 " + context.getString(R.string.pdf_spending_title), MARGIN + 14f, top + 20f, p)
                    p.isFakeBoldText = false; p.textSize = 11f
                    p.color = C_ACCENT
                    c.drawText(context.getString(R.string.pdf_spending_total_fmt, totalSpent.toInt(), curSym), MARGIN + 220f, top + 20f, p)
                    var ty = top + 40f
                    p.color = C_SEC
                    topSpending.take(5).forEach { row ->
                        val name = ProductNameLabel.of(context, row.name)
                        c.drawText("• " + context.getString(R.string.pdf_spending_row_fmt, name, row.spent.toInt(), curSym), MARGIN + 18f, ty, p)
                        ty += 16f
                    }
                }
                y += 14f
            }

            // Частые продукты (по числу записей)
            if (topFrequent.isNotEmpty() && y < PAGE_H - 90f) {
                val rows = topFrequent.size.coerceAtMost(5)
                y = drawCard(c, p, y, 40f + rows * 16f + 14f) { top ->
                    p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
                    c.drawText("🍽 " + context.getString(R.string.pdf_frequent_title), MARGIN + 14f, top + 20f, p)
                    p.isFakeBoldText = false; p.textSize = 11f
                    var ty = top + 40f
                    p.color = C_SEC
                    topFrequent.take(5).forEach { (name, cnt) ->
                        val label = ProductNameLabel.of(context, name)
                        c.drawText("• " + context.getString(R.string.pdf_frequent_row_fmt, label, cnt), MARGIN + 18f, ty, p)
                        ty += 16f
                    }
                }
                y += 14f
            }

            // Замеры тела за период (первое → последнее значение)
            if (sections.measurements && measureRows.isNotEmpty() && y < PAGE_H - 90f) {
                val rows = measureRows.size.coerceAtMost(8)
                y = drawCard(c, p, y, 40f + rows * 16f + 14f) { top ->
                    p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
                    c.drawText("📐 " + context.getString(R.string.pdf_measure_title), MARGIN + 14f, top + 20f, p)
                    p.isFakeBoldText = false; p.textSize = 11f
                    var ty = top + 40f
                    measureRows.take(8).forEach { m ->
                        val name = com.nutritrack.app.utils.BodyPartLabel.of(context, m.key)
                        val diff = m.last - m.first
                        val sign = if (diff > 0) "+" else ""
                        p.color = C_SEC
                        c.drawText(
                            "${m.emoji} " + context.getString(
                                R.string.pdf_measure_row_fmt,
                                name, String.format("%.1f", m.first), String.format("%.1f", m.last)
                            ),
                            MARGIN + 18f, ty, p
                        )
                        // изменение цветом: рост — оранжевый, уменьшение — зелёный
                        if (kotlin.math.abs(diff) >= 0.05f) {
                            p.color = if (diff > 0) C_CARB else C_PROT
                            c.drawText(
                                context.getString(R.string.pdf_measure_diff_fmt, "$sign${String.format("%.1f", diff)}"),
                                MARGIN + 360f, ty, p
                            )
                        }
                        ty += 16f
                    }
                }
                y += 14f
            }

            // Инсайты
            p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
            c.drawText(context.getString(R.string.rep_title), MARGIN, y + 14f, p)
            p.isFakeBoldText = false
            y += 24f
            insights.forEach { ins ->
                if (y > PAGE_H - 60f) return@forEach
                p.color = C_WHITE; p.textSize = 11f
                c.drawText("• " + stripEmoji(ins.title), MARGIN + 6f, y + 12f, p)
                y += 16f
                ins.advice?.let { adv ->
                    p.color = C_SEC; p.textSize = 9.5f
                    c.drawText("   $adv", MARGIN + 6f, y + 11f, p)
                    y += 15f
                }
            }

            // Проблемные продукты
            if (problemProducts.isNotEmpty() && y < PAGE_H - 80f) {
                y += 6f
                p.color = C_WHITE; p.textSize = 13f; p.isFakeBoldText = true
                c.drawText("⚠ " + context.getString(R.string.pdf_problem_products), MARGIN, y + 14f, p)
                p.isFakeBoldText = false
                y += 22f
                p.color = C_CARB; p.textSize = 11f
                problemProducts.take(8).forEach { (name, cnt) ->
                    if (y > PAGE_H - 50f) return@forEach
                    val label = ProductNameLabel.of(context, name)
                    c.drawText("• " + context.getString(R.string.pdf_problem_fmt, label, cnt), MARGIN + 6f, y + 12f, p)
                    y += 16f
                }
            }

            // Подвал 2-й страницы
            drawFooter()
            doc.finishPage(page)

            // ===== Страница 3: Микронутриенты =====
            if (sections.micro && microRows.isNotEmpty()) {
                pageNum = 3
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                c = page.canvas
                c.drawColor(C_BG)
                y = MARGIN
                p.color = C_WHITE; p.textSize = 16f; p.isFakeBoldText = true
                c.drawText(context.getString(R.string.pdf_micro_title), MARGIN, y + 14f, p)
                p.isFakeBoldText = false
                y += 26f
                p.color = C_SEC; p.textSize = 9.5f
                c.drawText(context.getString(R.string.pdf_micro_sub), MARGIN, y + 11f, p)
                y += 24f

                // Таблица: эмодзи+имя ... среднесут / норма ... %  статус
                p.textSize = 11f
                microRows.forEach { m ->
                    if (y > PAGE_H - 70f) return@forEach
                    val name = microName(context, m.key)
                    p.color = C_WHITE
                    c.drawText("${m.emoji} $name", MARGIN + 4f, y + 12f, p)
                    p.color = C_SEC
                    val unitL = when (m.unit) {
                        "мкг" -> context.getString(R.string.unit_mcg)
                        "мг"  -> context.getString(R.string.unit_mg)
                        else  -> m.unit
                    }
                    c.drawText(
                        "${fmtNum(m.avgPerDay)} / ${fmtNum(m.rda)} $unitL",
                        MARGIN + 200f, y + 12f, p
                    )
                    // статус по цвету
                    val (statusText, statusColor) = when {
                        m.percent < 50 -> context.getString(R.string.pdf_micro_low) to C_CARB
                        m.percent > 150 -> context.getString(R.string.pdf_micro_over) to C_FAT
                        else -> context.getString(R.string.pdf_micro_ok) to C_PROT
                    }
                    p.color = statusColor
                    c.drawText("${m.percent}%  $statusText", MARGIN + 340f, y + 12f, p)
                    y += 18f
                }

                y += 10f
                // Сводка: чего не хватает
                val low = microRows.filter { it.percent < 50 }.map { microName(context, it.key) }
                val over = microRows.filter { it.percent > 150 }.map { microName(context, it.key) }
                if (low.isNotEmpty()) {
                    p.color = C_CARB; p.textSize = 11f; p.isFakeBoldText = true
                    c.drawText("▼ " + context.getString(R.string.pdf_micro_deficit), MARGIN, y + 12f, p)
                    p.isFakeBoldText = false
                    y += 18f
                    p.color = C_WHITE; p.textSize = 10f
                    c.drawText(low.joinToString(", "), MARGIN + 6f, y + 11f, p)
                    y += 22f
                }
                if (over.isNotEmpty()) {
                    p.color = C_FAT; p.textSize = 11f; p.isFakeBoldText = true
                    c.drawText("▲ " + context.getString(R.string.pdf_micro_excess), MARGIN, y + 12f, p)
                    p.isFakeBoldText = false
                    y += 18f
                    p.color = C_WHITE; p.textSize = 10f
                    c.drawText(over.joinToString(", "), MARGIN + 6f, y + 11f, p)
                    y += 22f
                }

                // дисклеймер
                p.color = C_SEC; p.textSize = 8.5f
                c.drawText(context.getString(R.string.pdf_micro_note), MARGIN, PAGE_H - 44f, p)

                drawFooter()
                doc.finishPage(page)
            }

            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(dir, "NeoMeal_report_${start}_$end.pdf")
            file.outputStream().use { doc.writeTo(it) }
            doc.close()
            Result(file, null)
        } catch (e: Exception) {
            Result(null, e.message ?: "error")
        }
    }

    // Рисование примитивов

    private inline fun drawCard(c: Canvas, p: Paint, top: Float, height: Float, body: (Float) -> Unit): Float {
        p.color = C_CARD
        c.drawRoundRect(MARGIN, top, PAGE_W - MARGIN, top + height, 14f, 14f, p)
        body(top)
        return top + height
    }

 /** Столбики. Возвращает новый Y. */
    private fun drawBars(c: Canvas, p: Paint, top: Float, values: List<Float>, labels: List<String>, goal: Float, color: Int): Float {
        val h = 120f
        val bottom = top + h
        val left = MARGIN + 30f
        val right = PAGE_W - MARGIN
        val maxV = max(values.maxOrNull() ?: 1f, goal).coerceAtLeast(1f)
        // ось
        p.color = C_SEC; p.strokeWidth = 1f
        c.drawLine(left, bottom, right, bottom, p)
        // линия цели
        if (goal > 0f) {
            val gy = bottom - (goal / maxV) * h
            p.color = 0x55FFFFFF
            c.drawLine(left, gy, right, gy, p)
        }
        val n = values.size.coerceAtLeast(1)
        val slot = (right - left) / n
        val bw = slot * 0.55f
        val dayFmt = shortDayLabels(labels)
        // При месяце столбиков много — подписи значений реже, чтобы не слиплись
        val showValueEvery = if (values.size > 10) 5 else 1
        values.forEachIndexed { i, v ->
            val bh = (v / maxV) * h
            val x = left + i * slot + (slot - bw) / 2
            p.color = color
            c.drawRoundRect(x, bottom - bh, x + bw, bottom, 3f, 3f, p)
            // Значение над столбиком
            if (v > 0f && i % showValueEvery == 0) {
                p.color = C_WHITE; p.textSize = 6.5f
                val txt = v.toInt().toString()
                val tw = p.measureText(txt)
                c.drawText(txt, x + bw / 2 - tw / 2, (bottom - bh - 3f).coerceAtLeast(top + 6f), p)
            }
            // Подпись дня
            p.color = C_SEC; p.textSize = 7f
            c.drawText(dayFmt.getOrElse(i) { "" }, x, bottom + 10f, p)
        }
        return bottom + 14f
    }

 /** Линия (вес). Возвращает новый Y. */
    private fun drawLine(c: Canvas, p: Paint, top: Float, values: List<Float>, color: Int): Float {
        val h = 110f
        val bottom = top + h
        val left = MARGIN + 30f
        val right = PAGE_W - MARGIN
        val minV = values.minOrNull() ?: 0f
        val maxV = values.maxOrNull() ?: 1f
        val range = (maxV - minV).coerceAtLeast(0.1f)
        p.color = C_SEC; p.strokeWidth = 1f
        c.drawLine(left, bottom, right, bottom, p)
        val n = values.size.coerceAtLeast(2)
        val step = (right - left) / (n - 1)
        p.color = color; p.strokeWidth = 2.5f
        var prevX = 0f; var prevY = 0f
        values.forEachIndexed { i, v ->
            val x = left + i * step
            val yy = bottom - ((v - minV) / range) * (h - 10f)
            if (i > 0) c.drawLine(prevX, prevY, x, yy, p)
            c.drawCircle(x, yy, 3f, p)
            prevX = x; prevY = yy
        }
        // мин/макс подписи
        p.color = C_SEC; p.textSize = 8f; p.strokeWidth = 1f
        c.drawText(String.format("%.1f", maxV), MARGIN, top + 8f, p)
        c.drawText(String.format("%.1f", minV), MARGIN, bottom, p)
        return bottom + 14f
    }

    // Утилиты

    private fun lastDates(n: Int): List<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(n - 1))
        return (0 until n).map {
            val s = fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            s
        }
    }

    private fun shortDayLabels(dates: List<String>): List<String> {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outFmt = SimpleDateFormat("d/MM", Locale.getDefault())
        // Для месяца подписываем не каждый день, чтобы не слиплось
        val showEvery = if (dates.size > 10) 5 else 1
        return dates.mapIndexed { i, d ->
            if (i % showEvery != 0) "" else try { outFmt.format(inFmt.parse(d)!!) } catch (e: Exception) { "" }
        }
    }

 /** Убирает ведущий эмодзи из заголовка инсайта для ч/б PDF-строки. */
    private fun stripEmoji(s: String): String = s.trim()

    /** Значение нутриента на 100 г у продукта по ключу. */
    private fun microValue(p: com.nutritrack.app.data.db.entity.ProductEntity, key: String): Float? = when (key) {
        "vitA" -> p.vitA; "vitC" -> p.vitC; "vitD" -> p.vitD; "vitE" -> p.vitE
        "vitK" -> p.vitK; "vitB6" -> p.vitB6; "vitB12" -> p.vitB12; "folate" -> p.folate
        "calcium" -> p.calcium; "iron" -> p.iron; "magnesium" -> p.magnesium
        "zinc" -> p.zinc; "potassium" -> p.potassium; "sodium" -> p.sodium
        "selenium" -> p.selenium
        else -> null
    }

    private fun fmtNum(v: Float): String =
        if (v >= 100f) v.toInt().toString()
        else if (v >= 10f) String.format("%.0f", v)
        else String.format("%.1f", v)

    private fun microName(context: Context, key: String): String {
        val resId = when (key) {
            "vitA" -> R.string.nutr_vitA; "vitC" -> R.string.nutr_vitC
            "vitD" -> R.string.nutr_vitD; "vitE" -> R.string.nutr_vitE
            "vitK" -> R.string.nutr_vitK; "vitB6" -> R.string.nutr_vitB6
            "vitB12" -> R.string.nutr_vitB12; "folate" -> R.string.nutr_folate
            "calcium" -> R.string.nutr_calcium; "iron" -> R.string.nutr_iron
            "magnesium" -> R.string.nutr_magnesium; "zinc" -> R.string.nutr_zinc
            "potassium" -> R.string.nutr_potassium; "sodium" -> R.string.nutr_sodium
            "selenium" -> R.string.nutr_selenium
            else -> R.string.nutr_vitA
        }
        return context.getString(resId)
    }
}
