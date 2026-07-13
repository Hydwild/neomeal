package com.nutritrack.app.ui.faq

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentFaqBinding
import com.nutritrack.app.utils.FaqData

/** Экран FAQ — частые вопросы по функционалу. Ответы раскрываются по тапу. */
class FaqFragment : Fragment() {

    private var _binding: FragmentFaqBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFaqBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = binding.containerFaq
        container.removeAllViews()
        FaqData.items.forEach { item ->
            container.addView(buildItem(item))
        }
    }

    private fun buildItem(item: FaqData.Item): View {
        val ctx = requireContext()
        val card = androidx.cardview.widget.CardView(ctx).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#22193C"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Вопрос (строка с галочкой-стрелкой)
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }
        val questionView = TextView(ctx).apply {
            text = FaqData.question(ctx, item)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val arrow = TextView(ctx).apply {
            text = "+"
            setTextColor(android.graphics.Color.parseColor("#B39DDB"))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(10), 0, 0, 0)
        }
        header.addView(questionView)
        header.addView(arrow)

        // Ответ (скрыт по умолчанию)
        val answerView = TextView(ctx).apply {
            text = FaqData.answer(ctx, item)
            setTextColor(android.graphics.Color.parseColor("#D0CCE0"))
            textSize = 14f
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(dp(3).toFloat(), 1f)
            visibility = View.GONE
        }

        header.setOnClickListener {
            val show = answerView.visibility != View.VISIBLE
            answerView.visibility = if (show) View.VISIBLE else View.GONE
            arrow.text = if (show) "–" else "+"
        }

        col.addView(header)
        col.addView(answerView)
        card.addView(col)
        return card
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
