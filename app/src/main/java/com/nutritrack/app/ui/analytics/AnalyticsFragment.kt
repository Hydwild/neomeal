package com.nutritrack.app.ui.analytics
import com.nutritrack.app.utils.navigateAnim

import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentAnalyticsBinding
import com.nutritrack.app.viewmodel.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val vm:        AnalyticsViewModel by viewModels()
    private val profileVm: ProfileViewModel   by viewModels()
    private val budgetVm:  BudgetViewModel    by viewModels()
    private val achievementsVm: AchievementsViewModel by activityViewModels()

    private var currentProfile: com.nutritrack.app.data.db.entity.ProfileEntity? = null
    private var currentHistory: List<com.nutritrack.app.data.db.entity.WeightEntryEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCharts()
        observeData()
        loadYearHeatmap()

        vm.loadWeekStats()
        vm.loadWeekCaloriesByDay()
        budgetVm.loadStats()

        binding.btnSaveWeight.setOnClickListener { saveWeightFromInput() }
        binding.etWeight.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                saveWeightFromInput()
                // Скрыть клавиатуру
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.etWeight.windowToken, 0)
                true
            } else false
        }

        binding.btnSetWeightGoal.setOnClickListener { showWeightGoalDialog() }
        // При фокусе на поле веса — прокрутить к нему, чтобы клавиатура не перекрывала
        binding.etWeight.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.postDelayed({
                val b = _binding ?: return@postDelayed
                // Просим систему доскроллить так, чтобы поле и кнопка под ним были видны
                val r = android.graphics.Rect(0, 0, v.width, v.height + b.btnSaveWeight.height + dp(16))
                v.requestRectangleOnScreen(r, false)
            }, 250)
        }
        binding.analyticsScroll.setOnScrollChangeListener(
            android.view.View.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                (activity as? com.nutritrack.app.MainActivity)?.onContentScroll(scrollY - oldScrollY)
            })
        binding.btnBuildReport.setOnClickListener { vm.buildWeeklyReport(requireContext().applicationContext) }
        // Недельный отчёт показываем только в конце недели (воскресенье)
        val isEndOfWeek = java.util.Calendar.getInstance()
            .get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
        binding.cardWeeklyReport.visibility = if (isEndOfWeek) View.VISIBLE else View.GONE
        if (isEndOfWeek) vm.buildWeeklyReport(requireContext().applicationContext)
    }

    // Графики

 /** Годовой хитмэп: уровень 0–4 по попаданию дня в норму калорий. */
    private fun loadYearHeatmap() {
        val db = (requireActivity().application as com.nutritrack.app.NutriApp).database
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val profile = db.profileDao().getActiveProfileSync() ?: return@launch
            val goal = com.nutritrack.app.utils.CalorieCalculator.calculateDailyGoal(profile)
            val end = java.time.LocalDate.now()
            val start = end.minusDays(370)
            val entries = db.diaryDao().getEntriesForRange(
                start.toString(), end.toString(), prefs.activeProfileId)
            val byDay = entries.groupBy { it.date }
                .mapValues { (_, l) -> l.sumOf { e -> e.calories.toDouble() } }
            val levels = byDay.mapValues { (_, cal) ->
                val r = if (goal > 0) cal / goal else 0.0
                when {
                    cal <= 0.0 -> 0
                    r < 0.5    -> 1
                    r < 0.85   -> 2
                    r <= 1.10  -> 4   // в коридоре нормы — максимум
                    else       -> 3   // переел — почти
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _binding?.yearHeatmap?.setData(levels, end)
            }
        }
    }

    private fun setupCharts() {
        binding.barChartCalories.apply {
            description.isEnabled = false; legend.isEnabled = false
            setTouchEnabled(true); isDragEnabled = false; setScaleEnabled(false)
            setDrawGridBackground(false); setDrawBarShadow(false)
            setNoDataText(getString(R.string.an_no_week_data)); setNoDataTextColor(Color.parseColor("#9E9EB0"))
            axisRight.isEnabled = false
            axisLeft.apply { textColor = Color.parseColor("#9E9EB0"); gridColor = Color.parseColor("#33FFFFFF"); textSize = 10f; setDrawAxisLine(false) }
            xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; textColor = Color.parseColor("#9E9EB0"); setDrawGridLines(false); granularity = 1f; textSize = 10f }
            setBackgroundColor(Color.TRANSPARENT)
        }
        binding.lineChartWeight.apply {
            description.isEnabled = false; legend.isEnabled = false
            setTouchEnabled(true); isDragEnabled = true; setScaleEnabled(false)
            setDrawGridBackground(false)
            setNoDataText(getString(R.string.an_add_weight_chart)); setNoDataTextColor(Color.parseColor("#9E9EB0"))
            axisRight.isEnabled = false
            axisLeft.apply { textColor = Color.parseColor("#9E9EB0"); gridColor = Color.parseColor("#33FFFFFF"); textSize = 10f; setDrawAxisLine(false) }
            xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; textColor = Color.parseColor("#9E9EB0"); setDrawGridLines(false); granularity = 1f; textSize = 10f }
            setBackgroundColor(Color.TRANSPARENT)
        }
        binding.pieChartMacros.apply {
            description.isEnabled = false; isRotationEnabled = false; isHighlightPerTapEnabled = true
            setUsePercentValues(true); setDrawEntryLabels(false); holeRadius = 55f; transparentCircleRadius = 60f
            setHoleColor(Color.TRANSPARENT)
            setNoDataText(getString(R.string.an_no_week_data)); setNoDataTextColor(Color.parseColor("#9E9EB0"))
            legend.apply { textColor = Color.WHITE; textSize = 12f; form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE }
            setBackgroundColor(Color.TRANSPARENT)

            // Тап по сегменту диалог с топ-продуктами по этому макросу
            setOnChartValueSelectedListener(object :
                com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                override fun onValueSelected(
                    e: com.github.mikephil.charting.data.Entry?,
                    h: com.github.mikephil.charting.highlight.Highlight?
                ) {
                    val pieEntry = e as? com.github.mikephil.charting.data.PieEntry ?: return
                    showMacroDetailDialog(pieEntry.label ?: "")
                }
                override fun onNothingSelected() {}
            })
        }
    }

    // Данные

    private fun observeData() {
        vm.weeklyReport.observe(viewLifecycleOwner) { insights ->
            renderWeeklyReport(insights)
        }
        vm.activeProfile.observe(viewLifecycleOwner) { p ->
            currentProfile = p
            binding.tvCurrentWeight.text = p?.weight?.let {
                com.nutritrack.app.utils.UnitsHelper.formatWeight(requireContext(), it)
            } ?: "--"
            updateWeightGoalSummary()
        }

        vm.weightHistory.observe(viewLifecycleOwner) { history ->
            currentHistory = history ?: emptyList()
            updateWeightGoalSummary()
            if (history.isNullOrEmpty()) { binding.lineChartWeight.clear(); binding.lineChartWeight.invalidate(); binding.tvWeightChange.text = ""; return@observe }
            val recent = history.take(14).reversed()
            val sdf = SimpleDateFormat("d/MM", Locale.getDefault())
            val pf  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val entries = recent.mapIndexed { i, e -> Entry(i.toFloat(), e.weight) }
            val labels  = recent.map { e -> try { sdf.format(pf.parse(e.date) ?: Date()) } catch (ex: Exception) { "" } }
            val ds = LineDataSet(entries, getString(R.string.an_weight)).apply {
                color = Color.parseColor("#7C4DFF"); setCircleColor(Color.parseColor("#B39DDB"))
                circleHoleColor = Color.parseColor("#12094D"); lineWidth = 2.5f; circleRadius = 5f; circleHoleRadius = 2.5f
                setDrawValues(true); valueTextColor = Color.WHITE; valueTextSize = 9f
                valueFormatter = object : ValueFormatter() { override fun getPointLabel(e: Entry) = "${e.y}" }
                mode = LineDataSet.Mode.CUBIC_BEZIER; cubicIntensity = 0.2f; setDrawFilled(true); fillColor = Color.parseColor("#7C4DFF"); fillAlpha = 40
            }
            binding.lineChartWeight.apply {
                xAxis.valueFormatter = IndexAxisValueFormatter(labels); xAxis.labelCount = labels.size.coerceAtMost(7)
                data = LineData(ds); animateX(600, Easing.EaseOutQuad); invalidate()
            }
            if (history.size >= 2) {
                val diff = history[0].weight - history[1].weight
                binding.tvWeightChange.text = (if (diff >= 0f) "+" else "") +
                        com.nutritrack.app.utils.UnitsHelper.formatWeight(requireContext(), diff)
                binding.tvWeightChange.setTextColor(if (diff > 0f) Color.parseColor("#FF5252") else Color.parseColor("#69F0AE"))
            }
        }

        vm.weekCaloriesByDay.observe(viewLifecycleOwner) { dayList ->
            if (dayList.isNullOrEmpty()) return@observe
            val goal = vm.calorieGoal.value?.toFloat() ?: 2000f
            val sdf = SimpleDateFormat("EEE", Locale.getDefault())
            val pf  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val labels  = dayList.map { (d, _) -> try { sdf.format(pf.parse(d) ?: Date()) } catch (e: Exception) { "" } }
            val entries = dayList.mapIndexed { i, (_, cal) -> BarEntry(i.toFloat(), cal) }
            val colors  = dayList.map { (_, cal) ->
                when {
                    cal >= goal * 0.85f && cal <= goal * 1.15f -> Color.parseColor("#69F0AE")
                    cal > goal * 1.15f -> Color.parseColor("#FF5252")
                    cal > 0f           -> Color.parseColor("#B39DDB")
                    else               -> Color.parseColor("#424242")
                }
            }
            val ds = BarDataSet(entries, getString(R.string.an_macro_cal)).apply {
                this.colors = colors; valueTextColor = Color.WHITE; valueTextSize = 9f
                valueFormatter = object : ValueFormatter() { override fun getBarLabel(e: BarEntry) = if (e.y > 0f) "${e.y.toInt()}" else "" }
                setDrawValues(true)
            }
            val ll = LimitLine(goal, getString(R.string.an_goal)).apply {
                lineWidth = 1.5f; lineColor = Color.parseColor("#FFD740"); textColor = Color.parseColor("#FFD740"); textSize = 9f; enableDashedLine(10f, 5f, 0f)
            }
            binding.barChartCalories.axisLeft.removeAllLimitLines(); binding.barChartCalories.axisLeft.addLimitLine(ll)
            binding.barChartCalories.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            binding.barChartCalories.data = BarData(ds).apply { barWidth = 0.6f }
            binding.barChartCalories.animateY(600, Easing.EaseOutQuad); binding.barChartCalories.invalidate()
        }

        vm.weekStats.observe(viewLifecycleOwner) { stats ->
            binding.tvAvgCalories.text = getString(R.string.an_kcal_day_fmt, stats.avgCalories.toInt())
            binding.tvAvgWater.text    = getString(R.string.an_water_day_fmt, stats.avgWater.toInt())
            binding.tvTotalProtein.text = "${stats.totalProtein.toInt()}${getString(R.string.unit_g)}"
            binding.tvTotalFat.text     = getString(R.string.an_g_fmt, stats.totalFat.toInt())
            binding.tvTotalCarbs.text   = getString(R.string.an_g_fmt, stats.totalCarbs.toInt())
            val total = stats.totalProtein + stats.totalFat + stats.totalCarbs
            if (total > 0f) {
                val ds = PieDataSet(listOf(PieEntry(stats.totalProtein, getString(R.string.an_macro_protein)), PieEntry(stats.totalFat, getString(R.string.an_macro_fat)), PieEntry(stats.totalCarbs, getString(R.string.an_macro_carbs))), "").apply {
                    colors = listOf(Color.parseColor("#69F0AE"), Color.parseColor("#FFD740"), Color.parseColor("#FF6E40"))
                    sliceSpace = 2f; selectionShift = 5f; valueTextColor = Color.WHITE; valueTextSize = 11f
                    valueFormatter = object : ValueFormatter() { override fun getPieLabel(v: Float, e: PieEntry) = "${v.toInt()}%" }
                }
                binding.pieChartMacros.data = PieData(ds); binding.pieChartMacros.animateY(700, Easing.EaseOutQuad); binding.pieChartMacros.invalidate()
            }
        }

        budgetVm.stats.observe(viewLifecycleOwner) { stats ->
            if (stats.any { it.totalSpent > 0f }) {
                binding.cardBudget.visibility = View.VISIBLE
                binding.tvBudgetStats.text = stats.joinToString("\n") { getString(R.string.an_budget_row_fmt, it.label, it.totalSpent.toInt(), it.avgPerDay.toInt()) }
            }
        }

        budgetVm.topSpending.observe(viewLifecycleOwner) { top ->
            binding.containerTopSpending.removeAllViews()
            if (top.isEmpty()) {
                binding.tvTopSpendingTitle.visibility = View.GONE
                return@observe
            }
            binding.tvTopSpendingTitle.visibility = View.VISIBLE
            val medals = listOf("🥇", "🥈", "🥉", "4.", "5.")
            top.forEachIndexed { i, row ->
                val item = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                val name = TextView(requireContext()).apply {
                    text = "${medals.getOrElse(i) { "${i + 1}." }}  ${row.name}"
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val cost = TextView(requireContext()).apply {
                    text = getString(R.string.an_rub_fmt, row.spent.toInt())
                    setTextColor(android.graphics.Color.parseColor("#69F0AE"))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                item.addView(name); item.addView(cost)
                binding.containerTopSpending.addView(item)
            }
        }
    }

    private fun renderWeeklyReport(insights: List<com.nutritrack.app.utils.WeeklyReport.Insight>) {
        val b = _binding ?: return
        val container = b.reportContainer
        container.removeAllViews()
        val ctx = requireContext()
        insights.forEach { ins ->
            val block = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val title = android.widget.TextView(ctx).apply {
                text = ins.title
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            block.addView(title)
            ins.advice?.let { adv ->
                val advice = android.widget.TextView(ctx).apply {
                    text = adv
                    setTextColor(ctx.getColor(R.color.text_secondary))
                    textSize = 12.5f
                    setPadding(0, dp(2), 0, 0)
                }
                block.addView(advice)
            }
            container.addView(block)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // Цель по весу

    private fun saveWeightFromInput() {
        val w = binding.etWeight.text.toString().replace(',', '.').toFloatOrNull()
        if (w == null || w < 20f || w > 500f) {
            binding.etWeight.error = getString(R.string.an_weight_invalid); return
        }
        profileVm.saveWeight(w) { achievementsVm.checkAchievements() }
        com.nutritrack.app.utils.CompanionHelper.maybeCheer(requireContext(), "weight")
        binding.etWeight.setText("")
        android.widget.Toast.makeText(requireContext(), getString(R.string.an_weight_saved_fmt, w.toString()), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun updateWeightGoalSummary() {
        val b = _binding ?: return
        val profile = currentProfile
        val target = profile?.targetWeight ?: 0f
        if (profile == null || target <= 0f) {
            b.tvWeightGoalSummary.text = getString(R.string.an_goal_not_set)
            b.btnSetWeightGoal.text = getString(R.string.an_goal_set)
            return
        }
        b.btnSetWeightGoal.text = getString(R.string.an_goal_change)

        val current = profile.weight
        val remaining = current - target            // >0 нужно сбросить, <0 набрать
        if (kotlin.math.abs(remaining) < 0.1f) {
            b.tvWeightGoalSummary.text = getString(R.string.an_goal_reached)
            return
        }

        val targetStr = com.nutritrack.app.utils.UnitsHelper.formatWeight(requireContext(), target)
        val remainStr = com.nutritrack.app.utils.UnitsHelper.formatWeight(requireContext(), kotlin.math.abs(remaining))
        val head = getString(R.string.an_goal_remaining_fmt, targetStr, remainStr)

        val etaDays = estimateDaysToGoal(remaining)
        b.tvWeightGoalSummary.text = if (etaDays != null)
            head + "\n" + getString(R.string.an_goal_eta_fmt, etaDays)
        else
            head + "\n" + getString(R.string.an_goal_no_estimate)
    }

 /** Оценка дней до цели по темпу за последние записи. null — если темп не в сторону цели. */
    private fun estimateDaysToGoal(remaining: Float): Int? {
        val hist = currentHistory
        if (hist.size < 2) return null
        // hist[0] — самая свежая запись, берём окно до 14 последних
        val window = hist.take(14)
        val newest = window.first()
        val oldest = window.last()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dNew = try { fmt.parse(newest.date) } catch (e: Exception) { null } ?: return null
        val dOld = try { fmt.parse(oldest.date) } catch (e: Exception) { null } ?: return null
        val daysSpan = ((dNew.time - dOld.time) / (1000L * 60 * 60 * 24)).toInt()
        if (daysSpan <= 0) return null
        val weightDelta = newest.weight - oldest.weight   // >0 набрал, <0 сбросил
        val ratePerDay = weightDelta / daysSpan           // кг/день
        if (kotlin.math.abs(ratePerDay) < 0.001f) return null
        // Нужно: remaining>0 (сбрасывать) темп должен быть отрицательным
        val goingRightWay = (remaining > 0f && ratePerDay < 0f) || (remaining < 0f && ratePerDay > 0f)
        if (!goingRightWay) return null
        val days = kotlin.math.abs(remaining / ratePerDay)
        if (days < 1f || days > 3650f) return null
        return days.toInt()
    }

    private fun showWeightGoalDialog() {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.an_goal_dialog_title)
            val cur = currentProfile?.targetWeight ?: 0f
            if (cur > 0f) setText(cur.toString())
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.an_goal_dialog_title))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val t = input.text.toString().replace(',', '.').toFloatOrNull()
                if (t != null && t in 20f..500f) {
                    profileVm.setTargetWeight(t)
                }
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }


    // Детализация по тапу на сегмент круга БЖУ

 /**
 * При тапе на сегмент круга (Белки/Жиры/Углеводы) — показывает топ-продуктов,
 * которые дали больше всего этого макроса за неделю.
 */
    private fun showMacroDetailDialog(macroLabel: String) {
        val (selector, unit, _) = when (macroLabel) {
            getString(R.string.an_macro_protein)    -> Triple<(com.nutritrack.app.data.db.entity.DiaryEntryEntity) -> Float, String, String>(
                { it.protein }, getString(R.string.an_g_protein), "#69F0AE")
            getString(R.string.an_macro_fat)     -> Triple<(com.nutritrack.app.data.db.entity.DiaryEntryEntity) -> Float, String, String>(
                { it.fat }, getString(R.string.an_g_fat), "#FFD740")
            getString(R.string.an_macro_carbs) -> Triple<(com.nutritrack.app.data.db.entity.DiaryEntryEntity) -> Float, String, String>(
                { it.carbs }, getString(R.string.an_g_carbs), "#FF6E40")
            else -> return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val (entries, totalMacro) = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val app = requireActivity().application as com.nutritrack.app.NutriApp
                val db  = app.database
                val profile = db.profileDao().getActiveProfileSync()
                    ?: return@withContext emptyList<com.nutritrack.app.data.db.entity.DiaryEntryEntity>() to 0f

                val dates = com.nutritrack.app.utils.DateUtils.getWeekDates(0)
                val list  = db.diaryDao().getEntriesForRange(dates.first(), dates.last(), profile.id)
                val total = list.sumOf { selector(it).toDouble() }.toFloat()
                list to total
            }

            if (entries.isEmpty() || totalMacro <= 0f) {
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.an_no_week_data), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Группируем по названию продукта, суммируем граммы и макрос
            data class Agg(var grams: Float = 0f, var macro: Float = 0f, var times: Int = 0)
            val byProduct = HashMap<String, Agg>()
            entries.forEach { entry ->
                val key = entry.productName
                val agg = byProduct.getOrPut(key) { Agg() }
                agg.grams += entry.grams
                agg.macro += selector(entry)
                agg.times += 1
            }

            // Топ-10 по вкладу в макрос
            val top = byProduct.entries
                .sortedByDescending { it.value.macro }
                .take(10)

            val message = buildString {
                append(getString(R.string.an_top_by, macroLabel))
                append(getString(R.string.an_total, totalMacro.toInt(), unit))
                top.forEachIndexed { i, (name, a) ->
                    val pct = (a.macro / totalMacro * 100f).toInt()
                    append("${i + 1}. $name\n")
                    append(getString(R.string.an_macro_eaten_fmt, a.macro.toInt(), unit, a.grams.toInt()))
                    if (a.times > 1) append(getString(R.string.an_times_fmt, a.times))
                    append("  ·  $pct%\n")
                    if (i < top.lastIndex) append("\n")
                }
            }

            val emoji = when (macroLabel) {
                getString(R.string.an_macro_protein)    -> "💪"
                getString(R.string.an_macro_fat)     -> "🥑"
                getString(R.string.an_macro_carbs) -> "🍞"
                else -> ""
            }

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.an_macro_detail_fmt, emoji, macroLabel))
                .setMessage(message)
                .setPositiveButton(getString(R.string.an_close), null)
                .show()

            // Снимаем выделение, чтобы можно было снова тапнуть тот же сегмент
            binding.pieChartMacros.highlightValue(null)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
