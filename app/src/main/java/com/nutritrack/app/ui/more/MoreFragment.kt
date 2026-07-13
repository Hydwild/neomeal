package com.nutritrack.app.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentMoreBinding
import com.nutritrack.app.utils.navigateAnim

/**
 * Экран "Ещё" — список вторичных разделов, не поместившихся в нижнее меню.
 */
class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    // эмодзи, заголовок, подпись, id назначения в nav_graph
    private data class Section(
        val emoji: String,
        val title: String,
        val subtitle: String,
        val destId: Int
    )

    private val sections by lazy {
        listOf(
            Section("🍎", getString(R.string.sec_products), getString(R.string.sec_products_sub), R.id.productsFragment),
            Section("💊", getString(R.string.sec_micro), getString(R.string.sec_micro_sub), R.id.micronutrientsFragment),
            Section("🎯", getString(R.string.sec_goal), getString(R.string.sec_goal_sub), R.id.nutrientGoalFragment),
            Section("⏱️", getString(R.string.sec_fasting), getString(R.string.sec_fasting_sub), R.id.fastingFragment),
            Section("🕊️", getString(R.string.sec_lent), getString(R.string.sec_lent_sub), R.id.fastingModeFragment),
            Section("🏃", getString(R.string.sec_activity), getString(R.string.sec_activity_sub), R.id.fitnessFragment),
            Section("📐", getString(R.string.sec_measure), getString(R.string.sec_measure_sub), R.id.measurementsFragment),
            Section("📸", getString(R.string.sec_photos), getString(R.string.sec_photos_sub), R.id.progressPhotosFragment),
            Section("💡", getString(R.string.sec_tips), getString(R.string.sec_tips_sub), R.id.tipsFragment),
            Section("🆕", getString(R.string.sec_whatsnew), getString(R.string.sec_whatsnew_sub), R.id.whatsNewFragment),
            Section("❓", getString(R.string.sec_faq), getString(R.string.sec_faq_sub), R.id.faqFragment)
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.containerSections.removeAllViews()
        sections.forEach { binding.containerSections.addView(buildRow(it)) }
    }

    private fun buildRow(section: Section): View {
        val card = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = dp(16).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(android.graphics.Color.parseColor("#22193C"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
            layoutParams = lp
            isClickable = true
            foreground = androidx.core.content.ContextCompat.getDrawable(
                requireContext(), android.R.drawable.list_selector_background
            )
            setOnClickListener {
                NavHostFragment.findNavController(this@MoreFragment).navigateAnim(section.destId)
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val emoji = TextView(requireContext()).apply {
            text = section.emoji
            textSize = 24f
            val lp = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            gravity = android.view.Gravity.CENTER
        }

        val textCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setPadding(dp(12), 0, 0, 0)
        }
        val title = TextView(requireContext()).apply {
            text = section.title
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val sub = TextView(requireContext()).apply {
            text = section.subtitle
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
        }
        textCol.addView(title); textCol.addView(sub)

        val arrow = TextView(requireContext()).apply {
            text = "›"
            setTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            textSize = 22f
        }

        row.addView(emoji); row.addView(textCol); row.addView(arrow)
        card.addView(row)
        return card
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
