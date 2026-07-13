package com.nutritrack.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.nutritrack.app.data.db.AppDatabase
import com.nutritrack.app.data.db.entity.AchievementEntity
import com.nutritrack.app.data.db.entity.DailyGoalEntity
import com.nutritrack.app.utils.AchievementDef
import com.nutritrack.app.utils.AchievementsDefinitions
import com.nutritrack.app.utils.CalorieCalculator
import com.nutritrack.app.utils.DateUtils
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.StreakHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class AchievementDisplayItem(
    val def: AchievementDef,
    val entity: AchievementEntity?,
    val isUnlocked: Boolean,
    val progress: Int
)

class AchievementsViewModel(app: Application) : AndroidViewModel(app) {

    private val db    = AppDatabase.getInstance(app)
    private val prefs = PrefsManager(app)

    private val _achievementItems = MutableLiveData<List<AchievementDisplayItem>>()
    val achievementItems: LiveData<List<AchievementDisplayItem>> = _achievementItems

    private val _unlockedCount = MutableLiveData(0)
    val unlockedCount: LiveData<Int> = _unlockedCount

    private val _levelInfo = MutableLiveData<com.nutritrack.app.utils.LevelSystem.LevelInfo>()
    val levelInfo: LiveData<com.nutritrack.app.utils.LevelSystem.LevelInfo> = _levelInfo

    private val _newlyUnlocked = MutableLiveData<List<AchievementDisplayItem>>()
    val newlyUnlocked: LiveData<List<AchievementDisplayItem>> = _newlyUnlocked

    init { loadAchievements() }

    private fun activeProfileId(): Long = prefs.activeProfileId

    fun loadAchievements() {
        viewModelScope.launch(Dispatchers.IO) {
            val profileId = activeProfileId()
            val entities  = db.achievementDao().getAllSync(profileId)
            val entityMap = entities.associateBy { it.id }

            val items = AchievementsDefinitions.all.map { def ->
                val entity = entityMap[def.id]
                AchievementDisplayItem(
                    def = def, entity = entity,
                    isUnlocked = entity?.isUnlocked ?: false,
                    progress = entity?.progress ?: 0
                )
            }.filter { item ->
                // Секретные достижения скрыты в списке, пока не разблокированы
                item.def.category != "secret" || item.isUnlocked
            }
            _achievementItems.postValue(items)
            _unlockedCount.postValue(items.count { it.isUnlocked })

            // Расчёт XP и уровня (оба источника: достижения + дни выполнения норм)
            val unlocked = items.count { it.isUnlocked }
            val days = db.dailyGoalDao().getAllForProfile(profileId)
            val xp = com.nutritrack.app.utils.LevelSystem.totalXp(unlocked, days)
            _levelInfo.postValue(com.nutritrack.app.utils.LevelSystem.levelInfo(xp))
        }
    }

