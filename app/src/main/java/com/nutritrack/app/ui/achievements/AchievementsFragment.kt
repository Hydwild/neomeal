package com.nutritrack.app.ui.achievements

import com.nutritrack.app.R

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.nutritrack.app.adapter.AchievementsAdapter
import com.nutritrack.app.databinding.FragmentAchievementsBinding
import com.nutritrack.app.viewmodel.AchievementDisplayItem
import com.nutritrack.app.viewmodel.AchievementsViewModel

class AchievementsFragment : Fragment() {

    private var _binding: FragmentAchievementsBinding? = null
    private val binding get() = _binding!!
    private val vm: AchievementsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAchievementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        observeData()
        vm.checkAchievements()
    }

    private fun setupRecycler() {
        val adapter = AchievementsAdapter { item: AchievementDisplayItem ->
            showDetailDialog(item)
        }
        binding.rvAchievements.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvAchievements.adapter = adapter

        vm.achievementItems.observe(viewLifecycleOwner) { items: List<AchievementDisplayItem> ->
            adapter.submitList(items)
        }
    }

    private fun observeData() {
        // Счётчик разблокированных + проценты + статистика по редкости
        vm.unlockedCount.observe(viewLifecycleOwner) { count: Int ->
            val items = vm.achievementItems.value ?: emptyList()
            val total = items.size
            binding.tvProgress.text = getString(R.string.ach_count_fmt, count, total)
            binding.progressOverall.progress = count
            if (total > 0) binding.progressOverall.max = total

            val pct = if (total > 0) (count * 100 / total) else 0
            binding.tvProgressPercent.text = "$pct%"
            binding.tvProgressHint.text = when {
                pct == 0   -> getString(R.string.ach_start)
                pct < 25   -> getString(R.string.ach_good_start)
                pct < 50   -> getString(R.string.ach_progress)
                pct < 75   -> getString(R.string.ach_half)
                pct < 100  -> getString(R.string.ach_almost)
                else       -> getString(R.string.ach_all)
            }

            // Открытые по редкости
            val unlocked = items.filter { it.isUnlocked }
            binding.tvCommon.text    = unlocked.count { it.def.rarity == "common" }.toString()
            binding.tvRare.text      = unlocked.count { it.def.rarity == "rare" }.toString()
            binding.tvEpic.text      = unlocked.count { it.def.rarity == "epic" }.toString()
            binding.tvLegendary.text = unlocked.count { it.def.rarity == "legendary" }.toString()
        }

        // Уведомления о новых достижениях показывает MainActivity (единая точка),
        // чтобы не было конфликта при общем ViewModel. Здесь только обновляем список.
    }

    // Диалог деталей при нажатии на достижение
    private fun showDetailDialog(item: AchievementDisplayItem) {
        val def = item.def

        val progressText = if (def.maxProgress > 1)
            getString(R.string.ach_progress_fmt, item.progress, def.maxProgress) else ""

        val statusText = if (item.isUnlocked)
            getString(R.string.ach_got)
        else
            getString(R.string.ach_not_yet_fmt, progressText)

        val rarityText = when (def.rarity) {
            "rare"      -> getString(R.string.ach_rare)
            "epic"      -> getString(R.string.ach_epic)
            "legendary" -> getString(R.string.ach_legendary)
            else        -> getString(R.string.ach_common)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${def.icon} ${com.nutritrack.app.utils.DataLabel.tr(requireContext(), def.title)}")
            .setMessage("${com.nutritrack.app.utils.DataLabel.tr(requireContext(), def.description)}$statusText\n\n$rarityText")
            .setPositiveButton("OK", null)
            .show()
    }

    // Попап при разблокировке — принимает AchievementDisplayItem
    private fun showUnlockDialog(item: AchievementDisplayItem) {
        val def = item.def
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ach_new_title))
            .setMessage("${def.icon} ${com.nutritrack.app.utils.DataLabel.tr(requireContext(), def.title)}\n\n${com.nutritrack.app.utils.DataLabel.tr(requireContext(), def.description)}")
            .setPositiveButton(getString(R.string.ach_great), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
