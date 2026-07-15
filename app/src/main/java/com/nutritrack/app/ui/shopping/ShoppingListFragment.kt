package com.nutritrack.app.ui.shopping

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentShoppingListBinding
import com.nutritrack.app.utils.CategoryLabel
import com.nutritrack.app.utils.DateDisplay
import com.nutritrack.app.utils.HapticHelper
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.ProductNameLabel
import com.nutritrack.app.utils.ProductUnits
import com.nutritrack.app.utils.navigateAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Список покупок: все продукты из плана питания на 7 дней вперёд,
 * сгруппированные по категориям, с чек-боксами «куплено».
 *
 * Отметки хранятся в prefs и живут, пока не сменится период списка
 * (ключ — дата начала периода).
 */
class ShoppingListFragment : Fragment() {

    private var _binding: FragmentShoppingListBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PrefsManager

    /** Одна позиция списка: имя, граммы суммарно, категория. */
    private data class Item(val name: String, val grams: Int, val category: String)

    private var items: List<Item> = emptyList()
    private var checked: MutableSet<String> = mutableSetOf()
    private lateinit var periodStart: String
    private lateinit var periodEnd: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShoppingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())

        val start = LocalDate.now()
        val end = start.plusDays(6)
        periodStart = start.toString()
        periodEnd = end.toString()

        binding.tvShoppingSub.text = getString(
            R.string.sl_sub_fmt, DateDisplay.shortDate(start), DateDisplay.shortDate(end)
        )

        // Отметки прошлого периода сбрасываем
        if (prefs.getString(KEY_PERIOD) != periodStart) {
            prefs.setString(KEY_PERIOD, periodStart)
            prefs.setString(KEY_CHECKED, "")
        }
        checked = prefs.getString(KEY_CHECKED)
            .split(SEP).filter { it.isNotEmpty() }.toMutableSet()

        binding.btnOpenBuilder.setOnClickListener {
            findNavController().navigateAnim(R.id.planBuilderFragment)
        }
        binding.btnShare.setOnClickListener { shareList() }

        loadItems()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadItems() {
        val db = (requireActivity().application as NutriApp).database
        val profileId = prefs.activeProfileId
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val plans = db.mealPlanDao().getPlansForRange(periodStart, periodEnd, profileId)
                if (plans.isEmpty()) return@withContext emptyList()
                val mealItems = db.mealPlanDao().getItemsForPlansSync(plans.map { it.id })
                // Категории берём из справочника продуктов (по id, иначе по имени)
                val products = db.productDao().getAllProductsSync()
                val byId = products.associateBy { it.id }
                val byName = products.associateBy { it.name.lowercase().trim() }

                mealItems
                    .groupBy { it.productName.trim() }
                    .map { (name, group) ->
                        val product = byId[group.first().productId]
                            ?: byName[name.lowercase()]
                        Item(
                            name = name,
                            grams = group.sumOf { it.grams.toDouble() }.toInt(),
                            category = product?.category ?: ""
                        )
                    }
                    .sortedBy { it.name }
            }
            items = list
            if (_binding != null) render()
        }
    }

    private fun render() {
        val b = binding
        b.containerShopping.removeAllViews()
        if (items.isEmpty()) {
            b.layoutEmpty.visibility = View.VISIBLE
            b.tvShoppingProgress.visibility = View.GONE
            b.btnShare.visibility = View.GONE
            return
        }
        b.layoutEmpty.visibility = View.GONE
        b.btnShare.visibility = View.VISIBLE
        updateProgress()

        // Группируем по категориям; пустая категория — «Прочее» в конец
        val groups = items.groupBy { it.category }
            .toSortedMap(compareBy({ it.isEmpty() }, { it }))

        for ((category, group) in groups) {
            b.containerShopping.addView(makeSectionCard(category, group))
        }
    }

    private fun updateProgress() {
        val done = items.count { it.name in checked }
        binding.tvShoppingProgress.visibility = View.VISIBLE
        binding.tvShoppingProgress.text = getString(R.string.sl_done_fmt, done, items.size)
    }

    private fun makeSectionCard(category: String, group: List<Item>): View {
        val ctx = requireContext()
        val card = CardView(ctx).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ctx.getColor(R.color.card_bg))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        col.addView(TextView(ctx).apply {
            text = if (category.isEmpty()) getString(R.string.sl_other_cat)
                   else CategoryLabel.of(ctx, category)
            setTextColor(ctx.getColor(R.color.color_accent))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })
        group.forEach { item -> col.addView(makeRow(item)) }
        card.addView(col)
        return card
    }

    private fun makeRow(item: Item): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val unit = getString(
            if (ProductUnits.isLiquidName(item.name)) R.string.u_ml else R.string.u_g
        )
        val label = "${ProductNameLabel.of(ctx, item.name)} — ${item.grams} $unit"

        val checkBox = CheckBox(ctx).apply {
            text = label
            setTextColor(ctx.getColor(R.color.white))
            textSize = 14f
            isChecked = item.name in checked
            applyStrike(this)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnCheckedChangeListener { cb, isChecked ->
                HapticHelper.tap(cb)
                if (isChecked) checked.add(item.name) else checked.remove(item.name)
                prefs.setString(KEY_CHECKED, checked.joinToString(SEP))
                applyStrike(cb as CheckBox)
                updateProgress()
            }
        }
        row.addView(checkBox)
        return row
    }

    private fun applyStrike(cb: CheckBox) {
        cb.paintFlags =
            if (cb.isChecked) cb.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else cb.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        cb.alpha = if (cb.isChecked) 0.55f else 1f
    }

    // Экспорт в текст (системное окно «Поделиться»)

    private fun shareList() {
        val ctx = requireContext()
        val sb = StringBuilder()
        sb.append("🛒 ").append(getString(R.string.sl_title))
            .append(" (").append(binding.tvShoppingSub.text).append(")\n")
        val groups = items.groupBy { it.category }
            .toSortedMap(compareBy({ it.isEmpty() }, { it }))
        for ((category, group) in groups) {
            sb.append('\n')
                .append(if (category.isEmpty()) getString(R.string.sl_other_cat)
                        else CategoryLabel.of(ctx, category))
                .append(":\n")
            group.forEach { item ->
                val unit = getString(
                    if (ProductUnits.isLiquidName(item.name)) R.string.u_ml else R.string.u_g
                )
                val mark = if (item.name in checked) "☑" else "☐"
                sb.append(mark).append(' ')
                    .append(ProductNameLabel.of(ctx, item.name))
                    .append(" — ").append(item.grams).append(' ').append(unit).append('\n')
            }
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            },
            getString(R.string.sl_share)
        ))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_PERIOD = "shopping_period_start"
        private const val KEY_CHECKED = "shopping_checked"
        // Разделитель имён в prefs — управляющий символ, в названиях не встречается
        private const val SEP = "\u001F"
    }
}
