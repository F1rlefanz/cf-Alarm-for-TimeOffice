package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.CalendarEvent
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftConfig
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IShiftUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.ErrorHandler
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class ShiftUiState(
    val isLoading: Boolean = false,
    val currentShiftConfig: ShiftConfig? = null,
    val recognizedShifts: List<ShiftInfo> = emptyList(),
    val upcomingShift: ShiftInfo? = null,
    val error: String? = null
)

class ShiftViewModel(
    private val shiftUseCase: IShiftUseCase,
    private val errorHandler: ErrorHandler,
    private val calendarViewModel: CalendarViewModel? = null // Optional für lose Kopplung
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftUiState())
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()
    
    // MEMORY LEAK FIX: Event-basiertes System statt Callback
    private val _daysAheadChangedEvent = MutableStateFlow(System.currentTimeMillis())
    val daysAheadChangedEvent: StateFlow<Long> = _daysAheadChangedEvent.asStateFlow()
    
    @Deprecated("MEMORY LEAK: Use daysAheadChangedEvent StateFlow instead", level = DeprecationLevel.ERROR)
    fun setDaysAheadChangeCallback(callback: () -> Unit) {
        // ENTFERNT: Memory Leak durch Callback-Referenz
        // Verwende stattdessen daysAheadChangedEvent StateFlow
    }

    init {
        loadShiftConfig()
        observeCalendarEvents() // Neue reactive Schichterkennung
    }
    
    /**
     * REACTIVE PATTERN: Observiert Calendar Events automatisch
     * PERFORMANCE: Mit debouncing und distinctUntilChanged für Effizienz  
     * LOOSE COUPLING: Optional dependency für Entkopplung
     * MEMORY SAFE: Proper cleanup über viewModelScope
     */
    private fun observeCalendarEvents() {
        calendarViewModel?.let { calendarVM ->
            viewModelScope.launch {
                calendarVM.uiState
                    .map { it.events } // Nur Events extrahieren
                    .distinctUntilChanged() // Performance: Nur bei echten Änderungen
                    .debounce(300) // Performance: Batch Updates (300ms für UI-Responsiveness)
                    .collect { events: List<CalendarEvent> ->
                        if (events.isNotEmpty()) {
                            Logger.d(LogTags.SHIFT_RECOGNITION, "Calendar events changed, triggering shift recognition for ${events.size} events")
                            processCalendarEvents(events)
                        } else {
                            // Clear recognized shifts wenn keine Events vorhanden
                            _uiState.value = _uiState.value.copy(
                                recognizedShifts = emptyList(),
                                upcomingShift = null
                            )
                            Logger.d(LogTags.SHIFT_RECOGNITION, "No calendar events, clearing recognized shifts")
                        }
                    }
            }
        } ?: run {
            Logger.w(LogTags.SHIFT_RECOGNITION, "CalendarViewModel not provided - automatic shift recognition disabled")
        }
    }

    private fun loadShiftConfig() {
        viewModelScope.launch {
            shiftUseCase.getCurrentShiftConfig()
                .onSuccess { config ->
                    _uiState.value = _uiState.value.copy(currentShiftConfig = config)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = errorHandler.getErrorMessage(error)
                    )
                }
        }
    }

    fun updateShiftConfig(config: ShiftConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            shiftUseCase.saveShiftConfig(config)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentShiftConfig = config
                    )
                    
                    // REACTIVE FIX: Re-run shift recognition with updated config
                    calendarViewModel?.uiState?.value?.events?.let { currentEvents ->
                        if (currentEvents.isNotEmpty()) {
                            Logger.d(LogTags.SHIFT_RECOGNITION, "Shift config updated, re-processing ${currentEvents.size} calendar events with new definitions")
                            processCalendarEvents(currentEvents)
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorHandler.getErrorMessage(error)
                    )
                }
        }
    }

    fun updateDaysAhead(daysAhead: Int) {
        val currentConfig = _uiState.value.currentShiftConfig ?: return
        val updatedConfig = currentConfig.copy(daysAhead = daysAhead)
        
        // MEMORY LEAK FIX: Event-basiertes System statt Callback
        updateShiftConfig(updatedConfig)
        
        // MEMORY LEAK FIX: Trigger Event über StateFlow statt Callback
        _daysAheadChangedEvent.value = System.currentTimeMillis()
    }

    fun processCalendarEvents(events: List<CalendarEvent>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // Interface-Version verwendet recognizeShiftsInEvents
            shiftUseCase.recognizeShiftsInEvents(events)
                .onSuccess { shiftMatches ->
                    // Konvertiere ShiftMatch zu ShiftInfo für UI-Kompatibilität
                    val shifts = shiftMatches.map { match ->
                        ShiftInfo(
                            id = match.calendarEvent.id,
                            shiftType = com.github.f1rlefanz.cf_alarmfortimeoffice.model.ShiftType(
                                name = match.shiftDefinition.id,
                                displayName = match.shiftDefinition.name
                            ),
                            startTime = match.calendarEvent.startTime,
                            endTime = match.calendarEvent.endTime,
                            eventTitle = match.calendarEvent.title,
                            alarmTime = match.calculatedAlarmTime
                        )
                    }
                    
                    // Upcoming shift calculation - legacy method
                    val upcomingShift = shifts
                        .filter { it.startTime.isAfter(java.time.LocalDateTime.now()) }
                        .minByOrNull { it.startTime }
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        recognizedShifts = shifts,
                        upcomingShift = upcomingShift
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorHandler.getErrorMessage(error)
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * MEMORY LEAK PREVENTION: Comprehensive resource cleanup
     * PERFORMANCE OPTIMIZATION: Clear all state references
     */
    override fun onCleared() {
        super.onCleared()
        
        try {
            // MEMORY OPTIMIZATION: Clear event state to prevent memory leaks
            _daysAheadChangedEvent.value = 0L
            
            // MEMORY LEAK FIX: Clear UI state to release object references
            _uiState.value = ShiftUiState()
            
            Logger.d(LogTags.LIFECYCLE, "ShiftViewModel cleared - cleaning up resources and state references")
        } catch (e: Exception) {
            Logger.e(LogTags.LIFECYCLE, "Error during ShiftViewModel cleanup", e)
        }
        
        // Note: ViewModelScope automatically cancels all coroutines
        // UseCase cleanup wird durch DI Container gehandhabt
    }
}
