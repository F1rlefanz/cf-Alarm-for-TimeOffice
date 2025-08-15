package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AlarmInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.AppErrorState
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAlarmUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAlarmSkipUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.ErrorHandler
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AlarmUiState(
    val isLoading: Boolean = false,
    val activeAlarms: List<AlarmInfo> = emptyList(),
    val hasActiveAlarms: Boolean = false,
    val nextAlarmTime: String? = null,
    val error: String? = null
)

data class AlarmSkipUiState(
    val isNextAlarmSkipped: Boolean = false,
    val skippedAlarmId: Int? = null,
    val isLoading: Boolean = false,
    val error: AppErrorState? = null
)

/**
 * MEMORY LEAK FIXED: AlarmViewModel with proper resource cleanup
 * 
 * CRITICAL FIXES:
 * ✅ Added onCleared() for proper cleanup
 * ✅ Job tracking for Flow collections
 * ✅ Resource cleanup on destruction
 * ✅ Memory leak prevention
 */
class AlarmViewModel(
    private val alarmUseCase: IAlarmUseCase,
    private val alarmSkipUseCase: IAlarmSkipUseCase,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()
    
    private val _skipState = MutableStateFlow(AlarmSkipUiState())
    val skipState: StateFlow<AlarmSkipUiState> = _skipState.asStateFlow()
    
    // MEMORY LEAK FIX: Track Flow collection job for proper cleanup
    private var alarmObservationJob: Job? = null

    init {
        observeAlarmStatus()
        observeSkipStatus()
        // CLEANUP: Clean expired alarms on startup
        cleanupExpiredAlarmsOnStartup()
    }
    
    /**
     * CLEANUP: Remove expired alarms when ViewModel starts
     */
    private fun cleanupExpiredAlarmsOnStartup() {
        viewModelScope.launch {
            try {
                // Cast to concrete implementation to access cleanup method
                val repository = alarmUseCase as? com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.AlarmUseCase
                if (repository != null) {
                    // For now, trigger cleanup via deleteAll -> rebuild pattern
                    Logger.d(LogTags.ALARM, "Startup cleanup: checking for expired alarms")
                    
                    // Get all alarms and check for expired ones
                    alarmUseCase.getAllAlarms().onSuccess { allAlarms ->
                        val currentTime = System.currentTimeMillis()
                        val expiredAlarms = allAlarms.filter { it.triggerTime <= currentTime }
                        
                        if (expiredAlarms.isNotEmpty()) {
                            Logger.w(LogTags.ALARM, "Found ${expiredAlarms.size} expired alarms on startup, cleaning up")
                            // Delete each expired alarm
                            expiredAlarms.forEach { alarm ->
                                alarmUseCase.deleteAlarm(alarm.id)
                            }
                        } else {
                            Logger.d(LogTags.ALARM, "No expired alarms found on startup")
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "Error during startup cleanup", e)
            }
        }
    }

    /**
     * MEMORY LEAK FIX: Proper Job tracking für Flow collections
     */
    private fun observeAlarmStatus() {
        alarmObservationJob?.cancel() // Cancel any existing observation
        
        alarmObservationJob = viewModelScope.launch {
            try {
                alarmUseCase.activeAlarms
                    .distinctUntilChanged()
                    .collect { alarms ->
                        // FIXED: Only consider future alarms for "next alarm" calculation
                        val currentTime = System.currentTimeMillis()
                        val futureAlarms = alarms.filter { it.triggerTime > currentTime }
                        val nextAlarm = futureAlarms.minByOrNull { it.triggerTime }
                        
                        _uiState.value = _uiState.value.copy(
                            activeAlarms = alarms, // Show all alarms for debugging
                            hasActiveAlarms = alarms.isNotEmpty(),
                            nextAlarmTime = nextAlarm?.formattedTime // Only future alarms
                        )
                        
                        Logger.d(LogTags.ALARM, "Active alarms updated: ${alarms.size} total, ${futureAlarms.size} future")
                        
                        // CLEANUP: Log expired alarms for debugging
                        val expiredAlarms = alarms.filter { it.triggerTime <= currentTime }
                        if (expiredAlarms.isNotEmpty()) {
                            Logger.w(LogTags.ALARM, "Found ${expiredAlarms.size} expired alarms: ${expiredAlarms.map { it.formattedTime }}")
                        }
                    }
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "Error observing alarm status", e)
                _uiState.value = _uiState.value.copy(
                    error = errorHandler.getErrorMessage(e)
                )
            }
        }
    }

    private fun observeSkipStatus() {
        viewModelScope.launch {
            try {
                alarmSkipUseCase.skipStatusFlow
                    .catch { error ->
                        Logger.e(LogTags.ALARM_SKIP, "Error observing skip state", error)
                    }
                    .collect { skipState ->
                        _skipState.value = _skipState.value.copy(
                            isNextAlarmSkipped = skipState.isNextAlarmSkipped,
                            skippedAlarmId = skipState.skippedAlarmId,
                            isLoading = false
                        )
                    }
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM_SKIP, "Error in skip status observation", e)
            }
        }
    }

    fun setAlarmsForShift(shift: ShiftInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Legacy method - wird durch Interface nicht direkt unterstützt
                // Workaround: Konvertiere zu createAlarmsFromEvents call
                val alarmTime = shift.alarmTime
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                
                val alarmInfo = com.github.f1rlefanz.cf_alarmfortimeoffice.model.AlarmInfo(
                    id = shift.id.hashCode(), // Convert String to Int
                    shiftId = shift.id,
                    shiftName = shift.shiftType.displayName,
                    triggerTime = alarmTime,
                    formattedTime = java.time.format.DateTimeFormatter
                        .ofPattern(com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.DateTimeFormats.STANDARD_DATETIME)
                        .format(java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(alarmTime), 
                            java.time.ZoneId.systemDefault()
                        ))
                )
                
                alarmUseCase.saveAlarm(alarmInfo)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        Logger.i(LogTags.ALARM, "Alarm set for shift: ${shift.shiftType.displayName}")
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = errorHandler.getErrorMessage(error)
                        )
                        Logger.e(LogTags.ALARM, "Failed to set alarm for shift", error)
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorHandler.getErrorMessage(e)
                )
                Logger.e(LogTags.ALARM, "Exception setting alarm for shift", e)
            }
        }
    }

    fun cancelAlarm(alarmId: Int) {
        viewModelScope.launch {
            try {
                alarmUseCase.deleteAlarm(alarmId)
                    .onSuccess {
                        Logger.i(LogTags.ALARM, "Alarm cancelled: $alarmId")
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            error = errorHandler.getErrorMessage(error)
                        )
                        Logger.e(LogTags.ALARM, "Failed to cancel alarm: $alarmId", error)
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = errorHandler.getErrorMessage(e)
                )
                Logger.e(LogTags.ALARM, "Exception cancelling alarm: $alarmId", e)
            }
        }
    }

    fun cancelAllAlarms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                alarmUseCase.deleteAllAlarms()
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        Logger.i(LogTags.ALARM, "All alarms cancelled")
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = errorHandler.getErrorMessage(error)
                        )
                        Logger.e(LogTags.ALARM, "Failed to cancel all alarms", error)
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorHandler.getErrorMessage(e)
                )
                Logger.e(LogTags.ALARM, "Exception cancelling all alarms", e)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun skipNextAlarm() {
        viewModelScope.launch {
            _skipState.value = _skipState.value.copy(isLoading = true)
            
            alarmSkipUseCase.skipNextAlarm()
                .onSuccess { result ->
                    Logger.business(LogTags.ALARM_SKIP, "✅ Next alarm skip activated: ${result.alarmName}")
                    // State wird automatisch über skipStatusFlow aktualisiert
                }
                .onFailure { error ->
                    _skipState.value = _skipState.value.copy(
                        isLoading = false,
                        error = AppErrorState.validationError(error.message ?: "Failed to skip alarm")
                    )
                    Logger.e(LogTags.ALARM_SKIP, "❌ Failed to skip next alarm", error)
                }
        }
    }

    fun cancelSkip() {
        viewModelScope.launch {
            alarmSkipUseCase.cancelSkip()
                .onSuccess {
                    Logger.business(LogTags.ALARM_SKIP, "✅ Skip cancelled by user")
                    // State wird automatisch über skipStatusFlow aktualisiert
                }
                .onFailure { error ->
                    Logger.e(LogTags.ALARM_SKIP, "❌ Failed to cancel skip", error)
                }
        }
    }
    
    /**
     * MEMORY LEAK PREVENTION: Comprehensive resource cleanup
     * CRITICAL FIX: This was missing and causing memory leaks!
     */
    override fun onCleared() {
        super.onCleared()
        
        try {
            // MEMORY LEAK FIX: Cancel alarm observation job
            alarmObservationJob?.cancel()
            alarmObservationJob = null
            
            // MEMORY OPTIMIZATION: Clear state to release references
            _uiState.value = AlarmUiState()
            
            Logger.d(LogTags.LIFECYCLE, "AlarmViewModel cleared - cleaning up alarm observations and resources")
        } catch (e: Exception) {
            Logger.e(LogTags.LIFECYCLE, "Error during AlarmViewModel cleanup", e)
        }
        
        // Note: ViewModelScope automatically cancels all remaining coroutines
    }
}
