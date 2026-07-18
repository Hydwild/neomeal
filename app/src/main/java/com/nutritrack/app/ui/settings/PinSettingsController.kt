package com.nutritrack.app.ui.settings

import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.R
import com.nutritrack.app.utils.DialogHelper
import com.nutritrack.app.utils.PinLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Управление ПИН-кодом на экране настроек: установить / сменить / отключить.
 * Смена и отключение требуют текущий ПИН. Проверка PBKDF2 идёт в фоне,
 * чтобы не подвешивать UI. Вынесено из SettingsFragment.
 */
class PinSettingsController(
    private val fragment: Fragment,
    /** Строка-статус «Вкл/Выкл» в ряду настроек. */
    private val stateLabel: TextView,
) {
    private val ctx get() = fragment.requireContext()
    private fun s(id: Int) = ctx.getString(id)

    fun refreshLabel() {
        stateLabel.text = s(if (PinLock.isSet(ctx)) R.string.pin_on else R.string.pin_off)
    }

    fun onRowClicked() {
        if (!PinLock.isSet(ctx)) { promptNewPin(); return }
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.set_pin))
            .setItems(arrayOf(s(R.string.pin_change), s(R.string.pin_disable))) { _, which ->
                promptCurrentPin { ok ->
                    if (!ok) return@promptCurrentPin
                    if (which == 0) promptNewPin()
                    else { PinLock.clear(ctx); refreshLabel() }
                }
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    private fun pinInput() = EditText(ctx).apply {
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = s(R.string.pin_hint)
    }

    private fun wrap(v: View) = FrameLayout(ctx).apply {
        val pad = (20 * ctx.resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        addView(v)
    }

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    private fun promptCurrentPin(onResult: (Boolean) -> Unit) {
        val input = pinInput()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.pin_enter_current))
            .setView(wrap(input))
            .setPositiveButton(s(R.string.c_save)) { _, _ ->
                val pin = input.text.toString()
                // PBKDF2 в фоне
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.Default) { PinLock.check(ctx, pin) }
                    if (!ok) toast(s(R.string.pin_wrong))
                    onResult(ok)
                }
            }
            .setNegativeButton(s(R.string.dlg_cancel)) { _, _ -> onResult(false) }
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }

    private fun promptNewPin() {
        val input = pinInput()
        val d = AlertDialog.Builder(ctx)
            .setTitle(s(R.string.pin_new_title))
            .setMessage(s(R.string.pin_new_msg))
            .setView(wrap(input))
            .setPositiveButton(s(R.string.c_save)) { _, _ ->
                val pin = input.text.toString()
                if (pin.length < 4) { toast(s(R.string.pin_too_short)); return@setPositiveButton }
                val confirm = pinInput()
                val d2 = AlertDialog.Builder(ctx)
                    .setTitle(s(R.string.pin_confirm_title))
                    .setView(wrap(confirm))
                    .setPositiveButton(s(R.string.c_save)) { _, _ ->
                        if (confirm.text.toString() == pin) {
                            PinLock.set(ctx, pin); refreshLabel(); toast(s(R.string.pin_saved))
                        } else toast(s(R.string.pin_mismatch))
                    }
                    .setNegativeButton(s(R.string.dlg_cancel), null)
                    .create()
                d2.setOnShowListener { DialogHelper.tint(d2, ctx) }
                d2.show()
            }
            .setNegativeButton(s(R.string.dlg_cancel), null)
            .create()
        d.setOnShowListener { DialogHelper.tint(d, ctx) }
        d.show()
    }
}
