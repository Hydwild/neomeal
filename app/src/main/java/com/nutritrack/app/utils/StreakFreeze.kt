package com.nutritrack.app.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.nutritrack.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Заморозка стрика: вчерашний день помечается замороженным и не рвёт серию
 * (учитывается в [StreakCalculator]). Доступна раз в 7 дней.
 *
 * Логика была зашита в SettingsFragment; вынесена, чтобы вызывать заморозку
 * и с главного экрана (тап по стрику).
 */
object StreakFreeze {

    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

 /** Можно ли использовать заморозку сейчас (кулдаун 7 дней прошёл). */
    fun canFreeze(prefs: PrefsManager): Boolean {
        val last = prefs.lastFreezeDate
        return last.isEmpty() ||
            ChronoUnit.DAYS.between(LocalDate.parse(last, FMT), LocalDate.now()) >= 7
    }

 /** Дата, с которой заморозка снова доступна (для сообщения о кулдауне). */
    fun nextAvailable(prefs: PrefsManager): LocalDate =
        LocalDate.parse(prefs.lastFreezeDate, FMT).plusDays(7)

 /** Вчера уже заморожен? (для индикатора ❄️ у стрика) */
    fun isYesterdayFrozen(prefs: PrefsManager): Boolean =
        LocalDate.now().minusDays(1).format(FMT) in prefs.frozenDates

 /**
 * Полный сценарий с UI: кулдаун-тост или диалог подтверждения.
 * [anchor] — для хаптики, [onFrozen] — обновить экран после заморозки.
 */
    fun requestFreeze(context: Context, prefs: PrefsManager, anchor: View?, onFrozen: () -> Unit) {
        if (!canFreeze(prefs)) {
            val next = nextAvailable(prefs).format(DateTimeFormatter.ofPattern("dd.MM"))
            Toast.makeText(context, context.getString(R.string.freeze_available, next),
                Toast.LENGTH_SHORT).show()
            return
        }
        val d = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.freeze_title))
            .setMessage(context.getString(R.string.freeze_msg))
            .setPositiveButton(context.getString(R.string.freeze_btn)) { _, _ ->
                prefs.frozenDates = prefs.frozenDates + LocalDate.now().minusDays(1).format(FMT)
                prefs.lastFreezeDate = LocalDate.now().format(FMT)
                HapticHelper.success(anchor)
                Toast.makeText(context, context.getString(R.string.freeze_done),
                    Toast.LENGTH_LONG).show()
                onFrozen()
            }
            .setNegativeButton(context.getString(R.string.dlg_cancel), null)
            .create()
        // Единый стиль диалогов приложения
        d.setOnShowListener { DialogHelper.tint(d, context) }
        d.show()
    }
}
