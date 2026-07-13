package com.nutritrack.app.pets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.io.File

/**
 * View, проигрывающая покадровую анимацию питомца из распакованных PNG (filesDir).
 *
 * Состояния:
 *  - idle  — зацикленная базовая анимация, периодически вставляется blink.
 *  - blink/drink/happy/jump — проигрываются один раз ("one-shot") и возвращают в idle.
 *
 * Использование:
 *   petView.load("panda")           // загрузить питомца
 *   petView.play("drink")           // сыграть реакцию (после — авто-возврат в idle)
 */
class PetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val storage = PetStorage(context)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())

    private var petId: String? = null

    // Кэш кадров по действию: action -> список bitmap'ов
    private val cache = HashMap<String, List<Bitmap>>()

    private var currentAction = "idle"
    private var currentFrames: List<Bitmap> = emptyList()
    private var frameIndex = 0
    private var oneShot = false        // текущая анимация играется один раз?

    private val srcRect = Rect()
    private val dstRect = RectF()

    // Скорость: мс на кадр
    private var frameDelayMs = 130L

    // Периодическое моргание во время idle
    private var idleBlinkEnabled = true
    private var framesSinceBlink = 0
    private val blinkEveryFrames = 16   // при idle-тике 300мс ≈ раз в 5 сек

    private val ticker = object : Runnable {
        override fun run() {
            advanceFrame()
            handler.postDelayed(this, frameDelayMs)
        }
    }

    /** Загрузить питомца по id. Возвращает true, если пак установлен и idle есть. */
    fun load(id: String): Boolean {
        if (petId == id && currentFrames.isNotEmpty()) return true
        stop()
        recycleCache()
        petId = id
        if (!storage.isInstalled(id) || !storage.hasAction(id, "idle")) {
            currentFrames = emptyList()
            invalidate()
            return false
        }
        setAction("idle", oneShotMode = false)
        if (isAttachedToWindow) start()
        return currentFrames.isNotEmpty()
    }

    /** Сыграть действие один раз, затем вернуться в idle. */
    fun play(action: String) {
        val id = petId ?: return
        if (action == "idle") { setAction("idle", false); return }
        if (!storage.hasAction(id, action)) return   // нет такого действия — игнор
        setAction(action, oneShotMode = true)
        if (isAttachedToWindow) start()
    }

    fun setFrameDelay(ms: Long) { frameDelayMs = ms.coerceIn(40L, 500L) }

    private fun setAction(action: String, oneShotMode: Boolean) {
        currentAction = action
        oneShot = oneShotMode
        currentFrames = framesFor(action)
        frameIndex = 0
        framesSinceBlink = 0
        // Подбор темпа под действие
        frameDelayMs = when (action) {
            "jump"  -> 90L
            "happy" -> 100L
            "drink" -> 140L
            "blink" -> 70L
            else    -> 300L   // idle — спокойно, просто отсчёт до моргания
        }
        invalidate()
    }

    /** Загружает (с кэшированием) кадры действия в память. */
    private fun framesFor(action: String): List<Bitmap> {
        cache[action]?.let { return it }
        val id = petId ?: return emptyList()
        val files: List<File> = storage.frames(id, action)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmaps = files.mapNotNull { f ->
            runCatching { BitmapFactory.decodeFile(f.absolutePath, opts) }.getOrNull()
        }
        if (bitmaps.isNotEmpty()) cache[action] = bitmaps
        return bitmaps
    }

    private fun advanceFrame() {
        if (currentFrames.isEmpty()) return

        // Спокойный idle: НЕ перебираем кадры тела (без покачивания/прыжков).
        // Держим первый кадр и периодически моргаем.
        if (currentAction == "idle" && !oneShot) {
            frameIndex = 0
            invalidate()
            framesSinceBlink++
            if (framesSinceBlink >= blinkEveryFrames &&
                idleBlinkEnabled &&
                storage.hasAction(petId ?: "", "blink")
            ) {
                framesSinceBlink = 0
                setAction("blink", oneShotMode = true)
            }
            return
        }

        frameIndex++
        if (frameIndex >= currentFrames.size) {
            // дошли до конца проигрываемого действия
            if (oneShot) {
                setAction("idle", oneShotMode = false)   // вернуться в спокойный idle
            } else {
                frameIndex = 0
            }
        }
        invalidate()
    }

    private fun start() {
        if (currentFrames.isEmpty()) return
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, frameDelayMs)
    }

    private fun stop() {
        handler.removeCallbacks(ticker)
    }

    override fun onDraw(canvas: Canvas) {
        val frames = currentFrames
        if (frames.isEmpty()) return
        val bmp = frames[frameIndex.coerceIn(0, frames.size - 1)]
        srcRect.set(0, 0, bmp.width, bmp.height)
        // вписываем по центру с сохранением пропорций
        val vw = width.toFloat(); val vh = height.toFloat()
        val scale = minOf(vw / bmp.width, vh / bmp.height)
        val dw = bmp.width * scale; val dh = bmp.height * scale
        val left = (vw - dw) / 2f; val top = (vh - dh) / 2f
        dstRect.set(left, top, left + dw, top + dh)
        canvas.drawBitmap(bmp, srcRect, dstRect, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (currentFrames.isNotEmpty()) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun recycleCache() {
        for (list in cache.values) for (b in list) if (!b.isRecycled) b.recycle()
        cache.clear()
        currentFrames = emptyList()
    }

    /** Освободить ресурсы вручную (например, при смене питомца извне). */
    fun release() {
        stop()
        recycleCache()
    }
}
