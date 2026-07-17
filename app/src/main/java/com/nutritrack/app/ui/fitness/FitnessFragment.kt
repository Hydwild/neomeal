package com.nutritrack.app.ui.fitness

import com.nutritrack.app.R

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.databinding.FragmentFitnessBinding
import com.nutritrack.app.utils.HealthConnectManager
import com.nutritrack.app.viewmodel.FitnessViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

class FitnessFragment : Fragment() {

    private var _binding: FragmentFitnessBinding? = null
    private val binding get() = _binding!!

    private val vm: FitnessViewModel by viewModels()
    private lateinit var hcManager: HealthConnectManager

    // Запрос разрешений Health Connect
    // ВАЖНО: HC-разрешения запрашиваются ТОЛЬКО через HC-контракт,
    // обычный RequestMultiplePermissions для них НЕ работает.

    private val requestPermissions =
        registerForActivityResult(
            androidx.health.connect.client.PermissionController
                .createRequestPermissionResultContract()
        ) { granted ->
            if (granted.containsAll(HealthConnectManager.PERMISSIONS)) {
                syncToday()
            } else {
                binding.tvSyncStatus.text =
                    getString(R.string.fit_hc_perm_warn)
            }
        }

    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFitnessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hcManager = HealthConnectManager(requireContext())

        setupHealthConnectCard()
        setupManualInput()
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Health Connect

    private fun setupHealthConnectCard() {
        if (hcManager.isAvailable()) {
            binding.cardHealthConnect.visibility = View.VISIBLE
            binding.tvNoDevice.visibility        = View.GONE
            binding.btnSync.setOnClickListener { checkAndSync() }
        } else {
            binding.cardHealthConnect.visibility = View.GONE
            binding.tvNoDevice.visibility        = View.VISIBLE
        }
    }

    private fun checkAndSync() {
        // Проверка прав — suspend (IPC к Health Connect), нельзя блокировать main
        lifecycleScope.launch {
            if (hcManager.hasPermissions()) {
                syncToday()
            } else {
                // Запускаем HC-контракт с набором health-разрешений (Set, не Array)
                requestPermissions.launch(HealthConnectManager.PERMISSIONS)
            }
        }
    }

    private fun syncToday() {
        binding.tvSyncStatus.text = getString(R.string.fit_syncing)
        binding.btnSync.isEnabled = false

        lifecycleScope.launch {
            val data = hcManager.readDayDataSafe(LocalDate.now())
            binding.btnSync.isEnabled = true

            // Применяем, если есть ЛЮБЫЕ данные (шаги / калории / дистанция / пульс),
            // а не только шаги > 0 — иначе кнопка «ничего не меняет» при нулевых шагах.
            val hasAny = data != null &&
                (data.steps > 0 || data.caloriesBurned > 0f || data.distanceMeters > 0f || data.heartRateAvg > 0)
            if (hasAny) {
                vm.saveFitnessData(data!!)
                binding.tvSyncStatus.text = getString(R.string.fit_synced)
            } else {
                binding.tvSyncStatus.text = getString(R.string.fit_no_data)
            }

            // Импорт веса из Health Connect (источник — Zepp Life / Mi Fitness)
            val weights = hcManager.readWeightHistory(30).map { it.date to it.kg }
            if (weights.isNotEmpty()) {
                vm.importWeights(weights) { count, lastKg ->
                    if (_binding == null) return@importWeights
                    if (lastKg != null) {
                        binding.tvSyncStatus.text =
                            getString(R.string.fit_weight_synced_fmt, count, lastKg)
                    }
                }
            }
        }
    }

    // Ручной ввод

