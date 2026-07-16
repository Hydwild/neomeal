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

    // Шифрование бэкапа (опционально, по паролю пользователя)

    private const val ENC_FORMAT = "neomeal-encrypted"
    private const val PBKDF2_ITERATIONS = 150_000
    private const val GCM_TAG_BITS = 128

 /** Неверный пароль или повреждённый зашифрованный файл. */
    class WrongPasswordException : Exception("wrong password")

 /** Файл защищён паролем, а пароль не передан. */
    class PasswordRequiredException : Exception("password required")

 /** Конверт зашифрованного бэкапа: соль/IV/данные в base64. */
    private data class Envelope(
        val format: String = ENC_FORMAT,
        val version: Int = 1,
        val salt: String = "",
        val iv: String = "",
        val data: String = ""
    )

    private fun deriveKey(password: CharArray, salt: ByteArray): javax.crypto.spec.SecretKeySpec {
        val spec = javax.crypto.spec.PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        return javax.crypto.spec.SecretKeySpec(key, "AES")
    }

    private fun b64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun unb64(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.NO_WRAP)

    private fun encrypt(json: String, password: String): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            deriveKey(password.toCharArray(), salt),
            javax.crypto.spec.GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return gson.toJson(Envelope(salt = b64(salt), iv = b64(iv), data = b64(encrypted)))
    }

    private fun decrypt(envelopeJson: String, password: String): String {
        val env = gson.fromJson(envelopeJson, Envelope::class.java)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            deriveKey(password.toCharArray(), unb64(env.salt)),
            javax.crypto.spec.GCMParameterSpec(GCM_TAG_BITS, unb64(env.iv))
        )
        return try {
            cipher.doFinal(unb64(env.data)).toString(Charsets.UTF_8)
        } catch (_: javax.crypto.AEADBadTagException) {
            // GCM-тег не сошёлся — пароль неверный (или файл повреждён)
            throw WrongPasswordException()
        }
    }

 /** Быстрый признак зашифрованного файла (по заголовку конверта). */
    fun isEncrypted(context: Context, uri: Uri): Boolean = try {
        val head = context.contentResolver.openInputStream(uri)?.use { input ->
            ByteArray(256).let { buf ->
                val n = input.read(buf)
                if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
            }
        } ?: ""
        head.contains(ENC_FORMAT)
    } catch (_: Exception) { false }

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

 /**
 * Собрать все данные и записать JSON в выбранный uri.
 * [password] задан — файл шифруется AES-GCM (ключ из PBKDF2 по паролю).
 */
    suspend fun export(context: Context, uri: Uri, password: String? = null): Result<Int> = withContext(Dispatchers.IO) {
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
            val payload = if (password.isNullOrEmpty()) json else encrypt(json, password)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
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

 /**
 * Прочитать JSON из uri и ЗАМЕНИТЬ данные в базе.
 * Для зашифрованного файла нужен [password]:
 * без него — PasswordRequiredException, неверный — WrongPasswordException.
 */
    suspend fun import(context: Context, uri: Uri, password: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext Result.failure(Exception("Не удалось открыть файл"))

            if (json.contains(ENC_FORMAT)) {
                if (password.isNullOrEmpty())
                    return@withContext Result.failure(PasswordRequiredException())
                json = decrypt(json, password)
            }

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
