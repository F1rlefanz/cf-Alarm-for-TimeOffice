package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.di.AppContainer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * ViewModelFactory für Clean Architecture Dependency Injection
 * 
 * REFACTORED: Interface-basierte Dependency Injection mit Singleton-Pattern
 * ✅ Alle ViewModels verwenden Interface-Abhängigkeiten für bessere Testbarkeit
 * ✅ UseCase-Layer mit Interface-Abstraktion (Dependency Inversion)
 * ✅ Mock-fähige Dependencies für Unit Tests
 * ✅ Singleton ViewModels für State-Sharing zwischen Komponenten
 * ✅ Reactive Architecture für lose Kopplung
 */
class ViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    // SINGLETON PATTERN: Shared ViewModel Instances für State-Konsistenz
    private var _calendarViewModel: CalendarViewModel? = null
    private var _shiftViewModel: ShiftViewModel? = null
    private var _hueViewModel: HueViewModel? = null
    
    private fun getOrCreateCalendarViewModel(): CalendarViewModel {
        return _calendarViewModel ?: CalendarViewModel(
            calendarUseCase = appContainer.calendarUseCase,
            calendarSelectionRepository = appContainer.calendarSelectionRepository,
            errorHandler = appContainer.errorHandler,
            shiftUseCase = appContainer.shiftUseCase
        ).also { _calendarViewModel = it }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(
                    authDataStoreRepository = appContainer.authDataStoreRepository,
                    oauth2TokenManager = appContainer.oauth2TokenManager,
                    errorHandler = appContainer.errorHandler
                ) as T
            }
            modelClass.isAssignableFrom(CalendarViewModel::class.java) -> {
                getOrCreateCalendarViewModel() as T
            }
            modelClass.isAssignableFrom(ShiftViewModel::class.java) -> {
                // REACTIVE ARCHITECTURE: ShiftViewModel observiert CalendarViewModel
                (_shiftViewModel ?: ShiftViewModel(
                    shiftUseCase = appContainer.shiftUseCase,
                    errorHandler = appContainer.errorHandler,
                    calendarViewModel = getOrCreateCalendarViewModel() // REACTIVE: Für automatische Schichterkennung
                ).also { _shiftViewModel = it }) as T
            }
            modelClass.isAssignableFrom(AlarmViewModel::class.java) -> {
                AlarmViewModel(
                    alarmUseCase = appContainer.alarmUseCase, // Interface-Abhängigkeit
                    errorHandler = appContainer.errorHandler
                ) as T
            }
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(
                    authUseCase = appContainer.authUseCase, // Interface-Abhängigkeit  
                    shiftUseCase = appContainer.shiftUseCase, // Interface-Abhängigkeit
                    alarmUseCase = appContainer.alarmUseCase, // Interface-Abhängigkeit
                    calendarSelectionRepository = appContainer.calendarSelectionRepository, // FIXED: Calendar Selection Dependency
                    calendarViewModel = getOrCreateCalendarViewModel() // COORDINATION: Für refreshAll()
                ) as T
            }
            modelClass.isAssignableFrom(NavigationViewModel::class.java) -> {
                // NavigationViewModel wird jetzt auch über Factory erstellt
                // statt als Singleton im AppContainer
                NavigationViewModel() as T
            }
            modelClass.isAssignableFrom(HueViewModel::class.java) -> {
                getOrCreateHueViewModel() as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
