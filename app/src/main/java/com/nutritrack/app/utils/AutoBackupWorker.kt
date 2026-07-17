package com.nutritrack.app.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Еженедельный автоматический бэкап в выбранную пользователем папку (SAF).
 * Файл создаётся через DocumentsContract; пароль (если задан) берётся
 * из SecurePrefs — сам бэкап шифруется как обычный (AES-GCM).
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = PrefsManager(ctx)
        if (!prefs.autoBackupEnabled) return Result.success()
        val treeStr = prefs.autoBackupUri.ifEmpty { return Result.success() }

        return try {
            val tree = Uri.parse(treeStr)
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "neomeal_auto_" +
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
            val fileUri = DocumentsContract.createDocument(
                ctx.contentResolver, parent, "application/json", name)
                ?: return Result.retry()

            val password = SecurePrefs.getString(ctx, KEY_PASSWORD)
            val result = BackupManager.export(ctx, fileUri, password)
            if (result.isSuccess) {
                AppLog.d("auto backup ok: $name")
                Result.success()
            } else {
                AppLog.e("auto backup failed", result.exceptionOrNull() ?: Exception("unknown"))
                Result.retry()
            }
        } catch (e: Exception) {
            AppLog.e("auto backup crashed", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "auto_backup_weekly"
        const val KEY_PASSWORD = "auto_backup_password"

 /** Включить еженедельный бэкап (перепланирует при повторном вызове). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
