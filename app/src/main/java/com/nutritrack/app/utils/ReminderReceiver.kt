package com.nutritrack.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nutritrack.app.MainActivity
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.WaterEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Получает срабатывания будильника напоминаний и показывает уведомление
 * с интерактивными кнопками действий.
 *
 * Типы уведомлений:
 * - "water" кнопки [ Выпил] [⏰ Позже]
 * - "breakfast"/"lunch"/"dinner"/"snack" кнопки [ Поел] [⏰ Позже]
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "nutritrack_reminders"

        // Extras
        const val EXTRA_TYPE  = "reminder_type"
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_MSG   = "reminder_message"
        const val EXTRA_NOTIF_ID = "notif_id"

        // Actions (нажатие кнопок в уведомлении)
        const val ACTION_WATER_YES   = "com.nutritrack.app.ACTION_WATER_YES"
        const val ACTION_WATER_LATER = "com.nutritrack.app.ACTION_WATER_LATER"
        const val ACTION_MEAL_YES    = "com.nutritrack.app.ACTION_MEAL_YES"
        const val ACTION_MEAL_LATER  = "com.nutritrack.app.ACTION_MEAL_LATER"

        const val WATER_AMOUNT_ML = 250  // сколько мл добавляем при "Выпил"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Нажата кнопка " Выпил"
            ACTION_WATER_YES -> {
                addWater(context, WATER_AMOUNT_ML)
                cancelNotification(context, intent.getIntExtra(EXTRA_NOTIF_ID, 0))
                showToast(context, "💧 +$WATER_AMOUNT_ML мл воды записано!")
            }

            // Нажата кнопка "⏰ Позже" (вода)
            ACTION_WATER_LATER -> {
                cancelNotification(context, intent.getIntExtra(EXTRA_NOTIF_ID, 0))
                // Повторить через 30 минут
                ReminderScheduler.scheduleSnooze(
                    context, "water", 30,
                    "💧 Время попить воды", "Не забудь про водный баланс!"
                )
                showToast(context, "⏰ Напомним через 30 минут")
            }

            // Нажата кнопка " Поел"
            ACTION_MEAL_YES -> {
                val type = intent.getStringExtra(EXTRA_TYPE) ?: "meal"
                confirmMeal(context, type)
                cancelNotification(context, intent.getIntExtra(EXTRA_NOTIF_ID, 0))
                showToast(context, "✅ Приём пищи отмечен!")
            }

            // Нажата кнопка "⏰ Позже" (еда)
            ACTION_MEAL_LATER -> {
                val type  = intent.getStringExtra(EXTRA_TYPE) ?: "meal"
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Приём пищи"
                val msg   = intent.getStringExtra(EXTRA_MSG) ?: "Не забудь поесть!"
                cancelNotification(context, intent.getIntExtra(EXTRA_NOTIF_ID, 0))
                ReminderScheduler.scheduleSnooze(context, type, 20, title, msg)
                showToast(context, "⏰ Напомним через 20 минут")
            }

            // Умная проверка воды (по привычному времени)
            SmartWaterScheduler.ACTION -> {
                handleSmartWaterCheck(context)
            }

            // Срабатывание будильника показываем уведомление
            else -> {
                val type  = intent.getStringExtra(EXTRA_TYPE)  ?: "meal"
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Напоминание"
                val msg   = intent.getStringExtra(EXTRA_MSG)   ?: ""
                showNotification(context, type, title, msg)

                // Еженедельное напоминание (замеры) — переустановить на +7 дней
                if (intent.getBooleanExtra("weekly", false)) {
                    val hour = intent.getIntExtra("weekly_hour", 10)
                    val min  = intent.getIntExtra("weekly_minute", 0)
                    val dow  = intent.getIntExtra("weekly_dow", java.util.Calendar.SUNDAY)
                    ReminderScheduler.scheduleWeekly(
                        context, type, String.format("%02d:%02d", hour, min), title, msg, dow
                    )
                    return
                }

                // Для воды: переустанавливаем точный будильник на следующий день
                // (будильник разовый, поэтому повтор настраиваем вручную)
                if (type == "water") {
                    val hour = intent.getIntExtra(WaterReminderScheduler.EXTRA_HOUR, -1)
                    val min  = intent.getIntExtra(WaterReminderScheduler.EXTRA_MINUTE, -1)
                    val idx  = intent.getIntExtra(WaterReminderScheduler.EXTRA_IDX, -1)
                    if (hour >= 0 && min >= 0 && idx >= 0) {
                        WaterReminderScheduler.scheduleOne(context, hour, min, idx)
                    }
                } else {
                    // Для приёмов пищи (breakfast/lunch/dinner/snack) — тоже
                    // переустанавливаем на следующий день
                    val hour = intent.getIntExtra("meal_hour", -1)
                    val min  = intent.getIntExtra("meal_minute", -1)
                    if (hour >= 0 && min >= 0) {
                        val timeStr = String.format("%02d:%02d", hour, min)
                        ReminderScheduler.schedule(context, type, timeStr, title, msg)
                    }
                }
            }
        }
    }

    // Показ уведомления с кнопками

    private fun showNotification(context: Context, type: String, title: String, msg: String) {
        createChannel(context)

        val notifId = type.hashCode()

        // Открытие приложения по тапу
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)

        if (type == "water") {
            // Кнопки для воды
            val yesIntent = actionPending(context, ACTION_WATER_YES, type, title, msg, notifId, 1)
            val laterIntent = actionPending(context, ACTION_WATER_LATER, type, title, msg, notifId, 2)

            builder.addAction(0, "💧 Выпил ${WATER_AMOUNT_ML}мл", yesIntent)
            builder.addAction(0, "⏰ Позже", laterIntent)
        } else {
            // Кнопки для приёма пищи
            val yesIntent = actionPending(context, ACTION_MEAL_YES, type, title, msg, notifId, 3)
            val laterIntent = actionPending(context, ACTION_MEAL_LATER, type, title, msg, notifId, 4)

            builder.addAction(0, "✅ Поел", yesIntent)
            builder.addAction(0, "⏰ Позже", laterIntent)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())
    }

    // Создание PendingIntent для кнопки

    private fun actionPending(
        context: Context, action: String, type: String,
        title: String, msg: String, notifId: Int, requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MSG, msg)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getBroadcast(
            context,
            notifId * 10 + requestCode,  // уникальный requestCode
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // Действия с БД

    private fun addWater(context: Context, ml: Int) {
        val app = context.applicationContext as NutriApp
        // goAsync: без него процесс могут убить сразу после onReceive,
        // не дождавшись записи в БД
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db        = app.database
                val profileId = db.profileDao().getActiveProfileSync()?.id ?: 1L
                db.waterDao().insert(
                    WaterEntryEntity(
                        amountMl  = ml,
                        date      = DateUtils.today(),
                        profileId = profileId,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } finally {
                pending.finish()
            }
        }
    }

 /**
 * Анализирует привычное время питья и, если в типичном окне сегодня ещё не
 * пили, показывает персональное напоминание. Затем переустанавливает
 * ежедневную проверку на следующий день.
 */
    private fun handleSmartWaterCheck(context: Context) {
        val prefs = PrefsManager(context)
        // Если умные напоминания выключены — просто молча выходим (но не планируем заново)
        if (!prefs.smartWaterReminder) return

        val pending = goAsync()
        val app = context.applicationContext as NutriApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.database
                val profileId = db.profileDao().getActiveProfileSync()?.id ?: 1L

                // История за 14 дней
                val since = DateUtils.daysAgo(14)
                val history = db.waterDao().getWaterSince(since, profileId)
                val habit = SmartWaterAnalyzer.favoriteHour(history)

                if (habit != null) {
                    val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    // Показываем только если любимое окно уже наступило
                    if (nowHour >= habit.hour) {
                        val today = db.waterDao().getWaterForDateSync(DateUtils.today(), profileId)
                        if (!SmartWaterAnalyzer.drankAroundHour(today, habit.hour)) {
                            val title = context.getString(R.string.smart_water_title)
                            val msg = context.getString(R.string.smart_water_msg_fmt, habit.hour)
                            showNotification(context, "water", title, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Переустановить на следующий день в любом случае
                SmartWaterScheduler.schedule(context)
                pending.finish()
            }
        }
    }

    private fun confirmMeal(context: Context, type: String) {
        // Помечаем приём пищи как выполненный в prefs (для статистики плана питания)
        val prefs = PrefsManager(context)
        val key   = "meal_done_${type}_${DateUtils.today()}"
        prefs.setBool(key, true)
        // Здесь же можно автоматически добавить запланированные продукты в дневник,
        // если они были указаны в плане питания
        val app = context.applicationContext as NutriApp
        // goAsync: дождаться записи в дневник до завершения ресивера
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db        = app.database
                val profileId = db.profileDao().getActiveProfileSync()?.id ?: 1L
                val meal      = db.mealPlanDao().getMealPlanByKey(type, DateUtils.today(), profileId)
                meal?.let { m ->
                    // Получаем продукты приёма пищи и добавляем в дневник
                    val items = db.mealPlanDao().getItemsForMealSync(m.id)
                    items.forEach { item ->
                        db.diaryDao().insert(
                            com.nutritrack.app.data.db.entity.DiaryEntryEntity(
                                productId   = item.productId,
                                productName = item.productName,
                                grams       = item.grams,
                                calories    = item.calories,
                                protein     = item.protein,
                                fat         = item.fat,
                                carbs       = item.carbs,
                                date        = DateUtils.today(),
                                profileId   = profileId,
                                mealType    = type
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }

    // Вспомогательные

    private fun cancelNotification(context: Context, notifId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifId)
    }

    private fun showToast(context: Context, text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Напоминания о приёмах пищи и воде"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
