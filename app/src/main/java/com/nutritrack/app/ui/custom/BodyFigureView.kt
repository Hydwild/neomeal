package com.nutritrack.app.ui.custom

import android.content.Context
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.nutritrack.app.R

/**
 * Фигура тела с метками-замерами по зонам.
 * Рисует силуэт (vector drawable) и поверх — таблетки с актуальными размерами в см.
 * Тап по метке/зоне callback onZoneTap(key).
 *
 * Координаты зон заданы в долях (0..1) от области фигуры.
 */
class BodyFigureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

 /** key части значение в см (или null). */
    var values: Map<String, Float> = emptyMap()
        set(v) { field = v; invalidate() }

 /** Пол — определяет фигуру и координаты зон. */
    var isMale: Boolean = true
        set(v) {
            field = v
            figure = ContextCompat.getDrawable(
                context,
                if (v) R.drawable.figure_male else R.drawable.figure_female
            ) as? VectorDrawable
            invalidate()
        }

    var onZoneTap: ((String) -> Unit)? = null

    // Зона: key, доля X (0..1), доля Y (0..1), сторона метки (-1 слева, +1 справа, 0 по центру)
    private data class Zone(val key: String, val fx: Float, val fy: Float, val side: Int, val label: String, val labelFy: Float = -1f)

    // Мужская фигура (силуэт 800x800, руки согнуты вверх)
    // Л/П уже зеркально исправлены (его лево = справа от зрителя)
    // Мужская фигура (силуэт 1254x1254, руки с гантелями, ноги расставлены).
    // Координаты выверены по силуэту. Л/П зеркально (его лево = справа от зрителя).
    private val zonesMale = listOf(
        Zone("wrist_l",   0.74f, 0.120f, +1, "Запястье Л", 0.085f),
        Zone("wrist_r",   0.26f, 0.120f, -1, "Запястье П", 0.085f),
        Zone("biceps_l",  0.72f, 0.215f, +1, "Бицепс Л", 0.215f),
        Zone("biceps_r",  0.28f, 0.215f, -1, "Бицепс П", 0.215f),
        Zone("shoulders", 0.50f, 0.245f, -1, "Плечи", 0.345f),
        Zone("chest",     0.50f, 0.330f, +1, "Грудь", 0.355f),
        Zone("waist",     0.50f, 0.430f, -1, "Талия", 0.475f),
        Zone("belly",     0.50f, 0.540f, +1, "Живот", 0.475f),
        Zone("hips",      0.50f, 0.585f, +1, "Ягодицы", 0.605f),
        Zone("thigh_l",   0.58f, 0.640f, +1, "Бедро Л", 0.735f),
        Zone("thigh_r",   0.42f, 0.640f, -1, "Бедро П", 0.605f),
        Zone("calf_l",    0.59f, 0.800f, +1, "Икра Л", 0.875f),
        Zone("calf_r",    0.41f, 0.800f, -1, "Икра П", 0.875f)
    )

    // Женская фигура (руки подняты с гантелями, ноги расставлены).
    // Точки выверены по анатомии силуэта. Л = справа от зрителя, П = слева.
    private val zonesFemale = listOf(
        Zone("wrist_l",   0.73f, 0.120f, +1, "Запястье Л", 0.085f),
        Zone("wrist_r",   0.27f, 0.120f, -1, "Запястье П", 0.085f),
        Zone("biceps_l",  0.70f, 0.210f, +1, "Бицепс Л", 0.215f),
        Zone("biceps_r",  0.30f, 0.210f, -1, "Бицепс П", 0.215f),
        Zone("shoulders", 0.50f, 0.250f, -1, "Плечи", 0.345f),
        Zone("chest",     0.50f, 0.330f, +1, "Грудь", 0.355f),
        Zone("waist",     0.50f, 0.425f, -1, "Талия", 0.475f),
        Zone("belly",     0.50f, 0.535f, +1, "Живот", 0.475f),
        Zone("hips",      0.50f, 0.580f, +1, "Ягодицы", 0.605f),
        Zone("thigh_l",   0.58f, 0.640f, +1, "Бедро Л", 0.735f),
        Zone("thigh_r",   0.42f, 0.640f, -1, "Бедро П", 0.605f),
        Zone("calf_l",    0.59f, 0.800f, +1, "Икра Л", 0.875f),
        Zone("calf_r",    0.41f, 0.800f, -1, "Икра П", 0.875f)
    )

    private val zones get() = if (isMale) zonesMale else zonesFemale

    private var figure: VectorDrawable? = null

    private val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val pillLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        textAlign = Paint.Align.CENTER
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#66FFFFFF")
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // прямоугольники меток (для обработки тапа)
    private val pillRects = mutableListOf<Pair<RectF, String>>()

    private var figLeft = 0f
    private var figTop = 0f
    private var figW = 0f
    private var figH = 0f

    init {
        figure = ContextCompat.getDrawable(context, R.drawable.figure_male) as? VectorDrawable
    }


    private fun zoneLabel(key: String): String {
        val resId = when (key) {
            "wrist_l" -> R.string.bpf_wrist_l; "wrist_r" -> R.string.bpf_wrist_r
            "biceps_l" -> R.string.bpf_biceps_l; "biceps_r" -> R.string.bpf_biceps_r
            "shoulders" -> R.string.bpf_shoulders; "chest" -> R.string.bpf_chest
            "waist" -> R.string.bpf_waist; "belly" -> R.string.bpf_belly; "hips" -> R.string.bpf_hips
            "thigh_l" -> R.string.bpf_thigh_l; "thigh_r" -> R.string.bpf_thigh_r
            "calf_l" -> R.string.bpf_calf_l; "calf_r" -> R.string.bpf_calf_r
            else -> return key
        }
        return context.getString(resId)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pillRects.clear()
        val w = width.toFloat()
        val h = height.toFloat()

        // Фигура по центру, квадратная область (силуэт 800x800)
        val size = minOf(w, h * 0.95f)
        figW = size
        figH = size
        figLeft = (w - size) / 2f
        figTop = (h - size) / 2f

        figure?.let {
            it.setBounds(figLeft.toInt(), figTop.toInt(), (figLeft + figW).toInt(), (figTop + figH).toInt())
            it.draw(canvas)
        }

        // Метки
        zones.forEach { z ->
            val px = figLeft + figW * z.fx
            val py = figTop + figH * z.fy
            val valTxt = values[z.key]?.let { String.format("%.1f", it) }

            // Точка на зоне
            dotPaint.color = if (valTxt != null) Color.parseColor("#40C4FF") else Color.parseColor("#556070")
            canvas.drawCircle(px, py, dp(3f), dotPaint)

            // Позиция таблетки: смещаем в сторону от фигуры
            val pillW = dp(70f)
            val pillH = dp(33f)
            val gap = dp(if (z.side == 0) 0f else 30f)
            val pcx = when (z.side) {
                -1 -> figLeft + figW * 0.13f - gap * 0f   // слева
                +1 -> figLeft + figW * 0.87f + gap * 0f   // справа
                else -> px
            }.let {
                when (z.side) {
                    -1 -> (figLeft + dp(4f) + pillW / 2f)
                    +1 -> (figLeft + figW - dp(4f) - pillW / 2f)
                    else -> px
                }
            }
            // Y таблетки: если задан labelFy — используем его (точка остаётся на анатомии)
            val pcy = if (z.labelFy >= 0f) figTop + figH * z.labelFy else py

            // Линия от зоны к таблетке
            canvas.drawLine(px, py, pcx, pcy, linePaint)

            // Таблетка
            val rect = RectF(pcx - pillW/2f, pcy - pillH/2f, pcx + pillW/2f, pcy + pillH/2f)
            pillBg.color = if (valTxt != null) Color.parseColor("#2E2A4A") else Color.parseColor("#241F38")
            canvas.drawRoundRect(rect, dp(10f), dp(10f), pillBg)

            // подпись (название) сверху мелким, значение снизу крупным
            pillLabel.textSize = dp(8.5f)
            canvas.drawText(zoneLabel(z.key), pcx, rect.top + dp(12f), pillLabel)
            pillText.textSize = dp(13f)
            pillText.color = if (valTxt != null) Color.WHITE else Color.parseColor("#777788")
            canvas.drawText(valTxt ?: "—", pcx, rect.bottom - dp(7f), pillText)

            pillRects.add(rect to z.key)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x; val y = event.y
            pillRects.forEach { (rect, key) ->
                if (rect.contains(x, y)) {
                    performClick()
                    onZoneTap?.invoke(key)
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
