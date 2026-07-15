package com.nutritrack.app.ui.mealplan
import com.nutritrack.app.utils.navigateAnim

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.adapter.MealItemsAdapter
import com.nutritrack.app.adapter.ProductPickerAdapter
import com.nutritrack.app.data.db.entity.MealItemEntity
import com.nutritrack.app.data.db.entity.MealPlanEntity
import com.nutritrack.app.databinding.FragmentMealPlanBinding
import com.nutritrack.app.databinding.ItemMealSlotBinding
import com.nutritrack.app.utils.DateUtils
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.ReminderScheduler
import kotlinx.coroutines.*
import com.nutritrack.app.utils.MealRecommender

class MealPlanFragment : Fragment() {

    private var _binding: FragmentMealPlanBinding? = null
    private val binding get() = _binding!!

    private val db    by lazy { (requireActivity().application as NutriApp).database }
    private val prefs by lazy { PrefsManager(requireContext()) }
    private val achievementsVm: com.nutritrack.app.viewmodel.AchievementsViewModel by activityViewModels()

    // Слот, для которого запрашивается разрешение на уведомления
    private var pendingNotif: Triple<MealSlot, MealPlanEntity, ItemMealSlotBinding>? = null

    // Запрос разрешения POST_NOTIFICATIONS (Android 13+)
    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val (slot, meal, card) = pendingNotif ?: return@registerForActivityResult
            pendingNotif = null
            if (granted) {
                prefs.setBool("meal_notif_${slot.key}", true)
                // Берём актуальное время из UI, а не из захваченного meal
                val currentTime = card.tvTime.text?.toString()
                    ?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: meal.time
                ReminderScheduler.schedule(
                    requireContext(), slot.key, currentTime, meal.name, "Время — ${meal.name}!"
                )
                Toast.makeText(requireContext(), getString(R.string.mp_notif_on_fmt, currentTime), Toast.LENGTH_SHORT).show()
            } else {
                card.switchNotif.setOnCheckedChangeListener(null)
                card.switchNotif.isChecked = false
                bindNotifSwitch(card, slot, meal)
                showDeniedDialog()
            }
        }

    private data class MealSlot(
        val key: String,
        val defaultName: String,
        val emoji: String,
        val defaultTime: String
    )

    private val slots by lazy {
        listOf(
            MealSlot("breakfast", getString(R.string.meal_breakfast), "🌅", "08:00"),
            MealSlot("lunch",     getString(R.string.meal_lunch),     "☀️", "13:00"),
            MealSlot("dinner",    getString(R.string.meal_dinner),    "🌙", "19:00"),
            MealSlot("snack",     getString(R.string.meal_snack),     "🍎", "16:00")
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMealPlanBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onResume() {
        super.onResume()
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMealSlots()

        binding.btnPlanBuilder.setOnClickListener {
            // Premium-гейт (пока выключен — этап «временно бесплатно»)
            if (!com.nutritrack.app.utils.PremiumManager.gate(requireContext(),
                    com.nutritrack.app.utils.PremiumManager.Feature.MENU_BUILDER)) return@setOnClickListener
            android.widget.Toast.makeText(requireContext(),
                getString(com.nutritrack.app.R.string.premium_free_badge), android.widget.Toast.LENGTH_SHORT).show()
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigateAnim(com.nutritrack.app.R.id.planBuilderFragment)
        }

        // Список покупок: агрегат продуктов плана на 7 дней вперёд
        binding.btnShoppingList.setOnClickListener {
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigateAnim(com.nutritrack.app.R.id.shoppingListFragment)
        }

        // Секретное достижение: долгое нажатие на заголовок
        binding.tvMealTitle.setOnLongClickListener {
            achievementsVm.unlockSecret("secret_pivca")
            true
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun setupMealSlots() {
        val today     = DateUtils.today()
        val profileId = 1L
        observeSlot(binding.cardBreakfast, slots[0], today, profileId)
        observeSlot(binding.cardLunch,     slots[1], today, profileId)
        observeSlot(binding.cardDinner,    slots[2], today, profileId)
        observeSlot(binding.cardSnack,     slots[3], today, profileId)
    }

    private fun observeSlot(
        card: ItemMealSlotBinding, slot: MealSlot, today: String, profileId: Long
    ) {
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) {
                db.mealPlanDao().getMealPlanByKey(slot.key, today, profileId)
            }
            val meal = existing ?: withContext(Dispatchers.IO) {
                val id = db.mealPlanDao().insertMealPlan(
                    MealPlanEntity(
                        name = slot.defaultName, time = slot.defaultTime,
                        date = today, profileId = profileId, mealKey = slot.key
                    )
                )
                db.mealPlanDao().getMealPlanById(id)
            } ?: return@launch

            bindSlotCard(card, slot, meal)
        }
    }

    private fun loadRecommendations() {
        lifecycleScope.launch {
            val today     = DateUtils.today()
            val profileId = 1L

            // Читаем профиль, дневник и все продукты
            val data = withContext(Dispatchers.IO) {
                val profile  = db.profileDao().getActiveProfileSync()
                val entries  = db.diaryDao().getEntriesForDateSync(today, profile?.id ?: profileId)
                val products = db.productDao().getAllProductsSync()
                Triple(profile, entries, products)
            }
            val (profile, entries, products) = data
            profile ?: return@launch

            // Считаем съеденное
            val eatenCal  = entries.sumOf { it.calories.toDouble() }.toInt()
            val eatenProt = entries.sumOf { it.protein.toDouble() }.toFloat()

            // Дневная норма (используй свой CalorieCalculator)
            val goalCal = com.nutritrack.app.utils.CalorieCalculator.calculateDailyGoal(profile)

            // Генерируем рекомендацию
            val rec = MealRecommender.generate(
                goalCalories  = goalCal,
                eatenCalories = eatenCal,
                eatenProtein  = eatenProt,
                goal          = profile.goal,
                allProducts   = products
            )

            renderRecommendation(rec)
        }
    }

    private fun renderRecommendation(rec: com.nutritrack.app.utils.MealRecommender.Recommendation) {
        binding.cardRecommend.tvRecHeadline.text = rec.headline
        binding.cardRecommend.tvRecDetail.text   = rec.detail

        val container = binding.cardRecommend.layoutRecItems
        container.removeAllViews()

        if (rec.suggestions.isEmpty()) {
            binding.cardRecommend.tvRecListTitle.visibility = View.GONE
            return
        }
        binding.cardRecommend.tvRecListTitle.visibility = View.VISIBLE

        rec.suggestions.forEach { s ->
            val row = layoutInflater.inflate(R.layout.item_recommendation, container, false)
            row.findViewById<android.widget.TextView>(R.id.tvRecIcon).text = categoryEmoji(s.product.category)
            row.findViewById<android.widget.TextView>(R.id.tvRecName).text   = s.product.name
            row.findViewById<android.widget.TextView>(R.id.tvRecReason).text = s.reason
            row.findViewById<android.widget.TextView>(R.id.tvRecGrams).text  = "${s.grams} г"
            row.findViewById<android.widget.TextView>(R.id.tvRecCal).text    = "${s.calories} ккал"

            row.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val profile = db.profileDao().getActiveProfileSync() ?: return@launch
                    val factor  = s.grams / 100f
                    db.diaryDao().insert(
                        com.nutritrack.app.data.db.entity.DiaryEntryEntity(
                            productId   = s.product.id,
                            productName = s.product.name,
                            grams       = s.grams.toFloat(),
                            calories    = s.product.calories * factor,
                            protein     = s.product.protein  * factor,
                            fat         = s.product.fat      * factor,
                            carbs       = s.product.carbs    * factor,
                            date        = DateUtils.today(),
                            profileId   = profile.id
                        )
                    )
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(requireContext(),
                            "✅ Добавлено: ${s.product.name} ${s.grams}г",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            container.addView(row)
        }
    }

    private fun categoryEmoji(category: String): String = when (category) {
        "Мясо" -> "🥩"; "Птица" -> "🍗"; "Рыба" -> "🐟"; "Морепродукты" -> "🦐"
        "Яйца" -> "🥚"; "Молочные" -> "🥛"; "Крупы" -> "🌾"; "Хлеб" -> "🍞"
        "Овощи" -> "🥦"; "Фрукты" -> "🍎"; "Ягоды" -> "🫐"; "Орехи" -> "🥜"
        "Бобовые" -> "🫘"; "Напитки" -> "🥤"; "Спортпит" -> "💪"
        else -> "🍽️"
    }


    private fun bindSlotCard(
        b: ItemMealSlotBinding, slot: MealSlot, meal: MealPlanEntity
    ) {
        // Сверху — всегда тип приёма (Завтрак/Обед/Ужин/Перекус)
        b.tvMealName.text = "${slot.emoji} ${slot.defaultName}"
        // Подзаголовок с названием блюда показываем только если оно НЕ дублируется
        // продуктом в списке (иначе одно и то же видно дважды).
        fun refreshDishSubtitle(items: List<MealItemEntity>) {
            val custom = meal.name.isNotBlank() && meal.name != slot.defaultName
            val duplicatedInList = items.any { it.productName.trim().equals(meal.name.trim(), ignoreCase = true) }
            if (custom && !duplicatedInList) {
                b.tvMealDish.text = com.nutritrack.app.utils.RecipeLabel.tr(b.root.context, meal.name)
                b.tvMealDish.visibility = View.VISIBLE
            } else {
                b.tvMealDish.visibility = View.GONE
            }
        }
        refreshDishSubtitle(emptyList())
        b.tvTime.text     = meal.time

        b.switchNotif.isChecked = prefs.getBool("meal_notif_${slot.key}", false)
        bindNotifSwitch(b, slot, meal)

        b.tvTime.setOnClickListener {
            showTimePicker(slot, meal) { newTime ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.mealPlanDao().updateMealPlan(meal.copy(time = newTime))
                }
                b.tvTime.text = newTime
                if (b.switchNotif.isChecked) {
                    ReminderScheduler.schedule(requireContext(), slot.key, newTime, meal.name, "Время — ${meal.name}!")
                    Toast.makeText(requireContext(), "🔔 Перенесено на $newTime", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val itemsAdapter = MealItemsAdapter { item ->
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mp_delete_fmt, item.productName))
                .setPositiveButton(getString(R.string.c_delete)) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) { db.mealPlanDao().deleteMealItem(item) }
                }
                .setNegativeButton(getString(R.string.c_cancel), null)
                .show()
        }
        b.rvItems.layoutManager = LinearLayoutManager(requireContext())
        b.rvItems.adapter       = itemsAdapter
        b.rvItems.isNestedScrollingEnabled = false

        db.mealPlanDao().getItemsForMeal(meal.id).observe(viewLifecycleOwner) { items ->
            itemsAdapter.submitList(items)
            refreshDishSubtitle(items)
            val cal  = items.sumOf { it.calories.toDouble() }.toFloat()
            val prot = items.sumOf { it.protein.toDouble() }.toFloat()
            val fat  = items.sumOf { it.fat.toDouble() }.toFloat()
            val carb = items.sumOf { it.carbs.toDouble() }.toFloat()
            b.tvMacros.text   = "Б:${prot.toInt()} Ж:${fat.toInt()} У:${carb.toInt()}"
            b.tvCalories.text = b.root.context.getString(R.string.mp_kcal_fmt, cal.toInt())
            b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        b.btnAddProduct.setOnClickListener { showAddProductDialog(meal) }
    }

    private fun bindNotifSwitch(b: ItemMealSlotBinding, slot: MealSlot, meal: MealPlanEntity) {
        b.switchNotif.setOnCheckedChangeListener { _, checked ->
            // Берём АКТУАЛЬНОЕ время из UI, а не из захваченного meal —
            // оно могло быть изменено через TimePicker
            val currentTime = b.tvTime.text?.toString()?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: meal.time

            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingNotif = Triple(slot, meal, b)
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        showRationaleDialog()
                    } else {
                        requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    prefs.setBool("meal_notif_${slot.key}", true)
                    ReminderScheduler.schedule(requireContext(), slot.key, currentTime, meal.name, "Время — ${meal.name}!")
                    val now = java.util.Calendar.getInstance()
                    val target = java.util.Calendar.getInstance().apply {
                        val (h, m) = currentTime.split(":").let {
                            (it.getOrNull(0)?.toIntOrNull() ?: 8) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
                        }
                        set(java.util.Calendar.HOUR_OF_DAY, h)
                        set(java.util.Calendar.MINUTE, m)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val whenStr = if (target.timeInMillis <= now.timeInMillis)
                        "завтра в $currentTime" else "сегодня в $currentTime"
                    Toast.makeText(requireContext(),
                        "🔔 Напоминание $whenStr", Toast.LENGTH_LONG).show()
                }
            } else {
                prefs.setBool("meal_notif_${slot.key}", false)
                ReminderScheduler.cancel(requireContext(), slot.key)
            }
        }
    }

    private fun showRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔔 Нужно разрешение")
            .setMessage(getString(R.string.mp_notif_rationale))
            .setPositiveButton(getString(R.string.c_continue)) { _, _ ->
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(getString(R.string.c_cancel)) { _, _ ->
                pendingNotif?.let { (slot, meal, card) ->
                    card.switchNotif.setOnCheckedChangeListener(null)
                    card.switchNotif.isChecked = false
                    bindNotifSwitch(card, slot, meal)
                }
                pendingNotif = null
            }
            .show()
    }

    private fun showDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.mp_notif_blocked_title))
            .setMessage(getString(R.string.mp_notif_blocked_msg))
            .setPositiveButton("Открыть настройки") { _, _ ->
                startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showTimePicker(slot: MealSlot, meal: MealPlanEntity, onDone: (String) -> Unit) {
        val parts = meal.time.split(":")
        MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(parts.getOrNull(0)?.toIntOrNull() ?: 8)
            .setMinute(parts.getOrNull(1)?.toIntOrNull() ?: 0)
            .setTitleText("Время ${slot.defaultName}")
            .build()
            .also { picker ->
                picker.addOnPositiveButtonClickListener {
                    onDone(String.format("%02d:%02d", picker.hour, picker.minute))
                }
                picker.show(childFragmentManager, "time_${slot.key}")
            }
    }

    private fun showAddProductDialog(meal: MealPlanEntity) {
        val dialog    = BottomSheetDialog(requireContext(), R.style.BottomSheetStyle)
        val sheetView = layoutInflater.inflate(R.layout.dialog_add_food, null)
        dialog.setContentView(sheetView)
        dialog.setOnShowListener {
            val sheet = (it as com.google.android.material.bottomsheet.BottomSheetDialog)
                .findViewById<android.widget.FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
            sheet?.let { fl ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(fl)
                // Растягиваем на всю высоту экрана
                fl.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                behavior.state    = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable    = true
            }
        }


        val etSearch   = sheetView.findViewById<EditText>(R.id.etSearch)
        val rvProducts = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvProducts)
        val tvGrams    = sheetView.findViewById<TextView>(R.id.tvGrams)
        val btnMinus   = sheetView.findViewById<ImageButton>(R.id.btnMinus)
        val btnPlus    = sheetView.findViewById<ImageButton>(R.id.btnPlus)
        val btnAdd     = sheetView.findViewById<Button>(R.id.btnAdd)

        var selectedProduct: com.nutritrack.app.data.db.entity.ProductEntity? = null
        var grams = 100

        fun updateGrams() {
            val p = selectedProduct
            tvGrams.text = if (p != null) {
                "$grams г  (~${(p.calories * grams / 100f).toInt()} ккал)"
            } else "$grams г"
        }
        updateGrams()

        btnMinus.setOnClickListener {
            grams = if (grams > 100) (grams - 50).coerceAtLeast(10) else (grams - 10).coerceAtLeast(10)
            updateGrams()
        }
        btnPlus.setOnClickListener {
            grams = if (grams >= 100) grams + 50 else grams + 10
            updateGrams()
        }
        tvGrams.setOnClickListener {
            com.nutritrack.app.utils.GramInputHelper.showInputDialog(requireContext(), grams) {
                grams = it; updateGrams()
            }
        }

        val pickerAdapter = ProductPickerAdapter(
            onSelect = { product ->
                selectedProduct  = product
                btnAdd.isEnabled = true
                btnAdd.alpha     = 1f
                updateGrams()
            },
            onFavoriteToggle = { product ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val updated = product.copy(isFavorite = !product.isFavorite)
                    db.productDao().updateProduct(updated)
                }
            }
        )
        rvProducts.layoutManager = LinearLayoutManager(requireContext())
        rvProducts.adapter       = pickerAdapter

        // Подписываемся на список продуктов — изменения (включая избранное) сразу видны
        db.productDao().getAllProducts().observe(viewLifecycleOwner) { all ->
            val sorted = all.sortedWith(
                compareByDescending<com.nutritrack.app.data.db.entity.ProductEntity> { it.isFavorite }
                    .thenBy { it.name }
            )
            val q = etSearch.text.toString()
            pickerAdapter.submitList(
                if (q.isEmpty()) sorted
                else sorted.filter { it.name.contains(q, true) }
            )
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString()
                val cur = db.productDao().getAllProducts().value ?: return
                val sorted = cur.sortedWith(
                    compareByDescending<com.nutritrack.app.data.db.entity.ProductEntity> { it.isFavorite }
                        .thenBy { it.name }
                )
                pickerAdapter.submitList(
                    if (q.isEmpty()) sorted else sorted.filter { it.name.contains(q, true) }
                )
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        btnAdd.isEnabled = false
        btnAdd.alpha     = 0.5f
        btnAdd.setOnClickListener {
            val product = selectedProduct ?: return@setOnClickListener
            val factor  = grams / 100f
            lifecycleScope.launch(Dispatchers.IO) {
                db.mealPlanDao().insertMealItem(
                    MealItemEntity(
                        mealPlanId  = meal.id,
                        productId   = product.id,
                        productName = product.name,
                        grams       = grams.toFloat(),
                        calories    = product.calories * factor,
                        protein     = product.protein  * factor,
                        fat         = product.fat      * factor,
                        carbs       = product.carbs    * factor
                    )
                )
            }
            dialog.dismiss()
        }
        dialog.show()
    }
}
