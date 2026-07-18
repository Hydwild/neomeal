package com.nutritrack.app.ui.settings

import com.nutritrack.app.R

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
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
    private lateinit var dc: SettingsDialogsController

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
    ) { uri -> uri?.let { dc.applyCustomImage(it) } }

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
        dc = SettingsDialogsController(this, binding, prefs) { pickBackgroundLauncher.launch("image/*") }
        dc.initLabels()

        binding.tvCurrentLang.text = languages.firstOrNull { it.first == prefs.language }?.second ?: getString(R.string.set_lang_auto)

        // Тёмная тема — клик по всей строке + программная покраска тумблера
        setupCollapsibleSections()

        // Акцентный цвет
        binding.rowAccent.setOnClickListener { dc.showAccentPicker() }
        binding.rowBackground.setOnClickListener { dc.showBackgroundPicker() }

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
        binding.rowAppIcon.setOnClickListener { dc.showIconPicker() }

        // Сплит БЖУ
        binding.rowMacroSplit.setOnClickListener { dc.showMacroSplitDialog() }

        // Карточки главного экрана
        binding.rowDashCards.setOnClickListener { dc.showDashCardsDialog() }

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
        binding.rowDarkTheme.setOnClickListener { dc.showThemeModePicker() }

        // Стартовая вкладка
        binding.rowStartTab.setOnClickListener { dc.showStartTabPicker() }

        // Кнопки воды
        binding.rowWaterButtons.setOnClickListener { dc.showWaterButtonsDialog() }

        // Ручные цели
        binding.rowManualCalories.setOnClickListener {
            dc.showManualGoalDialog(false)
        }
        binding.rowManualWater.setOnClickListener {
            dc.showManualGoalDialog(true)
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
        binding.rowFont.setOnClickListener { dc.showFontPicker() }

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















    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    // ПИН-код






    // Авто-бэкап: опциональный пароль (хранится в Keystore-хранилище)


    // Иконка приложения


    // Сплит БЖУ


    // Карточки главного экрана

    // Режим темы


    // Стартовая вкладка


    // Кнопки воды

    // Ручные цели калорий/воды (0 = авто по профилю)


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
