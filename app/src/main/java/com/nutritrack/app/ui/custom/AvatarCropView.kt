package com.nutritrack.app.ui.custom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.min

/**
 * Вью для кадрирования аватара: круглая область, изображение можно двигать и масштабировать.
 * Возвращает квадратный bitmap содержимого круга через [exportCircle].
 */
class AvatarCropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val scaleDetector: ScaleGestureDetector

    private var scaleFactor = 1f
    private var transX = 0f
    private var transY = 0f
    private var baseScale = 1f

    private var lastX = 0f
    private var lastY = 0f

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#AA000000") }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 3f * resources.displayMetrics.density
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val circle = RectF()
    private var radius = 0f
    private var cx = 0f
    private var cy = 0f

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.5f, 5f)
                applyMatrix()
                return true
            }
        })
    }

    fun setBitmap(bmp: Bitmap) {
        bitmap = bmp
        post { resetToCenter() }
    }

    private fun resetToCenter() {
        val bmp = bitmap ?: return
        // Базовый масштаб — чтобы изображение заполнило круг
        baseScale = (2 * radius) / min(bmp.width.toFloat(), bmp.height.toFloat())
        scaleFactor = 1f
        val s = baseScale
        transX = cx - bmp.width * s / 2f
        transY = cy - bmp.height * s / 2f
        applyMatrix()
    }

    private fun applyMatrix() {
        if (bitmap == null) return
        val s = baseScale * scaleFactor
        // Удерживаем центр при масштабировании
        matrix.reset()
        matrix.postScale(s, s)
        matrix.postTranslate(transX, transY)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f; cy = h / 2f
        radius = min(w, h) / 2f * 0.82f
        circle.set(cx - radius, cy - radius, cx + radius, cy + radius)
        resetToCenter()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
            MotionEvent.ACTION_MOVE -> if (!scaleDetector.isInProgress) {
                transX += event.x - lastX
                transY += event.y - lastY
                lastX = event.x; lastY = event.y
                applyMatrix()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, matrix, null)
        // Затемнение вне круга
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawCircle(cx, cy, radius, clearPaint)
        canvas.restoreToCount(save)
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }

 /** Экспорт содержимого круга как квадратный bitmap размером [outSize]. */
    fun exportCircle(outSize: Int = 512): Bitmap? {
        val bmp = bitmap ?: return null
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        // Переносим систему координат: круг весь outSize
        val scale = outSize / (2 * radius)
        val m = Matrix(matrix)
        m.postTranslate(-(cx - radius), -(cy - radius))
        m.postScale(scale, scale)
        c.drawBitmap(bmp, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
