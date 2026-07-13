package com.nutritrack.app.ui.micronutrients

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.nutritrack.app.R
import com.nutritrack.app.data.MicronutrientNorms
import com.nutritrack.app.data.db.AppDatabase
import com.nutritrack.app.databinding.FragmentNutrientGoalBinding
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NutrientGoalFragment : Fragment() {

    private var _binding: FragmentNutrientGoalBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: NutrientGoalViewModel

    // Кэш продуктов и пол профиля — для расчёта % покрытия нормы на чипах
    private var productsCache: List<com.nutritrack.app.data.db.entity.ProductEntity> = emptyList()
    private var genderCache: String = "male"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNutrientGoalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.getInstance(requireContext())
        vm = ViewModelProvider(this, NutrientGoalViewModel.Factory(db))[NutrientGoalViewModel::class.java]

        // Подгружаем продукты и пол для процентов
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                productsCache = db.productDao().getAllProductsSync()
                genderCache = db.profileDao().getActiveProfileSync()?.gender ?: "male"
            }
        }

        buildChips()
        binding.btnBuild.setOnClickListener { onBuild() }
        vm.result.observe(viewLifecycleOwner) { render(it) }
    }

    private fun buildChips() {
        val ctx = requireContext()
        val accent = ctx.getColor(R.color.color_accent)
        val chipOff = android.graphics.Color.parseColor("#2A2342")
        val white = 0xFFFFFFFF.toInt()
        val offText = android.graphics.Color.parseColor("#CCC7E0")
        for (n in MicronutrientNorms.all) {
            val chip = Chip(ctx).apply {
                text = "${n.emoji} ${nutrientName(n.key)}"
                isCheckable = true
                tag = n.key
                // Пилюля: выбран — акцент + белый текст, не выбран — тёмный + приглушённый
                chipBackgroundColor = android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(accent, chipOff)
                )
                setTextColor(android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(white, offText)
                ))
                chipStrokeWidth = 0f
                isCheckedIconVisible = false
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(dp(22).toFloat()).build()
            }
            binding.chipGroup.addView(chip)
        }
    }

    private fun onBuild() {
        val keys = mutableListOf<String>()
        for (i in 0 until binding.chipGroup.childCount) {
            val chip = binding.chipGroup.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) keys.add(chip.tag as String)
        }
        if (keys.isEmpty()) {
            binding.resultContainer.removeAllViews()
            val hint = TextView(requireContext()).apply {
                text = getString(R.string.goal_pick_hint)
                textSize = 14f
                alpha = 0.7f
            }
            binding.resultContainer.addView(hint)
            return
        }
        vm.build(keys)
    }

    private fun render(items: List<DeficitSuggestion>) {
        val ctx = requireContext()
        binding.resultContainer.removeAllViews()
        for (s in items) {
            val block = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            val title = TextView(ctx).apply {
                text = "${s.emoji}  ${nutrientName(s.nutrientKey)}"
                textSize = 16f
                textStyle()
            }
            block.addView(title)

            // Подсказка
            block.addView(TextView(ctx).apply {
                text = getString(R.string.goal_tap_to_add)
                textSize = 11f
                alpha = 0.6f
                setPadding(0, dp(2), 0, dp(4))
            })

            // Продукты — кликабельные чипы (тап → подмешать в план)
            val flow = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }
            // Продукты — строки: сколько грамм покроют дневную норму
            val n = com.nutritrack.app.data.MicronutrientNorms.byKey(s.nutrientKey)
            for (productName in s.products) {
                val per100 = productValue(productName, s.nutrientKey)
                val norm = n?.rda(genderCache)
                val gramsForNorm = if (per100 != null && per100 > 0f && norm != null && norm > 0f)
                    ((norm / per100) * 100f).toInt() else null

                // цвет: чем меньше грамм нужно — тем богаче продукт
                val color = when {
                    gramsForNorm == null -> ctx.getColor(R.color.text_secondary)
                    gramsForNorm <= 100  -> ctx.getColor(R.color.color_success)
                    gramsForNorm <= 300  -> ctx.getColor(R.color.color_warning)
                    else                 -> ctx.getColor(R.color.text_secondary)
                }

                val mainText = if (gramsForNorm != null && norm != null)
                    getString(
                        R.string.goal_row_fmt,
                        com.nutritrack.app.utils.ProductNameLabel.of(ctx, productName),
                        gramsForNorm, formatNorm(norm), unitLabel(n.unit), nutrientName(s.nutrientKey)
                    )
                else com.nutritrack.app.utils.ProductNameLabel.of(ctx, productName)

                val row = TextView(ctx).apply {
                    val sp = android.text.SpannableString(mainText)
                    // подсветим часть с граммами цветом
                    text = sp
                    setTextColor(0xFFE6FFFFFF.toInt())
                    textSize = 14f
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(ctx.getColor(R.color.card_bg))
                        setStroke(dp(1), color)
                    }
                    isClickable = true
                    setOnClickListener { askMealAndAdd(productName) }
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(6)
                row.layoutParams = lp
                flow.addView(row)
            }
            block.addView(flow)
            binding.resultContainer.addView(block)
        }
    }

    /** Спрашивает приём пищи и подмешивает продукт в план питания на сегодня. */
    private fun askMealAndAdd(productName: String) {
        val mealKeys = listOf("breakfast", "lunch", "dinner", "snack")
        val mealNames = arrayOf(
            getString(R.string.meal_breakfast),
            getString(R.string.meal_lunch),
            getString(R.string.meal_dinner),
            getString(R.string.meal_snack)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.goal_add_to_meal_fmt,
                com.nutritrack.app.utils.ProductNameLabel.of(requireContext(), productName)))
            .setItems(mealNames) { _, which ->
                addProductToPlan(productName, mealKeys[which], mealNames[which])
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

    private fun addProductToPlan(productName: String, mealKey: String, mealName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(requireContext())
                val profile = db.profileDao().getActiveProfileSync() ?: return@withContext false
                val product = db.productDao().getAllProductsSync()
                    .firstOrNull { it.name == productName } ?: return@withContext false
                val today = DateUtils.today()
                // находим или создаём приём пищи
                val existing = db.mealPlanDao().getMealPlanByKey(mealKey, today, profile.id)
                val mealId: Long = existing?.id ?: db.mealPlanDao().insertMealPlan(
                    com.nutritrack.app.data.db.entity.MealPlanEntity(
                        name = mealName, time = defaultTimeFor(mealKey),
                        date = today, profileId = profile.id, mealKey = mealKey
                    )
                )
                // подмешиваем продукт (порция 100 г)
                db.mealPlanDao().insertMealItem(
                    com.nutritrack.app.data.db.entity.MealItemEntity(
                        mealPlanId = mealId,
                        productId = product.id,
                        productName = product.name,
                        grams = 100f,
                        calories = product.calories,
                        protein = product.protein,
                        fat = product.fat,
                        carbs = product.carbs
                    )
                )
                true
            }
            if (_binding == null) return@launch
            val msg = if (ok) getString(R.string.goal_added_to_plan_fmt,
                com.nutritrack.app.utils.ProductNameLabel.of(requireContext(), productName), mealName)
                      else getString(R.string.goal_add_failed)
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun defaultTimeFor(mealKey: String): String = when (mealKey) {
        "breakfast" -> "08:00"
        "lunch"     -> "13:00"
        "dinner"    -> "19:00"
        else        -> "16:00"
    }

    private fun TextView.textStyle() {
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun nutrientValue(p: com.nutritrack.app.data.db.entity.ProductEntity, key: String): Float? = when (key) {
        "vitA" -> p.vitA; "vitC" -> p.vitC; "vitD" -> p.vitD; "vitE" -> p.vitE
        "vitK" -> p.vitK; "vitB6" -> p.vitB6; "vitB12" -> p.vitB12; "folate" -> p.folate
        "calcium" -> p.calcium; "iron" -> p.iron; "magnesium" -> p.magnesium; "zinc" -> p.zinc
        "potassium" -> p.potassium; "sodium" -> p.sodium; "selenium" -> p.selenium
        else -> null
    }

    /** Значение нутриента продукта (на 100 г) по имени. */
    private fun productValue(productName: String, key: String): Float? {
        val product = productsCache.firstOrNull { it.name == productName } ?: return null
        return nutrientValue(product, key)
    }

    /** Норма: мелкие значения с десятой долей, крупные — целым. */
    private fun formatNorm(v: Float): String =
        if (v < 10f) String.format("%.1f", v) else v.toInt().toString()

    /** Локализация единиц измерения (хранятся по-русски). */
    private fun unitLabel(unit: String): String = when (unit) {
        "мкг" -> getString(R.string.unit_mcg)
        "мг"  -> getString(R.string.unit_mg)
        else  -> unit
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
