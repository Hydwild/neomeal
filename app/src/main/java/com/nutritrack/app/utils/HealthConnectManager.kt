package com.nutritrack.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nutritrack.app.data.db.entity.FitnessDataEntity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.aggregate.AggregationResult
import java.time.LocalDate
import java.time.ZoneId

/**
 * Чтение данных из Health Connect: шаги, активные калории, дистанция, пульс.
 * Источники (Mi Fitness, Samsung Health, Google Fit) пишут туда данные,
 * мы их читаем.
 */
class HealthConnectManager(private val context: Context) {

    companion object {
        const val HC_PACKAGE = "com.google.android.apps.healthdata"

        // Разрешения, которые запрашиваем (только чтение)
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class)
        )
    }

    private val client: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
                HealthConnectClient.getOrCreate(context)
            else null
        } catch (e: Exception) { null }
    }

 /** Доступен ли Health Connect на устройстве. */
    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

 /** Установлен ли, но требует обновления. */
    fun needsUpdate(): Boolean =
        HealthConnectClient.getSdkStatus(context) ==
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

 /** Есть ли уже выданные разрешения. */
    suspend fun hasPermissions(): Boolean {
        val c = client ?: return false
        return try {
            c.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
        } catch (e: Exception) { false }
    }

 /** Контракт для запроса разрешений (используется в Activity/Fragment). */
    fun permissionContract() =
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()

 /**
 * Читает данные за день. Возвращает null, если HC недоступен/нет прав.
 */
    suspend fun readDayData(date: LocalDate, profileId: Long): FitnessDataEntity? {
        val c = client ?: return null
        return try {
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val range = TimeRangeFilter.between(start, end)

            // Агрегируем шаги, калории, дистанцию
            val agg: AggregationResult = c.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL
                    ),
                    timeRangeFilter = range
                )
            )

            val steps = agg[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
            val calories = agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                ?.inKilocalories?.toFloat() ?: 0f
            val distance = agg[DistanceRecord.DISTANCE_TOTAL]
                ?.inMeters?.toFloat() ?: 0f

            // Пульс — читаем записи и считаем средний/мин/макс
            var hrAvg = 0; var hrMin = 0; var hrMax = 0
            try {
                val hrRecords = c.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = range
                    )
                ).records
                val bpm = hrRecords.flatMap { it.samples }.map { it.beatsPerMinute.toInt() }
                if (bpm.isNotEmpty()) {
                    hrAvg = bpm.average().toInt()
                    hrMin = bpm.min()
                    hrMax = bpm.max()
                }
            } catch (e: Exception) { /* пульс не критичен */ }

            FitnessDataEntity(
                date = date.toString(),
                profileId = profileId,
                steps = steps,
                caloriesBurned = calories,
                heartRateAvg = hrAvg,
                heartRateMax = hrMax,
                heartRateMin = hrMin,
                activeMinutes = 0,
                distanceMeters = distance,
                source = "health_connect"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

 /** Открыть Health Connect (или страницу установки в Play). */
    fun openHealthConnectApp() {
        try {
            // Пытаемся открыть настройки HC
            context.startActivity(
                Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )
        } catch (e: Exception) {
            // Фолбэк — установка из Play
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=$HC_PACKAGE")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            } catch (_: Exception) { }
        }
    }

    // Совместимость со старым FitnessFragment

    /** Запись веса из Health Connect: дата (yyyy-MM-dd) → кг. */
    data class HcWeight(val date: String, val kg: Float)

    /**
     * Читает историю веса из Health Connect за последние [days] дней.
     * Источник (Zepp Life / Mi Fitness) пишет вес в HC, мы его читаем.
     * Возвращает по одной записи на день (последняя за день), отсортировано.
     */
    suspend fun readWeightHistory(days: Int = 30): List<HcWeight> {
        val c = client ?: return emptyList()
        return try {
            val end = java.time.Instant.now()
            val startInstant = end.minus(java.time.Duration.ofDays(days.toLong()))
            val records = c.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInstant, end)
                )
            ).records
            // группируем по локальной дате, берём последнюю запись дня
            records
                .groupBy {
                    it.time.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                }
                .mapNotNull { (date, list) ->
                    val last = list.maxByOrNull { it.time } ?: return@mapNotNull null
                    val kg = last.weight.inKilograms.toFloat()
                    if (kg in 20f..400f) HcWeight(date, kg) else null
                }
                .sortedBy { it.date }
        } catch (_: Exception) { emptyList() }
    }

 /** Синхронная проверка прав (блокирующая обёртка). */
    fun hasRequiredPermissions(): Boolean =
        kotlinx.coroutines.runBlocking { hasPermissions() }

 /** Массив health-разрешений как строки. */
    fun getRequiredPermissionsArray(): Array<String> = PERMISSIONS.toTypedArray()

 /** Безопасное чтение данных за день (профиль по умолчанию = 1). */
    suspend fun readDayDataSafe(date: LocalDate, profileId: Long = 1L): FitnessDataEntity? =
        readDayData(date, profileId)

 /**
 * Читает последнюю запись веса из Health Connect (туда вес пишут весы Xiaomi через Mi Fitness).
 * Возвращает Pair(вес в кг, дата записи) или null, если данных нет / нет доступа.
 */
    suspend fun readLatestWeight(): Pair<Float, LocalDate>? {
        val c = client ?: return null
        return try {
            val zone = ZoneId.systemDefault()
            // Берём диапазон за последний год — этого достаточно для последнего замера
            val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant()
            val start = LocalDate.now().minusDays(365).atStartOfDay(zone).toInstant()
            val records = c.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records
            val last = records.maxByOrNull { it.time } ?: return null
            val kg = last.weight.inKilograms.toFloat()
            val date = last.time.atZone(zone).toLocalDate()
            if (kg <= 0f) null else kg to date
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
