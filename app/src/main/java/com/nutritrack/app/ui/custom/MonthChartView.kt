package com.nutritrack.app.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.nutritrack.app.ui.calendar.DayStats

/**
 * Мини-столбчатый график калорий за месяц.
 * Высота столбика = процент от цели (макс 130%), цвет — по статусу дня.
 * Горизонтальная пунктирная линия — цель калорий.
 */
class MonthChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

 /** Список столбцов: пары (день месяца, статистика). null — день без данных. */
    private var bars: List<Pair<Int, DayStats?>> = emptyList()
    private var calorieGoal: Int = 2000

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val goalLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
        color = Color.parseColor("#88FFFFFF")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9EB0")
        textSize = dpToPx(9f)
        textAlign = Paint.Align.CENTER
    }

    fun setData(data: List<Pair<Int, DayStats?>>, goal: Int) {
        bars = data
        calorieGoal = goal
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty() || calorieGoal <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val labelArea = dpToPx(14f)        // снизу — место для подписей
        val chartH = h - labelArea
        val padding = dpToPx(2f)
        val barCount = bars.size
        val barW = (w - padding * (barCount - 1)) / barCount
        // Шкала: 130% от цели = max высоты (чтобы переедание тоже было видно)
        val maxScale = 1.3f

        // Пунктирная линия "цель" на уровне 100% (= chartH / maxScale от низа)
        val goalY = chartH - chartH * (1f / maxScale)
        canvas.drawLine(0f, goalY, w, goalY, goalLinePaint)

        bars.forEachIndexed { idx, (day, stats) ->
            val x = idx * (barW + padding)

            // Подпись дня (1, 5, 10, 15, 20, 25, последний)
            if (day == 1 || day % 5 == 0 || idx == bars.lastIndex) {
                canvas.drawText(day.toString(), x + barW / 2, h - dpToPx(2f), labelPaint)
            }

            if (stats == null || stats.calories <= 0f) {
                // тонкая серая полоска — нет данных
                barPaint.color = Color.parseColor("#222236")
                val baselineH = dpToPx(2f)
                canvas.drawRoundRect(
                    x, chartH - baselineH, x + barW, chartH,
                    dpToPx(2f), dpToPx(2f), barPaint
                )
                return@forEachIndexed
            }

            val ratio = (stats.calories / calorieGoal).coerceAtMost(maxScale)
            val barH = (chartH * (ratio / maxScale)).coerceAtLeast(dpToPx(2f))
            barPaint.color = when (stats.status(calorieGoal)) {
                DayStats.Status.GREEN  -> Color.parseColor("#69F0AE")
                DayStats.Status.YELLOW -> Color.parseColor("#FFD740")
                DayStats.Status.RED    -> Color.parseColor("#FF6E40")
                else -> Color.parseColor("#444466")
            }
            val r = dpToPx(2.5f)
            canvas.drawRoundRect(
                x, chartH - barH, x + barW, chartH,
                r, r, barPaint
            )
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
