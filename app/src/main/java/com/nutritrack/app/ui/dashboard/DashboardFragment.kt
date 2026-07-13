package com.nutritrack.app.ui.dashboard
import com.nutritrack.app.utils.navigateAnim

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.adapter.DiaryAdapter
import com.nutritrack.app.adapter.ProductPickerAdapter
import com.nutritrack.app.adapter.WaterCupsAdapter
import com.nutritrack.app.data.api.OpenFoodFacts
import com.nutritrack.app.data.db.entity.DiaryEntryEntity
import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.databinding.FragmentDashboardBinding
import com.nutritrack.app.ui.scanner.BarcodeScannerActivity
import com.nutritrack.app.utils.DateUtils
import com.nutritrack.app.utils.GramInputHelper
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.viewmodel.AchievementsViewModel
import com.nutritrack.app.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val vm: DashboardViewModel by viewModels()
    // Общий с MainActivity — он показывает тосты о достижениях
    private val achievementsVm: AchievementsViewModel by activityViewModels()
    private val fastingVm: com.nutritrack.app.viewmodel.FastingViewModel by activityViewModels()

    private lateinit var diaryAdapter: DiaryAdapter
    private lateinit var cupsAdapter: WaterCupsAdapter
    private lateinit var prefs: PrefsManager

    private var isCircularMode = true
    private var isLiveWaterMode = false

    // Сканер штрихкодов — получает результат из BarcodeScannerActivity
    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("barcode")
            if (!barcode.isNullOrBlank()) lookupBarcode(barcode)
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
        isLiveWaterMode = prefs.isLiveWaterMode

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
        setupWaterCups()
        applyDisplayMode(animate = false)
        applyWaterMode(animate = false)
        observeData()
        setupWaterButtons()
        setupPet()
        observeFastingTimer()

        binding.btnAddFood.setOnClickListener { com.nutritrack.app.utils.HapticHelper.tap(it); showAddFoodDialog() }
        // Применяем выбранный шрифт к кнопке (она не наследует его из темы)
        com.nutritrack.app.utils.FontHelper.apply(binding.btnAddFood)
        binding.btnCalorieMode.setOnClickListener { showModePicker() }
        binding.btnWaterMode.setOnClickListener { showWaterModePicker() }

        // Колокольчик на карточке воды экран настроек напоминаний
        binding.btnWaterReminder.setOnClickListener {
            findNavController().navigateAnim(R.id.waterReminderFragment)
        }

        // Заголовок дневника — тап для выбора даты (история прошлых дней)
        binding.rowDiaryHeader.setOnClickListener { showDatePicker() }

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
        // setupPet сам зафиксирует текущий уровень воды как базовый.
        setupPet()
    }

 /** Карточка голодания на главной: видна только при активной сессии. */
    private fun observeFastingTimer() {
        binding.cardFastingTimer.setOnClickListener {
            try { findNavController().navigateAnim(R.id.fastingFragment) } catch (_: Exception) {}
        }
        fastingVm.timerState.observe(viewLifecycleOwner) { state ->
            if (state.isActive && state.protocol != null) {
                binding.cardFastingTimer.visibility = View.VISIBLE
                binding.tvFastingProtocolName.text = state.protocol.name
                binding.tvFastingTimerBig.text = state.elapsedFormatted
                binding.tvFastingPercentBig.text = "${state.progressPercent}%"
                binding.progressFastingDash.progress = state.progressPercent
                binding.tvFastingPhaseDash.text = when (state.phase) {
                    com.nutritrack.app.utils.FastingPhase.EARLY          -> "🍽️ Переваривание"
                    com.nutritrack.app.utils.FastingPhase.KETOSIS        -> "⚡ Кетоз"
                    com.nutritrack.app.utils.FastingPhase.AUTOPHAGY      -> "🧹 Аутофагия"
                    com.nutritrack.app.utils.FastingPhase.DEEP_AUTOPHAGY -> "🧬 Глубокая аутофагия"
                    com.nutritrack.app.utils.FastingPhase.COMPLETE       -> "🏆 Завершено!"
                    com.nutritrack.app.utils.FastingPhase.NOT_STARTED    -> ""
                }
            } else {
                binding.cardFastingTimer.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        waterAnimator?.cancel()
        waterAnimator = null
        binding.petWater.release()
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
        diaryAdapter = DiaryAdapter(
            onDelete = { entry -> vm.deleteDiaryEntry(entry) },
            onEdit   = { entry -> showEditEntryDialog(entry) },
            onRate   = { entry -> showMealFeedbackDialog(entry) }
        )
        binding.rvDiary.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = diaryAdapter
            isNestedScrollingEnabled = true
        }
    }

 /** Диалог изменения количества грамм у записи дневника. */
    private fun showEditEntryDialog(entry: com.nutritrack.app.data.db.entity.DiaryEntryEntity) {
        // Определяем единицу по названию (напиток мл)
        val liquidKeywords = listOf("сок","вода","кофе","чай","молоко","кефир","морс","компот",
            "лимонад","кола","напиток","смузи","коктейль","пиво","вино","квас","какао",
            "латте","капучино","айран","ряженка")
        val unit = if (liquidKeywords.any { entry.productName.lowercase().contains(it) }) "мл" else "г"

        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(entry.grams.toInt().toString())
            setSelection(text.length)
        }
        val container = android.widget.FrameLayout(requireContext()).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ds_edit_title_fmt, entry.productName))
            .setMessage(if (unit == getString(R.string.u_ml)) getString(R.string.ds_how_much_ml) else getString(R.string.ds_how_much_g))
            .setView(container)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                val newGrams = input.text.toString().toFloatOrNull()
                if (newGrams != null && newGrams > 0f) {
                    vm.updateDiaryEntry(entry, newGrams)
                    Toast.makeText(requireContext(),
                        "✅ ${entry.productName} — ${newGrams.toInt()}$unit",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(getString(R.string.c_delete)) { _, _ -> vm.deleteDiaryEntry(entry) }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

 /** Диалог оценки самочувствия после продукта (4 уровня). */
    private fun showMealFeedbackDialog(entry: com.nutritrack.app.data.db.entity.DiaryEntryEntity) {
        val ratings = listOf(
            "great" to getString(R.string.feedback_great),
            "ok" to getString(R.string.feedback_ok),
            "discomfort" to getString(R.string.feedback_discomfort),
            "bad" to getString(R.string.feedback_bad)
        )
        val labels = ratings.map { it.second }.toTypedArray()
        val name = com.nutritrack.app.utils.ProductNameLabel.of(requireContext(), entry.productName)
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.feedback_title_fmt, name))
            .setItems(labels) { _, which ->
                val rating = ratings[which].first
                saveMealFeedback(entry, rating)
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

    private fun saveMealFeedback(entry: com.nutritrack.app.data.db.entity.DiaryEntryEntity, rating: String) {
        val ctx = requireContext().applicationContext
        val profileId = prefs.activeProfileId
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                (ctx as NutriApp).database.mealFeedbackDao().insert(
                    com.nutritrack.app.data.db.entity.MealFeedbackEntity(
                        productId = entry.productId,
                        productName = entry.productName,
                        rating = rating,
                        date = com.nutritrack.app.utils.DateUtils.today(),
                        profileId = profileId
                    )
                )
            }
            android.widget.Toast.makeText(requireContext(),
                getString(R.string.feedback_saved), android.widget.Toast.LENGTH_SHORT).show()
            // Сразу обновим подсказки
            vm.todayEntries.value?.let { refreshDiaryWarnings(it) }
        }
    }

 /** Собирает предупреждения: превышение нормы (как раньше) + проблемные продукты. */
    private fun refreshDiaryWarnings(entries: List<com.nutritrack.app.data.db.entity.DiaryEntryEntity>) {
        val base = com.nutritrack.app.utils.NutritionWarnings.analyze(requireContext(), entries).toMutableMap()
        val ctx = requireContext().applicationContext
        val profileId = prefs.activeProfileId
        viewLifecycleOwner.lifecycleScope.launch {
            val problemEntryIds = withContext(Dispatchers.IO) {
                val dao = (ctx as NutriApp).database.mealFeedbackDao()
                val result = mutableListOf<Long>()
                // Уникальные продукты в списке проверяем число негативных оценок
                val byProduct = entries.groupBy { it.productId }
                for ((pid, group) in byProduct) {
                    if (pid <= 0) continue
                    if (dao.negativeCountForProduct(pid, profileId) >= 2) {
                        group.forEach { result.add(it.id) }
                    }
                }
                result
            }
            val warningText = getString(R.string.feedback_problem_hint)
            problemEntryIds.forEach { id ->
                val existing = base[id]
                // Не перезаписываем предупреждение о норме, а дополняем его
                base[id] = if (existing.isNullOrBlank()) warningText else "$existing\n$warningText"
            }
            diaryAdapter.setWarnings(base)
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

    private fun setupWaterCups() {
        cupsAdapter = WaterCupsAdapter { entry ->
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.ds_del_water_fmt, entry.amountMl))
                .setPositiveButton(getString(R.string.c_delete)) { _, _ -> vm.deleteWater(entry) }
                .setNegativeButton(getString(R.string.c_cancel), null)
                .show()
        }
        binding.rvWaterCups.apply {
            layoutManager = GridLayoutManager(requireContext(), 5)
            adapter = cupsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeData() {
        vm.activeProfile.observe(viewLifecycleOwner) { profile ->
            updateHeader(profile?.name, profile?.photoUri)
            loadStreak()
            loadSmartGreeting()
            checkSmartCoach()
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
            refreshDiaryWarnings(entries)

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
            updateSuggestions((goal - cal.toInt()), entries.isNotEmpty())
            updateDayCost(entries)
        }

        vm.todayWater.observe(viewLifecycleOwner) { entries ->
            cupsAdapter.submitList(entries)
            val total = entries.sumOf { it.amountMl }
            val goal  = vm.waterGoal.value ?: 2000
            reactPetToWater(total, goal)
            binding.progressWater.max = goal
            binding.progressWaterBar.max = goal
            animateWaterProgress(total.coerceAtMost(goal))

            val valueText = getString(R.string.ds_water_value_fmt, total)
            val goalText  = getString(R.string.ds_water_goal_fmt, goal)
            binding.tvWaterValue.text        = valueText
            binding.tvWaterGoal.text         = goalText
            binding.tvWaterValueClassic.text = valueText
            binding.tvWaterGoalClassic.text  = goalText

            val left = (goal - total).coerceAtLeast(0)
            val remainingText = if (left == 0) getString(R.string.ds_water_done) else getString(R.string.ds_water_left_fmt, left)
            val remainingColor = if (left == 0) requireContext().getColor(R.color.color_success)
                                 else requireContext().getColor(R.color.color_water)
            binding.tvWaterRemaining.text = remainingText
            binding.tvWaterRemaining.setTextColor(remainingColor)
            binding.tvWaterRemainingClassic.text = remainingText
            binding.tvWaterRemainingClassic.setTextColor(remainingColor)

            if (left == 0 && total > 0) celebrateOncePerDay("confetti_water")
        }

        vm.waterGoal.observe(viewLifecycleOwner) { goal ->
            binding.progressWater.max = goal
            binding.progressWaterBar.max = goal
        }
    }

 /** Системный множитель анимаций (0 = «убрать анимации» в спец.возможностях). */
    private fun animationsEnabled(): Boolean =
        android.provider.Settings.Global.getFloat(
            requireContext().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f

 /** Плавно дотягивает кольцо воды до нового значения + лёгкий «подскок» талисмана. */
    private var lastWaterShown = 0
    private var waterAnimator: android.animation.ValueAnimator? = null
    private fun animateWaterProgress(target: Int) {
        if (!animationsEnabled()) {
            _binding?.progressWater?.progress = target
            _binding?.progressWaterBar?.progress = target
            lastWaterShown = target
            return
        }
        waterAnimator?.cancel()
        waterAnimator = android.animation.ValueAnimator.ofInt(lastWaterShown, target).apply {
            duration = 600
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                val b = _binding ?: return@addUpdateListener
                val v = it.animatedValue as Int
                b.progressWater.progress = v
                b.progressWaterBar.progress = v
            }
            start()
        }
        lastWaterShown = target

        // Талисман подпрыгивает только в живом режиме (в классическом он скрыт).
        // Если активен анимированный питомец — у него своя реакция, статичный маскот не трогаем.
        if (isLiveWaterMode && !petLoaded) {
            _binding?.imgWaterMascot?.animate()
                ?.translationY(-8f)?.setDuration(160)
                ?.withEndAction {
                    _binding?.imgWaterMascot?.animate()
                        ?.translationY(0f)?.setDuration(220)
                        ?.setInterpolator(android.view.animation.OvershootInterpolator())
                        ?.start()
                }
                ?.start()
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

    private fun showWaterModePicker() {
        val checked = if (isLiveWaterMode) 0 else 1
        val items = arrayOf(
            getString(R.string.ds_water_mode_live),
            getString(R.string.ds_water_mode_classic)
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ds_water_mode_title))
            .setSingleChoiceItems(items, checked) { dialog, which ->
                isLiveWaterMode = (which == 0)
                prefs.isLiveWaterMode = isLiveWaterMode
                applyWaterMode(animate = true)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

    private fun applyWaterMode(animate: Boolean) {
        val live    = binding.layoutWaterLive
        val classic = binding.layoutWaterClassic
        if (isLiveWaterMode) {
            live.visibility = View.VISIBLE; classic.visibility = View.GONE
            // питомец — только в живом режиме и только если загружен
            binding.petWaterContainer.visibility =
                if (petLoaded) View.VISIBLE else View.GONE
            if (animate) { live.alpha = 0f; live.animate().alpha(1f).setDuration(350).start() }
        } else {
            classic.visibility = View.VISIBLE; live.visibility = View.GONE
            binding.petWaterContainer.visibility = View.GONE
            if (animate) { classic.alpha = 0f; classic.animate().alpha(1f).setDuration(350).start() }
        }
    }

    // ===== Питомец у воды =====
    private var petLoaded = false
    private var lastWaterTotal = -1
    private var petGoalCelebrated = false

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

    private fun setupPet() {
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        if (!prefs.petsEnabled) { hidePet(); return }
        val storage = com.nutritrack.app.pets.PetStorage(requireContext())
        val id = prefs.selectedPet
        petLoaded = if (storage.isInstalled(id)) binding.petWater.load(id) else false
        if (petLoaded) {
            // питомец виден только в живом режиме воды
            binding.petWaterContainer.visibility =
                if (isLiveWaterMode) View.VISIBLE else View.GONE
            binding.waterMascotContainer.visibility = View.GONE
            // Тап по питомцу — весёлая реакция (подпрыгивание/радость)
            binding.petWater.setOnClickListener {
                com.nutritrack.app.utils.HapticHelper.tap(it)
                val act = if (com.nutritrack.app.pets.PetStorage(requireContext())
                        .hasAction(prefs.selectedPet, "jump")) "jump" else "happy"
                binding.petWater.play(act)
            }
            // Базовый уровень воды фиксируем по текущему значению, чтобы
            // следующее РЕАЛЬНОЕ добавление вызвало реакцию (а не съелось как «первое»).
            val cur = vm.todayWater.value?.sumOf { it.amountMl } ?: 0
            val goal = vm.waterGoal.value ?: 2000
            lastWaterTotal = cur
            petGoalCelebrated = goal in 1..cur
        } else {
            hidePet()
        }
    }

    private fun hidePet() {
        petLoaded = false
        binding.petWaterContainer.visibility = View.GONE
        binding.waterMascotContainer.visibility = View.VISIBLE
    }

    private fun reactPetToWater(total: Int, goal: Int) {
        if (!petLoaded) { lastWaterTotal = total; return }
        // база ещё не инициализирована (питомец только что появился) — без реакции
        if (lastWaterTotal < 0) {
            lastWaterTotal = total
            petGoalCelebrated = goal in 1..total
            return
        }
        if (total > lastWaterTotal) {
            // вода добавилась
            if (goal in 1..total && !petGoalCelebrated) {
                petGoalCelebrated = true
                binding.petWater.play("happy")
            } else {
                binding.petWater.play("drink")
            }
        }
        if (total < goal) petGoalCelebrated = false
        lastWaterTotal = total
    }

    private fun setupWaterButtons() {
        binding.btnWater100.setOnClickListener { com.nutritrack.app.utils.HapticHelper.tap(it); com.nutritrack.app.utils.CompanionHelper.maybeCheer(requireContext(), "water"); vm.addWater(100) { deferAchievementCheck() } }
        binding.btnWater200.setOnClickListener { com.nutritrack.app.utils.HapticHelper.tap(it); vm.addWater(200) { deferAchievementCheck() } }
        binding.btnWater300.setOnClickListener { com.nutritrack.app.utils.HapticHelper.tap(it); com.nutritrack.app.utils.CompanionHelper.maybeCheer(requireContext(), "water"); vm.addWater(300) { deferAchievementCheck() } }
        binding.btnWater500.setOnClickListener { com.nutritrack.app.utils.HapticHelper.tap(it); vm.addWater(500) { deferAchievementCheck() } }
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

    // Стрик с учётом заморозок и отпуска
    private fun loadStreak() {
        val db = (requireActivity().application as com.nutritrack.app.NutriApp).database
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        val profileId = prefs.activeProfileId
        val goal = vm.calorieGoal.value ?: 2000

        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val end = java.time.LocalDate.now()
            val start = end.minusDays(60)
            val entries = db.diaryDao().getEntriesForRange(start.format(fmt), end.format(fmt), profileId)
            val byDay = entries.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { e -> e.calories.toDouble() } }

            val frozen = prefs.frozenDates
            val vacationOn = prefs.vacationMode
            val vacStart = prefs.vacationStart

            val shown = com.nutritrack.app.utils.StreakCalculator.calculate(
                byDay = byDay, goal = goal, end = end,
                frozen = frozen, vacationOn = vacationOn, vacationStart = vacStart
            )

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val b = _binding ?: return@withContext
                when {
                    vacationOn -> {
                        b.tvStreak.visibility = View.VISIBLE
                        b.tvStreak.text = getString(R.string.ds_streak_vac)
                    }
                    shown >= 2 -> {
                        b.tvStreak.visibility = View.VISIBLE
                        b.tvStreak.text = getString(R.string.ds_streak_fmt, shown, daysWord(shown))
                    }
                    else -> b.tvStreak.visibility = View.GONE
                }
            }
        }
    }

    private fun daysWord(n: Int): String {
        val m10 = n % 10; val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> getString(R.string.ds_day1)
            m10 in 2..4 && m100 !in 12..14 -> getString(R.string.ds_day24)
            else -> getString(R.string.ds_day5)
        }
    }

    // Умное приветствие по вчерашним данным
    private fun loadSmartGreeting() {
        val db = (requireActivity().application as com.nutritrack.app.NutriApp).database
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        val profileId = prefs.activeProfileId
        val calGoal = vm.calorieGoal.value ?: 2000
        val waterGoal = vm.waterGoal.value ?: 2000

        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val yesterday = java.time.LocalDate.now().minusDays(1).format(fmt)
            val entries = db.diaryDao().getEntriesForDateAndProfile(yesterday, profileId)
            val yCal = entries.sumOf { it.calories.toDouble() }
            val yWater = try {
                db.waterDao().getWaterForDateSync(yesterday, profileId).sumOf { it.amountMl }
            } catch (_: Exception) { -1 }

            val insight: String? = when {
                entries.isEmpty() -> null
                yWater in 1 until (waterGoal * 0.6).toInt() ->
                    getString(R.string.ds_y_water_low)
                yCal > 0 && (yCal / calGoal) in 0.85..1.10 ->
                    getString(R.string.ds_y_ok)
                yCal > calGoal * 1.25 ->
                    getString(R.string.ds_y_much)
                yCal in 1.0..(calGoal * 0.6) ->
                    getString(R.string.ds_y_low)
                else -> null
            }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val b = _binding ?: return@withContext
                if (insight != null) b.tvGreetingSub.text = insight
            }
        }
    }

    @Suppress("DEPRECATION") // SOFT_INPUT_ADJUST_RESIZE — рабочий способ для диалога с клавиатурой
    private fun showAddFoodDialog() {
        val dialog    = BottomSheetDialog(requireContext(), R.style.BottomSheetStyle)
        val sheetView = layoutInflater.inflate(R.layout.dialog_add_food, null)
        dialog.setContentView(sheetView)

        dialog.setOnShowListener {
            val sheet = (it as BottomSheetDialog).findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            sheet?.let { fl ->
                val behavior = BottomSheetBehavior.from(fl)
                fl.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val etSearch   = sheetView.findViewById<EditText>(R.id.etSearch)
        val rvProducts = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvProducts)
        val tvGrams    = sheetView.findViewById<TextView>(R.id.tvGrams)
        val btnMinus   = sheetView.findViewById<ImageButton>(R.id.btnMinus)
        val btnPlus    = sheetView.findViewById<ImageButton>(R.id.btnPlus)
        val btnAdd     = sheetView.findViewById<Button>(R.id.btnAdd)

        // Кнопка сканирования штрихкода (если есть в layout)
        sheetView.findViewById<ImageButton>(R.id.btnScan)?.setOnClickListener {
            dialog.dismiss()
            scanLauncher.launch(android.content.Intent(requireContext(), BarcodeScannerActivity::class.java))
        }

        var selectedProduct: ProductEntity? = null
        var grams = 100

        fun updateGrams() {
            val p = selectedProduct
            if (p != null) {
                val unit = com.nutritrack.app.utils.ProductUnits.unit(p)
                tvGrams.text = getString(R.string.ds_grams_kcal_fmt, grams, unit, (p.calories * grams / 100f).toInt())
            } else {
                tvGrams.text = getString(R.string.ds_grams_g_fmt, grams)
            }
        }
        updateGrams()

        btnMinus.setOnClickListener {
            val p = selectedProduct
            val step = if (p != null) com.nutritrack.app.utils.ProductUnits.step(p, grams)
            else if (grams > 100) 50 else 10
            grams = (grams - step).coerceAtLeast(10)
            updateGrams()
        }
        btnPlus.setOnClickListener {
            val p = selectedProduct
            val step = if (p != null) com.nutritrack.app.utils.ProductUnits.step(p, grams)
            else if (grams >= 100) 50 else 10
            grams += step
            updateGrams()
        }
        tvGrams.setOnClickListener {
            GramInputHelper.showInputDialog(requireContext(), grams) { newGrams -> grams = newGrams; updateGrams() }
        }

        val db = (requireActivity().application as NutriApp).database
        val pickerAdapter = ProductPickerAdapter(
            onSelect = { product ->
                selectedProduct = product
                grams = com.nutritrack.app.utils.ProductUnits.defaultAmount(product)
                btnAdd.isEnabled = true; btnAdd.alpha = 1f; updateGrams()
            },
            onFavoriteToggle = { product ->
                // Переключаем избранное локально + в БД
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val updated = product.copy(isFavorite = !product.isFavorite)
                    db.productDao().updateProduct(updated)
                }
            }
        )
        rvProducts.layoutManager = LinearLayoutManager(requireContext())
        rvProducts.adapter       = pickerAdapter

        // Кэш загруженного списка — фильтруем его (LiveData.value бывает null)
        var allProducts: List<com.nutritrack.app.data.db.entity.ProductEntity> = emptyList()

        // Автопоиск в Open Food Facts, когда локально пусто (с дебаунсом)
        var webSearchJob: kotlinx.coroutines.Job? = null

        fun webSearch(query: String, localResults: List<com.nutritrack.app.data.db.entity.ProductEntity>) {
            webSearchJob?.cancel()
            webSearchJob = viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(500)   // дебаунс
                try {
                    val svc = com.nutritrack.app.data.api.OpenFoodFacts.service
                    val enc = java.net.URLEncoder.encode(query, "UTF-8")
                    fun buildUrl(host: String) =
                        "https://$host/cgi/search.pl?search_terms=$enc" +
                                "&search_simple=1&action=process&json=1&page_size=30" +
                                "&sort_by=unique_scans_n&fields=product_name,product_name_ru,brands,nutriments,image_url,code"

                    val products = withContext(Dispatchers.IO) {
                        val acc = LinkedHashMap<String, com.nutritrack.app.data.api.OffProduct>()
                        try {
                            svc.searchByUrl(buildUrl("ru.openfoodfacts.org")).products.forEach { p ->
                                val key = p.code ?: p.bestName()
                                if (p.hasName()) acc.putIfAbsent(key, p)
                            }
                        } catch (e: Exception) { com.nutritrack.app.utils.AppLog.e("OFF ru search failed", e) }
                        try {
                            svc.searchByUrl(buildUrl("world.openfoodfacts.org")).products.forEach { p ->
                                val key = p.code ?: p.bestName()
                                if (p.hasName()) acc.putIfAbsent(key, p)
                            }
                        } catch (e: Exception) { com.nutritrack.app.utils.AppLog.e("OFF world search failed", e) }
                        acc.values.toList()
                    }
                    if (etSearch.text.toString().trim() != query) return@launch

                    // имена локальных продуктов — чтобы не дублировать в интернет-части
                    val localNames = localResults.map { it.name.lowercase().trim() }.toSet()

                    val found = products
                        .filter { (it.nutriments?.energyKcal100g ?: 0.0) > 0.0 }
                        .map { p ->
                            val brand = p.brands?.split(",")?.firstOrNull()?.trim()
                            val label = if (!brand.isNullOrBlank() && !p.bestName().contains(brand, true))
                                "${p.bestName()} · $brand" else p.bestName()
                            com.nutritrack.app.data.db.entity.ProductEntity(
                                id = 0L,
                                name = "🌐 $label",
                                calories = (p.nutriments?.energyKcal100g ?: 0.0).toFloat(),
                                protein  = (p.nutriments?.proteins100g ?: 0.0).toFloat(),
                                fat      = (p.nutriments?.fat100g ?: 0.0).toFloat(),
                                carbs    = (p.nutriments?.carbs100g ?: 0.0).toFloat(),
                                category = getString(R.string.cat_internet),
                                isCustom = true
                            )
                        }
                        .filter { it.name.removePrefix("🌐 ").lowercase().trim() !in localNames }
                        .distinctBy { it.name }
                        .take(30)

                    // запрос мог измениться, пока грузилось
                    if (etSearch.text.toString().trim() != query) return@launch

                    // Локальные — сверху, интернет — снизу
                    val combined = ArrayList<com.nutritrack.app.data.db.entity.ProductEntity>()
                    combined.addAll(localResults)
                    combined.addAll(found)
                    pickerAdapter.submitList(combined)

                    if (found.isNotEmpty()) {
                        Toast.makeText(requireContext(),
                            getString(R.string.ds_found_online_fmt, found.size), Toast.LENGTH_SHORT).show()
                    } else if (localResults.isEmpty()) {
                        Toast.makeText(requireContext(),
                            getString(R.string.ds_nothing_found), Toast.LENGTH_SHORT).show()
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                } catch (e: Exception) {
                    // нет сети — оставляем локальные (уже показаны)
                    com.nutritrack.app.utils.AppLog.e("online food search failed", e)
                }
            }
        }

        fun applyFilter() {
            val q = etSearch.text.toString().trim()
            val sorted = allProducts.sortedWith(
                compareByDescending<com.nutritrack.app.data.db.entity.ProductEntity> { it.isFavorite }
                    .thenBy { it.name }
            )
            val result = if (q.isEmpty()) sorted
            else sorted.filter { it.name.contains(q, ignoreCase = true) }
            pickerAdapter.submitList(ArrayList(result))

            // При осмысленном запросе ищем в Open Food Facts и дописываем СНИЗУ
            // (локальные продукты остаются сверху, интернет дополняет).
            if (q.length >= 3) webSearch(q, result)
            else webSearchJob?.cancel()
        }

        // Слушатель ввода — навешиваем сразу
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilter() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Загрузка списка
        db.productDao().getAllProducts().observe(viewLifecycleOwner) { all ->
            allProducts = all
            applyFilter()
        }

        btnAdd.isEnabled = false
        btnAdd.alpha     = 0.5f

        // Голосовой ввод: распознать разобрать на продукт + граммы найти и выбрать
        sheetView.findViewById<ImageButton>(R.id.btnVoice)?.setOnClickListener {
            onVoiceResult = handler@{ spoken ->
                val parsed = com.nutritrack.app.utils.VoiceFoodParser.parse(spoken)
                // Подставляем название в поиск
                etSearch.setText(parsed.query)
                applyFilter()
                // Пытаемся сразу найти лучший продукт
                val found = allProducts.firstOrNull {
                    it.name.equals(parsed.query, ignoreCase = true)
                } ?: allProducts.firstOrNull {
                    it.name.contains(parsed.query, ignoreCase = true)
                }
                if (found != null) {
                    selectedProduct = found
                    grams = parsed.grams ?: com.nutritrack.app.utils.ProductUnits.defaultAmount(found)
                    btnAdd.isEnabled = true; btnAdd.alpha = 1f
                    updateGrams()
                    val gramInfo = if (parsed.grams != null) ", ${parsed.grams} г" else ""
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.ds_voice_added_fmt, found.name, gramInfo), android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // Не нашли — просто оставляем текст в поиске, пусть выберет вручную
                    if (parsed.grams != null) { grams = parsed.grams; updateGrams() }
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.ds_pick_from_list), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            ensureMicAndStartVoice()
        }
        btnAdd.setOnClickListener {
            selectedProduct?.let { product ->
                // Действие добавления (исходная логика, обёрнута для переиспользования)
                val doAdd = {
                    if (product.id == 0L) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val clean = product.copy(name = product.name.removePrefix("🌐 ").trim())
                            val newId = withContext(Dispatchers.IO) {
                                val db2 = (requireActivity().application as NutriApp).database
                                db2.productDao().insertProduct(clean)
                            }
                            val saved = clean.copy(id = newId)
                            vm.addDiaryEntry(saved, grams.toFloat()) { achievementsVm.checkAchievements() }
                            com.nutritrack.app.utils.CompanionHelper.maybeCheer(requireContext(), "food")
                            dialog.dismiss()
                            Toast.makeText(requireContext(), getString(R.string.ds_added_fmt, saved.name, grams, com.nutritrack.app.utils.ProductUnits.unit(saved)), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        vm.addDiaryEntry(product, grams.toFloat()) { achievementsVm.checkAchievements() }
                        com.nutritrack.app.utils.CompanionHelper.maybeCheer(requireContext(), "food")
                        dialog.dismiss()
                        Toast.makeText(requireContext(), getString(R.string.ds_added_fmt, product.name, grams, com.nutritrack.app.utils.ProductUnits.unit(product)), Toast.LENGTH_SHORT).show()
                    }
                    Unit
                }
                // Проверка на аллергены профиля
                val profile = vm.activeProfile.value
                val userAllergens = com.nutritrack.app.utils.AllergenData.parse(profile?.allergens)
                val hits = if (userAllergens.isEmpty()) emptyList()
                    else com.nutritrack.app.utils.AllergenData.detect(product.name).filter { it in userAllergens }
                if (hits.isNotEmpty()) {
                    val names = hits.joinToString(", ") { com.nutritrack.app.utils.AllergenData.title(requireContext(), it) }
                    val d = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.allergen_warn_title))
                        .setMessage(getString(R.string.allergen_warn_msg_fmt, names))
                        .setPositiveButton(getString(R.string.allergen_warn_add)) { _, _ -> doAdd() }
                        .setNegativeButton(getString(R.string.dlg_cancel), null)
                        .create()
                    d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, requireContext()) }
                    d.show()
                } else {
                    doAdd()
                }
            }
        }

        // Быстрый доступ: повторить вчера + недавние/частые
        val quickRow = sheetView.findViewById<LinearLayout>(R.id.quickRow)
        viewLifecycleOwner.lifecycleScope.launch {
            quickRow.removeAllViews()

            // Кнопка "Повторить вчера"
            quickRow.addView(makeQuickChip(getString(R.string.ds_repeat_yesterday), accent = true) {
                vm.repeatYesterday { count ->
                    val msg = if (count > 0) "✅ Скопировано из вчера: $count" else "Вчера записей нет"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    achievementsVm.checkAchievements()
                }
                dialog.dismiss()
            })

            // Недавние (последние уникальные продукты)
            val recent = vm.getRecent()
            if (recent.isNotEmpty()) {
                quickRow.addView(makeQuickLabel("⏱️"))
                recent.forEach { entry ->
                    val unit = if (isLiquidNameLocal(entry.productName)) "мл" else "г"
                    quickRow.addView(makeQuickChip("${entry.productName} ${entry.grams.toInt()}$unit") {
                        vm.quickAddFromEntry(entry) { achievementsVm.checkAchievements() }
                        dialog.dismiss()
                        Toast.makeText(requireContext(),
                            getString(R.string.ds_entry_ok_fmt, entry.productName), Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }

        dialog.show()
    }

 /** Чип быстрого доступа. */
    private fun makeQuickChip(text: String, accent: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setBackgroundResource(
                if (accent) R.drawable.chip_selected else R.drawable.chip_bg
            )
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(8), 0) }
            layoutParams = lp
            maxLines = 1
            setOnClickListener { onClick() }
        }
    }

    private fun makeQuickLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            textSize = 16f
            gravity = android.view.Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(dp(4), 0, dp(8), 0) }
            layoutParams = lp
        }
    }

    private fun isLiquidNameLocal(name: String): Boolean {
        val n = name.lowercase()
        return listOf("сок","вода","кофе","чай","молоко","кефир","морс","компот","лимонад",
            "кола","напиток","смузи","коктейль","пиво","вино","квас","какао","латте","капучино",
            "айран","ряженка").any { n.contains(it) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Карточка подсказок: чем добить норму + усилить блюдо. */
    private fun updateSuggestions(remaining: Int, hasEntries: Boolean) {
        val b = _binding ?: return
        val profile = vm.activeProfile.value
        val allergens = com.nutritrack.app.utils.AllergenData.parse(profile?.allergens)
        val diets = profile?.diets?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val fill = com.nutritrack.app.utils.CalorieSuggester.fillGap(requireContext(), remaining, allergens, diets)
        val boost = com.nutritrack.app.utils.CalorieSuggester.boosters(requireContext(), allergens, diets)

        // Показываем добор, если есть заметный остаток; иначе усилители (если уже ел)
        val (list, subtitle) = when {
            remaining > 150 -> fill to getString(R.string.sug_sub_fill_fmt, remaining)
            hasEntries -> boost to getString(R.string.sug_sub_boost)
            else -> emptyList<com.nutritrack.app.utils.CalorieSuggester.Suggestion>() to ""
        }
        if (list.isEmpty()) { b.cardSuggestions.visibility = View.GONE; return }

        b.cardSuggestions.visibility = View.VISIBLE
        b.tvSuggestSub.text = subtitle
        val container = b.containerSuggest
        container.removeAllViews()
        list.take(5).forEach { s -> container.addView(buildSuggestionRow(s)) }

        // Сворачивание/разворачивание по нажатию на заголовок (по умолчанию свёрнуто)
        b.rowSuggestHeader.setOnClickListener {
            val show = b.suggestBody.visibility != View.VISIBLE
            b.suggestBody.visibility = if (show) View.VISIBLE else View.GONE
            b.tvSuggestArrow.text = if (show) "▾" else "▸"
        }
    }

    private fun buildSuggestionRow(s: com.nutritrack.app.utils.CalorieSuggester.Suggestion): View {
        val ctx = requireContext()
        val row = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(android.widget.TextView(ctx).apply {
            text = s.emoji; textSize = 18f; setPadding(0, 0, dp(12), 0)
        })
        val mid = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        mid.addView(android.widget.TextView(ctx).apply {
            text = s.title
            setTextColor(ctx.getColor(R.color.white)); textSize = 14f
        })
        if (s.conflict && s.conflictReason != null) {
            mid.addView(android.widget.TextView(ctx).apply {
                text = "⚠ " + s.conflictReason
                setTextColor(android.graphics.Color.parseColor("#FFB74D")); textSize = 11f
                setPadding(0, dp(1), 0, 0)
            })
        }
        row.addView(mid)
        row.addView(android.widget.TextView(ctx).apply {
            text = getString(R.string.sug_kcal_prot_fmt, s.kcal, s.proteinG)
            setTextColor(ctx.getColor(R.color.color_accent)); textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        return row
    }

    // Сканер штрихкодов: поиск в Open Food Facts

    private fun lookupBarcode(barcode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    OpenFoodFacts.service.getProductByBarcode(barcode)
                }
                val p = resp.product
                val n = p?.nutriments
                if (resp.status == 1 && p != null && n != null) {
                    showFoundProductDialog(
                        name     = p.bestName(),
                        calories = (n.energyKcal100g ?: 0.0).toFloat(),
                        protein  = (n.proteins100g ?: 0.0).toFloat(),
                        fat      = (n.fat100g ?: 0.0).toFloat(),
                        carbs    = (n.carbs100g ?: 0.0).toFloat(),
                        startGrams = p.servingGrams() ?: 100
                    )
                } else {
                    Toast.makeText(requireContext(),
                        getString(R.string.ds_not_in_base), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                com.nutritrack.app.utils.AppLog.e("barcode lookup failed", e)
                Toast.makeText(requireContext(),
                    getString(R.string.ds_net_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showFoundProductDialog(
        name: String, calories: Float, protein: Float, fat: Float, carbs: Float,
        startGrams: Int = 100
    ) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.ds_grams_hint)
            setText(startGrams.toString())
            setPadding(50, 30, 50, 30)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setMessage(getString(R.string.ds_per100_fmt, calories.toInt(), protein.toInt(), fat.toInt(), carbs.toInt()))
            .setView(input)
            .setPositiveButton(getString(R.string.c_add)) { _, _ ->
                val grams = input.text.toString().toFloatOrNull() ?: 100f
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = (requireActivity().application as NutriApp).database
                    val profile = db.profileDao().getActiveProfileSync() ?: return@launch
                    val factor = grams / 100f

                    // Сохраняем в свою базу как кастомный продукт
                    db.productDao().insertProduct(
                        ProductEntity(
                            name = name, calories = calories, protein = protein,
                            fat = fat, carbs = carbs, category = "Сканер", isCustom = true
                        )
                    )
                    // Пишем в дневник
                    db.diaryDao().insert(
                        DiaryEntryEntity(
                            productId = 0, productName = name, grams = grams,
                            calories = calories * factor, protein = protein * factor,
                            fat = fat * factor, carbs = carbs * factor,
                            date = DateUtils.today(), profileId = profile.id
                        )
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(),
                            getString(R.string.ds_added2_fmt, name, grams.toInt()), Toast.LENGTH_SHORT).show()
                        achievementsVm.checkAchievements()
                    }
                }
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

    //  Умный тренер
    private fun checkSmartCoach() {
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        if (!prefs.smartCoachEnabled) return
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = java.time.LocalDate.now()
        val last = prefs.lastCoachCheck
        if (last.isNotEmpty() &&
            java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(last, fmt), today) < 14) return

        val profile = vm.activeProfile.value ?: return
        val db = (requireActivity().application as com.nutritrack.app.NutriApp).database

        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val history = db.weightDao().getAllWeightSync(profile.id)
            val s = com.nutritrack.app.utils.SmartCoach.suggest(requireContext(), profile.goal, history)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (_binding == null) return@withContext
                if (s == null) { prefs.lastCoachCheck = today.format(fmt); return@withContext }
                val sign = if (s.deltaKcal > 0) "+" else ""
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("🤖 Умный тренер")
                    .setMessage(s.message + "\n\nНовая норма: ${(vm.calorieGoal.value ?: 2000) + s.deltaKcal} ккал/день.")
                    .setPositiveButton("Применить $sign${s.deltaKcal} ккал") { _, _ ->
                        prefs.calorieAdjustment += s.deltaKcal
                        com.nutritrack.app.utils.CalorieCalculator.adjustment = prefs.calorieAdjustment
                        prefs.lastCoachCheck = today.format(fmt)
                        com.nutritrack.app.utils.HapticHelper.success(view)
                        android.widget.Toast.makeText(requireContext(),
                            "Норма обновлена", android.widget.Toast.LENGTH_SHORT).show()
                        requireActivity().recreate()
                    }
                    .setNegativeButton("Не сейчас") { _, _ -> prefs.lastCoachCheck = today.format(fmt) }
                    .show()
            }
        }
    }

}
