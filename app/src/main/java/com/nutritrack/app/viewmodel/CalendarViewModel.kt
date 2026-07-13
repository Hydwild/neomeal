package com.nutritrack.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.repository.DiaryRepository
import com.nutritrack.app.data.repository.ProfileRepository
import com.nutritrack.app.data.repository.WaterRepository
import com.nutritrack.app.utils.CalorieCalculator
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.launch

data class DayStatus(
    val date: String,
    val calories: Float,
    val calorieGoal: Int,
    val water: Int,
    val waterGoal: Int,
    val protein: Float,
    val fat: Float,
    val carbs: Float
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as NutriApp).database
    private val diaryRepo = DiaryRepository(db.diaryDao())
    private val waterRepo = WaterRepository(db.waterDao())
    private val profileRepo = ProfileRepository(db.profileDao(), db.weightDao())

    private val _weekOffset = MutableLiveData(0)
    val weekOffset: LiveData<Int> = _weekOffset

    private val _weekStatuses = MutableLiveData<List<DayStatus>>()
    val weekStatuses: LiveData<List<DayStatus>> = _weekStatuses

    fun setWeekOffset(offset: Int) {
        _weekOffset.value = offset
        loadWeek(offset)
    }

    fun loadWeek(offset: Int = 0) = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val calGoal = CalorieCalculator.calculateDailyGoal(profile)
        val waterGoal = CalorieCalculator.calculateWaterGoal(profile)
        val dates = DateUtils.getWeekDates(offset)

        val statuses = dates.map { date ->
            val entries = diaryRepo.getEntriesForDateSync(date, profile.id)
            val water = waterRepo.getTotalSync(date, profile.id)
            DayStatus(
                date = date,
                calories = entries.sumOf { it.calories.toDouble() }.toFloat(),
                calorieGoal = calGoal,
                water = water,
                waterGoal = waterGoal,
                protein = entries.sumOf { it.protein.toDouble() }.toFloat(),
                fat = entries.sumOf { it.fat.toDouble() }.toFloat(),
                carbs = entries.sumOf { it.carbs.toDouble() }.toFloat()
            )
        }
        _weekStatuses.postValue(statuses)
    }
}