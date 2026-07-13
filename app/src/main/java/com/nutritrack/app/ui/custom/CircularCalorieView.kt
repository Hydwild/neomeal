package com.nutritrack.app.ui.custom

import com.nutritrack.app.R

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CircularCalorieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // Данные
    var calories: Int   = 0;    private set
    var goal:     Int   = 2000; private set
    var protein:  Float = 0f;   private set
    var fat:      Float = 0f;   private set
    var carbs:    Float = 0f;   private set

    // Цвета
    private val clrBg      = Color.parseColor("#2A1F4A")
    private val clrStart   = Color.parseColor("#7C4DFF")
    private val clrMid     = Color.parseColor("#40C4FF")
    private val clrEnd     = Color.parseColor("#69F0AE")
    private val clrOver    = Color.parseColor("#FF5252")
    private val clrWhite   = Color.WHITE
    private val clrGray    = Color.parseColor("#9E9EB0")
    private val clrProtein = Color.parseColor("#69F0AE")
    private val clrFat     = Color.parseColor("#FFD740")
    private val clrCarbs   = Color.parseColor("#FF6E40")

    // Paint
    private val pBg    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pArc   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pGlow  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pDot   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pNum   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val pKkal  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9E9EB0"); textAlign = Paint.Align.CENTER }
    private val pGoal  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9E9EB0"); textAlign = Paint.Align.CENTER }
    private val pPct   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pMT    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val pMV    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val pLine  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3D2F6A"); strokeWidth = 1.5f }

    // rectArc — бокс ЦЕНТРАЛЬНОЙ ЛИНИИ дуги (не внешней, не внутренней)
    private val rectArc = RectF()
    private var grad: SweepGradient? = null
    private var lastW = 0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w     = width.toFloat()
        val h     = height.toFloat()

        // Кольцо занимает всю высоту view — КБЖУ показываются цветными
        // плашками под кругом (в layout), а не внутри этого view.
        val ringH = h
        val cx    = w / 2f
        val cy    = ringH / 2f

        // Геометрия — ОДИН источник правды
        // arcCenterR = радиус ЦЕНТРАЛЬНОЙ ЛИНИИ дуги (пикселей до середины штриха)
        // stroke = толщина дуги
        // rectArc задаётся СТРОГО по arcCenterR центр линии = arcCenterR от (cx,cy)
        // точка тоже ставится на arcCenterR всегда совпадает с дугой
        val arcCenterR = min(w * 0.38f, ringH * 0.42f)
        val stroke     = arcCenterR * 0.175f

        if (lastW != width) {
            lastW = width
            grad = SweepGradient(cx, cy, intArrayOf(clrStart, clrMid, clrEnd, clrStart), null)
        }

        // rectArc — прямоугольник вокруг центральной линии
        rectArc.set(cx - arcCenterR, cy - arcCenterR, cx + arcCenterR, cy + arcCenterR)

        val progress = (calories.toFloat() / goal.toFloat()).coerceIn(0f, 1.1f)
        val sweep    = 270f * progress.coerceAtMost(1f)
        val arcColor = when {
            progress > 1.0f -> clrOver
            progress > 0.5f -> clrEnd
            else            -> clrStart
        }

        // Фон
        pBg.strokeWidth = stroke
        pBg.color       = clrBg
        canvas.drawArc(rectArc, 135f, 270f, false, pBg)

        // Прогресс
        if (progress > 0f) {
            pGlow.strokeWidth = stroke * 1.45f
            pGlow.color       = arcColor and 0x55FFFFFF.toInt()
            canvas.drawArc(rectArc, 135f, sweep, false, pGlow)

            pArc.strokeWidth = stroke
            pArc.shader      = if (progress < 1f) grad else null
            pArc.color       = arcColor
            canvas.drawArc(rectArc, 135f, sweep, false, pArc)

            // Точка: угол конца дуги, радиус = arcCenterR (то же что у rectArc)
            val endAngle = Math.toRadians((135.0 + sweep.toDouble()))
            val dotX     = (cx + arcCenterR * cos(endAngle)).toFloat()
            val dotY     = (cy + arcCenterR * sin(endAngle)).toFloat()
            pDot.color = arcColor
            pDot.setShadowLayer(18f, 0f, 0f, arcColor)
            canvas.drawCircle(dotX, dotY, stroke * 0.62f, pDot)
        }

        // Текст внутри кольца
        // Порядок сверху вниз:
        // 1) Процент — cy - arcCenterR * 0.48
        // 2) Число — cy - arcCenterR * 0.08 (чуть выше центра)
        // 3) ккал — сразу под числом
        // 4) цель — cy + arcCenterR * 0.36 (с запасом до дуги внизу)
        val percent = ((progress * 100f).toInt()).coerceAtMost(999)

        // 1. Процент
        pPct.textSize = arcCenterR * 0.21f
        pPct.color    = arcColor
        canvas.drawText("$percent%", cx, cy - arcCenterR * 0.48f, pPct)

        // 2. Число калорий
        pNum.textSize  = arcCenterR * 0.70f
        // baseline так, чтобы визуальный центр числа был на (cy - arcCenterR*0.08)
        val numMidY    = cy - arcCenterR * 0.08f
        val numBase    = numMidY + (-pNum.ascent() - pNum.descent()) / 2f
        canvas.drawText(calories.toString(), cx, numBase, pNum)

        // 3. «ккал» — под числом
        pKkal.textSize  = arcCenterR * 0.20f
        val kkalBase    = numBase + pNum.descent() + arcCenterR * 0.04f + pKkal.textSize * 0.85f
        canvas.drawText(context.getString(R.string.unit_kcal), cx, kkalBase, pKkal)

        // 4. «цель: N ккал» — ещё ниже, но не ближе чем 0.36 от arcCenterR
        pGoal.textSize  = arcCenterR * 0.175f
        val goalBase    = cy + arcCenterR * 0.36f
        canvas.drawText(context.getString(R.string.cv_goal_fmt, goal), cx, goalBase, pGoal)
    }

    // Анимация
    fun animateTo(newCal: Int, newGoal: Int, p: Float, f: Float, c: Float) {
        val fCal = calories; val fGoal = goal
        val fP = protein;    val fF = fat; val fC = carbs

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration     = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val t    = a.animatedFraction
                calories = (fCal  + (newCal  - fCal)  * t).toInt()
                goal     = (fGoal + (newGoal - fGoal) * t).toInt()
                protein  = fP + (p - fP) * t
                fat      = fF + (f - fF) * t
                carbs    = fC + (c - fC) * t
                invalidate()
            }
            start()
        }
    }
}
