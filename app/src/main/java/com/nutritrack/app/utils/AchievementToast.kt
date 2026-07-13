package com.nutritrack.app.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.nutritrack.app.R
import com.nutritrack.app.ui.custom.AchievementBadgeView
import java.lang.ref.WeakReference

/**
 * Всплывающие уведомления о достижениях СВЕРХУ, по одному, с надёжной очередью.
 *
 * Вся работа с очередью идёт ТОЛЬКО на главном потоке (через Handler main looper),
 * поэтому гонок нет — ни одно достижение не теряется.
 */
object AchievementToast {

    data class Item(
        val emoji: String,
        val title: String,
        val description: String,
        val rarity: String
    )

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Item>()
    private var showing = false
    private var activityRef: WeakReference<Activity>? = null

    private const val ANIM_IN = 450L
    private const val HOLD = 2600L
    private const val ANIM_OUT = 350L
    private const val GAP = 300L

 /** Показать одно достижение. */
    fun show(activity: Activity, emoji: String, title: String, description: String, rarity: String) {
        enqueueAll(activity, listOf(Item(emoji, title, description, rarity)))
    }

 /** Добавить несколько достижений в очередь — показываются по одному сверху. */
    fun enqueueAll(activity: Activity, items: List<Item>) {
        if (items.isEmpty()) return
        // Всё на главном потоке — никаких гонок
        handler.post {
            activityRef = WeakReference(activity)
            queue.addAll(items)
            if (!showing) showNext()
        }
    }

    private fun showNext() {
        if (queue.isEmpty()) { showing = false; return }
        val activity = activityRef?.get()
        if (activity == null || activity.isFinishing) {
            queue.clear(); showing = false; return
        }
        showing = true
        val item = queue.removeFirst()

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.view_achievement_toast, root, false)

        view.findViewById<AchievementBadgeView>(R.id.badge).apply {
            this.emoji = item.emoji
            this.rarity = item.rarity
            this.unlocked = true
        }
        view.findViewById<TextView>(R.id.tvToastTitle).text = item.title
        view.findViewById<TextView>(R.id.tvToastDesc).text  = item.description
        view.findViewById<TextView>(R.id.tvToastLabel).text = activity.getString(R.string.lay_ach_unlocked)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = dpToPx(activity, 50)
            marginStart = dpToPx(activity, 16)
            marginEnd = dpToPx(activity, 16)
        }
        root.addView(view, params)

        view.alpha = 0f
        view.translationY = -dpToPx(activity, 120).toFloat()
        view.animate()
            .alpha(1f).translationY(0f)
            .setDuration(ANIM_IN)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.9f))
            .withEndAction {
                handler.postDelayed({
                    view.animate()
                        .alpha(0f)
                        .translationY(-dpToPx(activity, 120).toFloat())
                        .setDuration(ANIM_OUT)
                        .withEndAction {
                            (view.parent as? ViewGroup)?.removeView(view)
                            // следующий тост на главном потоке после паузы
                            handler.postDelayed({ showNext() }, GAP)
                        }
                        .start()
                }, HOLD)
            }
            .start()

        // Тап — закрыть досрочно и перейти к следующему
        view.setOnClickListener {
            view.animate().cancel()
            (view.parent as? ViewGroup)?.removeView(view)
            handler.postDelayed({ showNext() }, GAP)
        }
    }

    private fun dpToPx(activity: Activity, dp: Int): Int =
        (dp * activity.resources.displayMetrics.density).toInt()
}
