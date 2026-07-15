package com.nutritrack.app.ui.dashboard

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.adapter.ProductPickerAdapter
import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.data.repository.FoodSearchRepository
import com.nutritrack.app.utils.AllergenData
import com.nutritrack.app.utils.AppLog
import com.nutritrack.app.utils.CompanionHelper
import com.nutritrack.app.utils.DialogHelper
import com.nutritrack.app.utils.GramInputHelper
import com.nutritrack.app.utils.ProductUnits
import com.nutritrack.app.utils.VoiceFoodParser
import com.nutritrack.app.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom-sheet добавления еды: поиск по локальной базе и Open Food Facts,
 * голосовой ввод, сканер, быстрые чипы («повторить вчера», недавние).
 *
 * Вынесен из DashboardFragment; лаунчеры сканера и голоса остаются во
 * фрагменте (их нужно регистрировать в его жизненном цикле) и передаются
 * колбэками.
 */
class AddFoodSheet(
    private val fragment: Fragment,
    private val vm: DashboardViewModel,
    /** Открыть сканер штрихкодов (лаунчер живёт во фрагменте). */
    private val onScanRequest: () -> Unit,
    /** Запустить голосовой ввод; обработчик получит распознанный текст. */
    private val onVoiceRequest: (handler: (String) -> Unit) -> Unit,
    /** Проверка достижений после успешного добавления. */
    private val onAchievementsCheck: () -> Unit,
) {

    private val ctx get() = fragment.requireContext()
    private fun getString(id: Int, vararg args: Any) = ctx.getString(id, *args)
    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION") // SOFT_INPUT_ADJUST_RESIZE — рабочий способ для диалога с клавиатурой
    fun show() {
        val dialog    = BottomSheetDialog(ctx, R.style.BottomSheetStyle)
        val sheetView = fragment.layoutInflater.inflate(R.layout.dialog_add_food, null)
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
        val rvProducts = sheetView.findViewById<RecyclerView>(R.id.rvProducts)
        val tvGrams    = sheetView.findViewById<TextView>(R.id.tvGrams)
        val btnMinus   = sheetView.findViewById<ImageButton>(R.id.btnMinus)
        val btnPlus    = sheetView.findViewById<ImageButton>(R.id.btnPlus)
        val btnAdd     = sheetView.findViewById<Button>(R.id.btnAdd)

        // Кнопка сканирования штрихкода (если есть в layout)
        sheetView.findViewById<ImageButton>(R.id.btnScan)?.setOnClickListener {
            dialog.dismiss()
            onScanRequest()
        }

        var selectedProduct: ProductEntity? = null
        var grams = 100

        fun updateGrams() {
            val p = selectedProduct
            if (p != null) {
                val unit = ProductUnits.unit(p)
                tvGrams.text = getString(R.string.ds_grams_kcal_fmt, grams, unit, (p.calories * grams / 100f).toInt())
            } else {
                tvGrams.text = getString(R.string.ds_grams_g_fmt, grams)
            }
        }
        updateGrams()

        btnMinus.setOnClickListener {
            val p = selectedProduct
            val step = if (p != null) ProductUnits.step(p, grams)
            else if (grams > 100) 50 else 10
            grams = (grams - step).coerceAtLeast(10)
            updateGrams()
        }
        btnPlus.setOnClickListener {
            val p = selectedProduct
            val step = if (p != null) ProductUnits.step(p, grams)
            else if (grams >= 100) 50 else 10
            grams += step
            updateGrams()
        }
        tvGrams.setOnClickListener {
            GramInputHelper.showInputDialog(ctx, grams) { newGrams -> grams = newGrams; updateGrams() }
        }

        val db = (fragment.requireActivity().application as NutriApp).database
        val pickerAdapter = ProductPickerAdapter(
            onSelect = { product ->
                selectedProduct = product
                grams = ProductUnits.defaultAmount(product)
                btnAdd.isEnabled = true; btnAdd.alpha = 1f; updateGrams()
            },
            onFavoriteToggle = { product ->
                // Переключаем избранное локально + в БД
                fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val updated = product.copy(isFavorite = !product.isFavorite)
                    db.productDao().updateProduct(updated)
                }
            }
        )
        rvProducts.layoutManager = LinearLayoutManager(ctx)
        rvProducts.adapter       = pickerAdapter

        // Кэш загруженного списка — фильтруем его (LiveData.value бывает null)
        var allProducts: List<ProductEntity> = emptyList()

        // Автопоиск в Open Food Facts, когда локально пусто (с дебаунсом)
        var webSearchJob: Job? = null

        fun webSearch(query: String, localResults: List<ProductEntity>) {
            webSearchJob?.cancel()
            webSearchJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
                delay(500)   // дебаунс
                try {
                    val products = FoodSearchRepository.searchByName(query)
                    if (etSearch.text.toString().trim() != query) return@launch

                    // имена локальных продуктов — чтобы не дублировать в интернет-части
                    val localNames = localResults.map { it.name.lowercase().trim() }.toSet()

                    val found = products
                        .filter { (it.nutriments?.energyKcal100g ?: 0.0) > 0.0 }
                        .map { p ->
                            val brand = p.brands?.split(",")?.firstOrNull()?.trim()
                            val label = if (!brand.isNullOrBlank() && !p.bestName().contains(brand, true))
                                "${p.bestName()} · $brand" else p.bestName()
                            ProductEntity(
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
                    val combined = ArrayList<ProductEntity>()
                    combined.addAll(localResults)
                    combined.addAll(found)
                    pickerAdapter.submitList(combined)

                    if (found.isNotEmpty()) {
                        Toast.makeText(ctx,
                            getString(R.string.ds_found_online_fmt, found.size), Toast.LENGTH_SHORT).show()
                    } else if (localResults.isEmpty()) {
                        Toast.makeText(ctx,
                            getString(R.string.ds_nothing_found), Toast.LENGTH_SHORT).show()
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                } catch (e: Exception) {
                    // нет сети — оставляем локальные (уже показаны)
                    AppLog.e("online food search failed", e)
                }
            }
        }

        fun applyFilter() {
            val q = etSearch.text.toString().trim()
            val sorted = allProducts.sortedWith(
                compareByDescending<ProductEntity> { it.isFavorite }
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
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { applyFilter() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Загрузка списка. Наблюдатель снимается при закрытии диалога,
        // чтобы повторные открытия не копили обсерверы на LiveData.
        val productsLive = db.productDao().getAllProducts()
        val productsObserver = Observer<List<ProductEntity>> { all ->
            allProducts = all
            applyFilter()
        }
        productsLive.observe(fragment.viewLifecycleOwner, productsObserver)
        dialog.setOnDismissListener {
            productsLive.removeObserver(productsObserver)
            webSearchJob?.cancel()
        }

        btnAdd.isEnabled = false
        btnAdd.alpha     = 0.5f

        // Голосовой ввод: распознать разобрать на продукт + граммы найти и выбрать
        sheetView.findViewById<ImageButton>(R.id.btnVoice)?.setOnClickListener {
            onVoiceRequest { spoken ->
                val parsed = VoiceFoodParser.parse(spoken)
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
                    grams = parsed.grams ?: ProductUnits.defaultAmount(found)
                    btnAdd.isEnabled = true; btnAdd.alpha = 1f
                    updateGrams()
                    val gramInfo = if (parsed.grams != null) ", ${parsed.grams} г" else ""
                    Toast.makeText(ctx,
                        getString(R.string.ds_voice_added_fmt, found.name, gramInfo), Toast.LENGTH_SHORT).show()
                } else {
                    // Не нашли — просто оставляем текст в поиске, пусть выберет вручную
                    if (parsed.grams != null) { grams = parsed.grams; updateGrams() }
                    Toast.makeText(ctx,
                        getString(R.string.ds_pick_from_list), Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnAdd.setOnClickListener {
            selectedProduct?.let { product ->
                // Действие добавления (исходная логика, обёрнута для переиспользования)
                val doAdd = {
                    if (product.id == 0L) {
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            val clean = product.copy(name = product.name.removePrefix("🌐 ").trim())
                            val newId = withContext(Dispatchers.IO) {
                                db.productDao().insertProduct(clean)
                            }
                            val saved = clean.copy(id = newId)
                            vm.addDiaryEntry(saved, grams.toFloat()) { onAchievementsCheck() }
                            CompanionHelper.maybeCheer(ctx, "food")
                            dialog.dismiss()
                            Toast.makeText(ctx, getString(R.string.ds_added_fmt, saved.name, grams, ProductUnits.unit(saved)), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        vm.addDiaryEntry(product, grams.toFloat()) { onAchievementsCheck() }
                        CompanionHelper.maybeCheer(ctx, "food")
                        dialog.dismiss()
                        Toast.makeText(ctx, getString(R.string.ds_added_fmt, product.name, grams, ProductUnits.unit(product)), Toast.LENGTH_SHORT).show()
                    }
                    Unit
                }
                // Проверка на аллергены профиля
                val profile = vm.activeProfile.value
                val userAllergens = AllergenData.parse(profile?.allergens)
                val hits = if (userAllergens.isEmpty()) emptyList()
                    else AllergenData.detect(product.name).filter { it in userAllergens }
                if (hits.isNotEmpty()) {
                    val names = hits.joinToString(", ") { AllergenData.title(ctx, it) }
                    val d = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle(getString(R.string.allergen_warn_title))
                        .setMessage(getString(R.string.allergen_warn_msg_fmt, names))
                        .setPositiveButton(getString(R.string.allergen_warn_add)) { _, _ -> doAdd() }
                        .setNegativeButton(getString(R.string.dlg_cancel), null)
                        .create()
                    d.setOnShowListener { DialogHelper.tint(d, ctx) }
                    d.show()
                } else {
                    doAdd()
                }
            }
        }

        // Быстрый доступ: повторить вчера + недавние/частые
        val quickRow = sheetView.findViewById<LinearLayout>(R.id.quickRow)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            quickRow.removeAllViews()

            // Кнопка "Повторить вчера"
            quickRow.addView(makeQuickChip(getString(R.string.ds_repeat_yesterday), accent = true) {
                vm.repeatYesterday { count ->
                    val msg = if (count > 0) "✅ Скопировано из вчера: $count" else "Вчера записей нет"
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    onAchievementsCheck()
                }
                dialog.dismiss()
            })

            // Недавние (последние уникальные продукты)
            val recent = vm.getRecent()
            if (recent.isNotEmpty()) {
                quickRow.addView(makeQuickLabel("⏱️"))
                recent.forEach { entry ->
                    val unit = ctx.getString(
                        if (ProductUnits.isLiquidName(entry.productName)) R.string.u_ml else R.string.u_g
                    )
                    quickRow.addView(makeQuickChip("${entry.productName} ${entry.grams.toInt()}$unit") {
                        vm.quickAddFromEntry(entry) { onAchievementsCheck() }
                        dialog.dismiss()
                        Toast.makeText(ctx,
                            getString(R.string.ds_entry_ok_fmt, entry.productName), Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }

        dialog.show()
    }

 /** Чип быстрого доступа. */
    private fun makeQuickChip(text: String, accent: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
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
        return TextView(ctx).apply {
            this.text = text
            setTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(dp(4), 0, dp(8), 0) }
            layoutParams = lp
        }
    }
}
