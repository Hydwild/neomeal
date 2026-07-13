package com.nutritrack.app.ui.level

import android.os.Bundle
import android.view.*
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.R
import com.nutritrack.app.ui.custom.AnimatedFrameView
import com.nutritrack.app.databinding.FragmentLevelBinding
import com.nutritrack.app.pets.PetCatalog
import com.nutritrack.app.pets.PetDownloader
import com.nutritrack.app.pets.PetStorage
import com.nutritrack.app.utils.LevelSystem
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.viewmodel.AchievementsViewModel
import kotlinx.coroutines.launch

class LevelFragment : Fragment() {

    private var _binding: FragmentLevelBinding? = null
    private val binding get() = _binding!!
    private val vm: AchievementsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLevelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = PrefsManager(requireContext())

        binding.switchShowLevel.isChecked = prefs.showLevelOnAvatar
        binding.rowShowLevel.setOnClickListener {
            binding.switchShowLevel.isChecked = !binding.switchShowLevel.isChecked
        }
        binding.switchShowLevel.setOnCheckedChangeListener { _, checked ->
            prefs.showLevelOnAvatar = checked
        }

        vm.levelInfo.observe(viewLifecycleOwner) { info -> render(info) }
        vm.loadAchievements()
    }

    private fun render(info: LevelSystem.LevelInfo) {
        val b = _binding ?: return
        b.tvLevelBadge.text = getString(R.string.level_badge_fmt, info.level)
        b.progressXp.max = info.xpForNext
        b.progressXp.progress = info.xpIntoLevel
        b.tvXp.text = getString(R.string.level_xp_fmt, info.xpIntoLevel, info.xpForNext)

        val prefs = PrefsManager(requireContext())
        renderFrames(info.level, prefs)
        renderCompanions(info.level, prefs)
    }

    private fun renderFrames(level: Int, prefs: PrefsManager) {
        val row = binding.rowFrames
        row.removeAllViews()
        LevelSystem.frames.forEach { frame ->
            val unlocked = frame.minLevel <= level || com.nutritrack.app.utils.PremiumManager.isPremium(requireContext())
            val selected = prefs.selectedFrame == frame.id
            val item = buildFrameChip(frame, unlocked, selected)
            if (unlocked) item.setOnClickListener {
                prefs.selectedFrame = frame.id
                renderFrames(level, prefs)
            }
            row.addView(item)
        }
    }

 /** Превью рамки: живая анимированная рамка вокруг кружка-плейсхолдера. */
    private fun buildFrameChip(frame: LevelSystem.Frame, unlocked: Boolean, selected: Boolean): View {
        val ctx = requireContext()
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val col = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT)
            alpha = if (unlocked) 1f else 0.45f
        }
        // Контейнер кружок + рамка
        val frameContainer = android.widget.FrameLayout(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(58), dp(58))
        }
        val circle = View(ctx).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ctx.getColor(R.color.card_bg))
                setStroke(dp(if (selected) 2 else 1), if (selected) ctx.getColor(R.color.color_accent) else 0x33FFFFFF)
            }
        }
        frameContainer.addView(circle)
        if (unlocked) {
            val af = AnimatedFrameView(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER)
                setFrame(frame)
            }
            frameContainer.addView(af)
        } else {
            frameContainer.addView(android.widget.TextView(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                text = "🔒"; textSize = 16f; gravity = Gravity.CENTER
            })
        }
        col.addView(frameContainer)
        col.addView(android.widget.TextView(ctx).apply {
            text = if (unlocked) getString(frame.titleRes) else getString(R.string.level_locked_fmt, frame.minLevel)
            setTextColor(ctx.getColor(R.color.text_secondary))
            textSize = 9f; gravity = Gravity.CENTER; maxLines = 2
            setPadding(0, dp(3), 0, 0)
        })
        return col
    }

    private fun renderCompanions(level: Int, prefs: PrefsManager) {
        val row = binding.rowCompanions
        row.removeAllViews()
        val storage = PetStorage(requireContext())
        LevelSystem.companions.forEach { comp ->
            val unlocked = comp.minLevel <= level || com.nutritrack.app.utils.PremiumManager.isPremium(requireContext())
            val installed = storage.isInstalled(comp.id)
            val selected = prefs.selectedPet == comp.id && installed

            val label = when {
                !unlocked   -> getString(R.string.level_locked_fmt, comp.minLevel)
                !installed  -> getString(R.string.pet_download)
                selected    -> getString(R.string.pet_selected)
                else        -> getString(R.string.pet_select)
            }
            val item = buildChip(
                emojiOrNull = if (unlocked) comp.emoji else "🔒",
                colorRing = requireContext().getColor(R.color.color_accent),
                label = label,
                unlocked = unlocked, selected = selected
            )
            if (unlocked) item.setOnClickListener {
                if (installed) {
                    prefs.selectedPet = comp.id
                    renderCompanions(level, prefs)
                } else {
                    downloadCompanion(comp, level, prefs)
                }
            }
            row.addView(item)
        }
    }

    private fun downloadCompanion(
        comp: LevelSystem.Companion, level: Int, prefs: PrefsManager
    ) {
        val pet = PetCatalog.byId(comp.id) ?: return
        android.widget.Toast.makeText(
            requireContext(), getString(R.string.pet_downloading),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = PetDownloader(requireContext()).download(pet)
            if (_binding == null) return@launch
            when (result) {
                is PetDownloader.Result.Success -> {
                    // первый скачанный — сразу выбрать
                    val storage = PetStorage(requireContext())
                    if (!storage.isInstalled(prefs.selectedPet)) prefs.selectedPet = comp.id
                    renderCompanions(level, prefs)
                }
                is PetDownloader.Result.Error -> {
                    android.widget.Toast.makeText(
                        requireContext(), getString(R.string.pet_download_error),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun buildChip(emojiOrNull: String?, colorRing: Int, label: String, unlocked: Boolean, selected: Boolean): View {
        val ctx = requireContext()
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val col = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(66), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val circle = android.widget.TextView(ctx).apply {
            width = dp(54); height = dp(54)
            gravity = Gravity.CENTER
            text = emojiOrNull ?: ""
            textSize = 22f
            alpha = if (unlocked) 1f else 0.4f
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ctx.getColor(R.color.card_bg))
                setStroke(dp(if (selected) 3 else 2), colorRing)
            }
        }
        col.addView(circle)
        if (label.isNotEmpty()) {
            col.addView(android.widget.TextView(ctx).apply {
                text = label
                setTextColor(ctx.getColor(R.color.text_secondary))
                textSize = 9f
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(0, dp(3), 0, 0)
            })
        }
        return col
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
