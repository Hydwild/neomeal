package com.nutritrack.app.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AchievementBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var emoji: String = "🏆"        ; set(v) { field = v; invalidate() }
    var rarity: String = "common"   ; set(v) { field = v; invalidate() }
    var unlocked: Boolean = false   ; set(v) { field = v; invalidate() }

    private val pFill   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pGlow   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pShine  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val pEmoji  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pLock   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6B6B8A.toInt(); textAlign = Paint.Align.CENTER
    }

    private val hexPath = Path()

    private fun rarityColors(): Pair<Int, Int> = when (rarity) {
        "legendary" -> 0xFFFFB300.toInt() to 0xFFFF6F00.toInt()
        "epic"      -> 0xFFAB47BC.toInt() to 0xFF6A1B9A.toInt()
        "rare"      -> 0xFF42A5F5.toInt() to 0xFF1565C0.toInt()
        else        -> 0xFF66BB6A.toInt() to 0xFF2E7D32.toInt()
    }

    private fun borderColor(): Int = when (rarity) {
        "legendary" -> 0xFFFFE082.toInt()
        "epic"      -> 0xFFCE93D8.toInt()
        "rare"      -> 0xFF90CAF9.toInt()
        else        -> 0xFFA5D6A7.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val r  = min(w, h) / 2f * 0.82f
        buildHexagon(cx, cy, r)

        if (unlocked) {
            val (c1, c2) = rarityColors()
            pGlow.strokeWidth = r * 0.18f
            pGlow.color = (borderColor() and 0x00FFFFFF) or 0x66000000
            canvas.drawPath(hexPath, pGlow)
            pFill.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, c1, c2, Shader.TileMode.CLAMP)
            canvas.drawPath(hexPath, pFill)
            pFill.shader = null
            pBorder.color = borderColor(); pBorder.strokeWidth = r * 0.09f
            canvas.drawPath(hexPath, pBorder)
            canvas.save(); canvas.clipPath(hexPath)
            canvas.drawCircle(cx - r * 0.3f, cy - r * 0.5f, r * 0.5f, pShine)
            canvas.restore()
            pEmoji.textSize = r * 0.95f
            val fm = pEmoji.fontMetrics
            canvas.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2f, pEmoji)
        } else {
            pFill.shader = null; pFill.color = 0xFF2A2342.toInt()
            canvas.drawPath(hexPath, pFill)
            pBorder.color = 0xFF3D3358.toInt(); pBorder.strokeWidth = r * 0.07f
            canvas.drawPath(hexPath, pBorder)
            pLock.textSize = r * 0.7f
            val fm = pLock.fontMetrics
            canvas.drawText("🔒", cx, cy - (fm.ascent + fm.descent) / 2f, pLock)
        }
    }

    private fun buildHexagon(cx: Float, cy: Float, r: Float) {
        hexPath.reset()
        for (i in 0 until 6) {
            val angle = Math.toRadians((60.0 * i - 90.0))
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
        }
        hexPath.close()
    }
}