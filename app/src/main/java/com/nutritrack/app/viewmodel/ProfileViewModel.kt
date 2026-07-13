package com.nutritrack.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.db.entity.ProfileEntity
import com.nutritrack.app.data.db.entity.WeightEntryEntity
import com.nutritrack.app.data.repository.ProfileRepository
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as NutriApp).database
    private val repo = ProfileRepository(db.profileDao(), db.weightDao())

    val allProfiles = repo.allProfiles
    val activeProfile = repo.activeProfile

    val weightHistory: LiveData<List<WeightEntryEntity>> = activeProfile.switchMap { profile ->
        profile?.let { repo.getWeightHistory(it.id) } ?: MutableLiveData(emptyList())
    }

    fun switchProfile(id: Long) = viewModelScope.launch { repo.switchProfile(id) }
    fun insertProfile(profile: ProfileEntity, onDone: (() -> Unit)? = null) = viewModelScope.launch { repo.insert(profile); onDone?.invoke() }
    fun updateProfile(profile: ProfileEntity, onDone: (() -> Unit)? = null) = viewModelScope.launch { repo.update(profile); onDone?.invoke() }
    fun deleteProfile(profile: ProfileEntity) = viewModelScope.launch { repo.delete(profile) }

    fun saveWeight(weight: Float, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val profile = repo.getActiveSync() ?: return@launch
        repo.insertWeight(WeightEntryEntity(weight = weight, date = DateUtils.today(), profileId = profile.id))
        repo.update(profile.copy(weight = weight))
        onDone?.invoke()
    }

    fun setAllergensAndDiets(allergens: String, diets: String, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val active = repo.getActiveSync() ?: return@launch
        repo.update(active.copy(allergens = allergens, diets = diets)); onDone?.invoke()
    }

    fun setTargetWeight(target: Float, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val profile = repo.getActiveSync() ?: return@launch
        repo.update(profile.copy(targetWeight = target))
        onDone?.invoke()
    }

 /**
 * Импорт последнего веса из Health Connect (весы Xiaomi пишут туда через Mi Fitness).
 * onResult(weight) — успех с импортированным весом, onResult(null) — данных нет / нет доступа.
 */
    fun syncWeightFromHealthConnect(hc: com.nutritrack.app.utils.HealthConnectManager,
                                    onResult: (Float?) -> Unit) = viewModelScope.launch {
        val profile = repo.getActiveSync() ?: run { onResult(null); return@launch }
        val result = hc.readLatestWeight()
        if (result == null) {
            onResult(null)
            return@launch
        }
        val (kg, date) = result
        val dateStr = date.toString()
        // Дедупликация по дате: если запись за этот день уже есть — обновляем её
        val existing = repo.getWeightForDate(dateStr, profile.id)
        if (existing != null) {
            repo.insertWeight(existing.copy(weight = kg))
        } else {
            repo.insertWeight(WeightEntryEntity(weight = kg, date = dateStr, profileId = profile.id))
        }
        // Текущий вес профиля обновляем только если импортируем сегодняшний замер
        if (dateStr == DateUtils.today()) {
            repo.update(profile.copy(weight = kg))
        }
        onResult(kg)
    }
}