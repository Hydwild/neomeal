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
import com.nutritrack.app.data.RecipeData
import com.nutritrack.app.databinding.FragmentShoppingListBinding
import com.nutritrack.app.utils.CategoryLabel
import com.nutritrack.app.utils.DateDisplay
import com.nutritrack.app.utils.HapticHelper
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.ProductNameLabel
import com.nutritrack.app.utils.ProductUnits
import com.nutritrack.app.utils.RecipeLabel
import com.nutritrack.app.utils.navigateAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Список покупок: продукты из плана питания на 7 дней вперёд.
 *
 * Блюда раскрываются до ингредиентов:
 *  - рецепты конструктора (productId == -1) — по списку ингредиентов RecipeData;
 *  - комбо-продукты — по составу comboItems ("имя:граммы;…"), пропорционально порции;
 *  - обычные продукты — как есть.
 *
 * Одинаковые ингредиенты с одинаковой единицей суммируются. Количества
 * рецептов бывают не в граммах («2 шт», «1 ст.л.») — такие складываются
 * по числу перед единицей, а вовсе без числа («по вкусу») — считаются разами.
 *
 * Отметки «куплено» хранятся в prefs до смены периода (ключ — имя+единица).
 */
class ShoppingListFragment : Fragment() {

    private var _binding: FragmentShoppingListBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PrefsManager

    /**
     * Позиция списка. [name] — русское имя-ключ (стабильное, для отметок),
     * [qty]+[unit] — количество ("г"/"мл"/"шт"/"ст.л."…); если количество
     * не распарсилось, qty == null и [count] — сколько раз встретилось.
     */
    private data class Item(
        val name: String,
        val unit: String,
        val qty: Float?,
        val count: Int,
        val category: String,
    ) {
        val checkKey: String get() = "$name|$unit"
    }

    private var items: List<Item> = emptyList()
    private var checked: MutableSet<String> = mutableSetOf()
    private var searchQuery: String = ""
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

        // Поиск: фильтрует секции на лету (ищет и по переводу, и по исходному имени)
        binding.etShoppingSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                render()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        loadItems()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Сборка списка