    fun checkAchievements() {
        viewModelScope.launch(Dispatchers.IO) {
            val profileId = activeProfileId()
            val today     = DateUtils.today()
            val profile   = db.profileDao().getActiveProfileSync()

            // Статистика
            val diaryToday      = db.diaryDao().getEntriesForDateSync(today, profileId)
            val totalEntries    = db.diaryDao().getTotalEntriesCount(profileId)
            val totalCalAll     = db.diaryDao().getTotalCaloriesAllTime(profileId)
            val totalProtAll    = db.diaryDao().getTotalProteinAllTime(profileId)
            val totalCarbAll    = db.diaryDao().getTotalCarbsAllTime(profileId)
            val diaryDays       = db.diaryDao().getDistinctDaysCount(profileId)
            val nightEntries    = db.diaryDao().countNightEntries(profileId)
            val earlyEntries    = db.diaryDao().countEarlyEntries(profileId)

            val waterToday      = db.waterDao().getTotalWaterSync(today, profileId)
            val waterCount      = db.waterDao().getWaterEntriesCount(profileId)
            val waterTotalAll   = db.waterDao().getTotalWaterAllTime(profileId)

            val weightLogs      = db.weightDao().getAllWeightSync(profileId).size
            val fastingDone     = db.fastingDao().getCompletedCount(profileId)

            // Замеры тела
            val allMeasures   = db.measurementDao().dumpAll().filter { it.profileId == profileId }
            val measureCount  = allMeasures.size
            // Уменьшение талии (см): первый - последний (положительное = похудел)
            val waistHist     = allMeasures.filter { it.bodyPart == "waist" }.sortedBy { it.id }
            val waistDrop     = if (waistHist.size >= 2)
                (waistHist.first().valueCm - waistHist.last().valueCm).coerceAtLeast(0f) else 0f
            // Рост бицепса (берём максимум из левого/правого): последний - первый
            val bicepsGain    = listOf("biceps_l", "biceps_r").maxOf { part ->
                val h = allMeasures.filter { it.bodyPart == part }.sortedBy { it.id }
                if (h.size >= 2) (h.last().valueCm - h.first().valueCm).coerceAtLeast(0f) else 0f
            }

            val calToday  = diaryToday.sumOf { it.calories.toDouble() }.toFloat()
            val protToday = diaryToday.sumOf { it.protein.toDouble() }.toFloat()
            val carbToday = diaryToday.sumOf { it.carbs.toDouble() }.toFloat()

            val calGoal   = profile?.let { CalorieCalculator.calculateDailyGoal(it) } ?: 2000
            val waterGoal = profile?.let { CalorieCalculator.calculateWaterGoal(it) } ?: 2000

            val profileComplete = profile != null &&
                    profile.name.isNotBlank() && profile.age > 0 &&
                    profile.height > 0 && profile.weight > 0

            val calorieGoalMet = calToday >= calGoal * 0.85f && calToday <= calGoal * 1.15f
            val waterGoalMet   = waterToday >= waterGoal

            // Факт за сегодня в daily_goals
            db.dailyGoalDao().upsert(
                DailyGoalEntity(
                    profileId = profileId, date = today,
                    calorieGoalMet = calorieGoalMet, waterGoalMet = waterGoalMet
                )
            )

            // Серии
            val allDays    = db.dailyGoalDao().getAllForProfile(profileId)
            val calStreak  = StreakHelper.calorieStreak(allDays)
            val watStreak  = StreakHelper.waterStreak(allDays)
            val perfStreak = StreakHelper.perfectStreak(allDays)

            // Проверка достижений
            val existing = db.achievementDao().getAllSync(profileId).associateBy { it.id }
            val justUnlocked = mutableListOf<AchievementDisplayItem>()

            AchievementsDefinitions.all.forEach { def ->
                val prev = existing[def.id]
                if (prev?.isUnlocked == true) return@forEach

                val (progress, unlocked) = evaluate(
                    def.id,
                    totalEntries = totalEntries, profileComplete = profileComplete,
                    waterCount = waterCount, weightLogs = weightLogs,
                    calGoalMet = calorieGoalMet, waterGoalMet = waterGoalMet,
                    totalCalAll = totalCalAll, totalProtAll = totalProtAll,
                    totalCarbAll = totalCarbAll, waterTotalAll = waterTotalAll,
                    protToday = protToday, carbToday = carbToday,
                    diaryDays = diaryDays, fastingDone = fastingDone,
                    calStreak = calStreak, watStreak = watStreak, perfStreak = perfStreak,
                    nightEntries = nightEntries, earlyEntries = earlyEntries,
                    measureCount = measureCount, waistDrop = waistDrop, bicepsGain = bicepsGain
                )

                if (unlocked) {
                    val updated = AchievementEntity(
                        id = def.id, profileId = profileId,
                        unlockedAt = System.currentTimeMillis().toString(),
                        isUnlocked = true, progress = def.maxProgress
                    )
                    db.achievementDao().insert(updated)
                    justUnlocked.add(AchievementDisplayItem(def, updated, true, def.maxProgress))
                } else if (prev == null || prev.progress != progress) {
                    db.achievementDao().insert(
                        AchievementEntity(
                            id = def.id, profileId = profileId,
                            unlockedAt = null, isUnlocked = false, progress = progress
                        )
                    )
                }
            }

            loadAchievements()
            if (justUnlocked.isNotEmpty()) _newlyUnlocked.postValue(justUnlocked)
        }
    }

    fun clearNewlyUnlocked() { _newlyUnlocked.postValue(emptyList()) }

