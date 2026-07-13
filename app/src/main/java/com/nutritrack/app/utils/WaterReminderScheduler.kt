package com.nutritrack.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Планировщик напоминаний о воде на ТОЧНОЕ время.
 *
 * Использует setExactAndAllowWhileIdle — пробивает Doze-режим и срабатывает
 * в точное время (в отличие от setRepeating, который мог опаздывать на часы).
 * Будильник разовый; ReminderReceiver переустанавливает его на следующий день.
 */
object WaterReminderScheduler {

    const val BASE_REQUEST = 900000
    const val EXTRA_HOUR = "extra_hour"
    const val EXTRA_MINUTE = "extra_minute"
    const val EXTRA_IDX = "extra_idx"

    fun schedule(context: Context, intervalMin: Int, startTime: String, endTime: String) {
        cancelAll(context)
        val (sh, sm) = parse(startTime)
        val (eh, em) = parse(endTime)
        val startMinutes = sh * 60 + sm
        val endMinutes   = eh * 60 + em
        if (endMinutes <= startMinutes || intervalMin <= 0) return

        var t = startMinutes
        var idx = 0
        while (t <= endMinutes && idx < 50) {
            scheduleOne(context, t / 60, t % 60, idx)
            t += intervalMin
            idx++
        }
    }

 /** Поставить один точный будильник на ближайшее наступление hour:minute. */
    fun scheduleOne(context: Context, hour: Int, minute: Int, idx: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.nutritrack.app.WATER_FIRE_$idx"   // уникальный action на слот
            putExtra(ReminderReceiver.EXTRA_TYPE, "water")
            putExtra(ReminderReceiver.EXTRA_TITLE, "💧 Время попить воды")
            putExtra(ReminderReceiver.EXTRA_MSG, "Не забудь про водный баланс!")
            // данные для переустановки на следующий день
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_IDX, idx)
        }
        val pending = PendingIntent.getBroadcast(
            context, BASE_REQUEST + idx, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Проверяем разрешение на точные будильники (Android 12+)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true

        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            } else {
                // нет разрешения на точные — ставим неточный (лучше чем ничего)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (idx in 0 until 50) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.nutritrack.app.WATER_FIRE_$idx"
                putExtra(ReminderReceiver.EXTRA_TYPE, "water")
            }
            val pending = PendingIntent.getBroadcast(
                context, BASE_REQUEST + idx, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pending != null) am.cancel(pending)
        }
    }

    fun countPerDay(intervalMin: Int, startTime: String, endTime: String): Int {
        val (sh, sm) = parse(startTime)
        val (eh, em) = parse(endTime)
        val start = sh * 60 + sm
        val end   = eh * 60 + em
        if (end <= start || intervalMin <= 0) return 0
        return (end - start) / intervalMin + 1
    }

    private fun parse(t: String): Pair<Int, Int> {
        val p = t.split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 9) to (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
