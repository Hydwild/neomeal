package com.nutritrack.app.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nutritrack.app.data.db.AppDatabase
import com.nutritrack.app.data.db.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Резервное копирование всех данных в один JSON-файл и восстановление из него.
 * Экспорт/импорт идёт через Storage Access Framework (пользователь сам выбирает,
 * куда сохранить / откуда взять файл) — не нужно разрешений на хранилище.
 */
object BackupManager {

    private const val VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

 /** Полный снимок базы. */
    data class BackupData(
        val version: Int = VERSION,
        val exportedAt: String = "",
        val products: List<ProductEntity> = emptyList(),
        val diary: List<DiaryEntryEntity> = emptyList(),
        val water: List<WaterEntryEntity> = emptyList(),
        val weight: List<WeightEntryEntity> = emptyList(),
        val profiles: List<ProfileEntity> = emptyList(),
        val achievements: List<AchievementEntity> = emptyList(),
        val fasting: List<FastingSessionEntity> = emptyList(),
        val mealPlans: List<MealPlanEntity> = emptyList(),
        val mealItems: List<MealItemEntity> = emptyList(),
        val dailyGoals: List<DailyGoalEntity> = emptyList(),
        val fitness: List<FitnessDataEntity> = emptyList(),
        // Настройки приложения
        val settings: Map<String, String> = emptyMap(),
        // Аватары профилей: profileId base64 (jpg)
        val avatars: Map<String, String> = emptyMap(),
        // Замеры тела
        val measurements: List<MeasurementEntity> = emptyList(),
        // Фото прогресса
        val progressPhotos: List<ProgressPhotoEntity> = emptyList()
    )

 /** Имя файла по умолчанию с датой. */
    fun defaultFileName(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
        return "neomeal_backup_$ts.json"
    }

 /** Собрать все данные и записать JSON в выбранный uri. */
    suspend fun export(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val profiles = db.profileDao().dumpAll()

            // Настройки приложения
            val prefs = PrefsManager(context)
            val settings = mapOf(
                "dark_theme" to prefs.isDarkTheme.toString(),
                "language" to prefs.language,
                "circular_calorie_mode" to prefs.getBool("circular_calorie_mode", true).toString()
            )

            // Аватары ВСЕХ профилей base64 (profileId base64)
            val avatars = HashMap<String, String>()
            profiles.forEach { p ->
                val uriStr = p.photoUri
                if (!uriStr.isNullOrEmpty()) {
                    try {
                        val u = Uri.parse(uriStr)
                        context.contentResolver.openInputStream(u)?.use { input ->
                            avatars[p.id.toString()] =
                                android.util.Base64.encodeToString(input.readBytes(), android.util.Base64.DEFAULT)
                        }
                    } catch (e: Exception) { com.nutritrack.app.utils.AppLog.e("backup avatar read failed", e) }
                }
            }

            val data = BackupData(
                exportedAt   = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                products     = db.productDao().dumpAll(),
                diary        = db.diaryDao().dumpAll(),
                water        = db.waterDao().dumpAll(),
                weight       = db.weightDao().dumpAll(),
                profiles     = profiles,
                achievements = db.achievementDao().dumpAll(),
                fasting      = db.fastingDao().dumpAll(),
                mealPlans    = db.mealPlanDao().dumpAllPlans(),
                mealItems    = db.mealPlanDao().dumpAllItems(),
                dailyGoals   = db.dailyGoalDao().dumpAll(),
                fitness      = db.fitnessDao().dumpAll(),
                settings     = settings,
                avatars      = avatars,
                measurements = db.measurementDao().dumpAll(),
                progressPhotos = db.progressPhotoDao().dumpAll()
            )
            val json = gson.toJson(data)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(Exception("Не удалось открыть файл для записи"))

            // Кол-во записей всего (для сообщения)
            val total = data.products.size + data.diary.size + data.water.size +
                    data.weight.size + data.profiles.size + data.achievements.size +
                    data.fasting.size + data.mealPlans.size + data.mealItems.size +
                    data.dailyGoals.size + data.fitness.size
            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

 /** Прочитать JSON из uri и ЗАМЕНИТЬ данные в базе. */
    suspend fun import(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext Result.failure(Exception("Не удалось открыть файл"))

            val data = gson.fromJson(json, BackupData::class.java)
                ?: return@withContext Result.failure(Exception("Файл повреждён или не является резервной копией"))

            val db = AppDatabase.getInstance(context)

            // Восстанавливаем аватары всех профилей из base64 в файлы (profileId путь)
            val restoredPaths = HashMap<Long, String>()
            if (data.avatars.isNotEmpty()) {
                val dir = java.io.File(context.filesDir, "avatars")
                if (!dir.exists()) dir.mkdirs()
                data.avatars.forEach { (pidStr, b64) ->
                    try {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        val dest = java.io.File(dir, "avatar_restored_${pidStr}_${System.currentTimeMillis()}.jpg")
                        java.io.FileOutputStream(dest).use { it.write(bytes) }
                        pidStr.toLongOrNull()?.let { pid -> restoredPaths[pid] = Uri.fromFile(dest).toString() }
                    } catch (e: Exception) { com.nutritrack.app.utils.AppLog.e("restore avatar failed", e) }
                }
            }

            // Полная замена: чистим и заливаем заново
            // Порядок: сначала зависимые, потом справочники — но т.к. REPLACE и нет строгих FK-cascade, грузим всё
            with(db) {
                // products
                productDao().deleteAll()
                productDao().insertAll(data.products)
                // diary
                diaryDao().deleteAll()
                diaryDao().insertAll(data.diary)
                // профили — заменяем; подставляем восстановленный путь аватара по id
                data.profiles.forEach { p ->
                    val fixed = restoredPaths[p.id]?.let { p.copy(photoUri = it) } ?: p
                    profileDao().insertAll(listOf(fixed))
                }
                // вода
                waterDao().insertAll(data.water)
                // вес
                weightDao().insertAll(data.weight)
                // достижения
                achievementDao().insertAll(data.achievements)
                // голодание
                fastingDao().insertAll(data.fasting)
                // планы питания
                mealPlanDao().insertAllPlans(data.mealPlans)
                mealPlanDao().insertAllItems(data.mealItems)
                // цели
                dailyGoalDao().insertAll(data.dailyGoals)
                // фитнес
                fitnessDao().insertAll(data.fitness)
                measurementDao().insertAll(data.measurements)
                progressPhotoDao().insertAll(data.progressPhotos)
            }

            // Восстанавливаем настройки
            if (data.settings.isNotEmpty()) {
                val prefs = PrefsManager(context)
                data.settings["dark_theme"]?.let { prefs.isDarkTheme = it.toBoolean() }
                data.settings["language"]?.let { prefs.language = it }
                data.settings["circular_calorie_mode"]?.let {
                    prefs.setBool("circular_calorie_mode", it.toBoolean())
                }
            }

            val total = data.products.size + data.diary.size + data.water.size +
                    data.weight.size + data.profiles.size + data.achievements.size +
                    data.fasting.size + data.mealPlans.size + data.mealItems.size +
                    data.dailyGoals.size + data.fitness.size
            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
