package com.nutritrack.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
/**
 * Планировщик напоминаний через AlarmManager.
 * Поддерживает ежедневные напоминания и отложенный повтор (snooze).
 */
object ReminderScheduler {

 /**
 * Запланировать ежедневное напоминание на указанное время.
 * @param type тип: "water" / "breakfast" / "lunch" / "dinner" / "snack"
 * @param time время в формате "HH:mm"
 */
    fun schedule(
        context: Context,
        type: String,
        time: String,
        title: String,
        message: String
    ) {
        val parts  = time.split(":")
        val hour   = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Если время уже прошло сегодня — переносим на завтра
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val pending = buildPendingIntent(context, type, title, message, hour, minute)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Точный будильник + пробивает Doze (вместо неточного setRepeating).
        // Разовый; ReminderReceiver переустановит на следующий день.
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            am.canScheduleExactAlarms() else true
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
        }
    }

 /**
 * ТЕСТ: запланировать уведомление через [seconds] секунд.
 * Для проверки, что будильники вообще срабатывают на устройстве.
 */
    fun scheduleTest(context: Context, seconds: Int = 10) {
        val triggerAt = System.currentTimeMillis() + seconds * 1000L
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TYPE, "meal")
            putExtra(ReminderReceiver.EXTRA_TITLE, context.getString(com.nutritrack.app.R.string.rs_test_title))
            putExtra(ReminderReceiver.EXTRA_MSG, context.getString(com.nutritrack.app.R.string.rs_test_msg))
        }
        val pending = PendingIntent.getBroadcast(
            context, 777001, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            am.canScheduleExactAlarms() else true
        try {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

 /**
 * Может ли приложение ставить точные будильники (Android 12+).
 */
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            am.canScheduleExactAlarms() else true
    }

 /**
 * Отложенный повтор (snooze) — разовое напоминание через N минут.
 */
    fun scheduleSnooze(
        context: Context,
        type: String,
        minutes: Int,
        title: String,
        message: String
    ) {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val pending   = buildPendingIntent(context, type, title, message, snooze = true)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

 /**
 * Отменить напоминание.
 */
    fun cancel(context: Context, type: String) {
        val pending = buildPendingIntent(context, type, "", "")
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending)
    }

    fun scheduleAll(context: Context) {
        val prefs = PrefsManager(context)

        if (prefs.breakfastEnabled) {
            schedule(context, "breakfast", prefs.breakfastTime, context.getString(com.nutritrack.app.R.string.rs_breakfast), context.getString(com.nutritrack.app.R.string.rs_breakfast_msg))
        }
        if (prefs.lunchEnabled) {
            schedule(context, "lunch", prefs.lunchTime, context.getString(com.nutritrack.app.R.string.rs_lunch), context.getString(com.nutritrack.app.R.string.rs_lunch_msg))
        }
        if (prefs.dinnerEnabled) {
            schedule(context, "dinner", prefs.dinnerTime, context.getString(com.nutritrack.app.R.string.rs_dinner), context.getString(com.nutritrack.app.R.string.rs_dinner_msg))
        }
        if (prefs.snackEnabled) {
            schedule(context, "snack", prefs.snackTime, context.getString(com.nutritrack.app.R.string.rs_snack), context.getString(com.nutritrack.app.R.string.rs_snack_msg))
        }
        if (prefs.getBool("water_reminder_enabled", false)) {
            val waterTime = prefs.getString("water_reminder_time", "12:00")
            schedule(context, "water", waterTime, context.getString(com.nutritrack.app.R.string.rr_water_title), context.getString(com.nutritrack.app.R.string.rr_water_msg))
        }
    }

    // Создание PendingIntent

 /**
 * Еженедельное напоминание (например, замеры тела).
 * Срабатывает в указанный день недели и время; receiver переустановит на +7 дней.
 * dayOfWeek: Calendar.MONDAY..SUNDAY (по умолчанию воскресенье).
 */
    fun scheduleWeekly(
        context: Context,
        type: String,
        time: String,
        title: String,
        message: String,
        dayOfWeek: Int = Calendar.SUNDAY
    ) {
        val parts  = time.split(":")
        val hour   = parts.getOrNull(0)?.toIntOrNull() ?: 10
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            // если момент уже прошёл на этой неделе — на следующую
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TYPE, type)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MSG, message)
            putExtra("weekly", true)
            putExtra("weekly_hour", hour)
            putExtra("weekly_minute", minute)
            putExtra("weekly_dow", dayOfWeek)
        }
        val pending = PendingIntent.getBroadcast(
            context, type.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            am.canScheduleExactAlarms() else true
        try {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
        }
    }

    private fun buildPendingIntent(
        context: Context,
        type: String,
        title: String,
        message: String,
        hour: Int = -1,
        minute: Int = -1,
        snooze: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            // action не задаём — это будет "срабатывание будильника"
            putExtra(ReminderReceiver.EXTRA_TYPE, type)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MSG, message)
            // данные для переустановки на следующий день
            putExtra("meal_hour", hour)
            putExtra("meal_minute", minute)
        }
        // Разный requestCode для обычного и snooze, чтобы не перетирали друг друга
        val requestCode = type.hashCode() + if (snooze) 100000 else 0
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
