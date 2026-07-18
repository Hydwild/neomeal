package com.nutritrack.app.ui.fasting

import android.os.Bundle
import android.view.*
import android.view.animation.ScaleAnimation
import android.view.animation.Animation
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.nutritrack.app.R
import com.nutritrack.app.adapter.FastingHistoryAdapter
import com.nutritrack.app.adapter.FastingProtocolAdapter
import com.nutritrack.app.databinding.FragmentFastingBinding
import com.nutritrack.app.utils.FastingPhase
import com.nutritrack.app.utils.FastingProtocol
import com.nutritrack.app.utils.FastingProtocols
import com.nutritrack.app.viewmodel.FastingViewModel

class FastingFragment : Fragment() {

    private var _binding: FragmentFastingBinding? = null
    private val binding get() = _binding!!

    private val vm: FastingViewModel by activityViewModels()
    private val achievementsVm: com.nutritrack.app.viewmodel.AchievementsViewModel by activityViewModels()

    private var pulseAnimationRunning = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFastingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupProtocolList()
        setupHistoryList()
        observeTimerState()
        vm.resumeTimerIfActive()
        binding.btnStopFasting.setOnClickListener { showStopDialog() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        pulseAnimationRunning = false
    }

    private fun setupProtocolList() {
        val adapter = FastingProtocolAdapter { protocol ->
            showProtocolDetailDialog(protocol)
        }
        binding.rvProtocols.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = adapter
            isNestedScrollingEnabled = false
        }
        adapter.submitList(FastingProtocols.all)
    }

    private fun setupHistoryList() {
        val historyAdapter = FastingHistoryAdapter()
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter        = historyAdapter
            isNestedScrollingEnabled = false
        }
        vm.completedSessions.observe(viewLifecycleOwner) { list ->
            historyAdapter.submitList(list)
        }
        vm.completedCount.observe(viewLifecycleOwner) { count ->
            binding.tvCompletedCount.text = getString(R.string.f_sessions, count)
        }
    }

    private fun observeTimerState() {
        vm.timerState.observe(viewLifecycleOwner) { state ->

            if (!state.isActive || state.protocol == null) {
                binding.cardTimer.visibility    = View.GONE
                binding.cardProtocols.visibility = View.VISIBLE
                stopPulseAnimation()
                return@observe
            }

            binding.cardTimer.visibility    = View.VISIBLE
            binding.cardProtocols.visibility = View.GONE

            binding.tvProtocolName.text = com.nutritrack.app.utils.DataLabel.tr(requireContext(), state.protocol.name)
            binding.tvElapsed.text   = state.elapsedFormatted
            binding.tvRemaining.text = getString(R.string.f_remaining, state.remainingFormatted)
            binding.circularProgress.progress = state.progressPercent
            binding.tvProgressPercent.text    = "${state.progressPercent}%"

            val (phaseText, phaseColor) = when (state.phase) {
                FastingPhase.EARLY          -> "🍽️ Ранняя стадия — переваривание" to 0xFF9E9E9E.toInt()
                FastingPhase.KETOSIS        -> "⚡ Кетоз — сжигание жира"          to 0xFFFFD740.toInt()
                FastingPhase.AUTOPHAGY      -> "🧹 Аутофагия — очищение клеток"    to 0xFF69F0AE.toInt()
                FastingPhase.DEEP_AUTOPHAGY -> "🧬 Глубокая аутофагия"             to 0xFF7C4DFF.toInt()
                FastingPhase.COMPLETE       -> "🏆 Цель достигнута!"               to 0xFFFF9800.toInt()
                FastingPhase.NOT_STARTED    -> "—"                                 to 0xFF757575.toInt()
            }
            binding.tvPhase.text = phaseText
            binding.tvPhase.setTextColor(phaseColor)

            val elapsedHours: Int = (state.elapsedMillis / 3_600_000L).toInt()
            val currentBenefits = FastingProtocols.getCurrentBenefits(state.protocol, elapsedHours)
            val nextBenefit     = FastingProtocols.getNextBenefit(state.protocol, elapsedHours)

            if (currentBenefits.isNotEmpty()) {
                binding.tvActiveBenefits.text = currentBenefits.joinToString("\n\n") {
                    "${it.icon} ${com.nutritrack.app.utils.DataLabel.tr(requireContext(), it.title)}\n${com.nutritrack.app.utils.DataLabel.tr(requireContext(), it.description)}"
                }
                binding.cardBenefits.visibility = View.VISIBLE
            } else {
                binding.cardBenefits.visibility = View.GONE
            }

            if (nextBenefit != null) {
                val hoursLeft: Int = nextBenefit.hoursFrom - elapsedHours
                binding.tvNextBenefit.text =
                    getString(R.string.f_next_effect, hoursLeft, nextBenefit.icon, nextBenefit.title)
                binding.tvNextBenefit.visibility = View.VISIBLE
            } else {
                binding.tvNextBenefit.visibility = View.GONE
            }

            if (!pulseAnimationRunning) startPulseAnimation()
        }
    }

    private fun showStopDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.f_finish_q))
            .setMessage(getString(R.string.f_choose_action))
            .setPositiveButton(getString(R.string.f_done)) { _, _ ->
                vm.stopFasting(completed = true)
                showCompletionMessage()
                achievementsVm.checkAchievements()
            }
            .setNeutralButton("❌ Прервать") { _, _ ->
                vm.stopFasting(completed = false)
            }
            .setNegativeButton(getString(R.string.c_continue), null)
            .show()
    }

    private fun showCompletionMessage() {
        android.widget.Toast.makeText(
            requireContext(),
            getString(R.string.f_finished_toast),
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    private fun showProtocolDetailDialog(protocol: FastingProtocol) {
        val sb = StringBuilder()
        sb.append("${protocol.icon} ${com.nutritrack.app.utils.DataLabel.tr(requireContext(), protocol.name)}\n")
        sb.append(getString(R.string.f_difficulty, com.nutritrack.app.utils.DataLabel.tr(requireContext(), protocol.difficulty)))
        sb.append(com.nutritrack.app.utils.DataLabel.tr(requireContext(), protocol.shortDescription))

        if (protocol.fastingHours > 0) {
            sb.append(getString(R.string.f_hours_line_fmt, protocol.fastingHours, protocol.eatingHours))
        }
        if (protocol.benefits.isNotEmpty()) {
            sb.append(getString(R.string.f_effects_header))
            protocol.benefits.forEach {
                sb.append(getString(R.string.f_effect_line_fmt, it.icon, it.hoursFrom, com.nutritrack.app.utils.DataLabel.tr(requireContext(), it.title)))
            }
            // Оговорка: этапы ориентировочны (аудит — маркировать силу доказательств)
            sb.append(getString(R.string.f_effects_note))
        }
        if (protocol.tips.isNotEmpty()) {
            sb.append(getString(R.string.f_tips_header))
            protocol.tips.forEach { sb.append("\n• ${com.nutritrack.app.utils.DataLabel.tr(requireContext(), it)}") }
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.f_protocol_title_fmt, protocol.icon, com.nutritrack.app.utils.DataLabel.tr(requireContext(), protocol.name)))
            .setMessage(sb.toString())
            .setPositiveButton(getString(R.string.f_start)) { _, _ -> confirmAndStart(protocol) }
            .setNegativeButton(getString(R.string.c_close), null)
            .show()
    }

    private fun confirmAndStart(protocol: FastingProtocol) {
        val warningText = when {
            protocol.fastingHours >= 24 ->
                getString(R.string.f_warn_extreme)
            protocol.fastingHours >= 20 ->
                getString(R.string.f_warn_hard)
            else -> null
        }
        if (warningText != null) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.f_warning_title))
                .setMessage(warningText)
                .setPositiveButton(getString(R.string.f_start_anyway)) { _, _ -> vm.startFasting(protocol.id) }
                .setNegativeButton(getString(R.string.c_cancel), null)
                .show()
        } else {
            vm.startFasting(protocol.id)
        }
    }

    private fun startPulseAnimation() {
        pulseAnimationRunning = true
        val anim = ScaleAnimation(
            1f, 1.04f, 1f, 1.04f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration    = 1_200L
            repeatCount = Animation.INFINITE
            repeatMode  = Animation.REVERSE
        }
        binding.tvElapsed.startAnimation(anim)
    }

    private fun stopPulseAnimation() {
        pulseAnimationRunning = false
        binding.tvElapsed.clearAnimation()
    }
}
