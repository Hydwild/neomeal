package com.nutritrack.app.ui.products

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.nutritrack.app.R
import com.nutritrack.app.adapter.ProductsAdapter
import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.databinding.FragmentProductsBinding
import com.nutritrack.app.viewmodel.DashboardViewModel
import com.nutritrack.app.viewmodel.ProductViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.nutritrack.app.databinding.DialogEditProductBinding
import com.nutritrack.app.data.MicronutrientNorms
import com.nutritrack.app.data.db.AppDatabase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductsFragment : Fragment() {
    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!
    private val vm: ProductViewModel by viewModels()
    private val dashVm: DashboardViewModel by viewModels()
    private lateinit var adapter: ProductsAdapter
    private var pendingPhotoUri: Uri? = null
    private var editDialogBinding: DialogEditProductBinding? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingPhotoUri = it; editDialogBinding?.ivProductPhoto?.setImageURI(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecycler()
        setupSearch()
        setupCategories()
        binding.fabAddProduct.setOnClickListener { showEditDialog(null) }
        binding.fabCombo.setOnClickListener { showComboBuilderDialog() }
        // Долгое нажатие на «+» тоже открывает конструктор (запасной жест)
        binding.fabAddProduct.setOnLongClickListener {
            showComboBuilderDialog()
            true
        }
    }

    private fun setupRecycler() {
        adapter = ProductsAdapter(
            onEdit = { showEditDialog(it) },
            onDelete = { vm.delete(it) },
            onAddToDiary = { product ->
                showGramPicker(product)
            }
        )
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = adapter
        vm.filteredProducts.observe(viewLifecycleOwner) { adapter.submitList(it) }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { vm.setQuery(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupCategories() {
        vm.allCategories.observe(viewLifecycleOwner) { cats ->
            binding.chipGroupCategories.removeAllViews()
            val allChip = Chip(requireContext()).apply {
                text = getString(R.string.pr_all)
                isCheckable = true
                isChecked = true
                setOnCheckedChangeListener { _, checked -> if (checked) vm.setCategory(null) }
            }
            binding.chipGroupCategories.addView(allChip)
            cats.forEach { cat ->
                val chip = Chip(requireContext()).apply {
                    text = com.nutritrack.app.utils.CategoryLabel.of(requireContext(), cat)
                    isCheckable = true
                    setOnCheckedChangeListener { _, checked -> if (checked) vm.setCategory(cat) }
                }
                binding.chipGroupCategories.addView(chip)
            }
        }
    }

    private fun showGramPicker(product: com.nutritrack.app.data.db.entity.ProductEntity) {
        val dialog    = BottomSheetDialog(requireContext(), R.style.BottomSheetStyle)
        val sheetView = layoutInflater.inflate(R.layout.dialog_gram_picker, null)
        dialog.setContentView(sheetView)

        val tvGrams  = sheetView.findViewById<android.widget.TextView>(R.id.tvGrams)
        val btnMinus = sheetView.findViewById<android.widget.ImageButton>(R.id.btnMinus)
        val btnPlus  = sheetView.findViewById<android.widget.ImageButton>(R.id.btnPlus)
        val btnAdd   = sheetView.findViewById<android.widget.Button>(R.id.btnAdd)
        val tvVitTitle  = sheetView.findViewById<android.widget.TextView>(R.id.tvVitTitle)
        val scrollVit   = sheetView.findViewById<View>(R.id.scrollVit)
        val vitContainer = sheetView.findViewById<android.widget.LinearLayout>(R.id.vitContainer)

        var grams = 100
        var gender = "male"

        fun renderVitamins() {
            vitContainer.removeAllViews()
            val ctx = requireContext()
            val factor = grams / 100f
            var shown = 0
            for (n in MicronutrientNorms.all) {
                val per100 = nutrientValue(product, n.key) ?: continue
                if (per100 <= 0f) continue
                val amount = per100 * factor
                val rda = n.rda(gender)
                val pct = if (rda > 0) ((amount / rda) * 100f).toInt() else 0
                val pctColor = when {
                    pct < 15  -> ctx.getColor(R.color.text_secondary)  // мало
                    pct <= 100 -> ctx.getColor(R.color.color_success)  // хорошо
                    else      -> ctx.getColor(R.color.color_warning)   // много
                }
                val left = getString(
                    R.string.gp_vit_left_fmt,
                    n.emoji, nutrientName(n.key), formatAmount(amount), unitLabel(n.unit)
                )
                val pctText = getString(R.string.gp_vit_pct_fmt, pct)
                val full = "$left  $pctText"
                val spannable = android.text.SpannableString(full)
                val start = full.length - pctText.length
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(pctColor),
                    start, full.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start, full.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val row = android.widget.TextView(ctx).apply {
                    text = spannable
                    setTextColor(0xFFE6FFFFFF.toInt())
                    textSize = 13f
                    setPadding(0, dpx(3), 0, dpx(3))
                }
                vitContainer.addView(row)
                shown++
            }
            val vis = if (shown > 0) View.VISIBLE else View.GONE
            tvVitTitle.visibility = vis
            scrollVit.visibility = vis
        }

        fun updateLabel() {
            val cal = (product.calories * grams / 100f).toInt()
            tvGrams.text = getString(R.string.pr_grams_cal_fmt, grams, cal)
            renderVitamins()
        }

        updateLabel()

        // Узнаём пол активного профиля для точной нормы и обновляем витамины
        viewLifecycleOwner.lifecycleScope.launch {
            val g = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext())
                    .profileDao().getActiveProfileSync()?.gender
            }
            if (g != null) { gender = g; renderVitamins() }
        }

        btnMinus.setOnClickListener {
            if (grams > 10) {
                grams = if (grams > 100) (grams - 50).coerceAtLeast(10) else (grams - 10).coerceAtLeast(10)
                updateLabel()
            }
        }
        btnPlus.setOnClickListener {
            grams = if (grams >= 100) grams + 50 else grams + 10
            updateLabel()
        }

        // Клик на число ручной ввод
        tvGrams.setOnClickListener {
            com.nutritrack.app.utils.GramInputHelper.showInputDialog(requireContext(), grams) { newGrams ->
                grams = newGrams
                updateLabel()
            }
        }

        btnAdd.setOnClickListener {
            dashVm.addDiaryEntry(product, grams.toFloat())
            dialog.dismiss()
            android.widget.Toast.makeText(
                requireContext(),
                "✅ Добавлено ${product.name} — ${grams}г",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        dialog.show()
    }


    private fun showEditDialog(product: ProductEntity?) {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetStyle)
        val b = DialogEditProductBinding.inflate(layoutInflater)
        editDialogBinding = b
        dialog.setContentView(b.root)

        product?.let {
            b.etName.setText(it.name)
            b.etCalories.setText(it.calories.toString())
            b.etProtein.setText(it.protein.toString())
            b.etFat.setText(it.fat.toString())
            b.etCarbs.setText(it.carbs.toString())
            b.etCategory.setText(it.category)
            b.etPrice.setText(it.price?.toString() ?: "")
            it.photoUri?.let { uri -> b.ivProductPhoto.setImageURI(Uri.parse(uri)) }
            b.tvTitle.text = getString(R.string.pr_edit)
        }

        b.ivProductPhoto.setOnClickListener { pickImage.launch("image/*") }

        b.btnSave.setOnClickListener {
            val name = b.etName.text.toString().trim()
            if (name.isEmpty()) { b.etName.error = getString(R.string.pr_err_name); return@setOnClickListener }
            val p = ProductEntity(
                id = product?.id ?: 0,
                name = name,
                calories = b.etCalories.text.toString().toFloatOrNull() ?: 0f,
                protein = b.etProtein.text.toString().toFloatOrNull() ?: 0f,
                fat = b.etFat.text.toString().toFloatOrNull() ?: 0f,
                carbs = b.etCarbs.text.toString().toFloatOrNull() ?: 0f,
                category = b.etCategory.text.toString().trim().ifEmpty { "Other" },
                photoUri = pendingPhotoUri?.toString() ?: product?.photoUri,
                isCustom = true,
                price = b.etPrice.text.toString().toFloatOrNull()
            )
            if (product == null) vm.insert(p) else vm.update(p)
            dialog.dismiss()
        }
        dialog.show()
    }

 /**
 * Конструктор комбо: выбираешь несколько продуктов и граммы каждого,
 * приложение считает суммарные КБЖУ и сохраняет комбо как кастомный продукт
 * (с пересчётом на 100 г — чтобы корректно добавлялось в дневник по граммам).
 */
    private fun showComboBuilderDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_combo_builder, null)
        val etName = view.findViewById<android.widget.EditText>(R.id.etComboName)
        val container = view.findViewById<android.widget.LinearLayout>(R.id.containerIngredients)
        val btnAdd = view.findViewById<android.widget.Button>(R.id.btnAddIngredient)
        val tvTotal = view.findViewById<android.widget.TextView>(R.id.tvComboTotal)

        val products = vm.allProducts.value ?: emptyList()
        val names = products.map {
            com.nutritrack.app.utils.ProductNameLabel.of(requireContext(), it.name)
        }
        // карта: отображаемое имя продукт
        val byLabel = products.associateBy {
            com.nutritrack.app.utils.ProductNameLabel.of(requireContext(), it.name)
        }
        val adapter = android.widget.ArrayAdapter(requireContext(),
            android.R.layout.simple_dropdown_item_1line, names)

        // строки ингредиентов: (выбранный продукт, граммы)
        val rows = mutableListOf<View>()

        fun recompute() {
            var cal = 0f; var pr = 0f; var ft = 0f; var cb = 0f; var grams = 0f
            for (row in rows) {
                val ac = row.findViewById<android.widget.AutoCompleteTextView>(R.id.acProduct)
                val eg = row.findViewById<android.widget.EditText>(R.id.etGrams)
                val product = byLabel[ac.text.toString().trim()] ?: continue
                val g = eg.text.toString().toFloatOrNull() ?: continue
                val k = g / 100f
                cal += product.calories * k; pr += product.protein * k
                ft += product.fat * k; cb += product.carbs * k; grams += g
            }
            tvTotal.text = getString(R.string.combo_total_fmt,
                cal.toInt(), pr.toInt(), ft.toInt(), cb.toInt(), grams.toInt())
        }

        fun addRow() {
            val row = layoutInflater.inflate(R.layout.item_combo_ingredient, container, false)
            val ac = row.findViewById<android.widget.AutoCompleteTextView>(R.id.acProduct)
            val eg = row.findViewById<android.widget.EditText>(R.id.etGrams)
            val rm = row.findViewById<android.widget.ImageButton>(R.id.btnRemove)
            ac.setAdapter(adapter)
            val watcher = object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = recompute()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            }
            ac.addTextChangedListener(watcher)
            eg.addTextChangedListener(watcher)
            rm.setOnClickListener { container.removeView(row); rows.remove(row); recompute() }
            container.addView(row); rows.add(row)
        }

        btnAdd.setOnClickListener { addRow() }
        addRow(); addRow() // стартуем с двух строк
        recompute()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.combo_title))
            .setView(view)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.pr_err_name), android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                var cal = 0f; var pr = 0f; var ft = 0f; var cb = 0f; var grams = 0f
                val composition = StringBuilder()
                for (row in rows) {
                    val ac = row.findViewById<android.widget.AutoCompleteTextView>(R.id.acProduct)
                    val eg = row.findViewById<android.widget.EditText>(R.id.etGrams)
                    val product = byLabel[ac.text.toString().trim()] ?: continue
                    val g = eg.text.toString().toFloatOrNull() ?: continue
                    val k = g / 100f
                    cal += product.calories * k; pr += product.protein * k
                    ft += product.fat * k; cb += product.carbs * k; grams += g
                    if (composition.isNotEmpty()) composition.append(";")
                    composition.append("${product.name}:${g.toInt()}")
                }
                if (grams <= 0f) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.combo_empty), android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // Пересчёт на 100 г, чтобы комбо вело себя как обычный продукт
                val per = 100f / grams
                val combo = com.nutritrack.app.data.db.entity.ProductEntity(
                    name = name,
                    calories = cal * per, protein = pr * per, fat = ft * per, carbs = cb * per,
                    category = getString(R.string.combo_category),
                    isCustom = true,
                    comboItems = composition.toString().ifEmpty { null }
                )
                vm.insert(combo)
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.combo_saved_fmt, name), android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .create().also { it.show(); com.nutritrack.app.utils.DialogHelper.tint(it, requireContext()) }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null; editDialogBinding = null }

    // ===== Витамины в шторке выбора граммов =====

    /** Значение нутриента продукта на 100 г по ключу (null, если данных нет). */
    private fun nutrientValue(p: ProductEntity, key: String): Float? = when (key) {
        "vitA" -> p.vitA; "vitC" -> p.vitC; "vitD" -> p.vitD; "vitE" -> p.vitE
        "vitK" -> p.vitK; "vitB6" -> p.vitB6; "vitB12" -> p.vitB12; "folate" -> p.folate
        "calcium" -> p.calcium; "iron" -> p.iron; "magnesium" -> p.magnesium; "zinc" -> p.zinc
        "potassium" -> p.potassium; "sodium" -> p.sodium; "selenium" -> p.selenium
        else -> null
    }

    private fun nutrientName(key: String): String {
        val resId = when (key) {
            "vitA" -> R.string.nutr_vitA; "vitC" -> R.string.nutr_vitC
            "vitD" -> R.string.nutr_vitD; "vitE" -> R.string.nutr_vitE
            "vitK" -> R.string.nutr_vitK; "vitB6" -> R.string.nutr_vitB6
            "vitB12" -> R.string.nutr_vitB12; "folate" -> R.string.nutr_folate
            "calcium" -> R.string.nutr_calcium; "iron" -> R.string.nutr_iron
            "magnesium" -> R.string.nutr_magnesium; "zinc" -> R.string.nutr_zinc
            "potassium" -> R.string.nutr_potassium; "sodium" -> R.string.nutr_sodium
            "selenium" -> R.string.nutr_selenium
            else -> R.string.nutr_vitA
        }
        return getString(resId)
    }

    /** Округление: <10 — один знак после запятой, иначе целое. */
    private fun formatAmount(v: Float): String =
        if (v < 10f) String.format("%.1f", v) else v.toInt().toString()

    private fun unitLabel(unit: String): String = when (unit) {
        "мкг" -> getString(R.string.unit_mcg)
        "мг"  -> getString(R.string.unit_mg)
        else  -> unit
    }

    private fun dpx(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}