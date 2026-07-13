package com.nutritrack.app.utils

import android.content.Context
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Лёгкий логгер действий пользователя для разбора жалоб на баги.
 *
 * - Хранит последние N записей в памяти + дублирует в файл (кольцевая ротация).
 * - Перехватывает необработанные исключения (краши) и дописывает стек в лог.
 * - Логи лежат во внутренней памяти приложения — не видны другим приложениям,
 * не требуют разрешений и не попадают в облачный бэкап.
 * - При «Обратной связи» лог прикрепляется к письму через FileProvider.
 *
 * Намеренно НЕ пишет персональные данные (вес, имя, замеры) — только события вида
 * «открыл экран X», «ошибка сети», чтобы лог можно было свободно пересылать.
 */
object AppLog {

    private const val MAX_LINES = 500
    private const val FILE_NAME = "applog.txt"
    private const val MAX_FILE_BYTES = 256 * 1024

    private val buffer = ConcurrentLinkedQueue<String>()
    private val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Volatile private var appContext: Context? = null
    @Volatile private var initialized = false

 /** Вызывается один раз из NutriApp.onCreate(). */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        runCatching {
            val f = logFile() ?: return@runCatching
            if (f.exists()) f.readLines().takeLast(MAX_LINES).forEach { buffer.add(it) }
        }

        e("=== app start v${appVersion()} · ${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT} ===")

        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                e("CRASH on ${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message}")
                throwable.stackTrace.take(8).forEach { e("    at $it") }
                flush()
            }
            prev?.uncaughtException(thread, throwable)
        }
    }

 /** Обычное событие (открыл экран, нажал кнопку). */
    fun d(msg: String) = add("D", msg)

 /** Важное событие или ошибка. */
    fun e(msg: String) = add("E", msg)

 /** Ошибка с исключением. */
    fun e(msg: String, t: Throwable) {
        add("E", "$msg — ${t.javaClass.simpleName}: ${t.message}")
        // Нефатальная ошибка уходит в Crashlytics как recordException
        runCatching {
            val fc = FirebaseCrashlytics.getInstance()
            fc.log(msg)
            fc.recordException(t)
        }
    }

    private fun add(level: String, msg: String) {
        val line = "${ts.format(Date())} $level $msg"
        buffer.add(line)
        // Дублируем как breadcrumb в Crashlytics (видно в отчёте до краша)
        runCatching { FirebaseCrashlytics.getInstance().log(line) }
        while (buffer.size > MAX_LINES) buffer.poll()
        if (buffer.size % 10 == 0) flush()
    }

 /** Сбросить буфер в файл. */
    fun flush() {
        val f = logFile() ?: return
        runCatching {
            if (f.exists() && f.length() > MAX_FILE_BYTES) f.delete()
            f.writeText(buffer.joinToString("\n"))
        }
    }

 /** Весь текущий лог одной строкой (для отправки/просмотра). */
    fun dump(): String = buffer.joinToString("\n")

 /** Путь к файлу лога (для шаринга через FileProvider). */
    fun logFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, FILE_NAME)
    }

    private fun appVersion(): String = runCatching {
        val ctx = appContext!!
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}
