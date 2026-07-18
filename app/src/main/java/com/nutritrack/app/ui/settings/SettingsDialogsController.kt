package com.nutritrack.app.ui.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.app.MainActivity
import com.nutritrack.app.R
import com.nutritrack.app.adapter.GradientPickerAdapter
import com.nutritrack.app.databinding.FragmentSettingsBinding
import com.nutritrack.app.utils.AccentHelper
import com.nutritrack.app.utils.AppIconHelper
import com.nutritrack.app.utils.BackgroundHelper
import com.nutritrack.app.utils.CalorieCalculator
import com.nutritrack.app.utils.DialogHelper
import com.nutritrack.app.utils.ImageDownscaler
import com.nutritrack.app.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Диалоги оформления и предпочтений из экрана настроек: фон/акцент/градиент/
 * шрифт/тема/иконка + сплит БЖУ, карточки главного, стартовая вкладка, кнопки
 * воды, ручные цели. Вынесено из SettingsFragment.
 *
 * Пикер картинки-фона запускается лаунчером во фрагменте (нужен lifecycle) и
 * делегирует сюда applyCustomImage.
 */
class SettingsDialogsController(
    private val fragment: Fragment,
    private val binding: FragmentSettingsBinding,
    private val prefs: PrefsManager,
    private val launchPickBackground: () -> Unit,
) {
    private val ctx get() = fragment.requireContext()
    private fun s(id: Int, vararg a: Any) = ctx.getString(id, *a)
    private val density get() = ctx.resources.displayMetrics.density
    private fun mainActivity() = fragment.activity as? MainActivity

 /** Первичные подписи в рядах настроек. */
    fun initLabels() {
        binding.tvThemeMode.text = themeModeLabel(prefs.themeMode)
        binding.tvCurrentAccent.text = AccentHelper.label(ctx)
        binding.tvCurrentBackground.text = currentBackgroundLabel()
        binding.tvCurrentFont.text = fontLabel(prefs.fontFamily)
        binding.tvStartTab.text = startTabLabel(prefs.startTab)
        binding.tvWaterButtons.text = prefs.waterButtons.joinToString("  ·  ") { "+$it" }
        binding.tvAppIcon.text = iconLabel(prefs.appIcon)
        binding.tvMacroSplit.text = "${prefs.macroProtein}/${prefs.macroFat}/${prefs.macroCarbs}"
        updateManualGoalLabels()
    }

    // Шрифт

    fun fontLabel(key: String): String = when (key) {
        "inter"      -> "Inter"
        "manrope"    -> "Manrope"
        "montserrat" -> "Montserrat"
        else         -> s(R.string.dlg_lang_system)
    }

    fun showFontPicker() {
        val keys   = listOf("system", "inter", "manrope", "montserrat")
        val labels = arrayOf(s(R.string.dlg_lang_system), "Inter", "Manrope", "Montserrat")
        AlertDialog.Builder(ctx)
            .setTitle(s(R.string.dlg_font_title))
            .setSingleChoiceItems(labels, keys.indexOf(prefs.fontFamily).coerceAtLeast(0)) { dialog, which ->
                prefs.fontFamily = keys[which]
                binding.tvCurrentFont.text = fontLabel(keys[which])
                dialog.dismiss()
                fragment.requireActivity().recreate()   // применить шрифт
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .show()
    }

    // Фон

    fun currentBackgroundLabel(): String = when (prefs.backgroundKey) {
        "custom_gradient" -> s(R.string.bg_custom_gradient)
        "custom_image"    -> s(R.string.bg_custom_image)
        else -> BackgroundHelper.presets.firstOrNull { it.key == prefs.backgroundKey }
            ?.let { s(it.labelRes) } ?: s(R.string.bg_default)
    }

    fun showBackgroundPicker() {
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_background_picker, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rvGradients)
        rv.layoutManager = GridLayoutManager(ctx, 3)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dialogView)
            .setNegativeButton(s(R.string.c_close), null)
            .create()
        dialog.setOnShowListener { DialogHelper.tint(dialog, ctx) }

        rv.adapter = GradientPickerAdapter(BackgroundHelper.presets, prefs.backgroundKey) { preset ->
            prefs.backgroundKey = preset.key
            binding.tvCurrentBackground.text = s(preset.labelRes)
            mainActivity()?.let { BackgroundHelper.apply(it.findViewById(R.id.rootBackground)) }
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnCustomGradient).setOnClickListener {
            dialog.dismiss(); showCustomGradientDialog()
        }
        dialogView.findViewById<View>(R.id.btnCustomImage).setOnClickListener {
            dialog.dismiss(); launchPickBackground()
        }
        dialog.show()
    }

    fun showCustomGradientDialog() {
        val view = fragment.layoutInflater.inflate(R.layout.dialog_gradient_palette, null)
        val preview = view.findViewById<View>(R.id.gradientPreview)
        val btnStart = view.findViewById<Button>(R.id.btnColorStart)
        val btnEnd = view.findViewById<Button>(R.id.btnColorEnd)
        val seekR = view.findViewById<SeekBar>(R.id.seekR)
        val seekG = view.findViewById<SeekBar>(R.id.seekG)
        val seekB = view.findViewById<SeekBar>(R.id.seekB)
        val etHex = view.findViewById<EditText>(R.id.etHex)

        val parts = prefs.customGradient.split(",").map { it.trim() }
        var colorStart = runCatching { Color.parseColor(parts.getOrElse(0) { "#667eea" }) }.getOrDefault(Color.parseColor("#667eea"))
        var colorEnd = runCatching { Color.parseColor(parts.getOrElse(1) { "#f093fb" }) }.getOrDefault(Color.parseColor("#f093fb"))
        var editingStart = true
        var suppress = false

        fun renderPreview() {
            preview.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(colorStart, colorEnd)
            ).apply { cornerRadius = 16f * density }
        }
        fun hexOf(c: Int) = String.format("#%06X", 0xFFFFFF and c)
        fun highlightButtons() {
            btnStart.alpha = if (editingStart) 1f else 0.5f
            btnEnd.alpha = if (editingStart) 0.5f else 1f
        }
        fun loadActive() {
            suppress = true
            val c = if (editingStart) colorStart else colorEnd
            seekR.progress = Color.red(c); seekG.progress = Color.green(c); seekB.progress = Color.blue(c)
            etHex.setText(hexOf(c)); suppress = false
        }
        fun applySeekToColor() {
            val c = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
            if (editingStart) colorStart = c else colorEnd = c
            suppress = true; etHex.setText(hexOf(c)); suppress = false
            renderPreview()
        }
        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!suppress && fromUser) applySeekToColor()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        seekR.setOnSeekBarChangeListener(seekListener)
        seekG.setOnSeekBarChangeListener(seekListener)
        seekB.setOnSeekBarChangeListener(seekListener)
        etHex.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(str: android.text.Editable?) {
                if (suppress) return
                val parsed = runCatching { Color.parseColor(str.toString().trim()) }.getOrNull() ?: return
                if (editingStart) colorStart = parsed else colorEnd = parsed
                suppress = true
                seekR.progress = Color.red(parsed); seekG.progress = Color.green(parsed); seekB.progress = Color.blue(parsed)
                suppress = false; renderPreview()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        btnStart.setOnClickListener { editingStart = true; highlightButtons(); loadActive() }
        btnEnd.setOnClickListener { editingStart = false; highlightButtons(); loadActive() }
        highlightButtons(); loadActive(); renderPreview()

        MaterialAlertDialogBuilder(ctx)
            .setTitle(s(R.string.bg_custom_gradient))
            .setView(view)
            .setPositiveButton(s(R.string.c_save)) { _, _ ->
                prefs.customGradient = "${hexOf(colorStart)},${hexOf(colorEnd)}"
                prefs.backgroundManual = true
                prefs.backgroundKey = "custom_gradient"
                binding.tvCurrentBackground.text = s(R.string.bg_custom_gradient)
                mainActivity()?.let {
                    BackgroundHelper.apply(it.findViewById(R.id.rootBackground))
                    BackgroundHelper.applyScrim(it.findViewById(R.id.backgroundScrim))
                }
            }
            .setNegativeButton(s(R.string.c_cancel), null)
            .create().also { it.show(); DialogHelper.tint(it, ctx) }
    }

    fun applyCustomImage(uri: android.net.Uri) {
        val appCtx = ctx.applicationContext
        val dest = java.io.File(appCtx.filesDir, "custom_bg.jpg")
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { ImageDownscaler.saveScaled(appCtx, uri, dest) }
            if (!ok) {
                Toast.makeText(ctx, s(R.string.bg_image_error), Toast.LENGTH_SHORT).show()
                return@launch
            }
            prefs.customImagePath = dest.absolutePath
            prefs.backgroundKey = "custom_image"
            prefs.backgroundManual = true
            binding.tvCurrentBackground.text = s(R.string.bg_custom_image)
            mainActivity()?.let { BackgroundHelper.apply(it.findViewById(R.id.rootBackground)) }
        }
    }

    // Акцент

    fun showAccentPicker() {
        val palette = AccentHelper.palette
        val labels = palette.map { AccentHelper.labelOf(ctx, it.first) }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle(s(R.string.set_accent_title))
            .setSingleChoiceItems(labels, palette.indexOfFirst { it.first == prefs.accentColor }.coerceAtLeast(0)) { dialog, which ->
                prefs.accentColor = palette[which].first
                binding.tvCurrentAccent.text = AccentHelper.labelOf(ctx, palette[which].first)
                dialog.dismiss()
                fragment.requireActivity().recreate()
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .show()
    }

    // Иконка

    fun iconLabel(key: String): String = s(when (key) {
        "dark"   -> R.string.icon_dark
        "purple" -> R.string.icon_purple
        else     -> R.string.icon_classic
    })

    fun showIconPicker() {
        val keys = AppIconHelper.KEYS
        val labels = keys.map { iconLabel(it) }.toTypedArray()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.set_app_icon))
            .setSingleChoiceItems(labels, keys.indexOf(prefs.appIcon).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                val key = keys[which]
                if (key == prefs.appIcon) return@setSingleChoiceItems
                prefs.appIcon = key
                binding.tvAppIcon.text = labels[which]
                AppIconHelper.apply(ctx, key)
                Toast.makeText(ctx, s(R.string.icon_changed), Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    // Тема

    fun themeModeLabel(mode: String): String = s(when (mode) {
        "dark"  -> R.string.theme_dark
        "light" -> R.string.theme_light
        else    -> R.string.theme_auto
    })

    fun showThemeModePicker() {
        val modes = listOf("auto", "light", "dark")
        val labels = modes.map { themeModeLabel(it) }.toTypedArray()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.set_theme_mode))
            .setSingleChoiceItems(labels, modes.indexOf(prefs.themeMode).coerceAtLeast(0)) { dialog, which ->
                val mode = modes[which]
                prefs.themeMode = mode
                prefs.isDarkTheme = mode == "dark"   // для бэкапа/совместимости
                dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(when (mode) {
                    "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                })
                fragment.requireActivity().recreate()
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    // Стартовая вкладка

    fun startTabLabel(tab: String): String = s(when (tab) {
        "plan"    -> R.string.nav_plan
        "stats"   -> R.string.nav_analytics
        "profile" -> R.string.nav_profile
        "more"    -> R.string.nav_more
        else      -> R.string.nav_home
    })

    fun showStartTabPicker() {
        val tabs = listOf("home", "plan", "stats", "profile", "more")
        val labels = tabs.map { startTabLabel(it) }.toTypedArray()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.set_start_tab))
            .setSingleChoiceItems(labels, tabs.indexOf(prefs.startTab).coerceAtLeast(0)) { dialog, which ->
                prefs.startTab = tabs[which]
                binding.tvStartTab.text = labels[which]
                dialog.dismiss()
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    // Кнопки воды

    fun showWaterButtonsDialog() {
        val current = prefs.waterButtons
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt(); setPadding(pad, pad / 2, pad, 0)
        }
        val inputs = current.map { v ->
            EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(v.toString()); hint = s(R.string.u_ml); col.addView(this)
            }
        }
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.set_water_buttons))
            .setMessage(s(R.string.set_water_buttons_msg))
            .setView(col)
            .setPositiveButton(s(R.string.c_save)) { _, _ ->
                val vols = inputs.mapIndexed { i, e -> e.text.toString().toIntOrNull()?.coerceIn(10, 2000) ?: current[i] }
                prefs.waterButtons = vols
                binding.tvWaterButtons.text = vols.joinToString("  ·  ") { "+$it" }
            }
            .setNeutralButton(s(R.string.set_water_buttons_reset)) { _, _ ->
                prefs.waterButtons = listOf(100, 200, 300, 500)
                binding.tvWaterButtons.text = prefs.waterButtons.joinToString("  ·  ") { "+$it" }
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    // Сплит БЖУ

    fun showMacroSplitDialog() {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt(); setPadding(pad, pad / 2, pad, 0)
        }
        fun field(label: String, value: Int): EditText {
            col.addView(TextView(ctx).apply {
                text = label; textSize = 12f; setTextColor(ctx.getColor(R.color.text_secondary))
            })
            return EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(value.toString()); col.addView(this)
            }
        }
        val p = field(s(R.string.macro_protein_lbl), prefs.macroProtein)
        val f = field(s(R.string.macro_fat_lbl), prefs.macroFat)
        val c = field(s(R.string.macro_carbs_lbl), prefs.macroCarbs)
        fun setMacros(pv: Int, fv: Int, cv: Int) {
            prefs.macroProtein = pv; prefs.macroFat = fv; prefs.macroCarbs = cv
            CalorieCalculator.proteinPct = pv; CalorieCalculator.fatPct = fv; CalorieCalculator.carbsPct = cv
            binding.tvMacroSplit.text = "$pv/$fv/$cv"
        }
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.set_macro_split))
            .setMessage(s(R.string.macro_split_msg))
            .setView(col)
            .setPositiveButton(s(R.string.c_save)) { _, _ ->
                val pv = p.text.toString().toIntOrNull() ?: 30
                val fv = f.text.toString().toIntOrNull() ?: 30
                val cv = c.text.toString().toIntOrNull() ?: 40
                if (pv + fv + cv != 100 || pv < 5 || fv < 5 || cv < 5) {
                    Toast.makeText(ctx, s(R.string.macro_split_invalid), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                setMacros(pv, fv, cv)
            }
            .setNeutralButton(s(R.string.set_water_buttons_reset)) { _, _ -> setMacros(30, 30, 40) }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    // Карточки главного

    fun showDashCardsDialog() {
        val keys = listOf("water", "fasting", "suggestions", "streak")
        val labels = arrayOf(
            s(R.string.dash_card_water), s(R.string.dash_card_fasting),
            s(R.string.dash_card_suggestions), s(R.string.dash_card_streak)
        )
        val hidden = prefs.hiddenDashboardCards.toMutableSet()
        val checked = keys.map { it !in hidden }.toBooleanArray()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.set_dash_cards))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) hidden.remove(keys[which]) else hidden.add(keys[which])
            }
            .setPositiveButton(s(R.string.c_save)) { _, _ -> prefs.hiddenDashboardCards = hidden }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    // Ручные цели

    fun updateManualGoalLabels() {
        binding.tvManualCalories.text =
            if (prefs.manualCalorieGoal > 0) s(R.string.cal_kcal_fmt, prefs.manualCalorieGoal)
            else s(R.string.goal_auto)
        binding.tvManualWater.text =
            if (prefs.manualWaterGoal > 0) "${prefs.manualWaterGoal} ${s(R.string.u_ml)}"
            else s(R.string.goal_auto)
    }

    fun showManualGoalDialog(isWater: Boolean) {
        val currentVal = if (isWater) prefs.manualWaterGoal else prefs.manualCalorieGoal
        val input = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            if (currentVal > 0) setText(currentVal.toString())
            hint = s(R.string.goal_auto_hint)
        }
        val container = FrameLayout(ctx).apply {
            val pad = (20 * density).toInt(); setPadding(pad, pad / 2, pad, 0); addView(input)
        }
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(if (isWater) R.string.set_manual_water else R.string.set_manual_cal))
            .setMessage(s(R.string.goal_manual_msg))
            .setView(container)
            .setPositiveButton(s(R.string.c_save)) { _, _ ->
                val v = input.text.toString().toIntOrNull() ?: 0
                if (isWater) {
                    prefs.manualWaterGoal = v; CalorieCalculator.manualWater = prefs.manualWaterGoal
                } else {
                    prefs.manualCalorieGoal = v; CalorieCalculator.manualCalories = prefs.manualCalorieGoal
                }
                updateManualGoalLabels()
                com.nutritrack.app.widget.MainWidgetProvider.updateAll(ctx)
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }
}
