package com.nutritrack.app.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.nutritrack.app.R
import java.io.File

/**
 * Фоновое оформление: коллекция градиентов-пресетов,
 * пользовательский градиент (2–3 цвета) и пользовательское изображение.
 * Выбор хранится в PrefsManager, применяется программно к корневому view.
 */
object BackgroundHelper {

    data class GradientPreset(
        val key: String,
        val labelRes: Int,
        val colors: List<String>,
        val angle: Int = 135
    )

    val presets = listOf(
        GradientPreset("default",  R.string.bg_default,  listOf("#667eea", "#764ba2", "#f093fb"), angle = 90),
        GradientPreset("sunset",   R.string.bg_sunset,   listOf("#FF512F", "#DD2476")),
        GradientPreset("ocean",    R.string.bg_ocean,    listOf("#2193b0", "#6dd5ed")),
        GradientPreset("forest",   R.string.bg_forest,   listOf("#11998e", "#38ef7d")),
        GradientPreset("aurora",   R.string.bg_aurora,   listOf("#00c3ff", "#7B68EE", "#ffff1c")),
        GradientPreset("peach",    R.string.bg_peach,    listOf("#ED4264", "#FFEDBC")),
        GradientPreset("midnight", R.string.bg_midnight, listOf("#232526", "#414345")),
        GradientPreset("grape",    R.string.bg_grape,    listOf("#8E2DE2", "#4A00E0")),
        GradientPreset("rose",     R.string.bg_rose,     listOf("#F761A1", "#8C1BAB")),
        GradientPreset("emerald",  R.string.bg_emerald,  listOf("#0BAB64", "#3BB78F")),
        GradientPreset("fire",     R.string.bg_fire,     listOf("#f12711", "#f5af19")),
        GradientPreset("sky",      R.string.bg_sky,      listOf("#2980B9", "#6DD5FA", "#FFFFFF")),
    )

 /** Фон тёмной темы (как в 106): тёмно-сине-фиолетовый. Не показывается в пикере. */
    private val darkThemeColors = listOf("#1A0533", "#12094D", "#0D0D2B")

 /**
 * Привязка фона к теме (пока фон не выбран вручную):
 * светлая тема — яркая «Классика», тёмная — тёмно-синий градиент.
 * Если выбран вручную (backgroundManual) — не вмешиваемся.
 */
    fun syncWithTheme(context: Context) {
        val prefs = PrefsManager(context)
        if (prefs.backgroundManual) return
        prefs.backgroundKey = if (prefs.isDarkTheme) "theme_dark" else "default"
    }

    private fun buildGradient(colors: List<String>, angle: Int = 135): GradientDrawable {
        val ints = colors.mapNotNull { runCatching { Color.parseColor(it.trim()) }.getOrNull() }
            .ifEmpty { listOf(Color.parseColor("#667eea"), Color.parseColor("#f093fb")) }
        val orientation = when (((angle % 360) + 360) % 360) {
            in 0..44, in 315..360 -> GradientDrawable.Orientation.LEFT_RIGHT
            in 45..134            -> GradientDrawable.Orientation.BL_TR
            in 135..224           -> GradientDrawable.Orientation.TOP_BOTTOM
            else                  -> GradientDrawable.Orientation.TR_BL
        }
        return GradientDrawable(orientation, ints.toIntArray()).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }

    fun currentDrawable(context: Context): Drawable {
        val prefs = PrefsManager(context)
        return when (prefs.backgroundKey) {
            "custom_gradient" -> {
                val parts = prefs.customGradient.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                buildGradient(parts)
            }
            "custom_image" -> {
                val path = prefs.customImagePath
                val f = File(path)
                if (path.isNotEmpty() && f.exists()) {
                    runCatching { BitmapDrawable(context.resources, BitmapFactory.decodeFile(path)) }
                        .getOrElse { defaultGradient() }
                } else defaultGradient()
            }
            "theme_dark" -> buildGradient(darkThemeColors)
            else -> {
                val preset = presets.firstOrNull { it.key == prefs.backgroundKey } ?: presets[0]
                buildGradient(preset.colors, preset.angle)
            }
        }
    }

    private fun defaultGradient() = buildGradient(presets[0].colors, presets[0].angle)

