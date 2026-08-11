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

    // Совместимость со старым форматом файла

 /**
 * До 1.4.2 классы [BackupData] и [Envelope] не были защищены от R8: их поля
 * переименовывались в a, b, c… и именно эти буквы попадали в файл, потому что
 * Gson пишет JSON по именам полей. Формат бэкапа оказывался завязан на
 * обфускацию — файл одной сборки мог не прочитаться другой (все поля
 * становились null). Теперь классы под -keep, а старые файлы разбираем по
 * таблицам ниже.
 */
    private val LEGACY_BACKUP_KEYS = mapOf(
        "a" to "version", "b" to "exportedAt", "c" to "products", "d" to "diary",
        "e" to "water", "f" to "weight", "g" to "profiles", "h" to "achievements",
        "i" to "fasting", "j" to "mealPlans", "k" to "mealItems", "l" to "dailyGoals",
        "m" to "fitness", "n" to "settings", "o" to "avatars", "p" to "measurements",
        "q" to "progressPhotos"
    )

    private val LEGACY_ENVELOPE_KEYS = mapOf(
        "a" to "format", "b" to "version", "c" to "salt", "d" to "iv", "e" to "data"
    )

    // Доступ для юнит-тестов (BackupLegacyFormatTest)
    internal fun legacyBackupKeysForTest(): Map<String, String> = LEGACY_BACKUP_KEYS
    internal fun legacyEnvelopeKeysForTest(): Map<String, String> = LEGACY_ENVELOPE_KEYS

 /**
 * Переименовать обфусцированные ключи старого формата в настоящие.
 * [marker] — поле, которое есть только в новом формате: если оно на месте,
 * файл уже нормальный и трогать его не нужно.
 */
    internal fun normalizeKeys(json: String, table: Map<String, String>, marker: String): String = try {
        val root = com.google.gson.JsonParser.parseString(json)
        if (!root.isJsonObject || root.asJsonObject.has(marker)) {
            json
        } else {
            val fixed = com.google.gson.JsonObject()
            root.asJsonObject.entrySet().forEach { (k, v) -> fixed.add(table[k] ?: k, v) }
            gson.toJson(fixed)
        }
    } catch (_: Exception) { json }

 // Gson кладёт null в non-null поля Kotlin, если имя в файле не совпало —
 // читаем коллекции только через эти помощники, иначе NPE.
    private fun <T> List<T>?.orEmptySafe(): List<T> = this ?: emptyList()
    private fun <K, V> Map<K, V>?.orEmptySafe(): Map<K, V> = this ?: emptyMap()

    private fun deriveKey(password: CharArray, salt: ByteArray): javax.crypto.spec.SecretKeySpec {
        val spec = javax.crypto.spec.PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        return javax.crypto.spec.SecretKeySpec(key, "AES")
    }

    // java.util.Base64 (API 26+) вместо android.util — крипта тестируется
    // обычными юнит-тестами без эмулятора (BackupCryptoTest)
    private fun b64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    private fun unb64(s: String): ByteArray =
        java.util.Base64.getDecoder().decode(s)

    internal fun encrypt(json: String, password: String): String {
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

    internal fun decrypt(envelopeJson: String, password: String): String {
        // Старые файлы содержат обфусцированные ключи конверта (salt → c и т.д.)
        val normalized = normalizeKeys(envelopeJson, LEGACY_ENVELOPE_KEYS, "salt")
        val env = gson.fromJson(normalized, Envelope::class.java)
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
                "circular_calorie_mode" to prefs.getBool("circular_calorie_mode", true).toString(),
                MealTemplates.PREFS_KEY to prefs.getString(MealTemplates.PREFS_KEY)
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

            // Файл мог быть создан сборкой, где поля BackupData были обфусцированы
            json = normalizeKeys(json, LEGACY_BACKUP_KEYS, "diary")

            val data = gson.fromJson(json, BackupData::class.java)
                ?: return@withContext Result.failure(Exception("Файл повреждён или не является резервной копией"))

            // Коллекции читаем через orEmptySafe: при незнакомой раскладке ключей
            // Gson оставляет в них null, и обращение уронило бы импорт.
            val inProducts     = data.products.orEmptySafe()
            val inDiary        = data.diary.orEmptySafe()
            val inWater        = data.water.orEmptySafe()
            val inWeight       = data.weight.orEmptySafe()
            val inProfiles     = data.profiles.orEmptySafe()
            val inAchievements = data.achievements.orEmptySafe()
            val inFasting      = data.fasting.orEmptySafe()
            val inMealPlans    = data.mealPlans.orEmptySafe()
            val inMealItems    = data.mealItems.orEmptySafe()
            val inDailyGoals   = data.dailyGoals.orEmptySafe()
            val inFitness      = data.fitness.orEmptySafe()
            val inSettings     = data.settings.orEmptySafe()
            val inAvatars      = data.avatars.orEmptySafe()
            val inMeasurements = data.measurements.orEmptySafe()
            val inPhotos       = data.progressPhotos.orEmptySafe()

            // Ниже идёт ПОЛНАЯ ЗАМЕНА данных. Если файл не распознан (ни профилей,
            // ни дневника), выходим ДО удаления — иначе пустой разбор стёр бы
            // дневник пользователя и ничего не записал взамен.
            if (inProfiles.isEmpty() && inDiary.isEmpty()) {
                return@withContext Result.failure(
                    Exception(context.getString(com.nutritrack.app.R.string.backup_unrecognized)))
            }

            val db = AppDatabase.getInstance(context)

            // Восстанавливаем аватары всех профилей из base64 в файлы (profileId путь)
            val restoredPaths = HashMap<Long, String>()
            if (inAvatars.isNotEmpty()) {
                val dir = java.io.File(context.filesDir, "avatars")
                if (!dir.exists()) dir.mkdirs()
                inAvatars.forEach { (pidStr, b64) ->
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
                productDao().insertAll(inProducts)
                // diary
                diaryDao().deleteAll()
                diaryDao().insertAll(inDiary)
                // профили — заменяем; подставляем восстановленный путь аватара по id
                inProfiles.forEach { p ->
                    val fixed = restoredPaths[p.id]?.let { p.copy(photoUri = it) } ?: p
                    profileDao().insertAll(listOf(fixed))
                }
                // вода
                waterDao().insertAll(inWater)
                // вес
                weightDao().insertAll(inWeight)
                // достижения
                achievementDao().insertAll(inAchievements)
                // голодание
                fastingDao().insertAll(inFasting)
                // планы питания
                mealPlanDao().insertAllPlans(inMealPlans)
                mealPlanDao().insertAllItems(inMealItems)
                // цели
                dailyGoalDao().insertAll(inDailyGoals)
                // фитнес
                fitnessDao().insertAll(inFitness)
                measurementDao().insertAll(inMeasurements)
                progressPhotoDao().insertAll(inPhotos)
            }

            // Восстанавливаем настройки
            if (inSettings.isNotEmpty()) {
                val prefs = PrefsManager(context)
                inSettings["dark_theme"]?.let { prefs.isDarkTheme = it.toBoolean() }
                inSettings["language"]?.let { prefs.language = it }
                inSettings["circular_calorie_mode"]?.let {
                    prefs.setBool("circular_calorie_mode", it.toBoolean())
                }
                inSettings[MealTemplates.PREFS_KEY]?.let {
                    prefs.setString(MealTemplates.PREFS_KEY, it)
                }
            }

            val total = inProducts.size + inDiary.size + inWater.size +
                    inWeight.size + inProfiles.size + inAchievements.size +
                    inFasting.size + inMealPlans.size + inMealItems.size +
                    inDailyGoals.size + inFitness.size
            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
