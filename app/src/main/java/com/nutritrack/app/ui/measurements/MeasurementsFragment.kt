package com.nutritrack.app.ui.measurements

import com.nutritrack.app.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.BodyPart
import com.nutritrack.app.data.BodyParts
import com.nutritrack.app.data.db.entity.MeasurementEntity
import com.nutritrack.app.databinding.FragmentMeasurementsBinding
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.launch

/**
 * Журнал замеров тела. Список частей по группам, у каждой — последнее значение.
 * Тап getString(R.string.m_change) диалог с ручным вводом + быстрыми кнопками (±0.1/±0.5/±1.0).
 * Этап 1 (без фигуры — заглушка). Фигура с рамками — Этап 2.
 */
class MeasurementsFragment : Fragment() {

    private var _binding: FragmentMeasurementsBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { (requireActivity().application as NutriApp).database }
    private val achievementsVm: com.nutritrack.app.viewmodel.AchievementsViewModel by activityViewModels()
    private var profileId: Long = 1L
    private var latest: MutableMap<String, Float> = mutableMapOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMeasurementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAnalytics.setOnClickListener {
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .let { nav ->
                    if (com.nutritrack.app.utils.PremiumManager.gate(requireContext(),
                            com.nutritrack.app.utils.PremiumManager.Feature.MEASUREMENT_ANALYTICS))
                        nav.navigate(com.nutritrack.app.R.id.measurementAnalyticsFragment)
                }
        }
        setupReminderSwitch()
        loadData()
    }

    private fun setupReminderSwitch() {
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        val key = "measurement_reminder_enabled"
        binding.switchReminder.isChecked = prefs.getBool(key, false)
        binding.switchReminder.setOnCheckedChangeListener { _, checked ->
            prefs.setBool(key, checked)
            if (checked) {
                com.nutritrack.app.utils.ReminderScheduler.scheduleWeekly(
                    requireContext(),
                    "measurement",
                    "10:00",
                    "📐 Пора сделать замеры",
                    getString(R.string.m_intro),
                    java.util.Calendar.SUNDAY
                )
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.ms_reminder_on), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                com.nutritrack.app.utils.ReminderScheduler.cancel(requireContext(), "measurement")
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.m_reminders_off), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = db.profileDao().getActiveProfileSync()
            profileId = profile?.id ?: 1L
            // Фигура по полу из профиля
            binding.bodyFigure.isMale = (profile?.gender ?: "male") != "female"

            latest.clear()
            db.measurementDao().getLatestForProfile(profileId).forEach {
                latest[it.bodyPart] = it.valueCm
            }
            renderList()
        }
    }

    private fun renderList() {
        // Фигура: актуальные значения + тап по зоне открывает редактирование
        binding.bodyFigure.values = latest.toMap()
        binding.bodyFigure.onZoneTap = { key ->
            com.nutritrack.app.data.BodyParts.byKey(key)?.let { showEditDialog(it) }
        }

        binding.containerParts.removeAllViews()
        BodyParts.groups.forEach { group ->
            // Заголовок группы
            val header = TextView(requireContext()).apply {
                text = com.nutritrack.app.utils.BodyPartLabel.group(requireContext(), group).uppercase()
                setTextColor(android.graphics.Color.parseColor("#B39DDB"))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(4), dp(12), 0, dp(8))
            }
            binding.containerParts.addView(header)

            // Карточка с частями группы
            val card = androidx.cardview.widget.CardView(requireContext()).apply {
                radius = dp(16).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(android.graphics.Color.parseColor("#22193C"))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(6)) }
                layoutParams = lp
            }
            val col = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            BodyParts.all.filter { it.group == group }.forEach { part ->
                col.addView(buildPartRow(part))
            }
            card.addView(col)
            binding.containerParts.addView(card)
        }
    }

    private fun buildPartRow(part: BodyPart): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            foreground = androidx.core.content.ContextCompat.getDrawable(
                requireContext(), android.R.drawable.list_selector_background
            )
            setOnClickListener { showEditDialog(part) }
        }

        val emoji = TextView(requireContext()).apply {
            text = part.emoji
            textSize = 18f
            val lp = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val title = TextView(requireContext()).apply {
            text = com.nutritrack.app.utils.BodyPartLabel.of(requireContext(), part.key)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }

        val value = TextView(requireContext()).apply {
            val v = latest[part.key]
            text = if (v != null) String.format("%.2f см", v) else "—"
            setTextColor(
                if (v != null) android.graphics.Color.parseColor("#40C4FF")
                else android.graphics.Color.parseColor("#9E9EB0")
            )
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val arrow = TextView(requireContext()).apply {
            text = "  ›"
            setTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            textSize = 18f
        }

        row.addView(emoji); row.addView(title); row.addView(value); row.addView(arrow)
        return row
    }

    private fun showEditDialog(part: BodyPart) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        // Текущее значение редактируется здесь
        var current = latest[part.key] ?: 0f

        val valueText = TextView(ctx).apply {
            text = String.format("%.2f см", current)
            setTextColor(android.graphics.Color.parseColor("#7C4DFF"))
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(valueText)

        // Поле ручного ввода
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.m_enter_exact)
            setText(if (current > 0f) String.format("%.2f", current) else "")
            setTextColor(android.graphics.Color.parseColor("#1A1340"))
            setHintTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            gravity = android.view.Gravity.CENTER
            textSize = 16f
        }
        container.addView(input)

        fun refresh() {
            valueText.text = getString(R.string.ms_cm_fmt, String.format("%.2f", current.coerceAtLeast(0f)))
            input.setText(String.format("%.2f", current.coerceAtLeast(0f)))
            input.setSelection(input.text.length)
        }

        fun step(delta: Float) {
            // синхронизируемся с тем, что в поле, затем меняем
            input.text.toString().replace(',', '.').toFloatOrNull()?.let { current = it }
            current = (current + delta).coerceAtLeast(0f)
            refresh()
        }

        // Ряды кнопок шагов
        container.addView(makeStepRow(ctx, listOf(-1.0f to "−1.0", -0.5f to "−0.5", -0.1f to "−0.1"), ::step))
        container.addView(makeStepRow(ctx, listOf(0.1f to "+0.1", 0.5f to "+0.5", 1.0f to "+1.0"), ::step))

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("${part.emoji} ${com.nutritrack.app.utils.BodyPartLabel.of(requireContext(), part.key)}")
            .setView(container)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                val finalValue = input.text.toString().replace(',', '.').toFloatOrNull() ?: current
                if (finalValue > 0f) saveMeasurement(part.key, finalValue)
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .apply {
                if (latest.containsKey(part.key)) {
                    setNeutralButton(getString(R.string.m_history)) { _, _ -> showHistory(part) }
                }
            }
            .show()
    }

    private fun makeStepRow(
        ctx: android.content.Context,
        steps: List<Pair<Float, String>>,
        onStep: (Float) -> Unit
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        steps.forEach { (delta, label) ->
            val btn = TextView(ctx).apply {
                text = label
                setTextColor(android.graphics.Color.WHITE)
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                setBackgroundResource(com.nutritrack.app.R.drawable.chip_bg)
                isClickable = true
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(dp(4), 0, dp(4), 0) }
                layoutParams = lp
                setOnClickListener { onStep(delta) }
            }
            row.addView(btn)
        }
        return row
    }

    private fun saveMeasurement(partKey: String, value: Float) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.measurementDao().insert(
                MeasurementEntity(
                    bodyPart = partKey,
                    valueCm = value,
                    date = DateUtils.today(),
                    profileId = profileId
                )
            )
            latest[partKey] = value
            renderList()
            // Проверка достижений за замеры
            achievementsVm.checkAchievements()
            android.widget.Toast.makeText(requireContext(),
                getString(R.string.ms_saved_fmt, String.format("%.2f", value)), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHistory(part: BodyPart) {
        viewLifecycleOwner.lifecycleScope.launch {
            val history = db.measurementDao().getHistory(part.key, profileId)
            val sb = StringBuilder()
            if (history.isEmpty()) {
                sb.append(getString(R.string.m_no_records))
            } else {
                history.reversed().forEach {
                    sb.append("${it.date}:  ${String.format("%.2f", it.valueCm)} см\n")
                }
                val first = history.first().valueCm
                val last = history.last().valueCm
                val diff = last - first
                sb.append("\nИзменение: ${if (diff >= 0) "+" else ""}${String.format("%.2f", diff)} см")
            }
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.ms_history_fmt, part.emoji, com.nutritrack.app.utils.BodyPartLabel.of(requireContext(), part.key)))
                .setMessage(sb.toString())
                .setPositiveButton(getString(R.string.c_close), null)
                .show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
