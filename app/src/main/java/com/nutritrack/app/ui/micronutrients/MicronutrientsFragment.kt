package com.nutritrack.app.ui.micronutrients

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.nutritrack.app.R
import com.nutritrack.app.data.db.AppDatabase
import com.nutritrack.app.databinding.FragmentMicronutrientsBinding
import com.nutritrack.app.utils.PrefsManager

class MicronutrientsFragment : Fragment() {

    private var _binding: FragmentMicronutrientsBinding? = null
    private val binding get() = _binding!!

    private lateinit var vm: MicronutrientViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMicronutrientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getInstance(requireContext())
        val prefs = PrefsManager(requireContext())
        val profileId = prefs.activeProfileId

        vm = androidx.lifecycle.ViewModelProvider(
            this, MicronutrientViewModel.Factory(db, profileId)
        )[MicronutrientViewModel::class.java]

        vm.items.observe(viewLifecycleOwner) { render(it) }
        vm.suggestions.observe(viewLifecycleOwner) { renderSuggestions(it) }
        vm.hasData.observe(viewLifecycleOwner) { has ->
            binding.tvMicroEmpty.visibility = if (has) View.GONE else View.VISIBLE
            binding.microContainer.visibility = if (has) View.VISIBLE else View.GONE
        }
        vm.load()
    }

    private fun render(items: List<NutrientProgress>) {
        val ctx = requireContext()
        binding.microContainer.removeAllViews()

        for (item in items) {
            val name = nutrientName(item.key)

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }

            // Верхняя строка: эмодзи + название ... проценты
            val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val title = TextView(ctx).apply {
                text = "${item.emoji}  $name"
                textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val pct = TextView(ctx).apply {
                text = "${item.percent}%"
                textSize = 15f
                setTextColor(colorFor(item))
            }
            header.addView(title)
            header.addView(pct)

            // Прогресс-бар
            val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = item.percent.coerceIn(0, 100)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(8)
                ).apply { topMargin = dp(4); bottomMargin = dp(2) }
            }

            // Подпись: съедено / норма
            val detail = TextView(ctx).apply {
                text = "${fmt(item.consumed)} / ${fmt(item.rda)} ${item.unit}"
                textSize = 12f
                setTextColor(Color.parseColor("#B3FFFFFF"))
            }

            row.addView(header)
            row.addView(bar)
            row.addView(detail)
            binding.microContainer.addView(row)
        }
    }

    private fun renderSuggestions(suggestions: List<DeficitSuggestion>) {
        if (suggestions.isEmpty()) {
            binding.suggestBox.visibility = View.GONE
            return
        }
        binding.suggestBox.visibility = View.VISIBLE
        val ctx = requireContext()
        binding.suggestContainer.removeAllViews()
        for (s in suggestions) {
            val name = nutrientName(s.nutrientKey)
            val line = TextView(ctx).apply {
                text = getString(
                    R.string.micro_suggest_line_fmt,
                    s.emoji, name,
                    s.products.joinToString(", ") {
                        com.nutritrack.app.utils.ProductNameLabel.of(ctx, it)
                    }
                )
                textSize = 14f
                setTextColor(Color.parseColor("#F2FFFFFF"))
                setPadding(0, dp(6), 0, dp(6))
            }
            binding.suggestContainer.addView(line)
        }
    }

    private fun colorFor(item: NutrientProgress): Int = when {
        item.isLow -> Color.parseColor("#E57373")   // дефицит — красноватый
        item.isOver -> Color.parseColor("#FFB74D")  // избыток — оранжевый
        else -> Color.parseColor("#66BB6A")          // норма — зелёный
    }

    private fun fmt(v: Float): String =
        if (v >= 100f) v.toInt().toString()
        else if (v >= 10f) String.format("%.0f", v)
        else String.format("%.1f", v)

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    /** Локализованное название нутриента по ключу. */
    private fun nutrientName(key: String): String {
        val resId = when (key) {
            "vitA" -> R.string.nutr_vitA
            "vitC" -> R.string.nutr_vitC
            "vitD" -> R.string.nutr_vitD
            "vitE" -> R.string.nutr_vitE
            "vitK" -> R.string.nutr_vitK
            "vitB6" -> R.string.nutr_vitB6
            "vitB12" -> R.string.nutr_vitB12
            "folate" -> R.string.nutr_folate
            "calcium" -> R.string.nutr_calcium
            "iron" -> R.string.nutr_iron
            "magnesium" -> R.string.nutr_magnesium
            "zinc" -> R.string.nutr_zinc
            "potassium" -> R.string.nutr_potassium
            "sodium" -> R.string.nutr_sodium
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
