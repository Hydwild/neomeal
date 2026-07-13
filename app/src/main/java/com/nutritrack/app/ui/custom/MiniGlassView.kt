package com.nutritrack.app.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.sin
import kotlin.random.Random

/**
 * Маленький стеклянный стакан воды для списка.
 * Возможности:
 * - наполнение снизу вверх с лёгким "переливом" (overshoot)
 * - градиент воды (глубина)
 * - двойная волна на поверхности
 * - поднимающиеся пузырьки
 * - блик на стекле
 * - скруглённые углы
 */
class MiniGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var fillLevel = 0f
    private var targetLevel = 0f
    private var wavePhase = 0f

    private val waterTop = Color.parseColor("#80D8FF")     // светлее (поверхность)
    private val waterBottom = Color.parseColor("#0091EA")  // насыщеннее (дно)
    private val waterColorLight = Color.parseColor("#B3E5FC")
    private val glassStroke = Color.parseColor("#73FFFFFF")
    private val glassFillTint = Color.parseColor("#14FFFFFF")

    private val paintWater = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintWaterBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintGlassBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = glassFillTint }
    private val paintGlass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = glassStroke
    }
    private val paintShine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#3DFFFFFF") }
    private val paintBubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#99FFFFFF") }

    private var fillAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null

    // Пузырьки: x (0..1 по ширине), стартовая фаза, скорость, радиус
    private data class Bubble(val xRel: Float, var phase: Float, val speed: Float, val r: Float)
    private val bubbles = mutableListOf<Bubble>()

 /** Наполнить стакан до уровня (0..1) с анимацией снизу вверх и лёгким переливом. */
    fun fill(level: Float = 1f) {
        targetLevel = level.coerceIn(0f, 1f)
        fillAnimator?.cancel()
        fillAnimator = ValueAnimator.ofFloat(0f, targetLevel).apply {
            duration = 850
            interpolator = OvershootInterpolator(1.4f)  // вода чуть "переливается" и оседает
            addUpdateListener {
                fillLevel = (it.animatedValue as Float).coerceIn(0f, 1.05f)
                invalidate()
            }
            start()
        }
        spawnBubbles()
        startWave()
    }

    private fun spawnBubbles() {
        bubbles.clear()
        val count = 4
        repeat(count) {
            bubbles.add(
                Bubble(
                    xRel = 0.25f + Random.nextFloat() * 0.5f,
                    phase = Random.nextFloat(),
                    speed = 0.6f + Random.nextFloat() * 0.6f,
                    r = dp(1f) + Random.nextFloat() * dp(1.5f)
                )
            )
        }
    }

    private fun startWave() {
        waveAnimator?.cancel()
        waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat() * 3).apply {
            duration = 2600
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                wavePhase = it.animatedValue as Float
                // двигаем пузырьки
                bubbles.forEach { b -> b.phase += 0.012f * b.speed; if (b.phase > 1f) b.phase -= 1f }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(3f)

        val topW = w - pad * 2
        val botW = topW * 0.80f
        val left = pad
        val right = w - pad
        val topY = pad
        val botY = h - pad
        val corner = dp(4f)

        // Путь стакана со скруглёнными нижними углами
        val glassPath = Path().apply {
            moveTo(left, topY)
            lineTo(right, topY)
            lineTo(left + (topW + botW) / 2f, botY - corner)
            quadTo(left + (topW + botW) / 2f, botY, left + (topW + botW) / 2f - corner, botY)
            lineTo(left + (topW - botW) / 2f + corner, botY)
            quadTo(left + (topW - botW) / 2f, botY, left + (topW - botW) / 2f, botY - corner)
            close()
        }

        // Лёгкая заливка-подложка (стекло)
        canvas.drawPath(glassPath, paintGlassBg)

        canvas.save()
        canvas.clipPath(glassPath)

        if (fillLevel > 0.001f) {
            val lvl = fillLevel.coerceAtMost(1f)
            val waterTopY = botY - (botY - topY) * lvl
            val amp = dp(2.2f)

            // Градиент воды (вертикальный)
            paintWater.shader = LinearGradient(
                0f, waterTopY, 0f, botY,
                waterTop, waterBottom, Shader.TileMode.CLAMP
            )

            // Задняя волна (светлее)
            paintWaterBack.color = waterColorLight
            paintWaterBack.alpha = 110
            canvas.drawPath(buildWave(left, right, botY, waterTopY, amp, wavePhase + 1.7f), paintWaterBack)

            // Передняя волна с градиентом
            canvas.drawPath(buildWave(left, right, botY, waterTopY, amp, wavePhase), paintWater)

            // Пузырьки (поднимаются от дна к поверхности)
            bubbles.forEach { b ->
                val bx = left + (right - left) * b.xRel
                val by = botY - (botY - waterTopY) * b.phase
                if (by > waterTopY) {
                    paintBubble.alpha = (160 * (1f - b.phase)).toInt().coerceIn(40, 160)
                    canvas.drawCircle(bx, by, b.r, paintBubble)
                }
            }
        }
        canvas.restore()

        // Контур стакана
        canvas.drawPath(glassPath, paintGlass)

        // Блик на стекле (диагональная полоска слева сверху)
        canvas.save()
        canvas.clipPath(glassPath)
        val shinePath = Path().apply {
            moveTo(left + dp(3f), topY + dp(3f))
            lineTo(left + dp(7f), topY + dp(3f))
            lineTo(left + dp(3f) + botW * 0.18f, botY - dp(4f))
            lineTo(left + dp(1f) + botW * 0.10f, botY - dp(4f))
            close()
        }
        canvas.drawPath(shinePath, paintShine)
        canvas.restore()
    }

    private fun buildWave(left: Float, right: Float, bottom: Float, surfaceY: Float, amp: Float, phase: Float): Path {
        val path = Path()
        path.moveTo(left, bottom)
        path.lineTo(left, surfaceY)
        val step = dp(2.5f)
        var x = left
        while (x <= right) {
            val rel = (x - left) / (right - left)
            // Сумма двух синусоид — более органичная волна
            val y = surfaceY +
                    amp * sin(rel * 2f * Math.PI.toFloat() * 1.5f + phase) +
                    amp * 0.4f * sin(rel * 2f * Math.PI.toFloat() * 3f + phase * 1.6f)
            path.lineTo(x, y)
            x += step
        }
        path.lineTo(right, surfaceY)
        path.lineTo(right, bottom)
        path.close()
        return path
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fillAnimator?.cancel()
        waveAnimator?.cancel()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
