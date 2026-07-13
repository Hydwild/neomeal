package com.nutritrack.app.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.nutritrack.app.ui.calendar.DayStats

/**
 * Один день в месячной сетке календаря.
 * Стиль: круглая шкала-кольцо вокруг числа (прогресс выполнения цели по калориям).
 * - Цель выполнена (зелёный) залитый цветной круг + белое число
 * - Частично цветное кольцо-прогресс вокруг числа
 * - Сегодня серая обводка-рамка
 * - Выбранный фиолетовый акцент
 */
class CalendarDayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var dayNumber: Int = 0
        set(value) { field = value; invalidate() }

    var status: DayStats.Status = DayStats.Status.NONE
        set(value) { field = value; invalidate() }

 /** Доля выполнения цели (0..1+) — для шкалы-кольца. */
    var progress: Float = 0f
        set(value) { field = value; invalidate() }

    var isToday: Boolean = false
        set(value) { field = value; invalidate() }

    var isSelectedDay: Boolean = false
        set(value) { field = value; invalidate() }

    var isCurrentMonth: Boolean = true
        set(value) { field = value; invalidate() }

    var isFuture: Boolean = false
        set(value) { field = value; invalidate() }

 /** День в серии подряд зелёных дней — рисуем в углу. */
    var isInStreak: Boolean = false
        set(value) { field = value; invalidate() }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val firePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()

    private fun statusColor(): Int = when (status) {
        DayStats.Status.GREEN  -> Color.parseColor("#43A047")
        DayStats.Status.YELLOW -> Color.parseColor("#FFB300")
        DayStats.Status.RED    -> Color.parseColor("#FF7043")
        else                   -> Color.TRANSPARENT
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dayNumber == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val ringStroke = dpToPx(3.5f)
        val radius = (minOf(w, h) / 2f) - dpToPx(6f)

        val hasData = status != DayStats.Status.NONE && isCurrentMonth && !isFuture
        val goalMet = status == DayStats.Status.GREEN
        val color = statusColor()

        // Выбранный день — фиолетовое кольцо снаружи
        if (isSelectedDay) {
            trackPaint.color = Color.parseColor("#7C4DFF")
            trackPaint.strokeWidth = dpToPx(2f)
            canvas.drawCircle(cx, cy, radius + dpToPx(3f), trackPaint)
        }

        when {
            goalMet -> {
                fillPaint.color = color
                canvas.drawCircle(cx, cy, radius, fillPaint)
            }
            hasData -> {
                trackPaint.color = Color.parseColor("#33FFFFFF")
                trackPaint.strokeWidth = ringStroke
                canvas.drawCircle(cx, cy, radius, trackPaint)

                arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
                arcPaint.color = color
                arcPaint.strokeWidth = ringStroke
                val sweep = (progress.coerceIn(0f, 1f)) * 360f
                canvas.drawArc(arcRect, -90f, sweep, false, arcPaint)
            }
            isToday -> {
                trackPaint.color = Color.parseColor("#66FFFFFF")
                trackPaint.strokeWidth = dpToPx(2f)
                canvas.drawCircle(cx, cy, radius, trackPaint)
            }
        }

        // Число дня
        textPaint.textSize = dpToPx(15f)
        textPaint.color = when {
            goalMet         -> Color.WHITE
            isSelectedDay   -> Color.WHITE
            !isCurrentMonth -> Color.parseColor("#3A3A4A")
            isFuture        -> Color.parseColor("#555566")
            else            -> Color.WHITE
        }
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(dayNumber.toString(), cx, textY, textPaint)

        // серия подряд зелёных дней
        if (isInStreak && isCurrentMonth && !isFuture) {
            firePaint.textSize = dpToPx(11f)
            canvas.drawText("🔥", w - dpToPx(8f), dpToPx(12f), firePaint)
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
