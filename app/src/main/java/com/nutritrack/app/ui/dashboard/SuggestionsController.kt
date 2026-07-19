package com.nutritrack.app.ui.dashboard

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentDashboardBinding
import com.nutritrack.app.utils.AllergenData
import com.nutritrack.app.utils.CalorieSuggester
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.viewmodel.DashboardViewModel

/**
 * Карточка подсказок главного экрана: чем добить дневную норму калорий и чем
 * усилить уже съеденное (с учётом аллергенов и диет профиля). Вынесено из
 * DashboardFragment. [bindingOf] — актуальный binding или null.
 */
class SuggestionsController(
    private val fragment: Fragment,
    private val bindingOf: () -> FragmentDashboardBinding?,
    private val vm: DashboardViewModel,
) {
    private val ctx get() = fragment.requireContext()
    private val prefs = PrefsManager(fragment.requireContext())
    private fun s(id: Int, vararg a: Any) = ctx.getString(id, *a)
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun update(remaining: Int, hasEntries: Boolean) {
        val b = bindingOf() ?: return
        if ("suggestions" in prefs.hiddenDashboardCards) {
            b.cardSuggestions.visibility = View.GONE; return
        }
        val profile = vm.activeProfile.value
        val allergens = AllergenData.parse(profile?.allergens)
        val diets = profile?.diets?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val fill = CalorieSuggester.fillGap(ctx, remaining, allergens, diets)
        val boost = CalorieSuggester.boosters(ctx, allergens, diets)

        // Показываем добор, если есть заметный остаток; иначе усилители (если уже ел)
        val (list, subtitle) = when {
            remaining > 150 -> fill to s(R.string.sug_sub_fill_fmt, remaining)
            hasEntries -> boost to s(R.string.sug_sub_boost)
            else -> emptyList<CalorieSuggester.Suggestion>() to ""
        }
        if (list.isEmpty()) { b.cardSuggestions.visibility = View.GONE; return }

        b.cardSuggestions.visibility = View.VISIBLE
        b.tvSuggestSub.text = subtitle
        val container = b.containerSuggest
        container.removeAllViews()
        list.take(5).forEach { row -> container.addView(buildRow(row)) }

        // Сворачивание/разворачивание по нажатию на заголовок (по умолчанию свёрнуто)
        b.rowSuggestHeader.setOnClickListener {
            val show = b.suggestBody.visibility != View.VISIBLE
            b.suggestBody.visibility = if (show) View.VISIBLE else View.GONE
            b.tvSuggestArrow.text = if (show) "▾" else "▸"
        }
    }

    private fun buildRow(sug: CalorieSuggester.Suggestion): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(ctx).apply {
            text = sug.emoji; textSize = 18f; setPadding(0, 0, dp(12), 0)
        })
        val mid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        mid.addView(TextView(ctx).apply {
            text = sug.title
            setTextColor(ctx.getColor(R.color.white)); textSize = 14f
        })
        if (sug.conflict && sug.conflictReason != null) {
            mid.addView(TextView(ctx).apply {
                text = "⚠ " + sug.conflictReason
                setTextColor(android.graphics.Color.parseColor("#FFB74D")); textSize = 11f
                setPadding(0, dp(1), 0, 0)
            })
        }
        row.addView(mid)
        row.addView(TextView(ctx).apply {
            text = s(R.string.sug_kcal_prot_fmt, sug.kcal, sug.proteinG)
            setTextColor(ctx.getColor(R.color.color_accent)); textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        return row
    }
}
