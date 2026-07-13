package com.nutritrack.app.ui.profile
import com.nutritrack.app.utils.navigateAnim

import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.nutritrack.app.R
import com.nutritrack.app.adapter.ProfilesAdapter
import com.nutritrack.app.data.db.entity.ProfileEntity
import com.nutritrack.app.databinding.DialogEditProfileBinding
import com.nutritrack.app.databinding.FragmentProfileBinding
import com.nutritrack.app.ui.custom.AchievementBadgeView
import com.nutritrack.app.viewmodel.AchievementDisplayItem
import com.nutritrack.app.viewmodel.AchievementsViewModel
import com.nutritrack.app.viewmodel.ProfileViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var profilesCount = 0

    private val vm: ProfileViewModel by viewModels()
    private val achievementsVm: AchievementsViewModel by activityViewModels()

    private var pendingAvatarUri: Uri? = null
    private var pendingAvatarBitmap: android.graphics.Bitmap? = null
    private var editBinding: DialogEditProfileBinding? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { showAvatarCropDialog(it) }
        }

 /** Диалог кадрирования миниатюры аватара (зум + перемещение). */
    private fun showAvatarCropDialog(uri: Uri) {
        val src = try {
            requireContext().contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) { null } ?: return

        val view = layoutInflater.inflate(R.layout.dialog_avatar_crop, null)
        val cropView = view.findViewById<com.nutritrack.app.ui.custom.AvatarCropView>(R.id.cropView)
        cropView.setBitmap(src)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.avatar_crop_title))
            .setView(view)
            .setPositiveButton(getString(R.string.c_done)) { _, _ ->
                val cropped = cropView.exportCircle(512)
                if (cropped != null) {
                    pendingAvatarBitmap = cropped
                    pendingAvatarUri = uri
                    editBinding?.ivAvatar?.setImageBitmap(cropped)
                }
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .create().also { it.show(); com.nutritrack.app.utils.DialogHelper.tint(it, requireContext()) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupProfileList()
        observeActiveProfile()
        observeAchievements()
        setupMenuRows()

        binding.btnEditProfile.setOnClickListener { vm.activeProfile.value?.let { showEditDialog(it) } }
        binding.btnAllergensDiet.setOnClickListener { showAllergensDietDialog() }
        binding.btnAddProfile.setOnClickListener {
            if (profilesCount >= 1) {
                if (!com.nutritrack.app.utils.PremiumManager.gate(requireContext(),
                        com.nutritrack.app.utils.PremiumManager.Feature.MULTI_PROFILES)) return@setOnClickListener
                android.widget.Toast.makeText(requireContext(),
                    getString(com.nutritrack.app.R.string.premium_free_badge), android.widget.Toast.LENGTH_SHORT).show()
            }
            showEditDialog(null)
        }
        binding.layoutBadges.setOnClickListener { findNavController().navigateAnim(R.id.achievementsFragment) }
        binding.tvAchievementProgress.setOnClickListener { findNavController().navigateAnim(R.id.achievementsFragment) }
        binding.rowLevelBadge.setOnClickListener { findNavController().navigateAnim(R.id.levelFragment) }
    }

    private fun showAllergensDietDialog() {
        val ctx = requireContext()
        val profile = vm.activeProfile.value ?: return
        val selectedAllergens = com.nutritrack.app.utils.AllergenData.parse(profile.allergens).toMutableSet()
        val selectedDiets = profile.diets.split(";").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        // Цвет текста по теме: на светлой теме фон диалога светлый — нужен тёмный текст
        val isDark = com.nutritrack.app.utils.PrefsManager(ctx).isDarkTheme
        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        fun sectionTitle(text: String) = android.widget.TextView(ctx).apply {
            this.text = text
            setTextColor(ctx.getColor(R.color.color_accent))
            textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        }

        // Пилюля-чип: скруглённая кнопка-тоггл (2 колонки, как в современных онбордингах)
        val accent = ctx.getColor(R.color.color_accent)
        val chipOff = if (isDark) android.graphics.Color.parseColor("#2A2342")
                      else android.graphics.Color.parseColor("#ECEAF4")
        val chipOffText = if (isDark) android.graphics.Color.parseColor("#CCC7E0")
                          else android.graphics.Color.parseColor("#1A1340")
        fun chipBg(color: Int) = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(22).toFloat(); setColor(color)
        }
        fun makeChip(label: String, checkedNow: Boolean, onToggle: (Boolean) -> Unit) =
            android.widget.TextView(ctx).apply {
                text = label
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(12), dp(11), dp(12), dp(11))
                var checked = checkedNow
                fun render() {
                    background = chipBg(if (checked) accent else chipOff)
                    setTextColor(if (checked) android.graphics.Color.WHITE else chipOffText)
                }
                render()
                setOnClickListener { checked = !checked; onToggle(checked); render() }
            }
        fun chipGrid(): android.widget.GridLayout = android.widget.GridLayout(ctx).apply {
            columnCount = 2
        }
        fun addChipTo(grid: android.widget.GridLayout, chip: android.widget.TextView) {
            val lp = android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            ).apply { width = 0; setMargins(dp(4), dp(4), dp(4), dp(4)) }
            grid.addView(chip, lp)
        }

        // Диеты
        root.addView(sectionTitle(getString(R.string.pr_diets_section)))
        val dietsGrid = chipGrid()
        com.nutritrack.app.utils.DietFilter.DIETS.filter { it.key != "none" }.forEach { diet ->
            val label = getString(dietNameRes(diet.key)) + if (diet.medical) " ⚠" else ""
            addChipTo(dietsGrid, makeChip(label, selectedDiets.contains(diet.key)) { c ->
                if (c) selectedDiets.add(diet.key) else selectedDiets.remove(diet.key)
            })
        }
        root.addView(dietsGrid)

        // Аллергены
        root.addView(sectionTitle(getString(R.string.pr_allergens_section)))
        val allergensGrid = chipGrid()
        com.nutritrack.app.utils.AllergenData.ALL.forEach { a ->
            addChipTo(allergensGrid, makeChip("${a.emoji} " + getString(a.titleRes), selectedAllergens.contains(a.code)) { c ->
                if (c) selectedAllergens.add(a.code) else selectedAllergens.remove(a.code)
            })
        }
        root.addView(allergensGrid)
        root.addView(android.widget.TextView(ctx).apply {
            text = getString(R.string.pr_allergens_disclaimer)
            setTextColor(ctx.getColor(R.color.text_secondary)); textSize = 11f
            setPadding(0, dp(12), 0, 0)
        })

        val scroll = androidx.core.widget.NestedScrollView(ctx).apply { addView(root) }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.pr_allergens_diet))
            .setView(scroll)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                vm.setAllergensAndDiets(
                    com.nutritrack.app.utils.AllergenData.join(selectedAllergens),
                    selectedDiets.joinToString(";")
                )
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        dialog.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(dialog, ctx) }
        dialog.show()
    }

    private fun dietNameRes(key: String): Int = when (key) {
        "vegan" -> R.string.diet_vegan
        "vegetarian" -> R.string.diet_vegetarian
        "no_pork" -> R.string.diet_no_pork
        "no_lactose" -> R.string.diet_no_lactose
        "no_gluten" -> R.string.diet_no_gluten
        "pregnant" -> R.string.diet_pregnant
        "nursing" -> R.string.diet_nursing
        "diabetic" -> R.string.diet_diabetic
        "allergic" -> R.string.diet_allergic
        else -> R.string.diet_none
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null; editBinding = null }

    override fun onResume() {
        super.onResume()
        // Рамка/бейдж могли измениться на экране уровней
        if (_binding != null) applyAvatarFrame()
    }

    // Профили

    private fun setupProfileList() {
        val adapter = ProfilesAdapter(
            onSwitch = { vm.switchProfile(it.id) },
            onEdit   = { showEditDialog(it) },
            onDelete = { profile ->
                if (profile.isActive) {
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.pf_cant_delete_active), android.widget.Toast.LENGTH_SHORT).show()
                    return@ProfilesAdapter
                }
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.pf_delete_fmt, profile.name))
                    .setPositiveButton(getString(R.string.c_delete3)) { _, _ -> vm.deleteProfile(profile) }
                    .setNegativeButton(getString(R.string.c_cancel3), null).show()
            }
        )
        binding.rvProfiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProfiles.adapter = adapter
        vm.allProfiles.observe(viewLifecycleOwner) {
            profilesCount = it.size
            adapter.submitList(it)
        }
    }

    // Активный профиль

    private fun observeActiveProfile() {
        vm.activeProfile.observe(viewLifecycleOwner) { profile ->
            profile ?: return@observe
            binding.tvName.text = profile.name
            binding.tvStatHeight.text = profile.height.toInt().toString()
            binding.tvStatWeight.text = profile.weight.toInt().toString()
            binding.tvStatAge.text    = profile.age.toString()

            if (!profile.photoUri.isNullOrEmpty()) {
                Glide.with(this).load(Uri.parse(profile.photoUri)).circleCrop().into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder)
            }
            applyAvatarFrame()
        }
    }

 /** Применяет выбранную рамку (цвет обводки) и угловой бейдж уровня. */
    private fun applyAvatarFrame() {
        val b = _binding ?: return
        val prefs = com.nutritrack.app.utils.PrefsManager(requireContext())
        val frame = com.nutritrack.app.utils.LevelSystem.frameById(prefs.selectedFrame)
        // Анимированная рамка поверх аватара
        b.avatarFrame.setFrame(frame)
        // Базовая обводка фото: нейтральная (рамка рисуется отдельной вью снаружи)
        b.ivAvatar.borderColor = requireContext().getColor(R.color.color_accent)
        // Угловой бейдж уровня — по флагу
        val info = achievementsVm.levelInfo.value
        if (prefs.showLevelOnAvatar && info != null) {
            b.tvAvatarLevel.visibility = View.VISIBLE
            b.tvAvatarLevel.text = info.level.toString()
        } else {
            b.tvAvatarLevel.visibility = View.GONE
        }
    }

    // Достижения — шестиугольные значки

    private fun observeAchievements() {
        achievementsVm.achievementItems.observe(viewLifecycleOwner) { items ->
            val unlocked = items.filter { it.isUnlocked }
            renderBadges(unlocked, items.size)
            achievementsVm.checkAchievements()
        }
        achievementsVm.levelInfo.observe(viewLifecycleOwner) { info ->
            binding.tvLevelChip.text = getString(R.string.level_badge_fmt, info.level)
            applyAvatarFrame()
        }
    }

    private fun renderBadges(unlocked: List<AchievementDisplayItem>, total: Int) {
        val container = binding.layoutBadges
        container.removeAllViews()

        if (unlocked.isEmpty()) {
            val tv = android.widget.TextView(requireContext()).apply {
                text = "🔒 Пока нет достижений"
                setTextColor(requireContext().getColor(R.color.text_secondary))
                textSize = 13f
            }
            container.addView(tv)
            binding.tvAchievementProgress.text = getString(R.string.pf_ach_locked)
            return
        }

        val size    = dpToPx(48)
        val overlap = dpToPx(12)
        // Сортируем по редкости: легендарные эпические редкие обычные
        val rarityOrder = mapOf("legendary" to 0, "epic" to 1, "rare" to 2, "common" to 3)
        val sorted  = unlocked.sortedBy { rarityOrder[it.def.rarity] ?: 4 }
        val visible = sorted.take(4)
        val extra   = sorted.size - visible.size

        visible.forEachIndexed { index, item ->
            val badge = AchievementBadgeView(requireContext()).apply {
                emoji    = item.def.icon
                rarity   = item.def.rarity
                this.unlocked = true
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    if (index > 0) marginStart = -overlap
                }
            }
            container.addView(badge)
        }

        if (extra > 0) {
            val plus = android.widget.TextView(requireContext()).apply {
                text = "+$extra"
                setTextColor(requireContext().getColor(R.color.white))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = requireContext().getDrawable(R.drawable.circle_bg)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = -overlap
                }
            }
            container.addView(plus)
        }

        binding.tvAchievementProgress.text = getString(R.string.pf_ach_progress_fmt, unlocked.size, total)
    }

    // Меню разделов

    private fun setupMenuRows() {
        binding.rowSettings.setOnClickListener     { findNavController().navigateAnim(R.id.settingsFragment) }
    }

    // Диалог редактирования

    private fun showEditDialog(profile: ProfileEntity?) {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetStyle)
        val b = DialogEditProfileBinding.inflate(layoutInflater)
        editBinding = b
        dialog.setContentView(b.root)

        // Задаём высоту листа ~90% экрана и раскрываем сразу,
        // чтобы закреплённая внизу кнопка "Сохранить" была видна без прокрутки
        dialog.setOnShowListener {
            val sheet = (it as BottomSheetDialog)
                .findViewById<android.widget.FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
            sheet?.let { s ->
                val h = (resources.displayMetrics.heightPixels * 0.90).toInt()
                s.layoutParams = s.layoutParams.apply { height = h }
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(s)
                behavior.peekHeight = h
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                // Запрещаем перетаскивание листа — иначе он перехватывает скролл
                // и после прокрутки вниз нельзя вернуться вверх внутренним ScrollView
                behavior.isDraggable = false
                behavior.skipCollapsed = true
            }
        }

        profile?.let {
            b.tvTitle.text = getString(R.string.pf_edit_profile)
            b.etName.setText(it.name)
            b.etAge.setText(it.age.toString())
            b.etHeight.setText(it.height.toString())
            b.etWeight.setText(it.weight.toString())
            b.rgGender.check(if (it.gender == "male") R.id.rbMale else R.id.rbFemale)
            when (it.goal) {
                "lose"    -> b.rgGoal.check(R.id.rbLose)
                "gain"    -> b.rgGoal.check(R.id.rbGain)
                "healthy" -> b.rgGoal.check(R.id.rbHealthy)
                else      -> b.rgGoal.check(R.id.rbMaintain)
            }
            when (it.activityLevel) {
                "sedentary"   -> b.rgActivity.check(R.id.rbSedentary)
                "light"       -> b.rgActivity.check(R.id.rbLight)
                "active"      -> b.rgActivity.check(R.id.rbActive)
                "very_active" -> b.rgActivity.check(R.id.rbVeryActive)
                else          -> b.rgActivity.check(R.id.rbModerate)
            }
            if (!it.photoUri.isNullOrEmpty())
                Glide.with(this).load(Uri.parse(it.photoUri)).circleCrop().into(b.ivAvatar)
        }

        b.ivAvatar.setOnClickListener { pickImage.launch("image/*") }
        b.tvChangeAvatarHint.setOnClickListener { pickImage.launch("image/*") }

        b.btnSave.setOnClickListener {
            val name = b.etName.text.toString().trim().ifEmpty { getString(R.string.pf_default_name) }
            val age  = b.etAge.text.toString().toIntOrNull() ?: 25
            val h    = b.etHeight.text.toString().toFloatOrNull() ?: 170f
            val w    = b.etWeight.text.toString().toFloatOrNull() ?: 70f
            if (age < 5  || age > 120) { b.etAge.error    = getString(R.string.pf_err_age); return@setOnClickListener }
            if (h < 100f || h > 250f)  { b.etHeight.error = getString(R.string.pf_err_height);    return@setOnClickListener }
            if (w < 20f  || w > 400f)  { b.etWeight.error = getString(R.string.pf_err_weight);     return@setOnClickListener }

            val gender   = if (b.rgGender.checkedRadioButtonId == R.id.rbMale) "male" else "female"
            val goal     = when (b.rgGoal.checkedRadioButtonId) {
                R.id.rbLose -> "lose"; R.id.rbGain -> "gain"; R.id.rbHealthy -> "healthy"; else -> "maintain"
            }
            val activity = when (b.rgActivity.checkedRadioButtonId) {
                R.id.rbSedentary  -> "sedentary"; R.id.rbLight -> "light"
                R.id.rbActive     -> "active";    R.id.rbVeryActive -> "very_active"; else -> "moderate"
            }
            // Копируем выбранное фото в папку приложения (чтобы не потерялось при переустановке
            // и попадало в бэкап). Если новое фото не выбрано — оставляем прежний путь.
            val savedAvatarPath = if (pendingAvatarBitmap != null) {
                saveAvatarBitmap(pendingAvatarBitmap!!) ?: profile?.photoUri
            } else if (pendingAvatarUri != null) {
                copyAvatarToInternal(pendingAvatarUri!!) ?: profile?.photoUri
            } else {
                profile?.photoUri
            }

            val updated = ProfileEntity(
                id = profile?.id ?: 0, name = name, age = age, height = h, weight = w,
                gender = gender, goal = goal, activityLevel = activity,
                photoUri = savedAvatarPath,
                isActive = profile?.isActive ?: false
            )
            if (profile == null) vm.insertProfile(updated) { achievementsVm.checkAchievements() }
            else vm.updateProfile(updated) { achievementsVm.checkAchievements() }
            pendingAvatarUri = null
            pendingAvatarBitmap = null
            dialog.dismiss()
        }
        dialog.setOnDismissListener { editBinding = null; pendingAvatarUri = null; pendingAvatarBitmap = null }
        dialog.show()
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

 /** Сохраняет кадрированный bitmap аватара во внутреннюю папку. */
    private fun saveAvatarBitmap(bmp: android.graphics.Bitmap): String? {
        return try {
            val dir = java.io.File(requireContext().filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()
            val dest = java.io.File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(dest).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(dest).toString()
        } catch (e: Exception) {
            null
        }
    }

 /**
 * Копирует выбранное изображение во внутреннюю папку приложения.
 * Возвращает file:// URI локальной копии (переживает переустановку, попадает в бэкап).
 */
    private fun copyAvatarToInternal(source: Uri): String? {
        return try {
            val dir = java.io.File(requireContext().filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()
            val dest = java.io.File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            requireContext().contentResolver.openInputStream(source)?.use { input ->
                java.io.FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(dest).toString()
        } catch (e: Exception) {
            null
        }
    }
}
