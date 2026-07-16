package com.nutritrack.app.ui.dashboard

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.nutritrack.app.R
import com.nutritrack.app.adapter.WaterCupsAdapter
import com.nutritrack.app.data.db.entity.WaterEntryEntity
import com.nutritrack.app.databinding.FragmentDashboardBinding
import com.nutritrack.app.pets.PetStorage
import com.nutritrack.app.utils.CompanionHelper
import com.nutritrack.app.utils.HapticHelper
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.viewmodel.DashboardViewModel

/**
 * Карточка воды на главном экране: стаканы, кнопки добавления, режимы
 * «живой/классический», анимации прогресса и питомец-талисман.
 *
 * Вынесена из DashboardFragment. Владелец обязан вызвать [release]
 * в onDestroyView (гасит аниматор и освобождает питомца).
 */
class WaterCardController(
    private val fragment: Fragment,
    private val binding: FragmentDashboardBinding,
    private val vm: DashboardViewModel,
    /** Проверка достижений после добавления воды (отложенная — см. фрагмент). */
    private val onAchievementCheck: () -> Unit,
    /** Норма воды выполнена (конфетти раз в день — решает фрагмент). */
    private val onGoalReached: () -> Unit,
) {

    private val ctx get() = fragment.requireContext()
    private val prefs = PrefsManager(fragment.requireContext())

    private lateinit var cupsAdapter: WaterCupsAdapter
    var isLiveMode = prefs.isLiveWaterMode
        private set

    // Питомец
    private var petLoaded = false
    private var lastWaterTotal = -1
    private var petGoalCelebrated = false

    // Анимация прогресса
    private var lastWaterShown = 0
    private var waterAnimator: ValueAnimator? = null
    private var released = false

    fun setup() {
        setupCups()
        setupButtons()
        applyMode(animate = false)
        refreshPet()
    }

 /** Вызывать из onDestroyView фрагмента. */
    fun release() {
        released = true
        waterAnimator?.cancel()
        waterAnimator = null
        binding.petWater.release()
    }

    // Данные

 /** Привязка списка воды за день (из observe во фрагменте). */
    fun bind(entries: List<WaterEntryEntity>) {
        cupsAdapter.submitList(entries)
        val total = entries.sumOf { it.amountMl }
        val goal  = vm.waterGoal.value ?: 2000
        reactPetToWater(total, goal)
        binding.progressWater.max = goal
        binding.progressWaterBar.max = goal
        animateProgress(total.coerceAtMost(goal))

        val valueText = ctx.getString(R.string.ds_water_value_fmt, total)
        val goalText  = ctx.getString(R.string.ds_water_goal_fmt, goal)
        binding.tvWaterValue.text        = valueText
        binding.tvWaterGoal.text         = goalText
        binding.tvWaterValueClassic.text = valueText
        binding.tvWaterGoalClassic.text  = goalText

        val left = (goal - total).coerceAtLeast(0)
        val remainingText = if (left == 0) ctx.getString(R.string.ds_water_done)
                            else ctx.getString(R.string.ds_water_left_fmt, left)
        val remainingColor = if (left == 0) ctx.getColor(R.color.color_success)
                             else ctx.getColor(R.color.color_water)
        binding.tvWaterRemaining.text = remainingText
        binding.tvWaterRemaining.setTextColor(remainingColor)
        binding.tvWaterRemainingClassic.text = remainingText
        binding.tvWaterRemainingClassic.setTextColor(remainingColor)

        if (left == 0 && total > 0) onGoalReached()
    }

    fun setGoalMax(goal: Int) {
        binding.progressWater.max = goal
        binding.progressWaterBar.max = goal
    }

    // Настройка

    private fun setupCups() {
        cupsAdapter = WaterCupsAdapter { entry ->
            // Удаляем сразу, вместо диалога — снекбар с «Отменить»
            vm.deleteWater(entry)
            val bar = com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                ctx.getString(R.string.sb_water_deleted_fmt, entry.amountMl),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).setAction(R.string.c_undo) { vm.restoreWater(entry) }
            fragment.activity?.findViewById<View>(R.id.bottom_navigation)
                ?.takeIf { it.visibility == View.VISIBLE }
                ?.let { bar.anchorView = it }
            bar.show()
        }
        binding.rvWaterCups.apply {
            layoutManager = GridLayoutManager(ctx, 5)
            adapter = cupsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupButtons() {
        binding.btnWater100.setOnClickListener { HapticHelper.tap(it); CompanionHelper.maybeCheer(ctx, "water"); vm.addWater(100) { onAchievementCheck() } }
        binding.btnWater200.setOnClickListener { HapticHelper.tap(it); vm.addWater(200) { onAchievementCheck() } }
        binding.btnWater300.setOnClickListener { HapticHelper.tap(it); CompanionHelper.maybeCheer(ctx, "water"); vm.addWater(300) { onAchievementCheck() } }
        binding.btnWater500.setOnClickListener { HapticHelper.tap(it); vm.addWater(500) { onAchievementCheck() } }
    }

    // Режим отображения

    fun showModePicker() {
        val checked = if (isLiveMode) 0 else 1
        val items = arrayOf(
            ctx.getString(R.string.ds_water_mode_live),
            ctx.getString(R.string.ds_water_mode_classic)
        )
        AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.ds_water_mode_title))
            .setSingleChoiceItems(items, checked) { dialog, which ->
                isLiveMode = (which == 0)
                prefs.isLiveWaterMode = isLiveMode
                applyMode(animate = true)
                dialog.dismiss()
            }
            .setNegativeButton(ctx.getString(R.string.c_cancel), null)
            .show()
    }

    private fun applyMode(animate: Boolean) {
        val live    = binding.layoutWaterLive
        val classic = binding.layoutWaterClassic
        if (isLiveMode) {
            live.visibility = View.VISIBLE; classic.visibility = View.GONE
            // питомец — только в живом режиме и только если загружен
            binding.petWaterContainer.visibility =
                if (petLoaded) View.VISIBLE else View.GONE
            if (animate) { live.alpha = 0f; live.animate().alpha(1f).setDuration(350).start() }
        } else {
            classic.visibility = View.VISIBLE; live.visibility = View.GONE
            binding.petWaterContainer.visibility = View.GONE
            if (animate) { classic.alpha = 0f; classic.animate().alpha(1f).setDuration(350).start() }
        }
    }

    // Анимация прогресса

 /** Системный множитель анимаций (0 = «убрать анимации» в спец.возможностях). */
    private fun animationsEnabled(): Boolean =
        android.provider.Settings.Global.getFloat(
            ctx.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f

 /** Плавно дотягивает кольцо воды до нового значения + лёгкий «подскок» талисмана. */
    private fun animateProgress(target: Int) {
        if (!animationsEnabled()) {
            binding.progressWater.progress = target
            binding.progressWaterBar.progress = target
            lastWaterShown = target
            return
        }
        waterAnimator?.cancel()
        waterAnimator = ValueAnimator.ofInt(lastWaterShown, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                if (released) return@addUpdateListener
                val v = it.animatedValue as Int
                binding.progressWater.progress = v
                binding.progressWaterBar.progress = v
            }
            start()
        }
        lastWaterShown = target

        // Талисман подпрыгивает только в живом режиме (в классическом он скрыт).
        // Если активен анимированный питомец — у него своя реакция, статичный маскот не трогаем.
        if (isLiveMode && !petLoaded) {
            binding.imgWaterMascot.animate()
                .translationY(-8f).setDuration(160)
                .withEndAction {
                    if (!released) binding.imgWaterMascot.animate()
                        .translationY(0f).setDuration(220)
                        .setInterpolator(OvershootInterpolator())
                        .start()
                }
                .start()
        }
    }

    // Питомец

 /** Перечитать выбранного питомца (вызывать и из onResume — мог смениться). */
    fun refreshPet() {
        if (!prefs.petsEnabled) { hidePet(); return }
        val storage = PetStorage(ctx)
        val id = prefs.selectedPet
        petLoaded = if (storage.isInstalled(id)) binding.petWater.load(id) else false
        if (petLoaded) {
            // питомец виден только в живом режиме воды
            binding.petWaterContainer.visibility =
                if (isLiveMode) View.VISIBLE else View.GONE
            binding.waterMascotContainer.visibility = View.GONE
            // Тап по питомцу — весёлая реакция (подпрыгивание/радость)
            binding.petWater.setOnClickListener {
                HapticHelper.tap(it)
                val act = if (PetStorage(ctx).hasAction(prefs.selectedPet, "jump")) "jump" else "happy"
                binding.petWater.play(act)
            }
            // Базовый уровень воды фиксируем по текущему значению, чтобы
            // следующее РЕАЛЬНОЕ добавление вызвало реакцию (а не съелось как «первое»).
            val cur = vm.todayWater.value?.sumOf { it.amountMl } ?: 0
            val goal = vm.waterGoal.value ?: 2000
            lastWaterTotal = cur
            petGoalCelebrated = goal in 1..cur
        } else {
            hidePet()
        }
    }

    private fun hidePet() {
        petLoaded = false
        binding.petWaterContainer.visibility = View.GONE
        binding.waterMascotContainer.visibility = View.VISIBLE
    }

    private fun reactPetToWater(total: Int, goal: Int) {
        if (!petLoaded) { lastWaterTotal = total; return }
        // база ещё не инициализирована (питомец только что появился) — без реакции
        if (lastWaterTotal < 0) {
            lastWaterTotal = total
            petGoalCelebrated = goal in 1..total
            return
        }
        if (total > lastWaterTotal) {
            // вода добавилась
            if (goal in 1..total && !petGoalCelebrated) {
                petGoalCelebrated = true
                binding.petWater.play("happy")
            } else {
                binding.petWater.play("drink")
            }
        }
        if (total < goal) petGoalCelebrated = false
        lastWaterTotal = total
    }
}
