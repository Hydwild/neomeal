package com.nutritrack.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.db.entity.FitnessDataEntity
import com.nutritrack.app.data.repository.ProfileRepository
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.launch

class FitnessViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as NutriApp).database
    private val repo = ProfileRepository(db.profileDao(), db.weightDao())
    val today = DateUtils.today()

    val activeProfile = repo.activeProfile

    val todayFitness: LiveData<FitnessDataEntity?> = activeProfile.switchMap { p ->
        p?.let { db.fitnessDao().getForDate(today, it.id) } ?: MutableLiveData(null)
    }

    val weekFitness = MutableLiveData<List<FitnessDataEntity>>(emptyList())

    init { loadWeek() }

    private fun loadWeek() = viewModelScope.launch {
        val p = repo.getActiveSync() ?: return@launch
        // Чистим возможные ранее накопленные дубли за одну дату
        db.fitnessDao().deleteDuplicates()
        val dates = DateUtils.getWeekDates(0)
        weekFitness.postValue(
            db.fitnessDao().getForRange(dates.first(), dates.last(), p.id)
        )
    }

    /**
     * Импортирует список замеров веса (дата → кг) из Health Connect.
     * Дедупликация по дате: если за день запись уже есть — обновляем.
     * [onDone] вызывается в главном потоке с числом импортированных дней
     * и последним весом (или null, если ничего не пришло).
     */
    fun importWeights(
        items: List<Pair<String, Float>>,
        onDone: (count: Int, lastKg: Float?) -> Unit
    ) = viewModelScope.launch {
        val p = repo.getActiveSync() ?: run { onDone(0, null); return@launch }
        var count = 0
        for ((date, kg) in items) {
            val existing = db.weightDao().getWeightForDate(date, p.id)
            if (existing != null) {
                repo.insertWeight(existing.copy(weight = kg))
            } else {
                repo.insertWeight(
                    com.nutritrack.app.data.db.entity.WeightEntryEntity(
                        weight = kg, date = date, profileId = p.id
                    )
                )
            }
            count++
        }
        onDone(count, items.maxByOrNull { it.first }?.second)
    }

    fun saveFitnessData(data: FitnessDataEntity) = viewModelScope.launch {
        val p = repo.getActiveSync() ?: return@launch
        // Дедупликация по дате: если за этот день запись уже есть — обновляем её
        // (а не плодим дубликаты, иначе недельный «Всего» растёт при каждом синке).
        val existing = db.fitnessDao().getForDateSync(data.date, p.id)
        if (existing != null) {
            db.fitnessDao().update(data.copy(id = existing.id, profileId = p.id))
        } else {
            db.fitnessDao().insert(data.copy(profileId = p.id))
        }
        loadWeek()
    }

    fun saveManualFitness(steps: Int, calBurned: Float, hr: Int) = viewModelScope.launch {
        val p = repo.getActiveSync() ?: return@launch
        val existing = db.fitnessDao().getForDateSync(today, p.id)
        if (existing != null) {
            db.fitnessDao().update(existing.copy(
                steps          = if (steps > 0) steps else existing.steps,
                caloriesBurned = if (calBurned > 0f) calBurned else existing.caloriesBurned,
                heartRateAvg   = if (hr > 0) hr else existing.heartRateAvg,
                source         = "manual"
            ))
        } else {
            db.fitnessDao().insert(FitnessDataEntity(
                date = today, profileId = p.id,
                steps = steps, caloriesBurned = calBurned,
                heartRateAvg = hr, source = "manual"
            ))
        }
        loadWeek()
    }
}