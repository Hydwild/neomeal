package com.nutritrack.app.pets

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Скачивает zip-пак питомца и распаковывает в filesDir/pets/<id>/.
 * Всё выполняется на Dispatchers.IO; прогресс отдаётся через колбэк.
 */
class PetDownloader(context: Context) {

    private val storage = PetStorage(context)
    private val cacheDir = context.cacheDir

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * @param onProgress 0..100, -1 если общий размер неизвестен (неопределённый прогресс)
     */
    suspend fun download(
        pet: PetCatalog.Pet,
        onProgress: (Int) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        val tmpZip = File(cacheDir, "${pet.id}_download.zip")
        try {
            // 1. Скачивание во временный файл
            val conn = (URL(pet.zipUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return@withContext Result.Error("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLength.toLong() // может быть -1
            conn.inputStream.use { input ->
                tmpZip.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                        } else {
                            onProgress(-1)
                        }
                    }
                }
            }

            // 2. Распаковка во временную папку, затем атомарная замена
            val finalDir = storage.petDir(pet.id)
            val unpackDir = File(cacheDir, "${pet.id}_unpack")
            unpackDir.deleteRecursively()
            unpackDir.mkdirs()

            ZipInputStream(tmpZip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(unpackDir, entry.name)
                    // Защита от path traversal (zip slip)
                    if (!outFile.canonicalPath.startsWith(unpackDir.canonicalPath + File.separator)) {
                        return@withContext Result.Error("Небезопасный путь в архиве")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 3. Замена старой папки на свежую
            finalDir.deleteRecursively()
            finalDir.parentFile?.mkdirs()
            if (!unpackDir.renameTo(finalDir)) {
                // renameTo может не сработать между разными точками — копируем вручную
                unpackDir.copyRecursively(finalDir, overwrite = true)
                unpackDir.deleteRecursively()
            }

            // 4. Проверка, что распаковались реальные кадры хотя бы для idle
            if (!storage.hasAction(pet.id, "idle")) {
                finalDir.deleteRecursively()
                return@withContext Result.Error("В паке нет кадров")
            }

            storage.markInstalled(pet.id)
            onProgress(100)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "Ошибка загрузки")
        } finally {
            tmpZip.delete()
        }
    }
}
