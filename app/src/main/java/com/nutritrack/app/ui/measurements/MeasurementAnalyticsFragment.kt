package com.nutritrack.app.ui.measurements

import com.nutritrack.app.R

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.BodyPart
import com.nutritrack.app.data.BodyParts
import com.nutritrack.app.data.MeasurementInsights
import com.nutritrack.app.databinding.FragmentMeasurementAnalyticsBinding
import kotlinx.coroutines.launch

/**
 * Аналитика замеров: соотношения тела, динамика по части (график),
 * прогресс, категория/уровень, предметные сравнения.
 */
class MeasurementAnalyticsFragment : Fragment() {

    private var _binding: FragmentMeasurementAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { (requireActivity().application as NutriApp).database }
    private var profileId: Long = 1L
    private var isMale: Boolean = true
    private var latest: Map<String, Float> = emptyMap()
    private var selectedPart: String = "biceps_l"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMeasurementAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = db.profileDao().getActiveProfileSync()
            profileId = profile?.id ?: 1L
            isMale = (profile?.gender ?: "male") != "female"

            latest = db.measurementDao().getLatestForProfile(profileId).associate { it.bodyPart to it.valueCm }

            buildRatios()
            buildChips()
            // выбрать первую часть, по которой есть данные
            selectedPart = latest.keys.firstOrNull() ?: "biceps_l"
            showPart(selectedPart)
        }
    }

 /** Соотношения тела (WHR, плечи/талия). */
    private fun buildRatios() {
        binding.containerRatios.removeAllViews()

        val waist = latest["waist"] ?: 0f
        val hips = latest["hips"] ?: 0f
        val shoulders = latest["shoulders"] ?: 0f

        MeasurementInsights.whrAssessment(waist, hips, isMale)?.let { (whr, cat) ->
            binding.containerRatios.addView(ratioCard(
                getString(R.string.ma_whr),
                String.format("%.2f", whr),
                "${cat.emoji} ${cat.label}"
            ))
        }
        MeasurementInsights.shoulderToWaist(shoulders, waist)?.let { (ratio, cat) ->
            binding.containerRatios.addView(ratioCard(
                getString(R.string.ma_shoulder_waist),
                String.format("%.2f", ratio),
                "${cat.emoji} ${cat.label}"
            ))
        }

        if (binding.containerRatios.childCount == 0) {
            val tv = TextView(requireContext()).apply {
                text = getString(R.string.ma_add_hint)
                setTextColor(Color.parseColor("#9E9EB0"))
                textSize = 13f
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
            binding.containerRatios.addView(tv)
        }
    }

    private fun ratioCard(title: String, value: String, assessment: String): View {
        val card = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = dp(16).toFloat(); cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#22193C"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, dp(8)) }
            layoutParams = lp
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val left = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(TextView(requireContext()).apply {
            text = title; setTextColor(Color.WHITE); textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        left.addView(TextView(requireContext()).apply {
            text = com.nutritrack.app.utils.DataLabel.tr(requireContext(), assessment); setTextColor(Color.parseColor("#B0BEC5")); textSize = 12f
            setPadding(0, dp(2), 0, 0)
        })
        val valTv = TextView(requireContext()).apply {
            text = value; setTextColor(Color.parseColor("#40C4FF")); textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        row.addView(left); row.addView(valTv)
        card.addView(row)
        return card
    }

 /** Чипы выбора части (только те, по которым есть замеры; если нет — все). */
    private fun buildChips() {
        binding.chipRow.removeAllViews()
        val parts = BodyParts.all.filter { latest.containsKey(it.key) }
            .ifEmpty { BodyParts.all }
        parts.forEach { part ->
            val chip = TextView(requireContext()).apply {
                text = "${part.emoji} ${part.title}"
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setBackgroundResource(
                    if (part.key == selectedPart) com.nutritrack.app.R.drawable.chip_selected
                    else com.nutritrack.app.R.drawable.chip_bg
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dp(8), 0) }
                layoutParams = lp
                isClickable = true
                setOnClickListener {
                    selectedPart = part.key
                    buildChips()
                    showPart(part.key)
                }
            }
            binding.chipRow.addView(chip)
        }
    }

    private fun showPart(partKey: String) {
        val part = BodyParts.byKey(partKey) ?: return
        binding.tvChartTitle.text = "${part.emoji} ${part.title}"

        viewLifecycleOwner.lifecycleScope.launch {
            val history = db.measurementDao().getHistory(partKey, profileId)

            // График
            if (history.size >= 2) {
                val entries = history.mapIndexed { i, m -> Entry(i.toFloat(), m.valueCm) }
                val ds = LineDataSet(entries, part.title).apply {
                    color = Color.parseColor("#7C4DFF")
                    setCircleColor(Color.parseColor("#B39DDB"))
                    lineWidth = 2.5f; circleRadius = 4f
                    setDrawValues(true); valueTextColor = Color.WHITE; valueTextSize = 9f
                    mode = LineDataSet.Mode.CUBIC_BEZIER; cubicIntensity = 0.2f
                    setDrawFilled(true); fillColor = Color.parseColor("#7C4DFF"); fillAlpha = 40
                }
                binding.chartPart.apply {
                    data = LineData(ds)
                    description.isEnabled = false; legend.isEnabled = false
                    axisRight.isEnabled = false
                    axisLeft.textColor = Color.parseColor("#9E9EB0")
                    xAxis.textColor = Color.parseColor("#9E9EB0")
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.valueFormatter = IndexAxisValueFormatter(history.map { it.date.substring(5) })
                    xAxis.labelRotationAngle = -30f
                    animateX(500)
                    invalidate()
                }
                binding.chartPart.visibility = View.VISIBLE
            } else {
                binding.chartPart.clear()
                binding.chartPart.visibility = View.GONE
            }

            // Прогресс
            if (history.isNotEmpty()) {
                val first = history.first().valueCm
                val last = history.last().valueCm
                val diff = last - first
                binding.tvProgressSummary.text = if (history.size >= 2) {
                    val sign = if (diff >= 0) "+" else ""
                    getString(R.string.ma_current_change_fmt, fmt(last), "$sign${fmt(diff)}", history.size)
                } else {
                    getString(R.string.ma_current_need_fmt, fmt(last))
                }

                // Категория / уровень
                showCategory(partKey, last)

                // Предметное сравнение
                val comp = MeasurementInsights.objectComparison(last)
                binding.tvComparison.text = comp?.let { getString(R.string.ma_circumference_fmt, com.nutritrack.app.utils.DataLabel.tr(requireContext(), it)) } ?: ""
            } else {
                binding.tvProgressSummary.text = getString(R.string.ma_no_records)
                binding.tvCategory.text = ""
                binding.tvComparison.text = ""
            }
        }
    }

    private fun showCategory(partKey: String, cm: Float) {
        val cat = when (partKey) {
            "biceps_l", "biceps_r" -> MeasurementInsights.bicepsLevel(cm)
            "waist" -> MeasurementInsights.waistAssessment(cm, isMale)
            else -> null
        }
        binding.tvCategory.text = cat?.let { "${it.emoji} ${com.nutritrack.app.utils.DataLabel.tr(requireContext(), it.label)}" } ?: ""
    }

    private fun fmt(v: Float) = String.format("%.1f", v)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
