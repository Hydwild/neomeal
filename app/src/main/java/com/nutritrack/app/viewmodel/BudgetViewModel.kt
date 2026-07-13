package com.nutritrack.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.repository.ProfileRepository
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class BudgetPeriodStats(
    val label: String,
    val totalSpent: Float,
    val daysCount: Int,
    val avgPerDay: Float
)

class BudgetViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as NutriApp).database
    private val profileRepo = ProfileRepository(db.profileDao(), db.weightDao())

    private val _stats = MutableLiveData<List<BudgetPeriodStats>>()
    val stats: LiveData<List<BudgetPeriodStats>> = _stats

    // Топ затратных продуктов за последние 30 дней
    private val _topSpending = MutableLiveData<List<com.nutritrack.app.data.db.dao.SpendingRow>>()
    val topSpending: LiveData<List<com.nutritrack.app.data.db.dao.SpendingRow>> = _topSpending

    fun loadStats() = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val results = mutableListOf<BudgetPeriodStats>()

        // Week
        val weekDates = DateUtils.getWeekDates(0)
        val weekSpent = db.diaryDao().getTotalSpentInRange(
            weekDates.first(), weekDates.last(), profile.id
        )
        results.add(BudgetPeriodStats(com.nutritrack.app.utils.LocaleHelper.wrap(getApplication()).getString(com.nutritrack.app.R.string.budget_this_week), weekSpent, 7, weekSpent / 7))

        // Month
        val cal = Calendar.getInstance()
        val monthStart = SimpleDateFormat("yyyy-MM-01", Locale.getDefault()).format(cal.time)
        val monthEnd = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        val monthSpent = db.diaryDao().getTotalSpentInRange(monthStart, monthEnd, profile.id)
        val daysInMonth = cal.get(Calendar.DAY_OF_MONTH)
        results.add(BudgetPeriodStats(com.nutritrack.app.utils.LocaleHelper.wrap(getApplication()).getString(com.nutritrack.app.R.string.budget_this_month), monthSpent, daysInMonth, monthSpent / daysInMonth))

        // Last 30 days
        val cal30 = Calendar.getInstance()
        cal30.add(Calendar.DAY_OF_YEAR, -30)
        val days30Start = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal30.time)
        val today = DateUtils.today()
        val days30Spent = db.diaryDao().getTotalSpentInRange(days30Start, today, profile.id)
        results.add(BudgetPeriodStats(com.nutritrack.app.utils.LocaleHelper.wrap(getApplication()).getString(com.nutritrack.app.R.string.budget_last_30), days30Spent, 30, days30Spent / 30))

        _stats.postValue(results)

        // Топ затрат за 30 дней
        val top = db.diaryDao().getTopSpendingInRange(days30Start, today, profile.id)
        _topSpending.postValue(top)
    }
}