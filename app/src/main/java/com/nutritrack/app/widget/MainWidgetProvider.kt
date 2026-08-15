package com.nutritrack.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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

     /** PendingIntent кнопки добавления воды. [code] — уникальный requestCode. */
        private fun waterPendingFor(context: Context, amount: Int, code: Int): PendingIntent {
            val intent = Intent(context, MainWidgetProvider::class.java).apply {
                action = ACTION_ADD_WATER
                putExtra(EXTRA_WATER_AMOUNT, amount)
            }
            return PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
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
                    // Кадр питомца готовим один раз на оба варианта разметки
                    val petBitmap: android.graphics.Bitmap? = try {
                        val prefs = com.nutritrack.app.utils.PrefsManager(context)
                        val petId = prefs.selectedPet
                        if (prefs.petsEnabled && petId.isNotBlank()) {
                            val storage = com.nutritrack.app.pets.PetStorage(context)
                            if (storage.isInstalled(petId)) {
                                val action = when {
                                    calPct >= 100 && waterPct >= 100 -> "happy"
                                    waterPct >= 100 -> "happy"
                                    water > 0 -> "drink"
                                    else -> "idle"
                                }
                                val chosen = if (storage.hasAction(petId, action)) action else "idle"
                                val frame = storage.frames(petId, chosen).firstOrNull()
                                if (frame != null && frame.exists()) {
                                    // Уменьшаем кадр: большой bitmap может превысить лимит
                                    // RemoteViews и уронить виджет.
                                    val opts = android.graphics.BitmapFactory.Options().apply {
                                        inSampleSize = 2
                                    }
                                    android.graphics.BitmapFactory.decodeFile(frame.absolutePath, opts)
                                } else null
                            } else null
                        } else null
                    } catch (_: Exception) { null }

                    // Открытие приложения — по тапу на виджет и на кнопку «Еда»
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openPending = PendingIntent.getActivity(
                        context, 1, openIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                 /**
                 * Собирает виджет для конкретной разметки.
                 * [withButtons] — в компактном виде кнопок нет: вместе с ними
                 * содержимое не помещалось в одну ячейку и обрезалось.
                 */
                    fun build(layoutId: Int, withButtons: Boolean) =
                        RemoteViews(context.packageName, layoutId).apply {
                            WidgetTheme.apply(context, this)
                            setTextViewText(R.id.widgetCalories,
                                locCtx.getString(R.string.widget_cal_fmt, cal, calGoal))
                            setProgressBar(R.id.widgetCalProgress, 100, calPct, false)
                            setTextViewText(R.id.widgetWater,
                                locCtx.getString(R.string.widget_water_fmt, water, waterGoal))
                            setProgressBar(R.id.widgetWaterProgress, 100, waterPct, false)
                            setOnClickPendingIntent(R.id.widgetRoot, openPending)
                            if (petBitmap != null) {
                                setImageViewBitmap(R.id.widgetPet, petBitmap)
                                setViewVisibility(R.id.widgetPet, android.view.View.VISIBLE)
                            }
                            if (withButtons) {
                                setOnClickPendingIntent(R.id.btnAddWater, waterPendingFor(context, 250, 0))
                                setOnClickPendingIntent(R.id.btnAddWater500, waterPendingFor(context, 500, 2))
                                setOnClickPendingIntent(R.id.btnOpenFood, openPending)
                            }
                        }

                    val full = build(R.layout.widget_main, withButtons = true)
                    val small = build(R.layout.widget_main_small, withButtons = false)

                    // Android 12+ сам выбирает разметку по фактическому размеру,
                    // на старых версиях показываем полную.
                    val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        RemoteViews(
                            mapOf(
                                android.util.SizeF(140f, 70f) to small,
                                android.util.SizeF(180f, 115f) to full
                            )
                        )
                    } else full


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
