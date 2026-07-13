package com.nutritrack.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Планировщик «умной» проверки воды. Ставит ОДИН точный будильник в день
 * (по умолчанию в 15:00 — после обеда, типичное окно), при срабатывании
 * ReminderReceiver анализирует привычку и решает, показывать ли напоминание.
 * Будильник разовый — переустанавливается на следующий день.
 */
object SmartWaterScheduler {

    const val REQUEST = 950000
    const val ACTION  = "com.nutritrack.app.SMART_WATER_CHECK"
    private const val CHECK_HOUR = 15
    private const val CHECK_MIN  = 0

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, CHECK_HOUR)
            set(Calendar.MINUTE, CHECK_MIN)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION
            putExtra(ReminderReceiver.EXTRA_TYPE, "smart_water")
        }
        val pending = PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        try {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION }
        val pending = PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pending != null) am.cancel(pending)
    }
}
