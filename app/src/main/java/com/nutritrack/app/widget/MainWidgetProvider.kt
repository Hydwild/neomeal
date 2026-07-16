package com.nutritrack.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nutritrack.app.MainActivity
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.WaterEntryEntity
import com.nutritrack.app.utils.CalorieCalculator
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Виджет на главный экран: показывает калории и воду за сегодня,
 * кнопка быстрого добавления воды (+250мл) и кнопка открытия приложения.
 */
class MainWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_ADD_WATER = "com.nutritrack.app.widget.ADD_WATER"
        const val EXTRA_WATER_AMOUNT = "water_amount"
        private const val WATER_AMOUNT = 250

 /** Обновить все экземпляры виджета. */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MainWidgetProvider::class.java))
            ids.forEach { renderWidget(context, mgr, it) }
        }

        private fun renderWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val db    = (context.applicationContext as NutriApp).database
                val today = DateUtils.today()
                val profile = db.profileDao().getActiveProfileSync()
                val profileId = profile?.id ?: 1L

                // Калории за сегодня
                val entries = db.diaryDao().getEntriesForDateSync(today, profileId)
                val cal     = entries.sumOf { it.calories.toDouble() }.toInt()
                val calGoal = profile?.let { CalorieCalculator.calculateDailyGoal(it) } ?: 2000

                // Вода за сегодня
                val water     = db.waterDao().getTotalWaterSync(today, profileId)
                val waterGoal = profile?.let { CalorieCalculator.calculateWaterGoal(it) } ?: 2000

                val calPct   = if (calGoal > 0) (cal * 100 / calGoal).coerceIn(0, 100) else 0
                val waterPct = if (waterGoal > 0) (water * 100 / waterGoal).coerceIn(0, 100) else 0

                withContext(Dispatchers.Main) {
                    // Локаль: виджет должен следовать выбранному языку приложения
                    val locCtx = com.nutritrack.app.utils.LocaleHelper.wrap(context.applicationContext)
                    val views = RemoteViews(context.packageName, R.layout.widget_main).apply {
                        WidgetTheme.apply(context, this)
                        setTextViewText(R.id.widgetCalories, locCtx.getString(R.string.widget_cal_fmt, cal, calGoal))
                        setProgressBar(R.id.widgetCalProgress, 100, calPct, false)
                        setTextViewText(R.id.widgetWater, locCtx.getString(R.string.widget_water_fmt, water, waterGoal))
                        setProgressBar(R.id.widgetWaterProgress, 100, waterPct, false)

                        // Кнопка "+250"
                        val waterIntent = Intent(context, MainWidgetProvider::class.java).apply {
                            action = ACTION_ADD_WATER
                            putExtra(EXTRA_WATER_AMOUNT, 250)
                        }
                        val waterPending = PendingIntent.getBroadcast(
                            context, 0, waterIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        setOnClickPendingIntent(R.id.btnAddWater, waterPending)

                        // Кнопка "+500"
                        val water500Intent = Intent(context, MainWidgetProvider::class.java).apply {
                            action = ACTION_ADD_WATER
                            putExtra(EXTRA_WATER_AMOUNT, 500)
                        }
                        val water500Pending = PendingIntent.getBroadcast(
                            context, 2, water500Intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        setOnClickPendingIntent(R.id.btnAddWater500, water500Pending)

                        // Кнопка "Еда" открыть приложение
                        val openIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val openPending = PendingIntent.getActivity(
                            context, 1, openIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        setOnClickPendingIntent(R.id.btnOpenFood, openPending)
                    }

                    // Питомец: поза зависит от прогресса дня
                    try {
                        val prefs = com.nutritrack.app.utils.PrefsManager(context)
                        val petId = prefs.selectedPet
                        if (prefs.petsEnabled && petId.isNotBlank()) {
                            val storage = com.nutritrack.app.pets.PetStorage(context)
                            if (storage.isInstalled(petId)) {
                                // выбор позы: обе нормы — happy; есть вода — drink; иначе idle
                                val action = when {
                                    calPct >= 100 && waterPct >= 100 -> "happy"
                                    waterPct >= 100 -> "happy"
                                    water > 0 -> "drink"
                                    else -> "idle"
                                }
                                val chosen = if (storage.hasAction(petId, action)) action else "idle"
                                val frame = storage.frames(petId, chosen).firstOrNull()
                                if (frame != null && frame.exists()) {
                                    // Уменьшаем кадр (виджет-иконка маленькая, большой bitmap
                                    // может превысить лимит RemoteViews и уронить виджет).
                                    val opts = android.graphics.BitmapFactory.Options().apply {
                                        inSampleSize = 2
                                    }
                                    val bmp = android.graphics.BitmapFactory.decodeFile(frame.absolutePath, opts)
                                    if (bmp != null) {
                                        views.setImageViewBitmap(R.id.widgetPet, bmp)
                                        views.setViewVisibility(R.id.widgetPet, android.view.View.VISIBLE)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) { /* нет питомца — виджет без него */ }

                    mgr.updateAppWidget(widgetId, views)
                }
            }
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderWidget(context, mgr, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ADD_WATER) {
            val amount = intent.getIntExtra(EXTRA_WATER_AMOUNT, WATER_AMOUNT)
            CoroutineScope(Dispatchers.IO).launch {
                val db = (context.applicationContext as NutriApp).database
                val profile = db.profileDao().getActiveProfileSync()
                val profileId = profile?.id ?: 1L
                db.waterDao().insert(
                    WaterEntryEntity(
                        amountMl = amount,
                        date = DateUtils.today(),
                        profileId = profileId
                    )
                )
                withContext(Dispatchers.Main) { updateAll(context) }
            }
        }
    }
}
