package com.nutritrack.app.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate

/**
 * Годовой хитмэп активности (как календарь контрибуций GitHub):
 * 53 колонки-недели × 7 строк-дней. Интенсивность клетки — насколько
 * день попал в норму калорий (0 — нет записей, 4 — в коридоре нормы).
 *
 * Дизайн: скруглённые клетки в фирменном фиолетовом, сегодняшний день
 * обведён. Данные подаются через [setData].
 */
class YearHeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Уровни 0..4 по датам ISO (yyyy-MM-dd). Отсутствие даты = уровень 0. */
    private var levels: Map<String, Int> = emptyMap()
    private var endDate: LocalDate = LocalDate.now()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.WHITE
    }

    // Палитра: от «пусто» к насыщенному акценту
    private val colors = intArrayOf(
        Color.parseColor("#22FFFFFF"),  // 0 — нет записей
        Color.parseColor("#4D7C4DFF"),  // 1 — мало
        Color.parseColor("#807C4DFF"),  // 2
        Color.parseColor("#B37C4DFF"),  // 3
        Color.parseColor("#FF7C4DFF"),  // 4 — норма выполнена
    )

    private val weeks = 53

    fun setData(levelsByDate: Map<String, Int>, end: LocalDate = LocalDate.now()) {
        levels = levelsByDate
        endDate = end
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // Клетка из ширины: 53 колонки + зазоры; высота = 7 строк
        val cell = (w - dp(4f) * (weeks - 1)) / weeks
        val h = (cell * 7 + dp(4f) * 6).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = dp(4f)
        val cell = (width - gap * (weeks - 1)) / weeks
        val radius = cell * 0.28f

        // Стартовая дата: назад до понедельника, всего weeks*7 клеток
        val totalDays = weeks * 7
        val endDow = endDate.dayOfWeek.value          // 1=Пн … 7=Вс
        val start = endDate.minusDays((totalDays - (7 - endDow) - 1).toLong())

        var d = start
        for (col in 0 until weeks) {
            for (row in 0 until 7) {
                if (d.isAfter(endDate)) break
                val level = (levels[d.toString()] ?: 0).coerceIn(0, 4)
                paint.color = colors[level]
                val left = col * (cell + gap)
                val top = paddingTop + row * (cell + gap)
                canvas.drawRoundRect(left, top, left + cell, top + cell, radius, radius, paint)
                if (d == endDate) {
                    canvas.drawRoundRect(left, top, left + cell, top + cell, radius, radius, todayStroke)
                }
                d = d.plusDays(1)
            }
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
