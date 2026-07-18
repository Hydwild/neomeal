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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager

    // Экспорт/импорт данных и ПИН вынесены в контроллеры (инициализируются в onViewCreated)
    private lateinit var dataExport: DataExportController
    private lateinit var pinSettings: PinSettingsController

    // Лаунчеры для выбора файла (Storage Access Framework) — делегируют контроллеру
    private val createBackupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { dataExport.onBackupFileCreated(it) } }

    private val openBackupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { dataExport.onBackupFilePicked(it) } }

    private val createCsvLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { dataExport.onCsvFileCreated(it) } }

    private val pickBackgroundLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { applyCustomImage(it) } }

    // Выбор папки для авто-бэкапа (SAF, права сохраняются навсегда)
    private val autoBackupFolderLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) { _binding?.switchAutoBackup?.isChecked = false; return@registerForActivityResult }
        dataExport.onBackupFolderPicked(uri)
    }

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

        dataExport = DataExportController(
            fragment = this,
            prefs = prefs,
            launchCsvSave = { name -> createCsvLauncher.launch(name) },
            onAutoBackupEnabled = { _binding?.switchAutoBackup?.isChecked = true }
        )

        binding.tvCurrentLang.text = languages.firstOrNull { it.first == prefs.language }?.second ?: getString(R.string.set_lang_auto)

        // Тёмная тема — клик по всей строке + программная покраска тумблера
        setupCollapsibleSections()

        binding.tvThemeMode.text = themeModeLabel(prefs.themeMode)

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
                // Виджеты тоже перекрашиваются (WidgetTheme читает эту опцию)
                com.nutritrack.app.widget.MainWidgetProvider.updateAll(requireContext())
                com.nutritrack.app.widget.FastingWidgetProvider.updateAll(requireContext())
                requireActivity().recreate()   // тема применяется в MainActivity.onCreate
            }
        }

        // Иконка приложения
        binding.tvAppIcon.text = iconLabel(prefs.appIcon)
        binding.rowAppIcon.setOnClickListener { showIconPicker() }

        // Сплит БЖУ
        updateMacroLabel()
        binding.rowMacroSplit.setOnClickListener { showMacroSplitDialog() }

        // Карточки главного экрана
        binding.rowDashCards.setOnClickListener { showDashCardsDialog() }

        // Версия — из BuildConfig, а не хардкод
        binding.tvAppVersion.text = getString(R.string.app_version_fmt,
            com.nutritrack.app.BuildConfig.VERSION_NAME)

        // ПИН-код (альтернатива биометрии) — логика в контроллере
        pinSettings = PinSettingsController(this, binding.tvPinState)
        pinSettings.refreshLabel()
        binding.rowPinCode.setOnClickListener { pinSettings.onRowClicked() }

        // Авто-бэкап раз в неделю
        binding.switchAutoBackup.isChecked = prefs.autoBackupEnabled
        binding.rowAutoBackup.setOnClickListener {
            if (prefs.autoBackupEnabled) {
                prefs.autoBackupEnabled = false
                com.nutritrack.app.utils.AutoBackupWorker.cancel(requireContext())
                binding.switchAutoBackup.isChecked = false
            } else {
                binding.switchAutoBackup.isChecked = true
                autoBackupFolderLauncher.launch(null)
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

        // Режим темы: авто (за системой) / светлая / тёмная
        binding.rowDarkTheme.setOnClickListener { showThemeModePicker() }

        // Стартовая вкладка
        binding.tvStartTab.text = startTabLabel(prefs.startTab)
        binding.rowStartTab.setOnClickListener { showStartTabPicker() }

        // Кнопки воды
        binding.tvWaterButtons.text = prefs.waterButtons.joinToString("  ·  ") { "+$it" }
        binding.rowWaterButtons.setOnClickListener { showWaterButtonsDialog() }

        // Ручные цели
        updateManualGoalLabels()
        binding.rowManualCalories.setOnClickListener {
            showManualGoalDialog(isWater = false)
        }
        binding.rowManualWater.setOnClickListener {
            showManualGoalDialog(isWater = true)
        }

        // Активность увеличивает норму
        binding.switchActivityBoost.isChecked = prefs.activityBoost
        binding.rowActivityBoost.setOnClickListener {
            binding.switchActivityBoost.isChecked = !binding.switchActivityBoost.isChecked
        }
        binding.switchActivityBoost.setOnCheckedChangeListener { _, checked ->
            prefs.activityBoost = checked
        }

        binding.rowLanguage.setOnClickListener { showLanguagePicker() }
        binding.rowExportPdf.setOnClickListener { dataExport.showPdfExportDialog() }

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
            createBackupLauncher.launch(com.nutritrack.app.utils.BackupManager.defaultFileName())
        }
        binding.rowImport.setOnClickListener {
            openBackupLauncher.launch(arrayOf("application/json"))
        }
        binding.rowClearData.setOnClickListener { confirmClearData() }
        binding.rowExportDiaryCsv.setOnClickListener { dataExport.showCsvPicker() }
        binding.rowRate.setOnClickListener { openRate() }
        binding.rowFeedback.setOnClickListener { openFeedback() }
        binding.rowPrivacy.setOnClickListener { openPrivacyPolicy() }
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
        val ctx = requireContext().applicationContext
        val dest = java.io.File(ctx.filesDir, "custom_bg.jpg")
        viewLifecycleOwner.lifecycleScope.launch {
            // Декодирование и сжатие большого фото — в фоновом потоке
            val ok = withContext(Dispatchers.IO) {
                com.nutritrack.app.utils.ImageDownscaler.saveScaled(ctx, uri, dest)
            }
            if (!ok) {
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.bg_image_error), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
            prefs.customImagePath = dest.absolutePath
            prefs.backgroundKey = "custom_image"
            prefs.backgroundManual = true  // иначе syncWithTheme сбросит фон при перезаходе
            binding.tvCurrentBackground.text = getString(R.string.bg_custom_image)
            (activity as? MainActivity)?.let {
                com.nutritrack.app.utils.BackgroundHelper.apply(it.findViewById(R.id.rootBackground))
            }
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




    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    // ПИН-код






    // Авто-бэкап: опциональный пароль (хранится в Keystore-хранилище)


    // Иконка приложения

    private fun iconLabel(key: String): String = getString(when (key) {
        "dark"   -> R.string.icon_dark
        "purple" -> R.string.icon_purple
        else     -> R.string.icon_classic
    })

    private fun showIconPicker() {
        val keys = com.nutritrack.app.utils.AppIconHelper.KEYS
        val labels = keys.map { iconLabel(it) }.toTypedArray()
        val current = keys.indexOf(prefs.appIcon).coerceAtLeast(0)
        val d = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.set_app_icon))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                val key = keys[which]
                if (key == prefs.appIcon) return@setSingleChoiceItems
                prefs.appIcon = key
                binding.tvAppIcon.text = labels[which]
                com.nutritrack.app.utils.AppIconHelper.apply(requireContext(), key)
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.icon_changed), android.widget.Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
        d.show()
    }

    // Сплит БЖУ

    private fun updateMacroLabel() {
        binding.tvMacroSplit.text =
            "${prefs.macroProtein}/${prefs.macroFat}/${prefs.macroCarbs}"
    }

    private fun showMacroSplitDialog() {
        val ctx = requireContext()
        val col = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        fun field(label: String, value: Int): android.widget.EditText {
            col.addView(android.widget.TextView(ctx).apply {
                text = label; textSize = 12f
                setTextColor(ctx.getColor(R.color.text_secondary))
            })
            return android.widget.EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(value.toString())
                col.addView(this)
            }
        }
        val p = field(getString(R.string.macro_protein_lbl), prefs.macroProtein)
        val f = field(getString(R.string.macro_fat_lbl), prefs.macroFat)
        val c = field(getString(R.string.macro_carbs_lbl), prefs.macroCarbs)
        val d = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.set_macro_split))
            .setMessage(getString(R.string.macro_split_msg))
            .setView(col)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                val pv = p.text.toString().toIntOrNull() ?: 30
                val fv = f.text.toString().toIntOrNull() ?: 30
                val cv = c.text.toString().toIntOrNull() ?: 40
                if (pv + fv + cv != 100 || pv < 5 || fv < 5 || cv < 5) {
                    android.widget.Toast.makeText(ctx,
                        getString(R.string.macro_split_invalid), android.widget.Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.macroProtein = pv; prefs.macroFat = fv; prefs.macroCarbs = cv
                com.nutritrack.app.utils.CalorieCalculator.proteinPct = pv
                com.nutritrack.app.utils.CalorieCalculator.fatPct = fv
                com.nutritrack.app.utils.CalorieCalculator.carbsPct = cv
                updateMacroLabel()
            }
            .setNeutralButton(getString(R.string.set_water_buttons_reset)) { _, _ ->
                prefs.macroProtein = 30; prefs.macroFat = 30; prefs.macroCarbs = 40
                com.nutritrack.app.utils.CalorieCalculator.proteinPct = 30
                com.nutritrack.app.utils.CalorieCalculator.fatPct = 30
                com.nutritrack.app.utils.CalorieCalculator.carbsPct = 40
                updateMacroLabel()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, ctx) }
        d.show()
    }

    // Карточки главного экрана

    private fun showDashCardsDialog() {
        val keys = listOf("water", "fasting", "suggestions", "streak")
        val labels = arrayOf(
            getString(R.string.dash_card_water),
            getString(R.string.dash_card_fasting),
            getString(R.string.dash_card_suggestions),
            getString(R.string.dash_card_streak)
        )
        val hidden = prefs.hiddenDashboardCards.toMutableSet()
        val checked = keys.map { it !in hidden }.toBooleanArray()
        val d = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.set_dash_cards))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) hidden.remove(keys[which]) else hidden.add(keys[which])
            }
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                prefs.hiddenDashboardCards = hidden
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
        d.show()
    }

    // Режим темы

    private fun themeModeLabel(mode: String): String = getString(when (mode) {
        "dark"  -> R.string.theme_dark
        "light" -> R.string.theme_light
        else    -> R.string.theme_auto
    })

    private fun showThemeModePicker() {
        val modes = listOf("auto", "light", "dark")
        val labels = modes.map { themeModeLabel(it) }.toTypedArray()
        val current = modes.indexOf(prefs.themeMode).coerceAtLeast(0)
        val d = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.set_theme_mode))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val mode = modes[which]
                prefs.themeMode = mode
                // Старый флаг — для бэкапа и обратной совместимости
                prefs.isDarkTheme = mode == "dark"
                dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(when (mode) {
                    "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                })
                requireActivity().recreate()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
        d.show()
    }

    // Стартовая вкладка

    private fun startTabLabel(tab: String): String = getString(when (tab) {
        "plan"    -> R.string.nav_plan
        "stats"   -> R.string.nav_analytics
        "profile" -> R.string.nav_profile
        "more"    -> R.string.nav_more
        else      -> R.string.nav_home
    })

    private fun showStartTabPicker() {
        val tabs = listOf("home", "plan", "stats", "profile", "more")
        val labels = tabs.map { startTabLabel(it) }.toTypedArray()
        val current = tabs.indexOf(prefs.startTab).coerceAtLeast(0)
        val d = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.set_start_tab))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.startTab = tabs[which]
                binding.tvStartTab.text = labels[which]
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
        d.show()
    }

    // Кнопки воды

    private fun showWaterButtonsDialog() {
        val ctx = requireContext()
        val current = prefs.waterButtons
        val col = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val inputs = current.map { v ->
            android.widget.EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(v.toString())
                hint = getString(R.string.u_ml)
                col.addView(this)
            }
        }
        val d = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.set_water_buttons))
            .setMessage(getString(R.string.set_water_buttons_msg))
            .setView(col)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                val vols = inputs.mapIndexed { i, e ->
                    e.text.toString().toIntOrNull()?.coerceIn(10, 2000) ?: current[i]
                }
                prefs.waterButtons = vols
                binding.tvWaterButtons.text = vols.joinToString("  ·  ") { "+$it" }
            }
            .setNeutralButton(getString(R.string.set_water_buttons_reset)) { _, _ ->
                prefs.waterButtons = listOf(100, 200, 300, 500)
                binding.tvWaterButtons.text =
                    prefs.waterButtons.joinToString("  ·  ") { "+$it" }
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
        d.show()
    }

    // Ручные цели калорий/воды (0 = авто по профилю)

    private fun updateManualGoalLabels() {
        binding.tvManualCalories.text =
            if (prefs.manualCalorieGoal > 0) getString(R.string.cal_kcal_fmt, prefs.manualCalorieGoal)
            else getString(R.string.goal_auto)
        binding.tvManualWater.text =
            if (prefs.manualWaterGoal > 0) "${prefs.manualWaterGoal} ${getString(R.string.u_ml)}"
            else getString(R.string.goal_auto)
    }

    private fun showManualGoalDialog(isWater: Boolean) {
        val ctx = requireContext()
        val currentVal = if (isWater) prefs.manualWaterGoal else prefs.manualCalorieGoal
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            if (currentVal > 0) setText(currentVal.toString())
            hint = getString(R.string.goal_auto_hint)
        }
        val container = android.widget.FrameLayout(ctx).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val title = getString(if (isWater) R.string.set_manual_water else R.string.set_manual_cal)
        val d = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage(getString(R.string.goal_manual_msg))
            .setView(container)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                val v = input.text.toString().toIntOrNull() ?: 0
                if (isWater) {
                    prefs.manualWaterGoal = v
                    com.nutritrack.app.utils.CalorieCalculator.manualWater = prefs.manualWaterGoal
                } else {
                    prefs.manualCalorieGoal = v
                    com.nutritrack.app.utils.CalorieCalculator.manualCalories = prefs.manualCalorieGoal
                }
                updateManualGoalLabels()
                com.nutritrack.app.widget.MainWidgetProvider.updateAll(requireContext())
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
        d.show()
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
            Triple(binding.secHeaderMain,      binding.secCardMain,      binding.tvChevronMain),
            Triple(binding.secHeaderLook,      binding.secCardLook,      binding.tvChevronLook),
            Triple(binding.secHeaderNutrition, binding.secCardNutrition, binding.tvChevronNutrition),
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