    private fun loadItems() {
        val db = (requireActivity().application as NutriApp).database
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val profileId = com.nutritrack.app.utils.ActiveProfile.id(requireContext(), db)
                val plans = db.mealPlanDao().getPlansForRange(periodStart, periodEnd, profileId)
                if (plans.isEmpty()) return@withContext emptyList()
                val mealItems = db.mealPlanDao().getItemsForPlansSync(plans.map { it.id })

                val products = db.productDao().getAllProductsSync()
                val byId = products.associateBy { it.id }
                val byName = products.associateBy { it.name.lowercase().trim() }
                val recipesByName = RecipeData.allRecipes().associateBy { it.name }

                // Аккумулятор: (имя+единица) -> [qty, count]
                data class Acc(var qty: Float, var parsed: Boolean, var count: Int, val category: String)
                val acc = LinkedHashMap<String, Acc>()

                fun add(name: String, unit: String, qty: Float?, times: Int = 1) {
                    val clean = name.trim()
                    val category = (byName[clean.lowercase()])?.category ?: ""
                    val key = "$clean|$unit"
                    val a = acc.getOrPut(key) { Acc(0f, qty != null, 0, category) }
                    if (qty != null) a.qty += qty
                    a.count += times
                }

                // Граммы/миллилитры обычного продукта или компонента комбо
                fun addProductGrams(name: String, grams: Float) {
                    val unit = getString(
                        if (ProductUnits.isLiquidName(name)) R.string.u_ml else R.string.u_g)
                    add(name, unit, grams)
                }

                for (mi in mealItems) {
                    val itemName = mi.productName.trim()
                    val recipe = if (mi.productId <= 0L) recipesByName[itemName] else null
                    val product = byId[mi.productId] ?: byName[itemName.lowercase()]

                    when {
                        // Рецепт конструктора: раскрываем по ингредиентам.
                        // grams=100 — «одна порция»; ингредиенты рецепта даны
                        // на ВСЕ порции (servings), пересчитываем.
                        recipe != null -> {
                            val portions = (mi.grams / 100f).roundToInt().coerceAtLeast(1)
                            for (ing in recipe.ingredients) {
                                val parsed = com.nutritrack.app.utils.AmountParser.parse(ing.amount)
                                if (parsed != null) {
                                    val (num, unit) = parsed
                                    add(ing.name, unit, num * portions / recipe.servings)
                                } else {
                                    add(ing.name, ing.amount, null, times = portions)
                                }
                            }
                        }

                        // Комбо-продукт: состав "имя:граммы;…" на всё блюдо,
                        // масштабируем на фактическую порцию.
                        product?.comboItems != null -> {
                            val parts = product.comboItems!!.split(";").mapNotNull { p ->
                                val i = p.lastIndexOf(':')
                                if (i <= 0) return@mapNotNull null
                                val g = p.substring(i + 1).toFloatOrNull() ?: return@mapNotNull null
                                p.substring(0, i) to g
                            }
                            val total = parts.sumOf { it.second.toDouble() }.toFloat()
                            if (total > 0f) {
                                val k = mi.grams / total
                                parts.forEach { (n, g) -> addProductGrams(n, g * k) }
                            } else {
                                addProductGrams(itemName, mi.grams)
                            }
                        }

                        // Обычный продукт
                        else -> addProductGrams(itemName, mi.grams)
                    }
                }

                acc.map { (key, a) ->
                    val name = key.substringBeforeLast('|')
                    Item(
                        name = name,
                        unit = key.substringAfterLast('|'),
                        qty = if (a.parsed) a.qty else null,
                        count = a.count,
                        category = a.category
                    )
                }.sortedBy { it.name }
            }
            items = list
            if (_binding != null) render()
        }
    }

    // Отрисовка

    private fun render() {
        val b = binding
        b.containerShopping.removeAllViews()
        if (items.isEmpty()) {
            b.layoutEmpty.visibility = View.VISIBLE
            b.tvShoppingProgress.visibility = View.GONE
            b.btnShare.visibility = View.GONE
            b.etShoppingSearch.visibility = View.GONE
            return
        }
        b.layoutEmpty.visibility = View.GONE
        b.btnShare.visibility = View.VISIBLE
        // Поиск имеет смысл, когда позиций достаточно много
        b.etShoppingSearch.visibility = if (items.size >= 8) View.VISIBLE else View.GONE
        updateProgress()

        val visible = if (searchQuery.isEmpty()) items else items.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                displayName(it).contains(searchQuery, ignoreCase = true)
        }

        // Группируем по категориям; пустая категория — «Прочее» в конец
        val groups = visible.groupBy { it.category }
            .toSortedMap(compareBy({ it.isEmpty() }, { it }))

        for ((category, group) in groups) {
            b.containerShopping.addView(makeSectionCard(category, group))
        }
    }

    private fun updateProgress() {
        val done = items.count { it.checkKey in checked }
        binding.tvShoppingProgress.visibility = View.VISIBLE
        binding.tvShoppingProgress.text = getString(R.string.sl_done_fmt, done, items.size)
    }

    private fun makeSectionCard(category: String, group: List<Item>): View {
        val ctx = requireContext()
        val card = CardView(ctx).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(cardColor())
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

 /** Цвет карточки из токена темы (fallback на ресурс). */
    private fun cardColor(): Int {
        val tv = android.util.TypedValue()
        return if (requireContext().theme.resolveAttribute(R.attr.appCardBg, tv, true)) tv.data
        else requireContext().getColor(R.color.card_bg)
    }

 /** "250 г" / "2 шт" / "по вкусу ×2" — единицы по локали. */
    private fun amountLabel(item: Item): String {
        val q = item.qty
        return if (q != null) {
            val num = if (q % 1f == 0f) q.toInt().toString()
                      else String.format("%.1f", q)
            "$num ${RecipeLabel.trUnit(requireContext(), item.unit)}"
        } else {
            RecipeLabel.trUnit(requireContext(), item.unit) +
                if (item.count > 1) " ×${item.count}" else ""
        }
    }

 /** Имя для показа: продуктовые имена и ингредиенты рецептов переводятся. */
    private fun displayName(item: Item): String {
        val ctx = requireContext()
        val viaProducts = ProductNameLabel.of(ctx, item.name)
        return if (viaProducts != item.name) viaProducts else RecipeLabel.tr(ctx, item.name)
    }

    private fun makeRow(item: Item): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val label = "${displayName(item)} — ${amountLabel(item)}"

        val checkBox = CheckBox(ctx).apply {
            text = label
            setTextColor(ctx.getColor(R.color.white))
            textSize = 14f
            isChecked = item.checkKey in checked
            applyStrike(this)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnCheckedChangeListener { cb, isChecked ->
                HapticHelper.tap(cb)
                if (isChecked) checked.add(item.checkKey) else checked.remove(item.checkKey)
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
                val mark = if (item.checkKey in checked) "☑" else "☐"
                sb.append(mark).append(' ')
                    .append(displayName(item))
                    .append(" — ").append(amountLabel(item)).append('\n')
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
        // Единицы берутся из ресурсов (R.string.u_g / u_ml) — они показываются
        // пользователю и должны переводиться
    }
}
