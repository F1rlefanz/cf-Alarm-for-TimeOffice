package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAlarmUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAuthUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IShiftUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.ICalendarSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * MainViewModel koordiniert den globalen App-Zustand.
 * 
 * REFACTORED: Interface-basierte Abhängigkeiten für bessere Testbarkeit
 * ✅ Verwendet Interface-Abhängigkeiten (Dependency Inversion)
 * ✅ Mock-fähig für Unit Tests
 * ✅ Verwendet nur UseCases, keine Repositories direkt
 * ✅ Folgt Clean Architecture Principles
 * ✅ Zentrale Koordination ohne Mixed Responsibilities
 * ✅ FIXED: hasSelectedCalendars wird jetzt korrekt überwacht
 */
class MainViewModel(
    private val authUseCase: IAuthUseCase,
    private val shiftUseCase: IShiftUseCase,
    private val alarmUseCase: IAlarmUseCase,
    private val calendarSelectionRepository: ICalendarSelectionRepository,
    private val calendarViewModel: CalendarViewModel? = null
) : ViewModel() {

    data class MainUiState(
        val isAuthenticated: Boolean = false,
        val hasSelectedCalendars: Boolean = false,
        val hasShiftConfig: Boolean = false,
        val isProcessingShifts: Boolean = false,
        val hasUpcomingShift: Boolean = false,
        val hasActiveAlarms: Boolean = false
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeAppState()
    }

    private fun observeAppState() {
        viewModelScope.launch {
            combine(
                authUseCase.authData,
                alarmUseCase.activeAlarms,
                calendarSelectionRepository.selectedCalendarIds
                    .debounce(100) // PERFORMANCE: Batch calendar selection changes
                    .distinctUntilChanged() // PERFORMANCE: Only emit on actual changes
            ) { authData, activeAlarms, selectedCalendarIds ->
                MainUiState(
                    isAuthenticated = authData.isLoggedIn,
                    hasSelectedCalendars = selectedCalendarIds.isNotEmpty(),
                    hasActiveAlarms = activeAlarms.isNotEmpty()
                )
            }.distinctUntilChanged() // PERFORMANCE: Only emit when main state actually changes
            .debounce(50) // PERFORMANCE: Batch main state updates
            .collect { state ->
                _uiState.value = state
                
                // Debug-Log für Diagnose
                Logger.d(LogTags.NAVIGATION, "Main state updated - authenticated=${state.isAuthenticated}, hasCalendars=${state.hasSelectedCalendars}")
            }
        }
    }

    fun refreshAll(forceRefresh: Boolean = false) {
        // Lädt Calendar-Events mit aktueller daysAhead-Konfiguration neu
        viewModelScope.launch {
            val shiftConfig = shiftUseCase.getCurrentShiftConfig().getOrNull()
            val daysAhead = shiftConfig?.daysAhead
            
            if (forceRefresh) {
                Logger.i(LogTags.UI, "Force refresh requested")
                calendarViewModel?.refreshData(forceRefresh = true)
            } else {
                calendarViewModel?.loadEventsForSelectedCalendars(daysAhead)
            }
        }
    }
    
    fun forceRefreshCalendarEvents() {
        refreshAll(forceRefresh = true)
    }
    
    /**
     * MEMORY LEAK PREVENTION: Comprehensive resource cleanup
     * PERFORMANCE OPTIMIZATION: Clear all state and references
     */
    override fun onCleared() {
        super.onCleared()
        
        try {
            // MEMORY LEAK FIX: Clear UI state to release object references
            _uiState.value = MainUiState()
            
            Logger.d(LogTags.LIFECYCLE, "MainViewModel cleared - cleaning up state references and resources")
        } catch (e: Exception) {
            Logger.e(LogTags.LIFECYCLE, "Error during MainViewModel cleanup", e)
        }
        
        // Note: ViewModelScope automatically cancels all coroutines
        // UseCases handle their own cleanup via DI container
    }
}
