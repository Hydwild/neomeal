package com.nutritrack.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.utils.AppLog
import com.nutritrack.app.utils.AutoBackupWorker
import com.nutritrack.app.utils.BackupManager
import com.nutritrack.app.utils.CsvExporter
import com.nutritrack.app.utils.DateUtils
import com.nutritrack.app.utils.DialogHelper
import com.nutritrack.app.utils.LocaleHelper
import com.nutritrack.app.utils.PdfReportExporter
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.PremiumManager
import com.nutritrack.app.utils.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экспорт/импорт данных из экрана настроек: резервная копия (JSON, опц.
 * шифрование), CSV, PDF-отчёт, авто-бэкап. Вынесено из SettingsFragment,
 * чтобы разгрузить экран.
 *
 * SAF-лаунчеры регистрируются во фрагменте (нужен его lifecycle) и
 * делегируют сюда; для запуска сохранения CSV и обновления тумблера
 * авто-бэкапа контроллер зовёт колбэки.
 */
class DataExportController(
    private val fragment: Fragment,
    private val prefs: PrefsManager,
    /** Запустить SAF-создание CSV-файла с этим именем. */
    private val launchCsvSave: (filename: String) -> Unit,
    /** Авто-бэкап включён — обновить тумблер во фрагменте. */
    private val onAutoBackupEnabled: () -> Unit,
) {
    private val ctx get() = fragment.requireContext()
    private val scope get() = fragment.viewLifecycleOwner.lifecycleScope
    private fun s(id: Int, vararg a: Any) = ctx.getString(id, *a)
    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    private fun wrap(v: android.view.View) = FrameLayout(ctx).apply {
        val pad = (20 * ctx.resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        addView(v)
    }

    private fun passwordInput() = EditText(ctx).apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = s(R.string.bk_pw_hint)
    }

    // Резервная копия (JSON)

 /** SAF выдал uri для сохранения — спросить пароль и экспортировать. */
    fun onBackupFileCreated(uri: Uri) {
        val input = passwordInput()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.bk_pw_title))
            .setMessage(s(R.string.bk_pw_msg))
            .setView(wrap(input))
            .setPositiveButton(s(R.string.bk_encrypt)) { _, _ ->
                val pw = input.text.toString()
                if (pw.isEmpty()) {
                    Toast.makeText(ctx, s(R.string.bk_pw_empty), Toast.LENGTH_SHORT).show()
                    runExport(uri, null)
                } else runExport(uri, pw)
            }
            .setNegativeButton(s(R.string.bk_plain)) { _, _ -> runExport(uri, null) }
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    private fun runExport(uri: Uri, password: String?) {
        scope.launch {
            BackupManager.export(ctx, uri, password)
                .onSuccess { count -> toast(s(R.string.set_imported_fmt, count)) }
                .onFailure { toast(s(R.string.err_export, it.message ?: "")) }
        }
    }

    // Импорт

    fun onBackupFilePicked(uri: Uri) {
        AlertDialog.Builder(ctx)
            .setTitle(s(R.string.restore_title))
            .setMessage(s(R.string.restore_msg))
            .setPositiveButton(s(R.string.restore_btn)) { _, _ ->
                if (BackupManager.isEncrypted(ctx, uri)) askImportPassword(uri)
                else doImport(uri, null)
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .show()
    }

    private fun askImportPassword(uri: Uri) {
        val input = passwordInput()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.bk_enter_pw_title))
            .setView(wrap(input))
            .setPositiveButton(s(R.string.restore_btn)) { _, _ -> doImport(uri, input.text.toString()) }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    private fun doImport(uri: Uri, password: String?) {
        scope.launch {
            BackupManager.import(ctx, uri, password)
                .onSuccess { count -> toast(s(R.string.bk_restored_fmt, count)) }
                .onFailure { err ->
                    when (err) {
                        is BackupManager.WrongPasswordException -> {
                            toast(s(R.string.bk_wrong_pw)); askImportPassword(uri)
                        }
                        is BackupManager.PasswordRequiredException -> askImportPassword(uri)
                        else -> toast(s(R.string.err_import, err.message ?: ""))
                    }
                }
        }
    }

    // CSV

    private var csvType: String = "diary"

    fun showCsvPicker() {
        val types  = listOf("diary", "water", "weight", "measures")
        val labels = arrayOf(
            s(R.string.csv_diary), s(R.string.csv_water),
            s(R.string.csv_weight), s(R.string.csv_measures)
        )
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.csv_pick_title))
            .setItems(labels) { _, which ->
                csvType = types[which]
                launchCsvSave("neomeal_${csvType}_${DateUtils.today()}.csv")
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    fun onCsvFileCreated(uri: Uri) {
        scope.launch {
            try {
                val db = (fragment.requireActivity().application as NutriApp).database
                // Экспортируем именно активный профиль, а не кэш
                val pid = com.nutritrack.app.utils.ActiveProfile.id(ctx, db)
                val csv = when (csvType) {
                    "measures" -> CsvExporter.measurementsCsv(ctx, db, pid)
                    "water"    -> CsvExporter.waterCsv(ctx, db, pid)
                    "weight"   -> CsvExporter.weightCsv(ctx, db, pid)
                    else       -> CsvExporter.diaryCsv(ctx, db, pid)
                }
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(0xEF); out.write(0xBB); out.write(0xBF)   // BOM для Excel
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                toast(s(R.string.set_csv_saved))
            } catch (e: Exception) {
                toast(s(R.string.err_export, e.message ?: ""))
            }
        }
    }

    // Авто-бэкап

    fun onBackupFolderPicked(uri: Uri) {
        ctx.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.autoBackupUri = uri.toString()
        askAutoBackupPassword()
    }

    private fun askAutoBackupPassword() {
        val input = passwordInput()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.bk_pw_title))
            .setMessage(s(R.string.auto_backup_pw_msg))
            .setView(wrap(input))
            .setPositiveButton(s(R.string.bk_encrypt)) { _, _ ->
                SecurePrefs.setString(ctx, AutoBackupWorker.KEY_PASSWORD,
                    input.text.toString().ifEmpty { null })
                enableAutoBackup()
            }
            .setNegativeButton(s(R.string.bk_plain)) { _, _ ->
                SecurePrefs.setString(ctx, AutoBackupWorker.KEY_PASSWORD, null)
                enableAutoBackup()
            }
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    private fun enableAutoBackup() {
        prefs.autoBackupEnabled = true
        AutoBackupWorker.schedule(ctx)
        onAutoBackupEnabled()
        toast(s(R.string.auto_backup_on))
    }

    // PDF-отчёт

    fun showPdfExportDialog() {
        if (!PremiumManager.gate(ctx, PremiumManager.Feature.PDF_REPORTS)) return
        val sectionLabels = arrayOf(
            s(R.string.pdf_sec_nutrition), s(R.string.pdf_sec_water),
            s(R.string.pdf_sec_weight), s(R.string.pdf_sec_cost),
            s(R.string.pdf_sec_micro), s(R.string.pdf_sec_measurements)
        )
        val checked = booleanArrayOf(true, true, true, true, true, true)
        AlertDialog.Builder(ctx)
            .setTitle(s(R.string.pdf_choose_sections))
            .setMultiChoiceItems(sectionLabels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(s(R.string.c_next)) { _, _ ->
                showPdfPeriodDialog(PdfReportExporter.Sections(
                    nutrition = checked[0], water = checked[1], weight = checked[2],
                    cost = checked[3], micro = checked[4], measurements = checked[5]
                ))
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .show()
    }

    private fun showPdfPeriodDialog(sections: PdfReportExporter.Sections) {
        val labels = arrayOf(s(R.string.pdf_period_week), s(R.string.pdf_period_month))
        AlertDialog.Builder(ctx)
            .setTitle(s(R.string.pdf_choose_period))
            .setItems(labels) { _, which -> generateAndSharePdf(if (which == 0) 7 else 30, sections) }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .show()
    }

    private fun generateAndSharePdf(days: Int, sections: PdfReportExporter.Sections) {
        // Локаль интерфейса, иначе PDF на системном языке (appContext её не наследует)
        val localeCtx = LocaleHelper.wrap(ctx.applicationContext)
        Toast.makeText(ctx, s(R.string.pdf_generating), Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PdfReportExporter.export(localeCtx, days, sections)
            }
            val file = result.file
            if (file == null || !file.exists()) {
                toast(s(R.string.pdf_err_fmt, result.error ?: "?")); return@launch
            }
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                fragment.startActivity(Intent.createChooser(intent, s(R.string.pdf_export_title)))
            } catch (e: Exception) {
                AppLog.e("pdf share failed", e)
                toast(s(R.string.pdf_err_fmt, e.message ?: "?"))
            }
        }
    }
}
