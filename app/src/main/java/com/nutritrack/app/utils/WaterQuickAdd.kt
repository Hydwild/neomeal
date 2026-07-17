package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.data.db.AppDatabase
import com.nutritrack.app.data.db.entity.WaterEntryEntity

/**
 * Быстрое добавление воды из системных поверхностей (Quick Settings плитка).
 * Пишет в БД активного профиля, дублирует в Health Connect (если включено)
 * и обновляет виджет. Возвращает итог за день.
 */
object WaterQuickAdd {

    suspend fun add(context: Context, ml: Int): Int {
        val app = context.applicationContext
        val db = AppDatabase.getInstance(app)
        val profileId = db.profileDao().getActiveProfileSync()?.id ?: 1L
        val entry = WaterEntryEntity(
            amountMl = ml,
            date = DateUtils.today(),
            profileId = profileId,
            timestamp = System.currentTimeMillis()
        )
        db.waterDao().insertWater(entry)
        HealthConnectWriter.writeWater(app, entry)
        com.nutritrack.app.widget.MainWidgetProvider.updateAll(app)
        return db.waterDao().getTotalWaterSync(DateUtils.today(), profileId)
    }
}
