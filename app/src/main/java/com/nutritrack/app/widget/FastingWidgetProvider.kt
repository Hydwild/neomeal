package com.nutritrack.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nutritrack.app.MainActivity
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Виджет голодания: показывает таймер активной сессии, фазу и прогресс.
 * Если активного голодания нет — показывает приглашение.
 */
class FastingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { renderWidget(context, mgr, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, FastingWidgetProvider::class.java)
            )
            ids.forEach { renderWidget(context, mgr, it) }
        }

        private fun renderWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = (context.applicationContext as NutriApp).database
                val profile = db.profileDao().getActiveProfileSync()
                val pid = profile?.id ?: 1L
                val session = db.fastingDao().getActiveSessionSync(pid)

                val views = RemoteViews(context.packageName, R.layout.widget_fasting)
                WidgetTheme.apply(context, views)
                val locCtx = com.nutritrack.app.utils.LocaleHelper.wrap(context.applicationContext)

                if (session != null) {
                    val now = System.currentTimeMillis()
                    val elapsedMs = (now - session.startTime).coerceAtLeast(0)
                    val totalMs = session.targetHours * 3_600_000L
                    val pct = if (totalMs > 0)
                        ((elapsedMs * 100) / totalMs).toInt().coerceIn(0, 100) else 0

                    val h = elapsedMs / 3_600_000L
                    val m = (elapsedMs % 3_600_000L) / 60_000L
                    val s = (elapsedMs % 60_000L) / 1000L
                    val timer = String.format("%02d:%02d:%02d", h, m, s)

                    val phase = when {
                        h < 4  -> locCtx.getString(R.string.wf_phase_digest)
                        h < 8  -> locCtx.getString(R.string.wf_phase_sugar)
                        h < 12 -> locCtx.getString(R.string.wf_phase_fat)
                        h < 16 -> locCtx.getString(R.string.wf_phase_ketosis)
                        h < 24 -> locCtx.getString(R.string.wf_phase_autophagy)
                        else   -> locCtx.getString(R.string.wf_phase_deep)
                    }

                    views.setTextViewText(R.id.wfTitle, "⏱️ ${session.protocolName}")
                    views.setTextViewText(R.id.wfTimer, timer)
                    views.setTextViewText(R.id.wfPhase, "$phase · $pct%")
                    views.setProgressBar(R.id.wfProgress, 100, pct, false)
                } else {
                    views.setTextViewText(R.id.wfTitle, locCtx.getString(R.string.wf_title_default))
                    views.setTextViewText(R.id.wfTimer, "--:--:--")
                    views.setTextViewText(R.id.wfPhase, locCtx.getString(R.string.wf_no_session))
                    views.setProgressBar(R.id.wfProgress, 100, 0, false)
                }

                // Тап по виджету открыть приложение
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pending = PendingIntent.getActivity(
                    context, 100, openIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.wfTimer, pending)
                views.setOnClickPendingIntent(R.id.wfTitle, pending)

                mgr.updateAppWidget(widgetId, views)
            }
        }
    }
}
