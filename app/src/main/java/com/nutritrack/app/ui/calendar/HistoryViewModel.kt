package com.nutritrack.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.db.entity.ProfileEntity
import com.nutritrack.app.utils.CalorieCalculator
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

data class DayStats(
    val date: String,
    val calories: Float = 0f,
    val protein: Float = 0f,
    val fat: Float = 0f,
    val carbs: Float = 0f,
    val waterMl: Int = 0,
    val weightKg: Float? = null,
    val entries: List<EntryRow> = emptyList()
) {
    data class EntryRow(
        val productName: String,
        val grams: Float,
        val calories: Float
    )

    fun progress(calorieGoal: Int): Float =
        if (calorieGoal <= 0) 0f else calories / calorieGoal

    fun status(calorieGoal: Int): Status {
        if (calorieGoal <= 0 || calories <= 0f) return Status.NONE
        val p = progress(calorieGoal)
        return when {
            p in 0.85f..1.10f -> Status.GREEN
            p in 0.60f..1.30f -> Status.YELLOW
            else              -> Status.RED
        }
    }

    enum class Status { GREEN, YELLOW, RED, NONE }
}

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NutriApp).database

    private val _currentMonth = MutableLiveData(YearMonth.now())
    val currentMonth: LiveData<YearMonth> = _currentMonth

    private val _monthStats = MutableLiveData<Map<String, DayStats>>(emptyMap())
    val monthStats: LiveData<Map<String, DayStats>> = _monthStats

    private val _selectedDay = MutableLiveData(DateUtils.today())
    val selectedDay: LiveData<String> = _selectedDay

    private val _selectedDayStats = MutableLiveData(DayStats(DateUtils.today()))
    val selectedDayStats: LiveData<DayStats> = _selectedDayStats

    private val _calorieGoal = MutableLiveData(2000)
    val calorieGoal: LiveData<Int> = _calorieGoal

    private val _waterGoal = MutableLiveData(2000)
    val waterGoal: LiveData<Int> = _waterGoal

    private var activeProfile: ProfileEntity? = null

    init { loadProfileAndData() }

    fun setMonth(month: YearMonth) {
        if (_currentMonth.value == month) return
        _currentMonth.value = month
        loadMonth()
    }

    fun previousMonth() = setMonth((_currentMonth.value ?: YearMonth.now()).minusMonths(1))
    fun nextMonth()     = setMonth((_currentMonth.value ?: YearMonth.now()).plusMonths(1))

    fun selectDay(date: String) {
        _selectedDay.value = date
        loadDayDetails(date)
    }

    private fun loadProfileAndData() = viewModelScope.launch {
        val profile = withContext(Dispatchers.IO) { db.profileDao().getActiveProfileSync() }
        activeProfile = profile
        if (profile != null) {
            _calorieGoal.value = CalorieCalculator.calculateDailyGoal(profile)
            _waterGoal.value   = CalorieCalculator.calculateWaterGoal(profile)
        }
        loadMonth()
        loadDayDetails(_selectedDay.value ?: DateUtils.today())
    }

    private fun loadMonth() = viewModelScope.launch {
        val profile = activeProfile ?: return@launch
        val month = _currentMonth.value ?: return@launch
        val start = month.atDay(1).toString()
        val end   = month.atEndOfMonth().toString()

        val map = withContext(Dispatchers.IO) {
            val entries = db.diaryDao().getEntriesForRange(start, end, profile.id)
            val byDate = entries.groupBy { it.date }
            val result = HashMap<String, DayStats>()
            byDate.forEach { (date, list) ->
                val water = db.waterDao().getTotalWaterSync(date, profile.id)
                result[date] = DayStats(
                    date     = date,
                    calories = list.sumOf { it.calories.toDouble() }.toFloat(),
                    protein  = list.sumOf { it.protein.toDouble()  }.toFloat(),
                    fat      = list.sumOf { it.fat.toDouble()      }.toFloat(),
                    carbs    = list.sumOf { it.carbs.toDouble()    }.toFloat(),
                    waterMl  = water
                )
            }
            result
        }
        _monthStats.value = map
    }

    private fun loadDayDetails(date: String) = viewModelScope.launch {
        val profile = activeProfile ?: return@launch
        val stats = withContext(Dispatchers.IO) {
            val entries = db.diaryDao().getEntriesForDateSync(date, profile.id)
            val water   = db.waterDao().getTotalWaterSync(date, profile.id)
            val weight = db.weightDao().getAllWeightSync(profile.id)
                .firstOrNull { it.date == date }?.weight

            DayStats(
                date     = date,
                calories = entries.sumOf { it.calories.toDouble() }.toFloat(),
                protein  = entries.sumOf { it.protein.toDouble()  }.toFloat(),
                fat      = entries.sumOf { it.fat.toDouble()      }.toFloat(),
                carbs    = entries.sumOf { it.carbs.toDouble()    }.toFloat(),
                waterMl  = water,
                weightKg = weight,
                entries  = entries.map {
                    DayStats.EntryRow(it.productName, it.grams, it.calories)
                }
            )
        }
        _selectedDayStats.value = stats
    }

    fun today(): String = DateUtils.today()
}
