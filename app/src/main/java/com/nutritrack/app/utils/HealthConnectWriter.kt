package com.nutritrack.app.utils

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.nutritrack.app.data.db.entity.DiaryEntryEntity
import com.nutritrack.app.data.db.entity.WaterEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Запись питания, воды и веса в Health Connect (опция в настройках,
 * по умолчанию выключена). Все методы — «тихие»: при выключенной опции,
 * недоступном HC или отсутствии прав просто ничего не делают.
 *
 * Дедупликация через clientRecordId (стабильный ключ из profileId и
 * timestamp записи) — повторная запись не плодит дубли, удаление записи
 * в приложении удаляет её и в Health Connect.
 *
 * Импортированный ИЗ Health Connect вес назад не пишется (см. ProfileViewModel).
 */
object HealthConnectWriter {

    val WRITE_PERMISSIONS = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    private fun client(context: Context): HealthConnectClient? = try {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
            HealthConnectClient.getOrCreate(context.applicationContext)
        else null
    } catch (_: Exception) { null }

    suspend fun hasWritePermissions(context: Context): Boolean {
        val c = client(context) ?: return false
        return try {
            c.permissionController.getGrantedPermissions().containsAll(WRITE_PERMISSIONS)
        } catch (_: Exception) { false }
    }

 /** Опция включена, HC доступен и права выданы. */
    private suspend fun ready(context: Context): HealthConnectClient? {
        if (!PrefsManager(context).hcWriteEnabled) return null
        val c = client(context) ?: return null
        return try {
            if (c.permissionController.getGrantedPermissions().containsAll(WRITE_PERMISSIONS)) c
            else null
        } catch (_: Exception) { null }
    }

    // Питание

    suspend fun writeDiaryEntry(context: Context, entry: DiaryEntryEntity) {
        val c = ready(context) ?: return
        try {
            val start = Instant.ofEpochMilli(entry.timestamp)
            val zone = ZoneId.systemDefault().rules.getOffset(start)
            c.insertRecords(listOf(NutritionRecord(
                startTime = start,
                startZoneOffset = zone,
                endTime = start.plusSeconds(60),
                endZoneOffset = zone,
                name = entry.productName,
                energy = Energy.kilocalories(entry.calories.toDouble()),
                protein = Mass.grams(entry.protein.toDouble()),
                totalFat = Mass.grams(entry.fat.toDouble()),
                totalCarbohydrate = Mass.grams(entry.carbs.toDouble()),
                dietaryFiber = entry.fiber?.let { Mass.grams(it.toDouble()) },
                sugar = entry.sugar?.let { Mass.grams(it.toDouble()) },
                mealType = when (entry.mealType) {
                    "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
                    "lunch"     -> MealType.MEAL_TYPE_LUNCH
                    "dinner"    -> MealType.MEAL_TYPE_DINNER
                    "snack"     -> MealType.MEAL_TYPE_SNACK
                    else        -> MealType.MEAL_TYPE_UNKNOWN
                },
                // Версия = времени вызова: редактирование записи (те же id)
                // обновляет её в HC, а не игнорируется как «не новее»
                metadata = Metadata(
                    clientRecordId = diaryId(entry),
                    clientRecordVersion = System.currentTimeMillis()
                )
            )))
        } catch (e: Exception) { AppLog.e("HC write nutrition failed", e) }
    }

    suspend fun deleteDiaryEntry(context: Context, entry: DiaryEntryEntity) {
        val c = ready(context) ?: return
        try {
            c.deleteRecords(NutritionRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(diaryId(entry)))
        } catch (e: Exception) { AppLog.e("HC delete nutrition failed", e) }
    }

    // Вода

    suspend fun writeWater(context: Context, entry: WaterEntryEntity) {
        val c = ready(context) ?: return
        try {
            val start = Instant.ofEpochMilli(entry.timestamp)
            val zone = ZoneId.systemDefault().rules.getOffset(start)
            c.insertRecords(listOf(HydrationRecord(
                startTime = start,
                startZoneOffset = zone,
                endTime = start.plusSeconds(60),
                endZoneOffset = zone,
                volume = Volume.milliliters(entry.amountMl.toDouble()),
                metadata = Metadata(
                    clientRecordId = waterId(entry),
                    clientRecordVersion = System.currentTimeMillis()
                )
            )))
        } catch (e: Exception) { AppLog.e("HC write hydration failed", e) }
    }

    suspend fun deleteWater(context: Context, entry: WaterEntryEntity) {
        val c = ready(context) ?: return
        try {
            c.deleteRecords(HydrationRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf(waterId(entry)))
        } catch (e: Exception) { AppLog.e("HC delete hydration failed", e) }
    }

    // Вес (один в день, ручной ввод — не эхо импорта из HC)

    suspend fun writeWeight(context: Context, kg: Float, date: String, profileId: Long) {
        val c = ready(context) ?: return
        try {
            val time = try {
                LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
            } catch (_: Exception) { Instant.now() }
            val zone = ZoneId.systemDefault().rules.getOffset(time)
            c.insertRecords(listOf(WeightRecord(
                time = time,
                zoneOffset = zone,
                weight = Mass.kilograms(kg.toDouble()),
                // версия = времени вызова: повторный ввод за день обновляет запись
                metadata = Metadata(
                    clientRecordId = "weight_${profileId}_$date",
                    clientRecordVersion = System.currentTimeMillis()
                )
            )))
        } catch (e: Exception) { AppLog.e("HC write weight failed", e) }
    }

    private fun diaryId(e: DiaryEntryEntity) = "diary_${e.profileId}_${e.timestamp}"
    private fun waterId(e: WaterEntryEntity) = "water_${e.profileId}_${e.timestamp}"
}
