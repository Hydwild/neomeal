package com.nutritrack.app.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.nutritrack.app.R

/**
 * Кастомная нижняя навигация со «стеклянным» фоном и плавно скользящей пилюлей.
 * Не зависит от Material BottomNavigationView — полный контроль над видом и анимацией.
 */
class GlassBottomNav @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Tab(val id: Int, val iconRes: Int, val label: String)

    private var tabs: List<Tab> = emptyList()
    private val icons = mutableListOf<Drawable?>()

    private var selectedIndex = 0
    private var indicatorCenterX = 0f      // текущая позиция центра пилюли (анимируется)
    private var targetCenterX = 0f
    private var animator: ValueAnimator? = null

    var onTabSelected: ((Int) -> Unit)? = null

    // Цвета/размеры
    private val glassColor      = Color.parseColor("#F2201733")
    private val strokeColor     = Color.parseColor("#1FFFFFFF")
    private val pillColor       get() = com.nutritrack.app.utils.AccentHelper.pillColor(context)
    private val activeColor     get() = com.nutritrack.app.utils.AccentHelper.color(context)
    private val inactiveColor: Int
        get() = if (com.nutritrack.app.utils.BackgroundHelper.backgroundLuminance(context) > 0.6f)
            Color.parseColor("#5A5470")   // тёмно-серый для светлого фона
        else Color.parseColor("#C9C4D9")  // светло-серый для тёмного фона

    private val dp = resources.displayMetrics.density
    private val barRadius   = 26f * dp
    private val pillWidth   = 64f * dp
    private val pillRadius  = 20f * dp
    private val iconSize    = 22f * dp
    private val labelSize   = 11f * dp
    private val iconTopPad  = 12f * dp
    // Вертикальные отступы «полной» пилюли (охватывает иконку + подпись)
    private val pillTopPad    = 7f * dp
    private val pillBottomPad  = 6f * dp

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glassColor }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f * dp; color = strokeColor
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = labelSize; isFakeBoldText = true
    }

    private val barRect = RectF()
    private val pillRect = RectF()

    /** Прозрачность фона таб-бара: 0 = прозрачный, 100 = исходный (плотный). */
    fun setBarAlpha(percent: Int) {
        val p = percent.coerceIn(0, 100)
        val rgb = 0x201733
        val a = (p * 255 / 100)
        bgPaint.color = (a shl 24) or rgb
        invalidate()
    }

    fun setTabs(newTabs: List<Tab>) {
        tabs = newTabs
        icons.clear()
        newTabs.forEach { icons.add(ContextCompat.getDrawable(context, it.iconRes)?.mutate()) }
        requestLayout(); invalidate()
    }

 /** Выбрать вкладку по id (например, из навигации) без вызова колбэка. */
    fun selectById(id: Int, animate: Boolean = true) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx >= 0 && idx != selectedIndex) selectIndex(idx, animate, notify = false)
        else if (idx >= 0 && indicatorCenterX == 0f) selectIndex(idx, false, notify = false)
    }

    private fun selectIndex(index: Int, animate: Boolean, notify: Boolean) {
        selectedIndex = index
        targetCenterX = cellCenterX(index)
        if (animate && indicatorCenterX != 0f) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(indicatorCenterX, targetCenterX).apply {
                duration = 280
                interpolator = DecelerateInterpolator()
                addUpdateListener { indicatorCenterX = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            indicatorCenterX = targetCenterX
            invalidate()
        }
        if (notify) onTabSelected?.invoke(tabs[index].id)
    }

    private fun cellWidth() = if (tabs.isEmpty()) 0f else width.toFloat() / tabs.size
    private fun cellCenterX(index: Int) = cellWidth() * index + cellWidth() / 2f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (66 * dp).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (indicatorCenterX == 0f && tabs.isNotEmpty()) indicatorCenterX = cellCenterX(selectedIndex)
        targetCenterX = cellCenterX(selectedIndex)
    }

    override fun onDraw(canvas: Canvas) {
        if (tabs.isEmpty()) return

        // Фон-стекло
        barRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(barRect, barRadius, barRadius, bgPaint)
        canvas.drawRoundRect(barRect, barRadius, barRadius, strokePaint)

        // Полная пилюля под активным разделом — от верха иконки до низа подписи
        val labelBaselineY = iconTopPad + iconSize + labelSize + 6f * dp
        val pillTop = iconTopPad - pillTopPad
        val pillBottom = labelBaselineY + pillBottomPad
        // Пилюля центрируется точно по активной ячейке (ширина = ячейка минус боковой зазор)
        val cwForPill = cellWidth()
        val sideGap = 6f * dp
        val dynWidth = (cwForPill - sideGap * 2f).coerceAtMost(pillWidth * 1.6f)
        val pillLeft = indicatorCenterX - dynWidth / 2f
        val pillRight = indicatorCenterX + dynWidth / 2f
        pillRect.set(pillLeft, pillTop, pillRight, pillBottom)
        pillPaint.color = pillColor
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint)

        // Иконки + подписи
        val cw = cellWidth()
        tabs.forEachIndexed { i, _ ->
            val cx = cw * i + cw / 2f
            val active = i == selectedIndex
            val color = if (active) activeColor else inactiveColor

            icons[i]?.let { d ->
                val left = (cx - iconSize / 2f).toInt()
                val top = iconTopPad.toInt()
                d.setBounds(left, top, (left + iconSize).toInt(), (top + iconSize).toInt())
                d.setTint(color)
                d.draw(canvas)
            }

            textPaint.color = color
            val labelY = iconTopPad + iconSize + labelSize + 6f * dp
            canvas.drawText(tabs[i].label, cx, labelY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && tabs.isNotEmpty()) {
            val idx = (event.x / cellWidth()).toInt().coerceIn(0, tabs.size - 1)
            if (idx != selectedIndex) {
                com.nutritrack.app.utils.HapticHelper.tap(this)
                selectIndex(idx, animate = true, notify = true)
            }
            performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
