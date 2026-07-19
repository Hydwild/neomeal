package com.nutritrack.app.ui.dashboard
import com.nutritrack.app.utils.navigateAnim

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.adapter.DiaryAdapter
import com.nutritrack.app.data.db.entity.DiaryEntryEntity
import com.nutritrack.app.databinding.FragmentDashboardBinding
import com.nutritrack.app.ui.scanner.BarcodeScannerActivity
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.viewmodel.AchievementsViewModel
import com.nutritrack.app.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val vm: DashboardViewModel by viewModels()
    // Общий с MainActivity — он показывает тосты о достижениях
    private val achievementsVm: AchievementsViewModel by activityViewModels()
    private val fastingVm: com.nutritrack.app.viewmodel.FastingViewModel by activityViewModels()

    private lateinit var diaryAdapter: DiaryAdapter
    private lateinit var prefs: PrefsManager
    private lateinit var waterCard: WaterCardController
    private lateinit var smartInsights: SmartInsightsController
    private lateinit var diaryEntries: DiaryEntryController
    private lateinit var scanner: ScannerResultController
    private lateinit var suggestions: SuggestionsController

    private var isCircularMode = true

    // Сканер штрихкодов — получает результат из BarcodeScannerActivity
    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("barcode")
            if (!barcode.isNullOrBlank()) scanner.lookupBarcode(barcode)
        }
    }

    // Голосовой ввод: куда передать распознанный текст (устанавливается при открытии диалога)
    private var onVoiceResult: ((String) -> Unit)? = null

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) onVoiceResult?.invoke(spoken)
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInput()
        else android.widget.Toast.makeText(requireContext(),
            getString(R.string.ds_mic_needed), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun ensureMicAndStartVoice() {
        val perm = android.Manifest.permission.RECORD_AUDIO
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), perm)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startVoiceInput()
        } else {
            micPermissionLauncher.launch(perm)
        }
    }

    private fun startVoiceInput() {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Мультиязык: пусть распознаёт на языке устройства/любом доступном
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.ds_voice_prompt))
            }
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(),
                getString(R.string.ds_voice_unavailable), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        isCircularMode = prefs.isCircularCalorieMode

        // Отступ сверху = статус-бар, чтобы приветствие не налезало на часы
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.dashboardContent) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top + dp(8), v.paddingRight, v.paddingBottom)
            insets
        }

        // Скрытие/показ таб-бара при скролле
        binding.dashboardScroll.setOnScrollChangeListener(
            androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                (activity as? com.nutritrack.app.MainActivity)?.onContentScroll(scrollY - oldScrollY)
            })

        setupDiaryList()
        waterCard = WaterCardController(
            fragment = this,
            binding = binding,
            vm = vm,
            onAchievementCheck = { deferAchievementCheck() },
            onGoalReached = { celebrateOncePerDay("confetti_water") }
        )
        applyDisplayMode(animate = false)
        waterCard.setup()
        smartInsights = SmartInsightsController(this, { _binding }, vm)
        scanner = ScannerResultController(this) { achievementsVm.checkAchievements() }
        suggestions = SuggestionsController(this, { _binding }, vm)
        observeData()
        observeFastingTimer()

        binding.btnAddFood.setOnClickListener { com.nutritrack.app.utils.HapticHelper.tap(it); showAddFoodDialog() }
        // Применяем выбранный шрифт к кнопке (она не наследует его из темы)
        com.nutritrack.app.utils.FontHelper.apply(binding.btnAddFood)
        binding.btnCalorieMode.setOnClickListener { showModePicker() }
        binding.btnWaterMode.setOnClickListener { waterCard.showModePicker() }

        // Колокольчик на карточке воды экран настроек напоминаний
        binding.btnWaterReminder.setOnClickListener {
            findNavController().navigateAnim(R.id.waterReminderFragment)
        }

        // Заголовок дневника — тап для выбора даты (история прошлых дней)
        binding.rowDiaryHeader.setOnClickListener { showDatePicker() }

        // Пустой дневник — тап открывает добавление еды (empty state как действие)
        binding.tvDiaryEmpty.setOnClickListener {
            com.nutritrack.app.utils.HapticHelper.tap(it)
            showAddFoodDialog()
        }

        // Слушаем дату, выбранную в календаре, — крепится один раз к жизненному циклу
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>("selected_date")
            ?.observe(viewLifecycleOwner) { date ->
                if (!date.isNullOrEmpty()) {
                    vm.setSelectedDate(date)
                    // Очищаем, чтобы не сработало повторно при пересоздании view
                    findNavController().currentBackStackEntry
                        ?.savedStateHandle?.remove<String>("selected_date")
                }
            }

        // Метка даты у дневника: "Сегодня" / "Вчера" / "12 мая"
        vm.selectedDate.observe(viewLifecycleOwner) { date ->
            binding.tvDiaryDateLabel.text = formatDateLabel(date)
        }

        // Тап по аватару профиль (как вкладка, чтобы bottom nav синхронизировался)
        binding.ivDashboardAvatar.setOnClickListener {
            navigateToTab(R.id.profileFragment)
        }

        // Долгий тап по аватару окошко выбора профиля (как в Telegram)
        binding.ivDashboardAvatar.setOnLongClickListener {
            showProfileSwitcher(it)
            true
        }
    }

 /** Popup-окошко со списком профилей рядом с аватаром. */
    private fun showProfileSwitcher(anchor: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val profiles = vm.getAllProfilesList()
            if (profiles.isEmpty()) return@launch

            val popupView = layoutInflater.inflate(R.layout.popup_profile_switch, null)
            val container = popupView.findViewById<LinearLayout>(R.id.containerProfiles)
            val rowAdd    = popupView.findViewById<View>(R.id.rowAddProfile)

            val popup = android.widget.PopupWindow(
                popupView,
                dp(240),
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                elevation = dp(12).toFloat()
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }

            profiles.forEach { p ->
                val row = layoutInflater.inflate(R.layout.item_profile_switch, container, false)
                val iv  = row.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.ivProfileAvatar)
                val tv  = row.findViewById<TextView>(R.id.tvProfileName)
                val chk = row.findViewById<TextView>(R.id.tvProfileCheck)

                tv.text = p.name.ifBlank { getString(R.string.ds_profile_n_fmt, p.id) }
                chk.visibility = if (p.isActive) View.VISIBLE else View.GONE

                if (!p.photoUri.isNullOrEmpty()) {
                    com.bumptech.glide.Glide.with(this@DashboardFragment)
                        .load(android.net.Uri.parse(p.photoUri))
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .into(iv)
                } else {
                    iv.setImageResource(R.drawable.ic_profile_placeholder)
                }

                row.setOnClickListener {
                    if (!p.isActive) {
                        vm.switchProfile(p.id) {
                            Toast.makeText(requireContext(),
                                getString(R.string.ds_profile_switched_fmt, p.name), Toast.LENGTH_SHORT).show()
                        }
                    }
                    popup.dismiss()
                }
                container.addView(row)
            }

            rowAdd.setOnClickListener {
                popup.dismiss()
                navigateToTab(R.id.profileFragment)
            }

            // Показываем под аватаром, выровнено по правому краю
            popup.showAsDropDown(anchor, -dp(184), dp(8))
        }
    }

    override fun onResume() {
        super.onResume()
        // Возобновляем таймер голодания если есть активная сессия
        fastingVm.resumeTimerIfActive()
        // Анимация появления карточек: задаём контроллер и запускаем один раз,
        // чтобы не было мерцания (без android:layoutAnimation в XML)
        val anim = android.view.animation.AnimationUtils.loadLayoutAnimation(
            requireContext(), R.anim.layout_cards_stagger
        )
        binding.dashboardContent.layoutAnimation = anim
        binding.dashboardContent.scheduleLayoutAnimation()

        // Питомец мог измениться на экране выбора — перечитываем.
        // refreshPet сам зафиксирует текущий уровень воды как базовый.
        waterCard.refreshPet()
        // Объёмы кнопок и ручные цели могли поменяться в настройках
        waterCard.refreshButtons()
        vm.refreshGoals()
        applyCardVisibility()
        // Шорткат «Еда» с иконки приложения — открыть шторку добавления
        if (pendingOpenAddFood) {
            pendingOpenAddFood = false
            view?.post { if (isResumed) showAddFoodDialog() }
        }
    }

    companion object {
        // Флаг шортката: MainActivity просит открыть шторку еды после навигации
        var pendingOpenAddFood = false
    }

 /** Скрытие карточек главного (настройки → «Карточки главного»). */
    private fun applyCardVisibility() {
        val hidden = prefs.hiddenDashboardCards
        binding.cardWater.visibility = if ("water" in hidden) View.GONE else View.VISIBLE
        if ("suggestions" in hidden) binding.cardSuggestions.visibility = View.GONE
        if ("fasting" in hidden) binding.cardFastingTimer.visibility = View.GONE
        if ("streak" in hidden) binding.tvStreak.visibility = View.GONE
    }

 /** Карточка голодания на главной: видна только при активной сессии. */
    private fun observeFastingTimer() {
        binding.cardFastingTimer.setOnClickListener {
            try { findNavController().navigateAnim(R.id.fastingFragment) } catch (_: Exception) {}
        }
        fastingVm.timerState.observe(viewLifecycleOwner) { state ->
            if (state.isActive && state.protocol != null &&
                "fasting" !in prefs.hiddenDashboardCards) {
                binding.cardFastingTimer.visibility = View.VISIBLE
                binding.tvFastingProtocolName.text = state.protocol.name
                binding.tvFastingTimerBig.text = state.elapsedFormatted
                binding.tvFastingPercentBig.text = "${state.progressPercent}%"
                binding.progressFastingDash.progress = state.progressPercent
                binding.tvFastingPhaseDash.text = when (state.phase) {
                    com.nutritrack.app.utils.FastingPhase.EARLY          -> getString(R.string.ds_fast_early)
                    com.nutritrack.app.utils.FastingPhase.KETOSIS        -> getString(R.string.ds_fast_ketosis)
                    com.nutritrack.app.utils.FastingPhase.AUTOPHAGY     -> getString(R.string.ds_fast_autophagy)
                    com.nutritrack.app.utils.FastingPhase.DEEP_AUTOPHAGY -> getString(R.string.ds_fast_deep)
                    com.nutritrack.app.utils.FastingPhase.COMPLETE       -> getString(R.string.ds_fast_complete)
                    com.nutritrack.app.utils.FastingPhase.NOT_STARTED    -> ""
                }
            } else {
                binding.cardFastingTimer.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        waterCard.release()
        _binding = null
    }

 /**
 * Переход на вкладку bottom nav программно — с теми же опциями, что использует
 * setupWithNavController при клике. Это синхронизирует BottomNavigationView,
 * чтобы кнопки нижнего меню работали корректно после перехода.
 */
    private fun navigateToTab(destId: Int) {
        val nav = findNavController()
        val options = androidx.navigation.NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(nav.graph.startDestinationId, false, true)
            .build()
        try { nav.navigate(destId, null, options) } catch (_: Exception) {}
    }

 /** Современная шапка: приветствие по времени суток + имя + аватар. */
    private fun updateHeader(name: String?, photoUri: String?) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val (timeGreeting, emoji) = when (hour) {
            in 5..11  -> getString(R.string.greet_morning) to "☀️"
            in 12..17 -> getString(R.string.greet_day) to "🌤️"
            in 18..22 -> getString(R.string.greet_evening) to "🌆"
            else      -> getString(R.string.greet_night) to "🌙"
        }
        binding.tvGreetingTime.text = "$timeGreeting $emoji"
        binding.tvGreeting.text = if (name.isNullOrBlank()) getString(R.string.greet_hi) else name

        // Подзаголовок-мотивация (зависит от времени)
        binding.tvGreetingSub.text = when (hour) {
            in 5..11  -> getString(R.string.greet_sub_morning)
            in 12..17 -> getString(R.string.greet_sub_day)
            in 18..22 -> getString(R.string.greet_sub_evening)
            else      -> getString(R.string.greet_sub_night)
        }

        // Аватар
        if (!photoUri.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(android.net.Uri.parse(photoUri))
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(binding.ivDashboardAvatar)
        } else {
            binding.ivDashboardAvatar.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }

    private fun setupDiaryList() {
        diaryEntries = DiaryEntryController(this, vm, { _binding }, { diaryAdapter })
        diaryAdapter = DiaryAdapter(
            onDelete = { entry -> diaryEntries.deleteEntryWithUndo(entry) },
            onEdit   = { entry -> diaryEntries.showEditEntryDialog(entry) },
            onRate   = { entry -> diaryEntries.showMealFeedbackDialog(entry) }
        )
        binding.rvDiary.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = diaryAdapter
            isNestedScrollingEnabled = true
        }
    }






 /** Открыть новый экран календаря-истории. Дата вернётся через savedStateHandle. */
    private fun showDatePicker() {
        findNavController().navigateAnim(R.id.calendarFragment)
    }

 /** Формирует понятную подпись даты: "Сегодня" / "Вчера" / "12 мая". */
    private fun formatDateLabel(date: String): String {
        val today = vm.today
        // Вычисляем "вчера" в том же формате
        val yesterday = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -1)
        }.let {
            String.format("%04d-%02d-%02d",
                it.get(java.util.Calendar.YEAR),
                it.get(java.util.Calendar.MONTH) + 1,
                it.get(java.util.Calendar.DAY_OF_MONTH))
        }
        return when (date) {
            today     -> getString(R.string.eaten_today_lbl)
            yesterday -> getString(R.string.eaten_yesterday_lbl)
            else -> {
                val parsed = com.nutritrack.app.utils.DateDisplay.parse(date)
                if (parsed != null)
                    getString(R.string.eaten_on, com.nutritrack.app.utils.DateDisplay.dayMonth(parsed))
                else getString(R.string.eaten_on, date)
            }
        }
    }

    private fun observeData() {
        vm.activeProfile.observe(viewLifecycleOwner) { profile ->
            updateHeader(profile?.name, profile?.photoUri)
            smartInsights.loadStreak()
            smartInsights.loadSmartGreeting()
            smartInsights.checkSmartCoach()
        }
        vm.calorieGoal.observe(viewLifecycleOwner) { goal ->
            binding.progressCalories.max = goal
        }

        // Карта цен для отображения стоимости порций в дневнике
        val db = (requireActivity().application as NutriApp).database
        db.productDao().getAllProducts().observe(viewLifecycleOwner) { products ->
            dayPriceMap = products.associate { it.id to it.price }
            diaryAdapter.setPriceMap(dayPriceMap)
            diaryAdapter.setComboMap(products.associate { it.id to it.comboItems })
            updateDayCost(vm.todayEntries.value ?: emptyList())
        }

        vm.todayEntries.observe(viewLifecycleOwner) { entries ->
            diaryAdapter.submitList(entries)
            binding.tvDiaryEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            binding.rvDiary.visibility      = if (entries.isEmpty()) View.GONE else View.VISIBLE
            binding.tvDiaryHint.visibility  = if (entries.isEmpty()) View.GONE else View.VISIBLE
            diaryEntries.refreshDiaryWarnings(entries)

            val cal   = entries.sumOf { it.calories.toDouble() }.toFloat()
            val prot  = entries.sumOf { it.protein.toDouble() }.toFloat()
            val fat   = entries.sumOf { it.fat.toDouble() }.toFloat()
            val carbs = entries.sumOf { it.carbs.toDouble() }.toFloat()
            val goal  = vm.calorieGoal.value ?: 2000

            binding.circularCalorieView.animateTo(cal.toInt(), goal, prot, fat, carbs)
            binding.progressCalories.progress = cal.toInt().coerceAtMost(goal)
            animateCaloriesText(cal.toInt(), goal)
            if (goal > 0 && cal >= goal * 0.85f && cal <= goal * 1.10f) celebrateOncePerDay("confetti_cal")
            binding.tvProtein.text = getString(R.string.ds_g_fmt, prot.toInt())
            binding.tvFat.text     = getString(R.string.ds_g_fmt, fat.toInt())
            binding.tvCarbs.text   = getString(R.string.ds_g_fmt, carbs.toInt())

            // Клетчатка и сахар — только когда у съеденного есть данные
            val fiber = entries.mapNotNull { it.fiber }.sum()
            val sugar = entries.mapNotNull { it.sugar }.sum()
            val parts = buildList {
                if (fiber >= 1f) add(getString(R.string.ds_fiber_fmt, fiber.toInt()))
                if (sugar >= 1f) add(getString(R.string.ds_sugar_fmt, sugar.toInt()))
            }
            binding.tvFiberSugar.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
            binding.tvFiberSugar.text = parts.joinToString("  ·  ")
            suggestions.update((goal - cal.toInt()), entries.isNotEmpty())
            updateDayCost(entries)
        }

        vm.todayWater.observe(viewLifecycleOwner) { entries ->
            waterCard.bind(entries)
        }

        vm.waterGoal.observe(viewLifecycleOwner) { goal ->
            waterCard.setGoalMax(goal)
        }
    }

    private fun showModePicker() {
        val checked = if (isCircularMode) 0 else 1
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ds_cal_mode_title))
            .setSingleChoiceItems(arrayOf("🔵  Круговой", "▬  Горизонтальный"), checked) { dialog, which ->
                isCircularMode = (which == 0)
                prefs.isCircularCalorieMode = isCircularMode
                applyDisplayMode(animate = true)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

    private fun applyDisplayMode(animate: Boolean) {
        val circular   = binding.layoutCircular
        val horizontal = binding.layoutHorizontal
        if (isCircularMode) {
            circular.visibility = View.VISIBLE; horizontal.visibility = View.GONE
            if (animate) { circular.alpha = 0f; circular.animate().alpha(1f).setDuration(350).start() }
        } else {
            horizontal.visibility = View.VISIBLE; circular.visibility = View.GONE
            if (animate) { horizontal.alpha = 0f; horizontal.animate().alpha(1f).setDuration(350).start() }
        }
    }

    // Карта цен продуктов (id → цена за 100 г) для подсчёта стоимости дня
    private var dayPriceMap: Map<Long, Float?> = emptyMap()

    private fun updateDayCost(entries: List<com.nutritrack.app.data.db.entity.DiaryEntryEntity>) {
        val b = _binding ?: return
        var total = 0f
        for (e in entries) {
            val price = dayPriceMap[e.productId] ?: continue
            if (price > 0f) total += e.grams / 100f * price
        }
        if (total > 0f) {
            b.tvDayCost.text = getString(R.string.ds_day_cost_fmt, total.toInt())
            b.tvDayCost.visibility = View.VISIBLE
        } else {
            b.tvDayCost.visibility = View.GONE
        }
    }

 /** Проверку достижений запускаем после отрисовки кадра — иначе первый показ
 * всплывающего достижения совпадает с обновлением списка воды и экран мерцает. */
    private fun deferAchievementCheck() {
        view?.post { achievementsVm.checkAchievements() }
    }

    // Count-up анимация калорий
    private var lastShownCal = 0

    private fun animateCaloriesText(target: Int, goal: Int) {
        if (lastShownCal == target) {
            binding.tvCaloriesValue.text = getString(R.string.ds_cal_value_fmt, target, goal)
            return
        }
        android.animation.ValueAnimator.ofInt(lastShownCal, target).apply {
            duration = 500
            addUpdateListener {
                val v = it.animatedValue as Int
                _binding?.tvCaloriesValue?.text = getString(R.string.ds_cal_value_fmt, v, goal)
            }
            start()
        }
        lastShownCal = target
    }

    // Конфетти (раз в день, выключаемо в настройках)
    private fun celebrateOncePerDay(key: String) {
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        val today = com.nutritrack.app.utils.DateUtils.today()
        val marker = "${key}_done"
        if (prefs.getString(marker, "") != today) {
            prefs.setString(marker, today)
            com.nutritrack.app.utils.HapticHelper.success(view)
            com.nutritrack.app.ui.custom.ConfettiView.celebrate(activity)
        }
    }




 /** Bottom-sheet добавления еды — логика вынесена в AddFoodSheet. */
    private fun showAddFoodDialog() {
        AddFoodSheet(
            fragment = this,
            vm = vm,
            onScanRequest = {
                scanLauncher.launch(android.content.Intent(requireContext(), BarcodeScannerActivity::class.java))
            },
            onVoiceRequest = { handler ->
                onVoiceResult = handler
                ensureMicAndStartVoice()
            },
            onAchievementsCheck = { achievementsVm.checkAchievements() }
        ).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

}
