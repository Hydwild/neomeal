package com.nutritrack.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.repository.DiaryRepository
import com.nutritrack.app.data.repository.ProfileRepository
import com.nutritrack.app.data.repository.WaterRepository
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.launch
import com.nutritrack.app.utils.CalorieCalculator

data class WeekStats(
    val avgCalories: Float,
    val avgWater: Float,
    val totalProtein: Float,
    val totalFat: Float,
    val totalCarbs: Float
)

class AnalyticsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as NutriApp).database
    private val diaryRepo = DiaryRepository(db.diaryDao())
    private val waterRepo = WaterRepository(db.waterDao())
    private val profileRepo = ProfileRepository(db.profileDao(), db.weightDao())

    val activeProfile = profileRepo.activeProfile
    val weightHistory: LiveData<List<com.nutritrack.app.data.db.entity.WeightEntryEntity>> =
        activeProfile.switchMap { profile ->
            profile?.let { profileRepo.getWeightHistory(it.id) } ?: MutableLiveData(emptyList())
        }

    private val _weekCaloriesByDay = MutableLiveData<List<Pair<String, Float>>>()
    val weekCaloriesByDay: LiveData<List<Pair<String, Float>>> = _weekCaloriesByDay

    val calorieGoal: LiveData<Int> = activeProfile.map { profile ->
        profile?.let { CalorieCalculator.calculateDailyGoal(it) } ?: 2000
    }

    fun loadWeekCaloriesByDay() = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val dates = DateUtils.getWeekDates(0)
        val result = dates.map { date ->
            val entries = diaryRepo.getEntriesForDateSync(date, profile.id)
            date to entries.sumOf { it.calories.toDouble() }.toFloat()
        }
        _weekCaloriesByDay.postValue(result)
    }

    private val _weekStats = MutableLiveData<WeekStats>()
    val weekStats: LiveData<WeekStats> = _weekStats

    fun loadWeekStats() = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val dates = DateUtils.getWeekDates(0)
        val start = dates.first()
        val end = dates.last()

        val entries = diaryRepo.getEntriesForRange(start, end, profile.id)
        val days = dates.size.toFloat()

        val totalCal = entries.sumOf { it.calories.toDouble() }.toFloat()
        val totalProt = entries.sumOf { it.protein.toDouble() }.toFloat()
        val totalFat = entries.sumOf { it.fat.toDouble() }.toFloat()
        val totalCarbs = entries.sumOf { it.carbs.toDouble() }.toFloat()

        var totalWater = 0
        dates.forEach { date ->
            totalWater += waterRepo.getTotalSync(date, profile.id)
        }

        _weekStats.postValue(WeekStats(
        avgCalories = totalCal / days,
        avgWater = totalWater / days,
        totalProtein = totalProt,
        totalFat = totalFat,
        totalCarbs = totalCarbs
    ))
    }

    private val _weeklyReport = MutableLiveData<List<com.nutritrack.app.utils.WeeklyReport.Insight>>()
    val weeklyReport: LiveData<List<com.nutritrack.app.utils.WeeklyReport.Insight>> = _weeklyReport

    fun buildWeeklyReport(ctx: android.content.Context) = viewModelScope.launch {
        // Оборачиваем в выбранный язык, чтобы инсайты были на языке приложения
        val lctx = com.nutritrack.app.utils.LocaleHelper.wrap(ctx)
        val profile = profileRepo.getActiveSync() ?: return@launch
        val dates = DateUtils.getWeekDates(0)

        val dayData = dates.map { date ->
            val entries = diaryRepo.getEntriesForDateSync(date, profile.id)
            com.nutritrack.app.utils.WeeklyReport.DayData(
                date = date,
                calories = entries.sumOf { it.calories.toDouble() }.toFloat(),
                protein  = entries.sumOf { it.protein.toDouble() }.toFloat(),
                waterMl  = waterRepo.getTotalSync(date, profile.id)
            )
        }

        // Вес: старейшая и свежайшая записи внутри недели (если есть)
        val history = profileRepo.getWeightHistorySync(profile.id)
        val inWeek = history.filter { it.date >= dates.first() && it.date <= dates.last() }
            .sortedBy { it.date }
        val wStart = inWeek.firstOrNull()?.weight
        val wEnd   = inWeek.lastOrNull()?.weight

        val calGoal = com.nutritrack.app.utils.CalorieCalculator.calculateDailyGoal(profile)
        val proteinGoal = calGoal * 0.30f / 4f
        val waterGoal = com.nutritrack.app.utils.CalorieCalculator.calculateWaterGoal(profile)

        val input = com.nutritrack.app.utils.WeeklyReport.Input(
            days = dayData,
            calorieGoal = calGoal,
            proteinGoalG = proteinGoal,
            waterGoalMl = waterGoal,
            weightStart = wStart,
            weightEnd = wEnd
        )
        _weeklyReport.postValue(com.nutritrack.app.utils.WeeklyReport.build(lctx, input))
    }
}