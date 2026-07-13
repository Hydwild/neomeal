package com.nutritrack.app.ui.whatsnew

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentWhatsNewBinding
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.WhatsNewData

/** Экран «Что нового» — список версий и изменений. */
class WhatsNewFragment : Fragment() {

    private var _binding: FragmentWhatsNewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWhatsNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = binding.containerVersions
        container.removeAllViews()
        WhatsNewData.versions.forEach { release ->
            container.addView(buildVersionCard(release))
        }
        // Отметить журнал как просмотренный
        PrefsManager(requireContext()).lastSeenChangelog = WhatsNewData.latestVersion()
    }

    private fun buildVersionCard(release: WhatsNewData.Release): View {
        val ctx = requireContext()
        val card = androidx.cardview.widget.CardView(ctx).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#22193C"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Заголовок версии + бейдж
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(ctx).apply {
            text = getString(release.titleRes)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(ctx).apply {
            text = "v${release.version}"
            setTextColor(android.graphics.Color.parseColor("#B39DDB"))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        col.addView(header)

        // Пункты
        WhatsNewData.lines(ctx, release).forEach { line ->
            col.addView(TextView(ctx).apply {
                text = "•  $line"
                setTextColor(android.graphics.Color.parseColor("#D0CCE0"))
                textSize = 13.5f
                setPadding(0, dp(7), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            })
        }

        card.addView(col)
        return card
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
