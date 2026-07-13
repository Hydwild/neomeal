package com.nutritrack.app.ui.custom

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Лёгкое конфетти. Вызов: ConfettiView.celebrate(activity). Управляется настройкой. */
class ConfettiView(context: Context) : View(context) {

    private data class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rotation: Float, var rotSpeed: Float, val color: Int, val size: Float
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    private val colors = intArrayOf(
        Color.parseColor("#B388FF"), Color.parseColor("#69F0AE"),
        Color.parseColor("#FFD740"), Color.parseColor("#FF6E40"),
        Color.parseColor("#40C4FF"), Color.parseColor("#FF80AB")
    )

    fun start() {
        particles.clear()
        val w = resources.displayMetrics.widthPixels.toFloat()
        val density = resources.displayMetrics.density
        repeat(80) {
            val angle = Math.toRadians(Random.nextDouble(240.0, 300.0))
            val speed = Random.nextDouble(8.0, 18.0).toFloat() * density
            particles.add(Particle(
                x = w / 2f + Random.nextFloat() * w * 0.3f - w * 0.15f,
                y = resources.displayMetrics.heightPixels * 0.75f,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat(),
                rotation = Random.nextFloat() * 360f,
                rotSpeed = Random.nextFloat() * 16f - 8f,
                color = colors[Random.nextInt(colors.size)],
                size = (4 + Random.nextFloat() * 6) * density
            ))
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            addUpdateListener {
                val gravity = 0.5f * resources.displayMetrics.density
                particles.forEach { p ->
                    p.x += p.vx; p.y += p.vy; p.vy += gravity; p.rotation += p.rotSpeed
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    (parent as? ViewGroup)?.removeView(this@ConfettiView)
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        particles.forEach { p ->
            paint.color = p.color
            canvas.save()
            canvas.rotate(p.rotation, p.x, p.y)
            canvas.drawRect(p.x - p.size / 2, p.y - p.size / 4, p.x + p.size / 2, p.y + p.size / 4, paint)
            canvas.restore()
        }
    }

    companion object {
        fun celebrate(activity: Activity?) {
            activity ?: return
            if (!com.nutritrack.app.utils.PrefsManager(activity).confettiEnabled) return
            val root = activity.window.decorView as? ViewGroup ?: return
            val view = ConfettiView(activity)
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            view.isClickable = false; view.isFocusable = false
            root.addView(view)
            view.start()
        }
    }
}
