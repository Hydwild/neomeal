package com.nutritrack.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Ужимает большое изображение перед сохранением в фон приложения.
 *
 * Раньше кастомный фон копировался байт-в-байт, а BitmapFactory.decodeFile
 * грузил его целиком: фото 4000×3000 = ~48 МБ в памяти на каждый экран,
 * отсюда лаги. Теперь картинка масштабируется под экран и жмётся в JPEG.
 */
object ImageDownscaler {

    // Фон достаточно ограничить размером экрана с запасом.
    private const val MAX_DIMEN = 2560
    private const val JPEG_QUALITY = 85

 /**
 * Считывает [uri], уменьшает до [MAX_DIMEN] по длинной стороне и
 * сохраняет JPEG в [dest]. Возвращает true при успехе.
 */
    fun saveScaled(context: Context, uri: Uri, dest: File): Boolean {
        return try {
            // 1) Читаем только размеры (без загрузки пикселей)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            // 2) inSampleSize — грубое уменьшение степенями двойки при декоде
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calcSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMEN)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            // 3) Точное уменьшение до предела длинной стороны
            val scaled = scaleToMax(decoded, MAX_DIMEN)
            if (scaled !== decoded) decoded.recycle()

            FileOutputStream(dest).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            scaled.recycle()
            true
        } catch (e: Exception) {
            AppLog.e("image downscale failed", e)
            false
        }
    }

    private fun calcSampleSize(w: Int, h: Int, max: Int): Int {
        var sample = 1
        var longest = maxOf(w, h)
        // Оставляем запас 2× для последующего точного масштабирования
        while (longest / 2 >= max * 2) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMax(src: Bitmap, max: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= max) return src
        val ratio = max.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
    }
}
