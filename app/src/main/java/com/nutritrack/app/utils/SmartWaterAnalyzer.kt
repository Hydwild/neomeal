package com.nutritrack.app.utils

import com.nutritrack.app.data.db.entity.WaterEntryEntity
import java.util.Calendar

/**
 * Локальный анализ привычного времени питья воды (без сети).
 * Находит «любимый час» по истории, чтобы умное напоминание срабатывало
 * тогда, когда пользователь обычно пьёт, а сегодня ещё не пил.
 */
object SmartWaterAnalyzer {

    data class Habit(val hour: Int, val confidence: Int) // hour 0..23, confidence = число записей в этом часе

 /**
 * Любимый час питья по записям. null — если данных мало для вывода.
 * minSamples — минимум записей в часе, чтобы считать привычкой.
 */
    fun favoriteHour(entries: List<WaterEntryEntity>, minSamples: Int = 3): Habit? {
        if (entries.isEmpty()) return null
        val byHour = IntArray(24)
        val cal = Calendar.getInstance()
        for (e in entries) {
            cal.timeInMillis = e.timestamp
            byHour[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        var bestHour = -1
        var bestCount = 0
        for (h in 0 until 24) {
            if (byHour[h] > bestCount) { bestCount = byHour[h]; bestHour = h }
        }
        if (bestHour < 0 || bestCount < minSamples) return null
        return Habit(bestHour, bestCount)
    }

 /** Пил ли пользователь сегодня в окне [hour-1 .. hour+1]. */
    fun drankAroundHour(todayEntries: List<WaterEntryEntity>, hour: Int): Boolean {
        if (todayEntries.isEmpty()) return false
        val cal = Calendar.getInstance()
        return todayEntries.any {
            cal.timeInMillis = it.timestamp
            val h = cal.get(Calendar.HOUR_OF_DAY)
            h in (hour - 1)..(hour + 1)
        }
    }
}
