package com.nutritrack.app.ui.pets

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentPetSelectBinding
import com.nutritrack.app.pets.PetCatalog
import com.nutritrack.app.pets.PetDownloader
import com.nutritrack.app.pets.PetStorage
import com.nutritrack.app.utils.PrefsManager
import kotlinx.coroutines.launch

class PetSelectFragment : Fragment() {

    private var _binding: FragmentPetSelectBinding? = null
    private val binding get() = _binding!!

    private lateinit var storage: PetStorage
    private lateinit var downloader: PetDownloader
    private lateinit var prefs: PrefsManager

    // Какие питомцы сейчас качаются (чтобы заблокировать кнопку)
    private val downloading = HashSet<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPetSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storage = PetStorage(requireContext())
        downloader = PetDownloader(requireContext())
        prefs = PrefsManager(requireContext())
        rebuild()
        updatePreview()
    }

    private fun rebuild() {
        binding.petContainer.removeAllViews()
        for (pet in PetCatalog.ALL) {
            binding.petContainer.addView(buildCard(pet))
        }
    }

    private fun buildCard(pet: PetCatalog.Pet): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.micro_card_bg)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(12)
            layoutParams = lp
        }

        val emoji = TextView(ctx).apply {
            text = pet.emoji
            textSize = 30f
        }
        card.addView(emoji, LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))

        val name = TextView(ctx).apply {
            text = getString(resId(pet.nameResName))
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
        }
        card.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Прогресс (скрыт по умолчанию)
        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        card.addView(progress, LinearLayout.LayoutParams(dp(90), dp(8)))

        val btn = Button(ctx).apply {
            isAllCaps = false
            textSize = 14f
        }
        card.addView(btn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        fun renderState() {
            val isInstalled = storage.isInstalled(pet.id)
            val isSelected = prefs.selectedPet == pet.id
            when {
                downloading.contains(pet.id) -> {
                    btn.text = getString(R.string.pet_downloading)
                    btn.isEnabled = false
                    progress.visibility = View.VISIBLE
                }
                !isInstalled -> {
                    btn.text = getString(R.string.pet_download)
                    btn.isEnabled = true
                    progress.visibility = View.GONE
                }
                isSelected -> {
                    btn.text = getString(R.string.pet_selected)
                    btn.isEnabled = false
                    progress.visibility = View.GONE
                }
                else -> {
                    btn.text = getString(R.string.pet_select)
                    btn.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
        renderState()

        btn.setOnClickListener {
            val isInstalled = storage.isInstalled(pet.id)
            if (!isInstalled) {
                startDownload(pet, progress) { renderState() }
            } else {
                prefs.selectedPet = pet.id
                rebuild()
                updatePreview()
            }
        }

        return card
    }

    private fun startDownload(
        pet: PetCatalog.Pet,
        progress: ProgressBar,
        onDone: () -> Unit
    ) {
        downloading.add(pet.id)
        onDone()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = downloader.download(pet) { pct ->
                if (pct >= 0) {
                    progress.isIndeterminate = false
                    progress.progress = pct
                } else {
                    progress.isIndeterminate = true
                }
            }
            downloading.remove(pet.id)
            when (result) {
                is PetDownloader.Result.Success -> {
                    // Если ещё ничего не выбрано — выбрать только что скачанного
                    if (!storage.isInstalled(prefs.selectedPet)) {
                        prefs.selectedPet = pet.id
                    }
                    rebuild()
                    updatePreview()
                }
                is PetDownloader.Result.Error -> {
                    if (_binding != null) {
                        android.widget.Toast.makeText(
                            requireContext(),
                            getString(R.string.pet_download_error),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDone()
                }
            }
        }
    }

    private fun updatePreview() {
        val id = prefs.selectedPet
        if (storage.isInstalled(id) && binding.petPreview.load(id)) {
            binding.petPreview.visibility = View.VISIBLE
            binding.petPreviewHint.visibility = View.GONE
            // лёгкая реакция при выборе
            binding.petPreview.play("happy")
        } else {
            binding.petPreview.visibility = View.GONE
            binding.petPreviewHint.visibility = View.VISIBLE
        }
    }

    private fun resId(name: String): Int =
        resources.getIdentifier(name, "string", requireContext().packageName)

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        binding.petPreview.release()
        super.onDestroyView()
        _binding = null
    }
}
