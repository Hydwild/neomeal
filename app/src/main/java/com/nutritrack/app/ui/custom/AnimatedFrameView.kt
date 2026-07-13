package com.nutritrack.app.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.nutritrack.app.utils.LevelSystem

/**
 * Анимированная рамка-кольцо поверх аватара. Накладывается в FrameLayout
 * над CircleImageView. Поддерживает стили: neon (пульсация свечения),
 * rainbow (вращающийся радужный градиент), shine (бегущий блик).
 * Для style="none" ничего не рисует.
 */
class AnimatedFrameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var style: String = "none"
    private var baseColor: Int = Color.WHITE

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var phase = 0f   // 0..1 — фаза анимации
    private var animator: ValueAnimator? = null

    init {
        // BlurMaskFilter (свечение) требует программного слоя
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setFrame(frame: LevelSystem.Frame) {
        style = frame.style
        baseColor = frame.color.toInt()
        if (style == "none") {
            stop(); visibility = GONE
        } else {
            visibility = VISIBLE
            start()
        }
        invalidate()
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (style == "neon") 2200L else 2600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stop() { animator?.cancel(); animator = null }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stop() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (style != "none" && animator == null) start()
    }

    override fun onDraw(canvas: Canvas) {
        if (style == "none") return
        val w = width.toFloat(); val h = height.toFloat()
        val stroke = w * 0.045f
        val r = (minOf(w, h) - stroke) / 2f - dp(1f)
        val cx = w / 2f; val cy = h / 2f

        when (style) {
            "neon" -> drawNeon(canvas, cx, cy, r, stroke)
            "rainbow" -> drawRainbow(canvas, cx, cy, r, stroke)
            "shine" -> drawShine(canvas, cx, cy, r, stroke)
        }
    }

 /** Дорогой неон: мягкий цветной ореол + плотное свечение + тонкое яркое ядро со светлым кантом. */
    private fun drawNeon(canvas: Canvas, cx: Float, cy: Float, r: Float, stroke: Float) {
        // Плавная пульсация (мягкая синусоида), яркость гуляет в узком приятном диапазоне
        val pulse = (Math.sin(phase * 2 * Math.PI).toFloat() + 1f) / 2f  // 0..1
        val core = stroke * 0.55f   // тонкое аккуратное ядро

        // 1) Внешний широкий мягкий ореол — цвет рассеивается в воздухе
        glowPaint.shader = null
        glowPaint.color = withAlpha(baseColor, (40 + pulse * 40).toInt())
        glowPaint.strokeWidth = core
        glowPaint.maskFilter = BlurMaskFilter(stroke * (3.0f + pulse * 1.2f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, r, glowPaint)

        // 2) Ближнее плотное свечение — насыщенный цвет у самой трубки
        glowPaint.color = withAlpha(baseColor, (150 + pulse * 80).toInt())
        glowPaint.strokeWidth = core * 1.4f
        glowPaint.maskFilter = BlurMaskFilter(stroke * 1.1f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, r, glowPaint)

        // 3) Цветное ядро трубки
        ringPaint.shader = null
        ringPaint.maskFilter = null
        ringPaint.strokeCap = Paint.Cap.ROUND
        ringPaint.color = baseColor
        ringPaint.strokeWidth = core
        canvas.drawCircle(cx, cy, r, ringPaint)

        // 4) Тонкий светлый внутренний кант — «горячая» сердцевина неоновой трубки
        ringPaint.color = withAlpha(blend(baseColor, Color.WHITE, 0.75f), (200 + pulse * 55).toInt())
        ringPaint.strokeWidth = core * 0.42f
        canvas.drawCircle(cx, cy, r, ringPaint)
        ringPaint.strokeCap = Paint.Cap.BUTT
    }

 /** Смешивает два цвета: t=0 → c1, t=1 → c2. */
    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * tt).toInt(),
            (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * tt).toInt(),
            (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * tt).toInt()
        )
    }

 /** Вращающийся радужный градиент по кольцу. */
    private fun drawRainbow(canvas: Canvas, cx: Float, cy: Float, r: Float, stroke: Float) {
        val colors = intArrayOf(
            Color.parseColor("#FF1744"), Color.parseColor("#FFEA00"),
            Color.parseColor("#00E676"), Color.parseColor("#00B0FF"),
            Color.parseColor("#D500F9"), Color.parseColor("#FF1744")
        )
        val sweep = SweepGradient(cx, cy, colors, null)
        val m = Matrix().apply { setRotate(phase * 360f, cx, cy) }
        sweep.setLocalMatrix(m)
        ringPaint.shader = sweep
        ringPaint.maskFilter = null
        ringPaint.strokeWidth = stroke
        canvas.drawCircle(cx, cy, r, ringPaint)

        // лёгкое свечение
        glowPaint.shader = sweep
        glowPaint.strokeWidth = stroke
        glowPaint.maskFilter = BlurMaskFilter(stroke * 1.8f, BlurMaskFilter.Blur.NORMAL)
        glowPaint.alpha = 120
        canvas.drawCircle(cx, cy, r, glowPaint)
        glowPaint.shader = null
        glowPaint.alpha = 255
    }

 /** Бегущий блик: базовое кольцо + светлый сектор, бегущий по кругу. */
    private fun drawShine(canvas: Canvas, cx: Float, cy: Float, r: Float, stroke: Float) {
        // базовое кольцо
        ringPaint.shader = null
        ringPaint.maskFilter = null
        ringPaint.color = withAlpha(baseColor, 200)
        ringPaint.strokeWidth = stroke
        canvas.drawCircle(cx, cy, r, ringPaint)

        // бегущий блик через узкий sweep-градиент (прозрачный белый прозрачный)
        val highlight = intArrayOf(
            Color.TRANSPARENT, Color.TRANSPARENT,
            withAlpha(Color.WHITE, 230), Color.TRANSPARENT, Color.TRANSPARENT
        )
        val stops = floatArrayOf(0f, 0.40f, 0.5f, 0.60f, 1f)
        val sweep = SweepGradient(cx, cy, highlight, stops)
        val m = Matrix().apply { setRotate(phase * 360f, cx, cy) }
        sweep.setLocalMatrix(m)
        ringPaint.shader = sweep
        ringPaint.strokeWidth = stroke
        canvas.drawCircle(cx, cy, r, ringPaint)
        ringPaint.shader = null
    }

    private fun withAlpha(color: Int, a: Int): Int =
        Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
