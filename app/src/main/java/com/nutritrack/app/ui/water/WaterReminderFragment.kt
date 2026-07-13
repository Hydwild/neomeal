package com.nutritrack.app.ui.water

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.nutritrack.app.databinding.FragmentWaterReminderBinding
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.WaterReminderScheduler

class WaterReminderFragment : Fragment() {

    private var _binding: FragmentWaterReminderBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableReminders(true)
            } else {
                binding.switchWaterReminder.isChecked = false
                android.widget.Toast.makeText(requireContext(),
                    "Без разрешения уведомления не работают", android.widget.Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWaterReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())

        // Восстановить сохранённые настройки
        val enabled = prefs.getBool("water_reminder_enabled", false)
        binding.switchWaterReminder.isChecked = enabled
        tintSwitch(enabled)
        binding.layoutSettings.visibility = if (enabled) View.VISIBLE else View.GONE

        // Интервал
        when (prefs.getString("water_interval_min", "120")) {
            "60"  -> binding.rb60.isChecked = true
            "90"  -> binding.rb90.isChecked = true
            "180" -> binding.rb180.isChecked = true
            else  -> binding.rb120.isChecked = true
        }

        binding.tvStartTime.text = prefs.getString("water_start_time", "09:00")
        binding.tvEndTime.text   = prefs.getString("water_end_time", "21:00")
        updateSummary()

        // Главный переключатель
        binding.rowEnable.setOnClickListener {
            binding.switchWaterReminder.isChecked = !binding.switchWaterReminder.isChecked
        }
        binding.switchWaterReminder.setOnCheckedChangeListener { _, checked ->
            tintSwitch(checked)
            if (checked) {
                // Android 13+ — запросить разрешение
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enableReminders(true)
                }
            } else {
                enableReminders(false)
            }
        }

        // Интервал
        binding.rgInterval.setOnCheckedChangeListener { _, id ->
            val min = when (id) {
                binding.rb60.id  -> "60"
                binding.rb90.id  -> "90"
                binding.rb180.id -> "180"
                else             -> "120"
            }
            prefs.setString("water_interval_min", min)
            reschedule()
            updateSummary()
        }

        // Время начала/конца
        binding.rowStartTime.setOnClickListener {
            pickTime(binding.tvStartTime.text.toString()) { t ->
                binding.tvStartTime.text = t
                prefs.setString("water_start_time", t)
                reschedule(); updateSummary()
            }
        }
        binding.rowEndTime.setOnClickListener {
            pickTime(binding.tvEndTime.text.toString()) { t ->
                binding.tvEndTime.text = t
                prefs.setString("water_end_time", t)
                reschedule(); updateSummary()
            }
        }

        // Кнопка настроек батареи / автозапуска
        binding.btnBatterySettings.setOnClickListener { openBatterySettings() }

        // Умное напоминание
        binding.switchSmartWater.isChecked = prefs.smartWaterReminder
        binding.rowSmartWater.setOnClickListener {
            binding.switchSmartWater.isChecked = !binding.switchSmartWater.isChecked
        }
        binding.switchSmartWater.setOnCheckedChangeListener { _, checked ->
            prefs.smartWaterReminder = checked
            if (checked) {
                com.nutritrack.app.utils.SmartWaterScheduler.schedule(requireContext())
            } else {
                com.nutritrack.app.utils.SmartWaterScheduler.cancel(requireContext())
            }
        }
    }

 /** Открывает системные настройки приложения (батарея, автозапуск, уведомления). */
    private fun openBatterySettings() {
        // Сначала пробуем экран отключения оптимизации батареи
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = android.net.Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
            return
        } catch (_: Exception) { }

        // Фолбэк — открыть страницу приложения в настройках
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ).apply {
                data = android.net.Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(),
                "Открой Настройки → Приложения → NeoMeal вручную",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun enableReminders(on: Boolean) {
        prefs.setBool("water_reminder_enabled", on)
        binding.layoutSettings.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            reschedule()
            android.widget.Toast.makeText(requireContext(), "🔔 Напоминания включены", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            WaterReminderScheduler.cancelAll(requireContext())
        }
        updateSummary()
    }

    private fun reschedule() {
        if (!prefs.getBool("water_reminder_enabled", false)) return
        WaterReminderScheduler.schedule(
            requireContext(),
            intervalMin = prefs.getString("water_interval_min", "120").toIntOrNull() ?: 120,
            startTime   = prefs.getString("water_start_time", "09:00"),
            endTime     = prefs.getString("water_end_time", "21:00")
        )
    }

    private fun updateSummary() {
        if (!binding.switchWaterReminder.isChecked) { binding.tvSummary.text = ""; return }
        val interval = prefs.getString("water_interval_min", "120").toIntOrNull() ?: 120
        val start = prefs.getString("water_start_time", "09:00")
        val end   = prefs.getString("water_end_time", "21:00")
        val count = WaterReminderScheduler.countPerDay(interval, start, end)
        binding.tvSummary.text = "Будет ~$count напоминаний в день, с $start до $end"
    }

    private fun pickTime(current: String, onDone: (String) -> Unit) {
        val parts = current.split(":")
        MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(parts.getOrNull(0)?.toIntOrNull() ?: 9)
            .setMinute(parts.getOrNull(1)?.toIntOrNull() ?: 0)
            .setTitleText("Выбери время")
            .build()
            .also { picker ->
                picker.addOnPositiveButtonClickListener {
                    onDone(String.format("%02d:%02d", picker.hour, picker.minute))
                }
                picker.show(childFragmentManager, "water_time")
            }
    }

    private fun tintSwitch(checked: Boolean) {
        val thumb = if (checked) 0xFF7C4DFF.toInt() else 0xFFB0B0C0.toInt()
        val track = if (checked) 0xFF5E3DB8.toInt() else 0xFF3D3358.toInt()
        binding.switchWaterReminder.thumbTintList =
            android.content.res.ColorStateList.valueOf(thumb)
        binding.switchWaterReminder.trackTintList =
            android.content.res.ColorStateList.valueOf(track)
    }
}