    private fun setupManualInput() {
        // Кнопка "Ввести вручную" раскрывает/скрывает форму
        binding.cardManual.setOnClickListener {
            val isVisible = binding.layoutManualInput.visibility == View.VISIBLE
            binding.layoutManualInput.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding.btnSaveManual.setOnClickListener {
            val steps     = binding.etSteps.text.toString().toIntOrNull() ?: 0
            val calBurned = binding.etCalBurned.text.toString().toFloatOrNull() ?: 0f
            val hr        = binding.etHeartRate.text.toString().toIntOrNull() ?: 0

            if (steps == 0 && calBurned == 0f && hr == 0) {
                android.widget.Toast.makeText(
                    requireContext(), getString(R.string.fit_enter_value),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            vm.saveManualFitness(steps, calBurned, hr)
            binding.layoutManualInput.visibility = View.GONE

            // Очищаем поля
            binding.etSteps.setText("")
            binding.etCalBurned.setText("")
            binding.etHeartRate.setText("")

            android.widget.Toast.makeText(
                requireContext(), getString(R.string.fit_saved),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Наблюдение за данными

    private fun observeData() {
        vm.todayFitness.observe(viewLifecycleOwner) { data ->
            if (data == null) {
                showEmptyState()
                return@observe
            }
            updateUI(data)
        }

        vm.weekFitness.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) return@observe

            val avgSteps   = list.filter { it.steps > 0 }
                .map { it.steps }
                .average()
                .let { if (it.isNaN()) 0.0 else it }
                .toInt()
            val totalSteps = list.sumOf { it.steps }

            binding.tvWeekAvgSteps.text   = getString(R.string.fit_avg_steps_fmt, avgSteps.formatNumber())
            binding.tvWeekTotalSteps.text = getString(R.string.fit_total_steps_fmt, totalSteps.formatNumber())
        }
    }

    private fun showEmptyState() {
        binding.tvSteps.text       = "0"
        binding.tvCalBurned.text   = getString(R.string.cal_kcal_fmt, 0)
        binding.tvHeartRate.text   = "—"
        binding.tvHrZone.text      = "—"
        binding.tvDistance.text    = "—"
        binding.tvActiveMin.text   = "—"
        binding.progressSteps.progress  = 0
        binding.tvStepsPercent.text = "0%"
    }

    private fun updateUI(data: com.nutritrack.app.data.db.entity.FitnessDataEntity) {
        // Шаги
        binding.tvSteps.text = data.steps.formatNumber()
        val stepsPercent = ((data.steps.toFloat() / 10_000f) * 100f)
            .toInt().coerceAtMost(100)
        binding.progressSteps.progress = stepsPercent
        binding.tvStepsPercent.text    = "$stepsPercent%"

        // Калории сожжено
        binding.tvCalBurned.text = if (data.caloriesBurned > 0f)
            getString(R.string.cal_kcal_fmt, data.caloriesBurned.toInt()) else "—"

        // Пульс
        if (data.heartRateAvg > 0) {
            binding.tvHeartRate.text = getString(R.string.fit_bpm_fmt, data.heartRateAvg)
            binding.tvHrZone.text    = getHeartRateZone(data.heartRateAvg).first
            binding.tvHrZone.setTextColor(getHeartRateZone(data.heartRateAvg).second)
        } else {
            binding.tvHeartRate.text = "—"
            binding.tvHrZone.text    = "—"
        }

        // Дистанция
        binding.tvDistance.text = if (data.distanceMeters > 0f)
            getString(R.string.fit_km_fmt, String.format("%.1f", data.distanceMeters / 1000f)) else "—"

        // Активные минуты
        binding.tvActiveMin.text = if (data.activeMinutes > 0)
            getString(R.string.fit_min_fmt, data.activeMinutes) else "—"

        // Источник данных

    }

    // Зона пульса

    private fun getHeartRateZone(hr: Int): Pair<String, Int> = when {
        hr <= 0   -> "—"                 to 0xFF757575.toInt()
        hr < 60   -> getString(R.string.fit_hr_rest)      to 0xFF2196F3.toInt()
        hr < 100  -> getString(R.string.fit_hr_norm)      to 0xFF4CAF50.toInt()
        hr < 120  -> getString(R.string.fit_hr_warmup)    to 0xFF8BC34A.toInt()
        hr < 140  -> getString(R.string.fit_hr_fatburn)   to 0xFFFFEB3B.toInt()
        hr < 160  -> getString(R.string.fit_hr_aerobic)   to 0xFFFF9800.toInt()
        hr < 180  -> getString(R.string.fit_hr_anaerobic) to 0xFFFF5722.toInt()
        else      -> getString(R.string.fit_hr_max)       to 0xFFF44336.toInt()
    }

    // Форматирование

    private fun Int.formatNumber(): String =
        if (this >= 1_000) String.format("%,d", this).replace(",", " ") else toString()
}
