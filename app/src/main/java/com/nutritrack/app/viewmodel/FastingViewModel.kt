package com.nutritrack.app.viewmodel

import android.app.Application
import android.os.CountDownTimer
import androidx.lifecycle.*
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.db.entity.FastingSessionEntity
import com.nutritrack.app.data.repository.ProfileRepository
import com.nutritrack.app.utils.FastingPhase
import com.nutritrack.app.utils.FastingProtocol
import com.nutritrack.app.utils.FastingProtocols
import com.nutritrack.app.utils.FastingTimerState
import kotlinx.coroutines.launch

class FastingViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as NutriApp).database
    private val profileRepo = ProfileRepository(db.profileDao(), db.weightDao())

    val activeProfile = profileRepo.activeProfile

    // Активная сессия голодания из БД
    val activeSession: LiveData<FastingSessionEntity?> = activeProfile.switchMap { profile ->
        profile ?: return@switchMap MutableLiveData(null)
        db.fastingDao().getActiveSession(profile.id)
    }

    // История завершённых сессий
    val completedSessions: LiveData<List<FastingSessionEntity>> = activeProfile.switchMap { profile ->
        profile ?: return@switchMap MutableLiveData(emptyList())
        db.fastingDao().getCompletedSessions(profile.id)
    }

    // Количество завершённых сессий — LiveData-версия для switchMap
    val completedCount: LiveData<Int> = activeProfile.switchMap { profile ->
        profile ?: return@switchMap MutableLiveData(0)
        db.fastingDao().getCompletedCountLive(profile.id)
    }

    private val _timerState = MutableLiveData(FastingTimerState())
    val timerState: LiveData<FastingTimerState> = _timerState

    private var countdownTimer: CountDownTimer? = null

    // Запустить новое голодание
    fun startFasting(protocolId: String) = viewModelScope.launch {
        val profile  = profileRepo.getActiveSync() ?: return@launch
        val protocol = FastingProtocols.getById(protocolId) ?: return@launch

        // Закрыть активную сессию если была
        db.fastingDao().getActiveSessionSync(profile.id)?.let { existing ->
            db.fastingDao().update(
                existing.copy(
                    endTime         = System.currentTimeMillis(),
                    isCompleted     = false,
                    durationMinutes = ((System.currentTimeMillis() - existing.startTime) / 60_000L).toInt()
                )
            )
        }

        val session = FastingSessionEntity(
            profileId    = profile.id,
            startTime    = System.currentTimeMillis(),
            targetHours  = protocol.fastingHours,
            protocolName = protocol.name
        )
        val id = db.fastingDao().insert(session)
        startTimer(session.copy(id = id), protocol)
        com.nutritrack.app.widget.FastingWidgetProvider.updateAll(getApplication())
    }

    // Остановить голодание
    fun stopFasting(completed: Boolean) = viewModelScope.launch {
        val profile = profileRepo.getActiveSync() ?: return@launch
        val now     = System.currentTimeMillis()
        // Закрываем ВСЕ незавершённые сессии (на случай "застрявших") — у всех ставим endTime
        val active = db.fastingDao().getActiveSessionSync(profile.id)
        if (active != null) {
            db.fastingDao().update(
                active.copy(
                    endTime         = now,
                    isCompleted     = completed,
                    durationMinutes = ((now - active.startTime) / 60_000L).toInt()
                )
            )
        }
        countdownTimer?.cancel()
        countdownTimer = null
        _timerState.postValue(FastingTimerState())
        com.nutritrack.app.widget.FastingWidgetProvider.updateAll(getApplication())
    }

    // Восстановить таймер при возврате в приложение
    fun resumeTimerIfActive() = viewModelScope.launch {
        val profile  = profileRepo.getActiveSync() ?: return@launch
        val session  = db.fastingDao().getActiveSessionSync(profile.id)
        if (session == null) {
            // Активной сессии нет — гарантированно гасим таймер и сбрасываем состояние
            countdownTimer?.cancel()
            countdownTimer = null
            if (_timerState.value?.isActive == true) {
                _timerState.postValue(FastingTimerState())
            }
            return@launch
        }
        val protocol = FastingProtocols.all.firstOrNull { it.name == session.protocolName }
            ?: return@launch
        startTimer(session, protocol)
    }

    // Внутренний запуск таймера
    private fun startTimer(session: FastingSessionEntity, protocol: FastingProtocol) {
        countdownTimer?.cancel()
        val targetMs = protocol.fastingHours.toLong() * 3_600_000L

        fun buildState(): FastingTimerState {
            val elapsed       = System.currentTimeMillis() - session.startTime
            val remaining     = (targetMs - elapsed).coerceAtLeast(0L)
            val percent       = ((elapsed.toFloat() / targetMs.toFloat()) * 100f).toInt()
                .coerceIn(0, 100)
            val elapsedHours  = (elapsed / 3_600_000L).toInt()
            val phase = when {
                elapsed >= targetMs     -> FastingPhase.COMPLETE
                elapsedHours >= 20      -> FastingPhase.DEEP_AUTOPHAGY
                elapsedHours >= 16      -> FastingPhase.AUTOPHAGY
                elapsedHours >= 8       -> FastingPhase.KETOSIS
                else                    -> FastingPhase.EARLY
            }
            return FastingTimerState(
                isActive          = true,
                protocol          = protocol,
                elapsedMillis     = elapsed,
                targetMillis      = targetMs,
                progressPercent   = percent,
                elapsedFormatted  = formatMillis(elapsed),
                remainingFormatted = formatMillis(remaining),
                phase             = phase
            )
        }

        _timerState.postValue(buildState())
        val msRemaining = (targetMs - (System.currentTimeMillis() - session.startTime))
            .coerceAtLeast(0L)
        countdownTimer = object : CountDownTimer(msRemaining, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val state = buildState()
                _timerState.postValue(state)
                if (state.phase == FastingPhase.COMPLETE) {
                    cancel()
                    viewModelScope.launch { stopFasting(completed = true) }
                }
            }
            override fun onFinish() {
                viewModelScope.launch { stopFasting(completed = true) }
            }
        }.start()
    }

    // Форматирование времени
    private fun formatMillis(ms: Long): String {
        if (ms <= 0L) return "00:00:00"
        val totalSeconds = ms / 1_000L
        val hours   = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        countdownTimer?.cancel()
    }
}