 /** Список цветов текущего фона (для оценки яркости). Пусто — если фон-картинка. */
    private fun currentColors(context: Context): List<Int> {
        val prefs = PrefsManager(context)
        return when (prefs.backgroundKey) {
            "custom_gradient" -> prefs.customGradient.split(",")
                .mapNotNull { runCatching { Color.parseColor(it.trim()) }.getOrNull() }
            "custom_image" -> emptyList()
            "theme_dark" -> darkThemeColors.mapNotNull { runCatching { Color.parseColor(it) }.getOrNull() }
            else -> {
                val preset = presets.firstOrNull { it.key == prefs.backgroundKey } ?: presets[0]
                preset.colors.mapNotNull { runCatching { Color.parseColor(it) }.getOrNull() }
            }
        }
    }

 /** Воспринимаемая яркость 0..1 (по среднему цвету фона). */
    fun backgroundLuminance(context: Context): Float {
        // Своя картинка — считаем реальную яркость (уменьшенной копии),
        // иначе на светлом фото текст остаётся светлым и не виден.
        val prefs = PrefsManager(context)
        if (prefs.backgroundKey == "custom_image") {
            val path = prefs.customImagePath
            if (path.isNotEmpty()) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    val bmp = BitmapFactory.decodeFile(path, opts) ?: return 0.5f
                    var sum = 0.0; var n = 0
                    val stepX = (bmp.width / 20).coerceAtLeast(1)
                    val stepY = (bmp.height / 20).coerceAtLeast(1)
                    var x = 0
                    while (x < bmp.width) {
                        var y = 0
                        while (y < bmp.height) {
                            val px = bmp.getPixel(x, y)
                            sum += 0.299 * Color.red(px) / 255.0 + 0.587 * Color.green(px) / 255.0 + 0.114 * Color.blue(px) / 255.0
                            n++; y += stepY
                        }
                        x += stepX
                    }
                    bmp.recycle()
                    if (n > 0) return (sum / n).toFloat()
                }
            }
            return 0.5f
        }
        val colors = currentColors(context)
        if (colors.isEmpty()) return 0.5f
        var sum = 0.0
        for (c in colors) {
            // относительная яркость по восприятию
            val r = Color.red(c) / 255.0
            val g = Color.green(c) / 255.0
            val b = Color.blue(c) / 255.0
            sum += 0.299 * r + 0.587 * g + 0.114 * b
        }
        return (sum / colors.size).toFloat()
    }

 /**
 * Прозрачность затемняющей вуали (alpha 0..255).
 * Светлый фон плотнее вуаль (текст белый, нужен контраст).
 * Тёмный фон почти прозрачная.
 */
    fun scrimAlpha(context: Context): Int {
        val lum = backgroundLuminance(context)
        val isImage = PrefsManager(context).backgroundKey == "custom_image"
        // Светлый фон — плотнее вуаль для читаемости текста.
        // Для своих картинок потолок выше: фото бывает очень светлым.
        val cap = if (isImage) 130 else 70
        return (8 + lum * 90).toInt().coerceIn(0, cap)
    }

    fun apply(view: View) {
        view.background = currentDrawable(view.context)
    }

 /** Применить вуаль к scrim-view поверх фона. */
    fun applyScrim(scrim: View) {
        scrim.setBackgroundColor(Color.argb(scrimAlpha(scrim.context), 0, 0, 0))
    }

    fun previewDrawable(preset: GradientPreset): Drawable = buildGradient(preset.colors, preset.angle)

 /**
 * Мягкий фон для диалогов под выбранный градиент (для светлой темы).
 * Берёт цвета текущего фона, осветляет их до пастельных и строит градиент со скруглением.
 */
    fun dialogBackground(context: Context, cornerRadiusPx: Float): Drawable {
        val base = currentColors(context).ifEmpty {
            presets[0].colors.mapNotNull { runCatching { Color.parseColor(it) }.getOrNull() }
        }
        // Осветляем каждый цвет (миксуем с белым ~78%) — получаем пастельный фон, на котором читается тёмный текст
        val tinted = base.map { c ->
            val r = (Color.red(c) * 0.22 + 255 * 0.78).toInt().coerceIn(0, 255)
            val g = (Color.green(c) * 0.22 + 255 * 0.78).toInt().coerceIn(0, 255)
            val b = (Color.blue(c) * 0.22 + 255 * 0.78).toInt().coerceIn(0, 255)
            Color.rgb(r, g, b)
        }.ifEmpty { listOf(Color.WHITE, Color.WHITE) }
        val arr = if (tinted.size == 1) listOf(tinted[0], tinted[0]) else tinted
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, arr.toIntArray()).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
            cornerRadius = cornerRadiusPx
        }
    }
}
