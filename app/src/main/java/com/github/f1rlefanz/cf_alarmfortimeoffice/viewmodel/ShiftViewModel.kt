package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.CalendarEvent
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftDefinition
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftRecognitionEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

data class ShiftState(
    val isLoading: Boolean = false,
    val nextShiftAlarm: ShiftMatch? = null,
    val allUpcomingShifts: List<ShiftMatch> = emptyList(),
    val shiftDefinitions: List<ShiftDefinition> = emptyList(),
    val autoAlarmEnabled: Boolean = true,
    val error: String? = null
)

class ShiftViewModel(application: Application) : AndroidViewModel(application) {
    
    private val shiftConfigRepository = ShiftConfigRepository(application)
    private val shiftRecognitionEngine = ShiftRecognitionEngine(shiftConfigRepository)
    
    private val _shiftState = MutableStateFlow(ShiftState())
    val shiftState: StateFlow<ShiftState> = _shiftState.asStateFlow()
    
    private val _calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    
    init {
        Timber.d("ShiftViewModel initialized")
        observeShiftConfig()
    }
    
    private fun observeShiftConfig() {
        viewModelScope.launch {
            combine(
                shiftConfigRepository.shiftDefinitions,
                shiftConfigRepository.autoAlarmEnabled,
                _calendarEvents
            ) { definitions, autoEnabled, events ->
                Triple(definitions, autoEnabled, events)
            }.collectLatest { (definitions, autoEnabled, events) ->
                _shiftState.update { currentState ->
                    currentState.copy(
                        shiftDefinitions = definitions,
                        autoAlarmEnabled = autoEnabled
                    )
                }
                
                if (events.isNotEmpty()) {
                    processCalendarEvents(events)
                }
            }
        }
    }
    
    fun updateCalendarEvents(events: List<CalendarEvent>) {
        Timber.d("Updating calendar events: ${events.size} events")
        _calendarEvents.value = events
    }
    
    private suspend fun processCalendarEvents(events: List<CalendarEvent>) {
        _shiftState.update { it.copy(isLoading = true, error = null) }
        
        try {
            val nextShift = shiftRecognitionEngine.findNextShiftAlarm(events)
            val allShifts = shiftRecognitionEngine.getAllMatchingShifts(events)
            
            _shiftState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    nextShiftAlarm = nextShift,
                    allUpcomingShifts = allShifts,
                    error = null
                )
            }
            
            Timber.i("Processed shift recognition: nextShift=${nextShift?.shiftDefinition?.name}, allShifts=${allShifts.size}")
            
        } catch (e: Exception) {
            Timber.e(e, "Error processing calendar events")
            _shiftState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    error = "Fehler bei der Schicht-Erkennung: ${e.localizedMessage}"
                )
            }
        }
    }
    
    fun toggleAutoAlarm() {
        viewModelScope.launch {
            val newState = !_shiftState.value.autoAlarmEnabled
            shiftConfigRepository.saveAutoAlarmEnabled(newState)
            Timber.d("Auto alarm toggled to: $newState")
        }
    }
    
    fun updateShiftDefinition(definition: ShiftDefinition) {
        viewModelScope.launch {
            val currentDefinitions = _shiftState.value.shiftDefinitions.toMutableList()
            val index = currentDefinitions.indexOfFirst { it.id == definition.id }
            
            if (index >= 0) {
                currentDefinitions[index] = definition
            } else {
                currentDefinitions.add(definition)
            }
            
            shiftConfigRepository.saveShiftDefinitions(currentDefinitions)
            Timber.d("Updated shift definition: ${definition.name}")
        }
    }
    
    fun deleteShiftDefinition(definitionId: String) {
        viewModelScope.launch {
            val currentDefinitions = _shiftState.value.shiftDefinitions.toMutableList()
            currentDefinitions.removeAll { it.id == definitionId }
            shiftConfigRepository.saveShiftDefinitions(currentDefinitions)
            Timber.d("Deleted shift definition: $definitionId")
        }
    }
    
    fun resetToDefaults() {
        viewModelScope.launch {
            shiftConfigRepository.resetToDefaults()
            Timber.i("Shift configuration reset to defaults")
        }
    }
    
    fun refreshShiftRecognition() {
        val currentEvents = _calendarEvents.value
        if (currentEvents.isNotEmpty()) {
            viewModelScope.launch {
                processCalendarEvents(currentEvents)
            }
        }
    }
}
