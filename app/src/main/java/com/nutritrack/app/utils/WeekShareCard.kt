package com.nutritrack.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.content.FileProvider
import com.nutritrack.app.R
import java.io.File

/**
 * Карточка итогов недели для соцсетей: рисуется на Canvas (1080×1080),
 * сохраняется в cache и отдаётся через системное окно «Поделиться».
 * Только агрегаты — без чувствительных деталей (вес не включаем по умолчанию).
 */
object WeekShareCard {

    data class Stats(
        val dateRange: String,
        val streakDays: Int,
        val avgCalories: Int,
        val avgWaterMl: Int,
        val daysOnTarget: Int,
    )

    fun shareCard(context: Context, stats: Stats) {
        val bmp = render(context, stats)
        val file = File(context.cacheDir, "neomeal_week.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(
            intent, context.getString(R.string.share_week_title)))
    }

    private fun render(ctx: Context, s: Stats): Bitmap {
        val size = 1080
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Фирменный градиент
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(
                    Color.parseColor("#1A0533"),
                    Color.parseColor("#12094D"),
                    Color.parseColor("#0D0D2B")
                ), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B39DDB") }
        val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9E9EB0") }

        // Шапка
        white.textSize = 64f; white.isFakeBoldText = true
        c.drawText("🥗 NeoMeal", 80f, 140f, white)
        secondary.textSize = 40f
        c.drawText(s.dateRange, 80f, 210f, secondary)

        // Большой стрик по центру
        white.textSize = 200f
        val streakText = "🔥 ${s.streakDays}"
        c.drawText(streakText, 80f, 480f, white)
        accent.textSize = 46f
        c.drawText(ctx.getString(R.string.share_streak_lbl), 80f, 550f, accent)

        // Метрики
        fun metric(y: Float, emoji: String, value: String, label: String) {
            white.textSize = 72f; white.isFakeBoldText = true
            c.drawText("$emoji $value", 80f, y, white)
            secondary.textSize = 38f
            c.drawText(label, 80f, y + 52f, secondary)
        }
        metric(700f, "⚡", ctx.getString(R.string.share_kcal_fmt, s.avgCalories),
            ctx.getString(R.string.share_kcal_lbl))
        metric(850f, "💧", ctx.getString(R.string.share_water_fmt, s.avgWaterMl),
            ctx.getString(R.string.share_water_lbl))

        // Дни в норме
        accent.textSize = 44f
        c.drawText(ctx.getString(R.string.share_days_fmt, s.daysOnTarget), 80f, 990f, accent)

        return bmp
    }
}
