package com.nutritrack.app.utils

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nutritrack.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Плитка быстрых настроек: «+250 мл воды» из шторки, не открывая приложение.
 * Подзаголовок плитки показывает итог за день после добавления.
 */
class WaterTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.qs_water_label)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val total = try { WaterQuickAdd.add(this@WaterTileService, 250) } catch (e: Exception) {
                AppLog.e("QS tile water add failed", e); return@launch
            }
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    // Короткое подтверждение: итог дня в подписи плитки (API 29+)
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        subtitle = getString(R.string.qs_water_total_fmt, total)
                    }
                    updateTile()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