 /**
 * Разблокировать секретное достижение по id (пасхалка по жесту).
 * Если уже открыто — ничего не делает (повторно не показывает).
 */
    fun unlockSecret(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val def = AchievementsDefinitions.getById(id) ?: return@launch
            val pid = activeProfileId()
            val existing = db.achievementDao().getById(id)
            if (existing?.isUnlocked == true) return@launch  // уже найдено

            val updated = AchievementEntity(
                id = id,
                profileId = pid,
                unlockedAt = System.currentTimeMillis().toString(),
                isUnlocked = true,
                progress = def.maxProgress
            )
            db.achievementDao().insert(updated)
            loadAchievements()
            _newlyUnlocked.postValue(listOf(AchievementDisplayItem(def, updated, true, def.maxProgress)))
        }
    }

    private fun evaluate(
        id: String,
        totalEntries: Int, profileComplete: Boolean,
        waterCount: Int, weightLogs: Int,
        calGoalMet: Boolean, waterGoalMet: Boolean,
        totalCalAll: Float, totalProtAll: Float, totalCarbAll: Float,
        waterTotalAll: Int, protToday: Float, carbToday: Float,
        diaryDays: Int, fastingDone: Int,
        calStreak: Int, watStreak: Int, perfStreak: Int,
        nightEntries: Int, earlyEntries: Int,
        measureCount: Int = 0, waistDrop: Float = 0f, bicepsGain: Float = 0f
    ): Pair<Int, Boolean> {
        return when (id) {
            // Старт
            "first_meal"       -> (if (totalEntries >= 1) 1 else 0) to (totalEntries >= 1)
            "profile_complete" -> (if (profileComplete) 1 else 0) to profileComplete
            "first_water"      -> (if (waterCount >= 1) 1 else 0) to (waterCount >= 1)
            "first_weight"     -> (if (weightLogs >= 1) 1 else 0) to (weightLogs >= 1)

            // Калории
            "calorie_goal_1"       -> (if (calGoalMet) 1 else 0) to calGoalMet
            "calorie_total_10000"  -> totalCalAll.toInt().coerceAtMost(10000) to (totalCalAll >= 10000f)
            "calorie_total_100000" -> totalCalAll.toInt().coerceAtMost(100000) to (totalCalAll >= 100000f)
            "calorie_streak_3"     -> calStreak.coerceAtMost(3) to (calStreak >= 3)
            "calorie_streak_7"     -> calStreak.coerceAtMost(7) to (calStreak >= 7)
            "calorie_streak_30"    -> calStreak.coerceAtMost(30) to (calStreak >= 30)
            "calorie_streak_100"   -> calStreak.coerceAtMost(100) to (calStreak >= 100)

            // Вода
            "water_goal_1"      -> (if (waterGoalMet) 1 else 0) to waterGoalMet
            "water_total_100l"  -> waterTotalAll.coerceAtMost(100000) to (waterTotalAll >= 100000)
            "water_total_1000l" -> waterTotalAll.coerceAtMost(1000000) to (waterTotalAll >= 1000000)
            "water_tower"       -> waterTotalAll.coerceAtMost(5000000) to (waterTotalAll >= 5000000)
            "water_streak_7"    -> watStreak.coerceAtMost(7) to (watStreak >= 7)
            "water_streak_30"   -> watStreak.coerceAtMost(30) to (watStreak >= 30)

            // Белок / углеводы
            "protein_hero"       -> protToday.toInt().coerceAtMost(150) to (protToday >= 150f)
            "protein_total_1kg"  -> totalProtAll.toInt().coerceAtMost(1000) to (totalProtAll >= 1000f)
            "protein_total_10kg" -> totalProtAll.toInt().coerceAtMost(10000) to (totalProtAll >= 10000f)
            "carb_king"          -> carbToday.toInt().coerceAtMost(400) to (carbToday >= 400f)
            "carb_total_50kg"    -> totalCarbAll.toInt().coerceAtMost(50000) to (totalCarbAll >= 50000f)

            // Вес / дневник
            "weight_log_30"  -> weightLogs.coerceAtMost(30) to (weightLogs >= 30)
            "diary_days_30"  -> diaryDays.coerceAtMost(30) to (diaryDays >= 30)
            "diary_days_365" -> diaryDays.coerceAtMost(365) to (diaryDays >= 365)

            // Голодание
            "fasting_16h"      -> fastingDone.coerceAtMost(1) to (fastingDone >= 1)
            "fasting_streak_7" -> fastingDone.coerceAtMost(7) to (fastingDone >= 7)

            // Спец
            "perfect_day"  -> (if (calGoalMet && waterGoalMet) 1 else 0) to (calGoalMet && waterGoalMet)
            "perfect_week" -> perfStreak.coerceAtMost(7) to (perfStreak >= 7)

            // Время суток
            "night_owl"  -> (if (nightEntries > 0) 1 else 0) to (nightEntries > 0)
            "early_bird" -> (if (earlyEntries > 0) 1 else 0) to (earlyEntries > 0)

            // Замеры тела
            "first_measure"  -> (if (measureCount >= 1) 1 else 0) to (measureCount >= 1)
            "measure_log_10" -> measureCount.coerceAtMost(10) to (measureCount >= 10)
            "waist_minus_5"  -> waistDrop.toInt().coerceAtMost(5) to (waistDrop >= 5f)
            "biceps_plus_2"  -> bicepsGain.toInt().coerceAtMost(2) to (bicepsGain >= 2f)

            else -> 0 to false
        }
    }
}
