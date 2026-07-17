package com.nutritrack.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.db.entity.DiaryEntryEntity
import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.data.db.entity.WaterEntryEntity
import com.nutritrack.app.data.repository.DiaryRepository
import com.nutritrack.app.data.repository.ProductRepository
import com.nutritrack.app.data.repository.ProfileRepository
import com.nutritrack.app.data.repository.WaterRepository
import com.nutritrack.app.utils.CalorieCalculator
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as NutriApp).database
    private val diaryRepo   = DiaryRepository(db.diaryDao())
    private val waterRepo   = WaterRepository(db.waterDao())
    private val profileRepo = ProfileRepository(db.profileDao(), db.weightDao())
    private val productRepo = ProductRepository(db.productDao())
    // achievementManager убран — проверку достижений ведёт AchievementsViewModel

    val activeProfile = profileRepo.activeProfile
    val today = DateUtils.today()

    // Выбранная дата для просмотра дневника (по умолчанию — сегодня)
    private val _selectedDate = MutableLiveData(today)
    val selectedDate: LiveData<String> = _selectedDate

    fun setSelectedDate(date: String) { _selectedDate.value = date }
    fun isToday(): Boolean = _selectedDate.value == today

    /** Обновить виджет на главном экране (калории/вода за сегодня). */
    private fun refreshWidget() {
        com.nutritrack.app.widget.MainWidgetProvider.updateAll(getApplication())
    }

    // Записи дневника за ВЫБРАННЫЙ день (реагируют и на смену даты, и на смену профиля)
    val todayEntries: LiveData<List<DiaryEntryEntity>> =
        androidx.lifecycle.MediatorLiveData<List<DiaryEntryEntity>>().apply {
            var source: LiveData<List<DiaryEntryEntity>>? = null
            fun rebind() {
                val profile = activeProfile.value
                val date = _selectedDate.value ?: today
                source?.let { removeSource(it) }
                if (profile == null) { value = emptyList(); return }
                val newSource = diaryRepo.getEntriesForDate(date, profile.id)
                source = newSource
                addSource(newSource) { value = it ?: emptyList() }
            }
            addSource(activeProfile) { rebind() }
            addSource(_selectedDate) { rebind() }
        }

    // Вода — пока показываем только за сегодня (редактирование воды за прошлые дни — отдельная задача)
    val todayWater: LiveData<List<WaterEntryEntity>> = activeProfile.switchMap { profile ->
        profile?.let { waterRepo.getWaterForDate(today, it.id) } ?: MutableLiveData(emptyList())
    }

    // Тик пересчёта целей: настройки (ручные цели, буст активности) меняются
    // на другом экране — дашборд дёргает refreshGoals() в onResume
    private val goalTick = MutableLiveData(Unit)
    fun refreshGoals() { goalTick.value = Unit }

    // Фитнес за сегодня — для опции «активность увеличивает норму»
    private val todayFitness: LiveData<com.nutritrack.app.data.db.entity.FitnessDataEntity?> =
        activeProfile.switchMap { profile ->
            profile?.let { db.fitnessDao().getForDate(today, it.id) }
                ?: MutableLiveData(null)
        }

    val calorieGoal: LiveData<Int> = MediatorLiveData<Int>().apply {
        fun recompute() {
            val profile = activeProfile.value ?: return
            val base = CalorieCalculator.calculateDailyGoal(profile)
            val boost = if (com.nutritrack.app.utils.PrefsManager(getApplication()).activityBoost)
                (todayFitness.value?.caloriesBurned ?: 0f).toInt() else 0
            value = base + boost
        }
        addSource(activeProfile) { recompute() }
        addSource(todayFitness) { recompute() }
        addSource(goalTick) { recompute() }
    }

    val waterGoal: LiveData<Int> = MediatorLiveData<Int>().apply {
        fun recompute() {
            value = activeProfile.value?.let { CalorieCalculator.calculateWaterGoal(it) } ?: 2000
        }
        addSource(activeProfile) { recompute() }
        addSource(goalTick) { recompute() }
    }

    val allProducts = productRepo.allProducts

    fun addDiaryEntry(product: ProductEntity, grams: Float, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val factor = grams / 100f
        val entry = DiaryEntryEntity(
            productId   = product.id,
            productName = product.name,
            grams       = grams,
            calories    = product.calories * factor,
            protein     = product.protein * factor,
            fat         = product.fat * factor,
            carbs       = product.carbs * factor,
            fiber       = product.fiber?.times(factor),
            sugar       = product.sugar?.times(factor),
            date        = today,
            profileId   = profile.id
        )
        diaryRepo.insert(entry)
        com.nutritrack.app.utils.HealthConnectWriter.writeDiaryEntry(getApplication(), entry)
        refreshWidget()
        // Запись сохранена — теперь безопасно проверять достижения
        onDone?.invoke()
    }

    fun deleteDiaryEntry(entry: DiaryEntryEntity) = viewModelScope.launch {
        diaryRepo.delete(entry)
        com.nutritrack.app.utils.HealthConnectWriter.deleteDiaryEntry(getApplication(), entry)
        refreshWidget()
    }

 /** «Отменить» после удаления: вернуть запись (тот же timestamp — HC-запись восстановится). */
    fun restoreDiaryEntry(entry: DiaryEntryEntity) = viewModelScope.launch {
        val restored = entry.copy(id = 0)
        diaryRepo.insert(restored)
        com.nutritrack.app.utils.HealthConnectWriter.writeDiaryEntry(getApplication(), restored)
        refreshWidget()
    }

 /** Изменить количество грамм у записи дневника (КБЖУ пересчитываются). */
    fun updateDiaryEntry(entry: DiaryEntryEntity, newGrams: Float) = viewModelScope.launch {
        if (entry.grams <= 0f) return@launch
        val factor = newGrams / entry.grams
        val updated = entry.copy(
            grams    = newGrams,
            calories = entry.calories * factor,
            protein  = entry.protein * factor,
            fat      = entry.fat * factor,
            carbs    = entry.carbs * factor,
            fiber    = entry.fiber?.times(factor),
            sugar    = entry.sugar?.times(factor)
        )
        diaryRepo.update(updated)
        // Обновляем и запись в Health Connect (тот же clientRecordId — upsert)
        com.nutritrack.app.utils.HealthConnectWriter.writeDiaryEntry(getApplication(), updated)
        refreshWidget()
    }

    // Быстрый ввод

    suspend fun getRecent(): List<DiaryEntryEntity> {
        val profile = profileRepo.getActiveSync() ?: return emptyList()
        return db.diaryDao().getRecentEntries(profile.id, 8)
    }

    // Переключение профилей (popup на дашборде)

    suspend fun getAllProfilesList(): List<com.nutritrack.app.data.db.entity.ProfileEntity> =
        db.profileDao().dumpAll().sortedBy { it.id }

    fun switchProfile(id: Long, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        db.profileDao().deactivateAll()
        db.profileDao().activateProfile(id)
        onDone?.invoke()
    }

    suspend fun getFrequent(): List<DiaryEntryEntity> {
        val profile = profileRepo.getActiveSync() ?: return emptyList()
        return db.diaryDao().getFrequentEntries(profile.id, 8)
    }

 /** Быстро добавить продукт по образцу прошлой записи (тот же вес и КБЖУ). */
    fun quickAddFromEntry(sample: DiaryEntryEntity, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val entry = sample.copy(
            id = 0,
            date = _selectedDate.value ?: today,
            profileId = profile.id,
            timestamp = System.currentTimeMillis()
        )
        diaryRepo.insert(entry)
        com.nutritrack.app.utils.HealthConnectWriter.writeDiaryEntry(getApplication(), entry)
        refreshWidget()
        onDone?.invoke()
    }

 /** Повторить вчерашний день: копирует все вчерашние записи на выбранную дату. */
    fun repeatYesterday(onDone: ((Int) -> Unit)? = null) = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val target = _selectedDate.value ?: today
        val yesterday = try {
            java.time.LocalDate.parse(target).minusDays(1).toString()
        } catch (_: Exception) { return@launch }

        val entries = db.diaryDao().getEntriesForDateAndProfile(yesterday, profile.id)
        entries.forEach { e ->
            val copy = e.copy(
                id = 0, date = target, profileId = profile.id,
                timestamp = System.currentTimeMillis()
            )
            diaryRepo.insert(copy)
            com.nutritrack.app.utils.HealthConnectWriter.writeDiaryEntry(getApplication(), copy)
        }
        refreshWidget()
        onDone?.invoke(entries.size)
    }

    fun addWater(ml: Int, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val entry = WaterEntryEntity(amountMl = ml, date = today, profileId = profile.id)
        waterRepo.insert(entry)
        com.nutritrack.app.utils.HealthConnectWriter.writeWater(getApplication(), entry)
        refreshWidget()
        onDone?.invoke()
    }

    fun deleteWater(entry: WaterEntryEntity) = viewModelScope.launch {
        waterRepo.delete(entry)
        com.nutritrack.app.utils.HealthConnectWriter.deleteWater(getApplication(), entry)
        refreshWidget()
    }

 /** «Отменить» после удаления воды. */
    fun restoreWater(entry: WaterEntryEntity) = viewModelScope.launch {
        val restored = entry.copy(id = 0)
        waterRepo.insert(restored)
        com.nutritrack.app.utils.HealthConnectWriter.writeWater(getApplication(), restored)
        refreshWidget()
    }
}
