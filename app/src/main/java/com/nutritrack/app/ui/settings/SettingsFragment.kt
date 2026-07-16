package com.nutritrack.app.ui.settings

import com.nutritrack.app.R

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.nutritrack.app.MainActivity
import com.nutritrack.app.NutriApp
import com.nutritrack.app.databinding.FragmentSettingsBinding
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.BackupManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager

    // Лаунчеры для выбора файла (Storage Access Framework)
    private val createBackupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { doExport(it) } }

    private val openBackupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { confirmImport(it) } }

    // Что экспортируем в CSV: "diary" или "measures"
    private var csvType: String = "diary"
    private val createCsvLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { doExportCsv(it) } }

    private val pickBackgroundLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { applyCustomImage(it) } }

    // Запрос write-разрешений Health Connect (для тумблера «Запись в HC»)
    private val hcWritePermLauncher = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val ok = granted.containsAll(com.nutritrack.app.utils.HealthConnectWriter.WRITE_PERMISSIONS)
        prefs.hcWriteEnabled = ok
        _binding?.switchHcWrite?.isChecked = ok
        if (!ok) android.widget.Toast.makeText(requireContext(),
            getString(R.string.set_hc_write_denied), android.widget.Toast.LENGTH_LONG).show()
    }

    private val languages = listOf(
        "auto" to "🌐 Авто",
        "ru"   to "🇷🇺 Русский",
        "en"   to "🇬🇧 English",
        "es"   to "🇪🇸 Español",
        "de"   to "🇩🇪 Deutsch",
        "fr"   to "🇫🇷 Français",
        "it"   to "🇮🇹 Italiano",
        "pt"   to "🇵🇹 Português",
        "zh"   to "🇨🇳 中文",
        "ja"   to "🇯🇵 日本語",
        "ko"   to "🇰🇷 한국어",
        "ar"   to "🇸🇦 العربية",
        "tr"   to "🇹🇷 Türkçe",
        "hi"   to "🇮🇳 हिन्दी",
        "pl"   to "🇵🇱 Polski",
        "nl"   to "🇳🇱 Nederlands",
        "sv"   to "🇸🇪 Svenska",
        "fi"   to "🇫🇮 Suomi"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())

        binding.tvCurrentLang.text = languages.firstOrNull { it.first == prefs.language }?.second ?: getString(R.string.set_lang_auto)

        // Тёмная тема — клик по всей строке + программная покраска тумблера
        setupCollapsibleSections()

        binding.switchDarkTheme.isChecked = prefs.isDarkTheme
        tintSwitch(binding.switchDarkTheme.isChecked)

        // Акцентный цвет
        binding.tvCurrentAccent.text = com.nutritrack.app.utils.AccentHelper.label(requireContext())
        binding.rowAccent.setOnClickListener { showAccentPicker() }
        binding.tvCurrentBackground.text = currentBackgroundLabel()
        binding.rowBackground.setOnClickListener { showBackgroundPicker() }

        // Вибро-отклик
        binding.switchHaptic.isChecked = prefs.hapticEnabled
        binding.rowHaptic.setOnClickListener {
            binding.switchHaptic.isChecked = !binding.switchHaptic.isChecked
        }
        binding.switchHaptic.setOnCheckedChangeListener { _, checked ->
            prefs.hapticEnabled = checked
            if (checked) com.nutritrack.app.utils.HapticHelper.tap(binding.switchHaptic)
        }

        binding.seekStatusBar.progress = prefs.bottomBarAlpha
        binding.tvStatusBarValue.text = "${prefs.bottomBarAlpha}%"
        binding.seekStatusBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                binding.tvStatusBarValue.text = "$value%"
                (activity as? com.nutritrack.app.MainActivity)?.applyBottomBarAlpha(value)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                prefs.bottomBarAlpha = binding.seekStatusBar.progress
            }
        })

        // Празднование целей (конфетти)
        binding.switchConfetti.isChecked = prefs.confettiEnabled
        binding.rowConfetti.setOnClickListener {
            binding.switchConfetti.isChecked = !binding.switchConfetti.isChecked
        }
        binding.switchConfetti.setOnCheckedChangeListener { _, checked ->
            prefs.confettiEnabled = checked
        }

        // Режим отпуска
        binding.switchVacation.isChecked = prefs.vacationMode
        binding.rowVacation.setOnClickListener {
            binding.switchVacation.isChecked = !binding.switchVacation.isChecked
        }
        binding.switchVacation.setOnCheckedChangeListener { _, checked ->
            val today = com.nutritrack.app.utils.DateUtils.today()
            if (checked) {
                prefs.vacationMode = true
                prefs.vacationStart = today
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.vac_on), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val start = prefs.vacationStart
                if (start.isNotEmpty()) {
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    var d = java.time.LocalDate.parse(start, fmt)
                    val end = java.time.LocalDate.now()
                    val frozen = prefs.frozenDates.toMutableSet()
                    var guard = 0
                    while (!d.isAfter(end) && guard < 366) {
                        frozen.add(d.format(fmt)); d = d.plusDays(1); guard++
                    }
                    prefs.frozenDates = frozen
                }
                prefs.vacationMode = false
                prefs.vacationStart = ""
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.vac_off), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Заморозка вчерашнего дня (раз в 7 дней) — логика в StreakFreeze
        binding.rowFreeze.setOnClickListener {
            com.nutritrack.app.utils.StreakFreeze.requestFreeze(
                requireContext(), prefs, binding.root
            ) { /* на экране настроек обновлять нечего */ }
        }

        // Material You: только Android 12+ (system_* цвета)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            binding.rowDynamicColors.visibility = View.VISIBLE
            binding.switchDynamicColors.isChecked = prefs.dynamicColors
            binding.rowDynamicColors.setOnClickListener {
                binding.switchDynamicColors.isChecked = !binding.switchDynamicColors.isChecked
            }
            binding.switchDynamicColors.setOnCheckedChangeListener { _, checked ->
                if (prefs.dynamicColors == checked) return@setOnCheckedChangeListener
                prefs.dynamicColors = checked
                requireActivity().recreate()   // тема применяется в MainActivity.onCreate
            }
        }

        // Запись питания/воды/веса в Health Connect
        binding.switchHcWrite.isChecked = prefs.hcWriteEnabled
        binding.rowHcWrite.setOnClickListener {
            val hc = com.nutritrack.app.utils.HealthConnectManager(requireContext())
            if (!hc.isAvailable()) {
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.set_hc_unavailable), android.widget.Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (prefs.hcWriteEnabled) {
                // Выключаем без вопросов
                prefs.hcWriteEnabled = false
                binding.switchHcWrite.isChecked = false
            } else {
                // Включаем: если права уже есть — сразу, иначе запрашиваем
                viewLifecycleOwner.lifecycleScope.launch {
                    if (com.nutritrack.app.utils.HealthConnectWriter.hasWritePermissions(requireContext())) {
                        prefs.hcWriteEnabled = true
                        _binding?.switchHcWrite?.isChecked = true
                    } else {
                        hcWritePermLauncher.launch(
                            com.nutritrack.app.utils.HealthConnectWriter.WRITE_PERMISSIONS)
                    }
                }
            }
        }

        binding.rowDarkTheme.setOnClickListener {
            binding.switchDarkTheme.isChecked = !binding.switchDarkTheme.isChecked
        }
        binding.switchDarkTheme.setOnCheckedChangeListener { _, checked ->
            tintSwitch(checked)
            prefs.isDarkTheme = checked
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            requireActivity().recreate()
        }

        binding.rowLanguage.setOnClickListener { showLanguagePicker() }
        binding.rowExportPdf.setOnClickListener { showPdfExportDialog() }

        // Начало недели
        binding.tvCurrentWeekStart.text = if (prefs.weekStartMonday) getString(R.string.week_monday) else getString(R.string.week_sunday)
        binding.rowWeekStart.setOnClickListener {
            val labels = arrayOf(getString(R.string.week_monday), getString(R.string.week_sunday))
            val current = if (prefs.weekStartMonday) 0 else 1
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.set_week_title))
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    prefs.weekStartMonday = which == 0
                    binding.tvCurrentWeekStart.text = labels[which]
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .show()
        }

        // Единицы измерения
        binding.tvCurrentUnits.text = if (prefs.units == "imperial") getString(R.string.units_imperial) else getString(R.string.units_metric)
        binding.rowUnits.setOnClickListener {
            val labels = arrayOf(getString(R.string.units_metric_full), getString(R.string.units_imperial_full))
            val current = if (prefs.units == "imperial") 1 else 0
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.set_units_title))
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    prefs.units = if (which == 1) "imperial" else "metric"
                    binding.tvCurrentUnits.text = if (which == 1) getString(R.string.units_imperial) else getString(R.string.units_metric)
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .show()
        }

        // Валюта
        updateCurrencyLabel()
        binding.rowCurrency.setOnClickListener {
            val currencies = com.nutritrack.app.utils.CurrencyHelper.currencies
            val labels = currencies.map {
                if (it.code == "auto") getString(R.string.set_currency_auto) else it.label
            }.toTypedArray()
            val current = currencies.indexOfFirst { it.code == prefs.currency }.coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.set_currency_title))
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    prefs.currency = currencies[which].code
                    updateCurrencyLabel()
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .show()
        }

        // Умный тренер
        binding.switchCoach.isChecked = prefs.smartCoachEnabled
        binding.rowCoach.setOnClickListener {
            binding.switchCoach.isChecked = !binding.switchCoach.isChecked
        }
        binding.switchCoach.setOnCheckedChangeListener { _, checked ->
            prefs.smartCoachEnabled = checked
            if (checked) android.widget.Toast.makeText(requireContext(),
                getString(R.string.coach_on), android.widget.Toast.LENGTH_SHORT).show()
        }
        val adj = prefs.calorieAdjustment
        binding.tvCoachAdj.text = getString(R.string.set_coach_adj_fmt, if (adj > 0) "+" else "", adj)
        binding.rowCoachAdj.setOnClickListener {
            if (prefs.calorieAdjustment == 0) {
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.coach_none), android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.coach_reset_title))
                .setMessage(getString(R.string.coach_reset_msg))
                .setPositiveButton(getString(R.string.coach_reset_btn)) { _, _ ->
                    prefs.calorieAdjustment = 0
                    com.nutritrack.app.utils.CalorieCalculator.adjustment = 0
                    binding.tvCoachAdj.text = getString(R.string.set_coach_zero)
                    requireActivity().recreate()
                }
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .show()
        }

        // Блокировка входа
        binding.switchAppLock.isChecked = prefs.appLockEnabled
        binding.rowAppLock.setOnClickListener {
            binding.switchAppLock.isChecked = !binding.switchAppLock.isChecked
        }
        binding.switchAppLock.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val bm = androidx.biometric.BiometricManager.from(requireContext())
                val can = bm.canAuthenticate(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                if (can == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                    prefs.appLockEnabled = true
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.applock_on), android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    binding.switchAppLock.isChecked = false
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.applock_need),
                        android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                prefs.appLockEnabled = false
            }
        }


        // Шрифт интерфейса
        binding.tvCurrentFont.text = fontLabel(prefs.fontFamily)
        binding.rowFont.setOnClickListener { showFontPicker() }

        binding.rowExport.setOnClickListener {
            createBackupLauncher.launch(BackupManager.defaultFileName())
        }
        binding.rowImport.setOnClickListener {
            openBackupLauncher.launch(arrayOf("application/json"))
        }
        binding.rowClearData.setOnClickListener { confirmClearData() }
        binding.rowExportDiaryCsv.setOnClickListener {
            csvType = "diary"
            createCsvLauncher.launch("neomeal_diary_${com.nutritrack.app.utils.DateUtils.today()}.csv")
        }
        binding.rowExportMeasuresCsv.setOnClickListener {
            csvType = "measures"
            createCsvLauncher.launch("neomeal_measurements_${com.nutritrack.app.utils.DateUtils.today()}.csv")
        }
        binding.rowRate.setOnClickListener { openRate() }
        binding.rowFeedback.setOnClickListener { openFeedback() }
        binding.rowPrivacy.setOnClickListener { openPrivacyPolicy() }
    }

    private fun doExport(uri: android.net.Uri) {
        // Спрашиваем, защитить ли файл паролем (бэкап содержит все данные открытым текстом)
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.bk_pw_hint)
        }
        val container = android.widget.FrameLayout(requireContext()).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val d = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.bk_pw_title))
            .setMessage(getString(R.string.bk_pw_msg))
            .setView(container)
            .setPositiveButton(getString(R.string.bk_encrypt)) { _, _ ->
                val pw = input.text.toString()
                if (pw.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.bk_pw_empty), android.widget.Toast.LENGTH_SHORT).show()
                    runExport(uri, null)
                } else runExport(uri, pw)
            }
            .setNegativeButton(getString(R.string.bk_plain)) { _, _ -> runExport(uri, null) }
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
        d.show()
    }

    private fun runExport(uri: android.net.Uri, password: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = BackupManager.export(requireContext(), uri, password)
            result.onSuccess { count ->
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.set_imported_fmt, count), android.widget.Toast.LENGTH_LONG).show()
            }.onFailure {
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.err_export, it.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun doExportCsv(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = (requireActivity().application as com.nutritrack.app.NutriApp).database
                val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
                val pid = prefs.activeProfileId
                val csv = if (csvType == "measures")
                    com.nutritrack.app.utils.CsvExporter.measurementsCsv(db, pid)
                else
                    com.nutritrack.app.utils.CsvExporter.diaryCsv(db, pid)
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    // BOM для корректной кириллицы в Excel
                    out.write(0xEF); out.write(0xBB); out.write(0xBF)
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.set_csv_saved), android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.err_export, e.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fontLabel(key: String): String = when (key) {
        "inter"      -> "Inter"
        "manrope"    -> "Manrope"
        "montserrat" -> "Montserrat"
        else         -> getString(R.string.dlg_lang_system)
    }

    private fun showFontPicker() {
        val keys   = listOf("system", "inter", "manrope", "montserrat")
        val labels = arrayOf(getString(R.string.dlg_lang_system), "Inter", "Manrope", "Montserrat")
        val current = keys.indexOf(prefs.fontFamily).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dlg_font_title))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.fontFamily = keys[which]
                binding.tvCurrentFont.text = fontLabel(keys[which])
                dialog.dismiss()
                // Перезапуск активити чтобы применить шрифт
                requireActivity().recreate()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun currentBackgroundLabel(): String {
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        return when (prefs.backgroundKey) {
            "custom_gradient" -> getString(R.string.bg_custom_gradient)
            "custom_image"    -> getString(R.string.bg_custom_image)
            else -> {
                val p = com.nutritrack.app.utils.BackgroundHelper.presets
                    .firstOrNull { it.key == prefs.backgroundKey }
                if (p != null) getString(p.labelRes) else getString(R.string.bg_default)
            }
        }
    }

    private fun showBackgroundPicker() {
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_background_picker, null)
        val rv = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvGradients)
        rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton(getString(R.string.c_close), null)
            .create()
        dialog.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(dialog, requireContext()) }

        rv.adapter = com.nutritrack.app.adapter.GradientPickerAdapter(
            com.nutritrack.app.utils.BackgroundHelper.presets,
            prefs.backgroundKey
        ) { preset ->
            prefs.backgroundKey = preset.key
            binding.tvCurrentBackground.text = getString(preset.labelRes)
            (activity as? MainActivity)?.let {
                com.nutritrack.app.utils.BackgroundHelper.apply(it.findViewById(R.id.rootBackground))
            }
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnCustomGradient).setOnClickListener {
            dialog.dismiss()
            showCustomGradientDialog()
        }
        dialogView.findViewById<View>(R.id.btnCustomImage).setOnClickListener {
            dialog.dismiss()
            pickBackgroundLauncher.launch("image/*")
        }
        dialog.show()
    }

    private fun showCustomGradientDialog() {
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_gradient_palette, null)

        val preview = view.findViewById<View>(R.id.gradientPreview)
        val btnStart = view.findViewById<android.widget.Button>(R.id.btnColorStart)
        val btnEnd = view.findViewById<android.widget.Button>(R.id.btnColorEnd)
        val seekR = view.findViewById<android.widget.SeekBar>(R.id.seekR)
        val seekG = view.findViewById<android.widget.SeekBar>(R.id.seekG)
        val seekB = view.findViewById<android.widget.SeekBar>(R.id.seekB)
        val etHex = view.findViewById<android.widget.EditText>(R.id.etHex)

        // Текущие два цвета градиента
        val parts = prefs.customGradient.split(",").map { it.trim() }
        var colorStart = runCatching { android.graphics.Color.parseColor(parts.getOrElse(0) { "#667eea" }) }.getOrDefault(android.graphics.Color.parseColor("#667eea"))
        var colorEnd = runCatching { android.graphics.Color.parseColor(parts.getOrElse(1) { "#f093fb" }) }.getOrDefault(android.graphics.Color.parseColor("#f093fb"))
        var editingStart = true
        var suppress = false

        fun renderPreview() {
            preview.background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorStart, colorEnd)
            ).apply {
                cornerRadius = 16f * resources.displayMetrics.density
            }
        }
        fun hexOf(c: Int) = String.format("#%06X", 0xFFFFFF and c)
        fun highlightButtons() {
            btnStart.alpha = if (editingStart) 1f else 0.5f
            btnEnd.alpha = if (editingStart) 0.5f else 1f
        }
        // Подгрузить слайдеры/HEX из активного цвета
        fun loadActive() {
            suppress = true
            val c = if (editingStart) colorStart else colorEnd
            seekR.progress = android.graphics.Color.red(c)
            seekG.progress = android.graphics.Color.green(c)
            seekB.progress = android.graphics.Color.blue(c)
            etHex.setText(hexOf(c))
            suppress = false
        }
        // Применить значения слайдеров к активному цвету
        fun applySeekToColor() {
            val c = android.graphics.Color.rgb(seekR.progress, seekG.progress, seekB.progress)
            if (editingStart) colorStart = c else colorEnd = c
            suppress = true
            etHex.setText(hexOf(c))
            suppress = false
            renderPreview()
        }

        val seekListener = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (!suppress && fromUser) applySeekToColor()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        }
        seekR.setOnSeekBarChangeListener(seekListener)
        seekG.setOnSeekBarChangeListener(seekListener)
        seekB.setOnSeekBarChangeListener(seekListener)

        etHex.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppress) return
                val parsed = runCatching { android.graphics.Color.parseColor(s.toString().trim()) }.getOrNull() ?: return
                if (editingStart) colorStart = parsed else colorEnd = parsed
                suppress = true
                seekR.progress = android.graphics.Color.red(parsed)
                seekG.progress = android.graphics.Color.green(parsed)
                seekB.progress = android.graphics.Color.blue(parsed)
                suppress = false
                renderPreview()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        btnStart.setOnClickListener { editingStart = true; highlightButtons(); loadActive() }
        btnEnd.setOnClickListener { editingStart = false; highlightButtons(); loadActive() }

        highlightButtons(); loadActive(); renderPreview()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.bg_custom_gradient))
            .setView(view)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                prefs.customGradient = "${hexOf(colorStart)},${hexOf(colorEnd)}"
                prefs.backgroundManual = true
                prefs.backgroundKey = "custom_gradient"
                binding.tvCurrentBackground.text = getString(R.string.bg_custom_gradient)
                (activity as? MainActivity)?.let {
                    com.nutritrack.app.utils.BackgroundHelper.apply(it.findViewById(R.id.rootBackground))
                    com.nutritrack.app.utils.BackgroundHelper.applyScrim(it.findViewById(R.id.backgroundScrim))
                }
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .create().also { it.show(); com.nutritrack.app.utils.DialogHelper.tint(it, requireContext()) }
    }

    private fun applyCustomImage(uri: android.net.Uri) {
        try {
            val dest = java.io.File(requireContext().filesDir, "custom_bg.jpg")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
            prefs.customImagePath = dest.absolutePath
            prefs.backgroundKey = "custom_image"
            prefs.backgroundManual = true  // иначе syncWithTheme сбросит фон при перезаходе
            binding.tvCurrentBackground.text = getString(R.string.bg_custom_image)
            (activity as? MainActivity)?.let {
                com.nutritrack.app.utils.BackgroundHelper.apply(it.findViewById(R.id.rootBackground))
            }
        } catch (e: Exception) {
            com.nutritrack.app.utils.AppLog.e("custom bg image failed", e)
            android.widget.Toast.makeText(requireContext(), getString(R.string.bg_image_error), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAccentPicker() {
        val palette = com.nutritrack.app.utils.AccentHelper.palette
        val labels = palette.map { com.nutritrack.app.utils.AccentHelper.labelOf(requireContext(), it.first) }.toTypedArray()
        val current = palette.indexOfFirst { it.first == prefs.accentColor }.coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.set_accent_title))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.accentColor = palette[which].first
                binding.tvCurrentAccent.text = com.nutritrack.app.utils.AccentHelper.labelOf(requireContext(), palette[which].first)
                dialog.dismiss()
                requireActivity().recreate()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun confirmImport(uri: android.net.Uri) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.restore_title))
            .setMessage(getString(R.string.restore_msg))
            .setPositiveButton(getString(R.string.restore_btn)) { _, _ ->
                // Зашифрованный файл — сначала пароль
                if (BackupManager.isEncrypted(requireContext(), uri)) askImportPassword(uri)
                else doImport(uri, null)
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun askImportPassword(uri: android.net.Uri) {
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.bk_pw_hint)
        }
        val container = android.widget.FrameLayout(requireContext()).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val d = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.bk_enter_pw_title))
            .setView(container)
            .setPositiveButton(getString(R.string.restore_btn)) { _, _ ->
                doImport(uri, input.text.toString())
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
        d.show()
    }

    private fun doImport(uri: android.net.Uri, password: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = BackupManager.import(requireContext(), uri, password)
            result.onSuccess { count ->
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.bk_restored_fmt, count),
                    android.widget.Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                when (err) {
                    is BackupManager.WrongPasswordException -> {
                        android.widget.Toast.makeText(requireContext(),
                            getString(R.string.bk_wrong_pw), android.widget.Toast.LENGTH_LONG).show()
                        askImportPassword(uri)   // дать попробовать ещё раз
                    }
                    is BackupManager.PasswordRequiredException -> askImportPassword(uri)
                    else -> android.widget.Toast.makeText(requireContext(),
                        getString(R.string.err_import, err.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    // Программная покраска тумблера (надёжнее селекторов на тёмном фоне)
    private fun tintSwitch(checked: Boolean) {
        val thumbColor = if (checked) 0xFF7C4DFF.toInt() else 0xFFB0B0C0.toInt()
        val trackColor = if (checked) 0xFF5E3DB8.toInt() else 0xFF3D3358.toInt()
        binding.switchDarkTheme.thumbTintList =
            android.content.res.ColorStateList.valueOf(thumbColor)
        binding.switchDarkTheme.trackTintList =
            android.content.res.ColorStateList.valueOf(trackColor)
    }

    private fun showLanguagePicker() {
        val labels  = languages.map { it.second }.toTypedArray()
        val current = languages.indexOfFirst { it.first == prefs.language }.coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dlg_choose_lang))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val newLang = languages[which].first
                if (newLang != prefs.language) {
                    prefs.language = newLang
                    dialog.dismiss()
                    restartApp()
                } else dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun confirmClearData() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_title))
            .setMessage(getString(R.string.clear_msg))
            .setPositiveButton(getString(R.string.clear_btn)) { _, _ ->
                val app = requireActivity().application as NutriApp
                val db  = app.database
                val profileId = prefs.activeProfileId
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    // Только активный профиль — раньше deleteAll() стирал
                    // дневник и воду ВСЕХ профилей
                    db.diaryDao().deleteAllForProfile(profileId)
                    db.waterDao().deleteAllForProfile(profileId)
                    // Очистка серий достижений (если метод есть)
                    try { db.dailyGoalDao().deleteAllForProfile(profileId) } catch (e: Exception) { com.nutritrack.app.utils.AppLog.e("deleteAllForProfile failed", e) }
                }
                android.widget.Toast.makeText(requireContext(), getString(R.string.set_data_cleared), android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun openRate() {
        val pkg = requireContext().packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun updateCurrencyLabel() {
        val code = prefs.currency
        binding.tvCurrentCurrency.text = if (code == "auto") {
            getString(R.string.set_currency_auto)
        } else {
            com.nutritrack.app.utils.CurrencyHelper.currencies.firstOrNull { it.code == code }?.label ?: code
        }
    }

    private fun showPdfExportDialog() {
        if (!com.nutritrack.app.utils.PremiumManager.gate(requireContext(),
                com.nutritrack.app.utils.PremiumManager.Feature.PDF_REPORTS)) return
        // Шаг 1 — какие разделы включить
        val sectionLabels = arrayOf(
            getString(R.string.pdf_sec_nutrition),
            getString(R.string.pdf_sec_water),
            getString(R.string.pdf_sec_weight),
            getString(R.string.pdf_sec_cost),
            getString(R.string.pdf_sec_micro),
            getString(R.string.pdf_sec_measurements)
        )
        val checked = booleanArrayOf(true, true, true, true, true, true)
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pdf_choose_sections))
            .setMultiChoiceItems(sectionLabels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(getString(R.string.c_next)) { _, _ ->
                val sections = com.nutritrack.app.utils.PdfReportExporter.Sections(
                    nutrition = checked[0], water = checked[1], weight = checked[2],
                    cost = checked[3], micro = checked[4], measurements = checked[5]
                )
                showPdfPeriodDialog(sections)
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun showPdfPeriodDialog(sections: com.nutritrack.app.utils.PdfReportExporter.Sections) {
        val labels = arrayOf(getString(R.string.pdf_period_week), getString(R.string.pdf_period_month))
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pdf_choose_period))
            .setItems(labels) { _, which ->
                val days = if (which == 0) 7 else 30
                generateAndSharePdf(days, sections)
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun generateAndSharePdf(
        days: Int,
        sections: com.nutritrack.app.utils.PdfReportExporter.Sections
    ) {
        // Оборачиваем в выбранную локаль, чтобы PDF был на языке интерфейса,
        // а не на системном/дефолтном (applicationContext локаль не наследует).
        val ctx = com.nutritrack.app.utils.LocaleHelper.wrap(requireContext().applicationContext)
        android.widget.Toast.makeText(requireContext(), getString(R.string.pdf_generating), android.widget.Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.nutritrack.app.utils.PdfReportExporter.export(ctx, days, sections)
            }
            val file = result.file
            if (file == null || !file.exists()) {
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.pdf_err_fmt, result.error ?: "?"), android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(), "${requireContext().packageName}.provider", file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.pdf_export_title)))
            } catch (e: Exception) {
                com.nutritrack.app.utils.AppLog.e("pdf share failed", e)
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.pdf_err_fmt, e.message ?: "?"), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openPrivacyPolicy() {
        val url = getString(R.string.privacy_policy_url)
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(),
                getString(R.string.settings_privacy_open_error), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFeedback() {
        com.nutritrack.app.utils.AppLog.flush()
        val logFile = com.nutritrack.app.utils.AppLog.logFile()
        // Если лог есть — отправляем письмо с вложением через ACTION_SEND,
        // чтобы лог действий приехал вместе с жалобой.
        if (logFile != null && logFile.exists()) {
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    logFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("neomeal@bk.ru"))
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.st_feedback_subj))
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_body_hint))
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.st_feedback_subj)))
                return
            } catch (e: Exception) {
                com.nutritrack.app.utils.AppLog.e("feedback attach failed", e)
                // упадём на простой mailto ниже
            }
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:neomeal@bk.ru")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.st_feedback_subj))
        }
        try { startActivity(intent) } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), getString(R.string.no_mail), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartApp() {
        startActivity(Intent(requireContext(), MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        requireActivity().finish()
    }

    // Сворачиваемые секции настроек
    private fun setupCollapsibleSections() {
        val sections = listOf(
            Triple(binding.secHeaderMain,   binding.secCardMain,   binding.tvChevronMain),
            Triple(binding.secHeaderUi,     binding.secCardUi,     binding.tvChevronUi),
            Triple(binding.secHeaderStreak, binding.secCardStreak, binding.tvChevronStreak),
            Triple(binding.secHeaderCoach,  binding.secCardCoach,  binding.tvChevronCoach),
            Triple(binding.secHeaderSec,    binding.secCardSec,    binding.tvChevronSec),
            Triple(binding.secHeaderData,   binding.secCardData,   binding.tvChevronData),
            Triple(binding.secHeaderAbout,  binding.secCardAbout,  binding.tvChevronAbout),
        )
        sections.forEach { (header, card, chevron) ->
            header.setOnClickListener {
                com.nutritrack.app.utils.HapticHelper.tap(it)
                val opening = card.visibility != View.VISIBLE
                androidx.transition.TransitionManager.beginDelayedTransition(
                    binding.root as android.view.ViewGroup,
                    androidx.transition.AutoTransition().setDuration(180))
                card.visibility = if (opening) View.VISIBLE else View.GONE
                chevron.animate().rotation(if (opening) 90f else 0f).setDuration(180).start()
            }
        }
        // Первая секция открыта по умолчанию
        binding.secCardMain.visibility = View.VISIBLE
        binding.tvChevronMain.rotation = 90f
    }

}
