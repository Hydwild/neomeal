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

    val calorieGoal: LiveData<Int> = activeProfile.map { profile ->
        profile?.let { CalorieCalculator.calculateDailyGoal(it) } ?: 2000
    }

    val waterGoal: LiveData<Int> = activeProfile.map { profile ->
        profile?.let { CalorieCalculator.calculateWaterGoal(it) } ?: 2000
    }

    val allProducts = productRepo.allProducts

    fun addDiaryEntry(product: ProductEntity, grams: Float, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val factor = grams / 100f
        diaryRepo.insert(DiaryEntryEntity(
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
        ))
        refreshWidget()
        // Запись сохранена — теперь безопасно проверять достижения
        onDone?.invoke()
    }

    fun deleteDiaryEntry(entry: DiaryEntryEntity) = viewModelScope.launch {
        diaryRepo.delete(entry)
        refreshWidget()
    }

 /** Изменить количество грамм у записи дневника (КБЖУ пересчитываются). */
    fun updateDiaryEntry(entry: DiaryEntryEntity, newGrams: Float) = viewModelScope.launch {
        if (entry.grams <= 0f) return@launch
        val factor = newGrams / entry.grams
        diaryRepo.update(entry.copy(
            grams    = newGrams,
            calories = entry.calories * factor,
            protein  = entry.protein * factor,
            fat      = entry.fat * factor,
            carbs    = entry.carbs * factor
        ))
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
        diaryRepo.insert(sample.copy(
            id = 0,
            date = _selectedDate.value ?: today,
            profileId = profile.id,
            timestamp = System.currentTimeMillis()
        ))
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
            diaryRepo.insert(e.copy(
                id = 0, date = target, profileId = profile.id,
                timestamp = System.currentTimeMillis()
            ))
        }
        refreshWidget()
        onDone?.invoke(entries.size)
    }

    fun addWater(ml: Int, onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        waterRepo.insert(WaterEntryEntity(amountMl = ml, date = today, profileId = profile.id))
        refreshWidget()
        onDone?.invoke()
    }

    fun deleteWater(entry: WaterEntryEntity) = viewModelScope.launch {
        waterRepo.delete(entry)
        refreshWidget()
    }
}
