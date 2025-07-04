package com.github.f1rlefanz.cf_alarmfortimeoffice.di

import android.content.Context
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.OAuth2TokenManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.storage.SecureTokenStorage
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.storage.TokenStorageRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.usecase.TokenRefreshUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.data.AuthDataStoreRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.ErrorHandler
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.AlarmRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IAlarmRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IAuthDataStoreRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.ICalendarRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IShiftConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmManagerService
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftRecognitionEngine
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.AlarmUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.AuthUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.CalendarAuthUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.CalendarUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.ShiftUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAlarmUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAuthUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.ICalendarAuthUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.ICalendarUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IShiftUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.data.CalendarSelectionRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.ICalendarSelectionRepository

/**
 * Dependency Container für Clean Architecture mit Interface-basierter DI
 * 
 * REFACTORED: Interface-basierte Dependency Injection
 * ✅ Alle Repositories implementieren Interfaces für bessere Testbarkeit
 * ✅ UseCase-Layer verwendet Interface-Abhängigkeiten (Dependency Inversion)
 * ✅ Konsistente Result-basierte APIs für Fehlerbehandlung
 * ✅ OAuth2TokenManager für langlebige Token-Authentifizierung
 * ✅ Backwards compatibility mit bestehendem System
 * ✅ Schrittweise Migration möglich
 * 
 * TESTING IMPROVEMENTS:
 * - Mock-fähige Repository-Interfaces
 * - Testbare UseCase-Layer durch Interface-Abhängigkeiten
 * - Entkoppelte Business Logic von Infrastructure
 */
class AppContainer(private val context: Context) {
    
    // ==============================
    // TOKEN MANAGEMENT (New OAuth2 Infrastructure)
    // ==============================
    val secureTokenStorage: SecureTokenStorage by lazy {
        SecureTokenStorage(context)
    }
    
    val tokenStorageRepository: TokenStorageRepository by lazy {
        TokenStorageRepository(context)
    }
    
    val oauth2TokenManager: OAuth2TokenManager by lazy {
        OAuth2TokenManager(
            context = context,
            tokenStorage = tokenStorageRepository
        )
    }
    
    val tokenRefreshUseCase: TokenRefreshUseCase by lazy {
        TokenRefreshUseCase(
            oauth2TokenManager = oauth2TokenManager,
            tokenStorage = tokenStorageRepository
        )
    }
    
    // ==============================
    // REPOSITORY INTERFACES & IMPLEMENTATIONS
    // ==============================
    
    // Interface-basierte Repository-Implementierungen für bessere Testbarkeit
    val authDataStoreRepository: IAuthDataStoreRepository by lazy {
        AuthDataStoreRepository(context)
    }
    
    val calendarRepository: ICalendarRepository by lazy {
        CalendarRepository().apply {
            setContext(context) // Set context for network connectivity checks
        }
    }
    
    val shiftConfigRepository: IShiftConfigRepository by lazy {
        ShiftConfigRepository(context)
    }
    
    val alarmRepository: IAlarmRepository by lazy {
        AlarmRepository(context)
    }
    
    val calendarSelectionRepository: ICalendarSelectionRepository by lazy {
        CalendarSelectionRepository(context)
    }
    
    // ==============================
    // SERVICES & ENGINES
    // ==============================
    val alarmManagerService: AlarmManagerService by lazy {
        AlarmManagerService(context.applicationContext as android.app.Application)
    }
    
    // LEGACY: Kept for backwards compatibility during migration
    val credentialAuthManager: CredentialAuthManager by lazy {
        CredentialAuthManager(context)
    }
    
    val shiftRecognitionEngine: ShiftRecognitionEngine by lazy {
        ShiftRecognitionEngine(shiftConfigRepository)
    }
    
    val errorHandler: ErrorHandler by lazy {
        ErrorHandler()
    }
    
    // ==============================
    // USE CASE INTERFACES & IMPLEMENTATIONS (Domain Layer)
    // ==============================
    
    // Interface-basierte UseCase-Implementierungen für bessere Testbarkeit
    val authUseCase: IAuthUseCase by lazy {
        AuthUseCase(
            authDataStoreRepository = authDataStoreRepository
        )
    }
    
    val calendarAuthUseCase: ICalendarAuthUseCase by lazy {
        CalendarAuthUseCase(
            authDataStoreRepository = authDataStoreRepository,
            calendarRepository = calendarRepository
        )
    }
    
    val calendarUseCase: ICalendarUseCase by lazy {
        CalendarUseCase(
            calendarRepository = calendarRepository,
            authDataStoreRepository = authDataStoreRepository,
            tokenRefreshUseCase = tokenRefreshUseCase
        )
    }
    
    val shiftUseCase: IShiftUseCase by lazy {
        ShiftUseCase(
            shiftConfigRepository = shiftConfigRepository,
            shiftRecognitionEngine = shiftRecognitionEngine
        )
    }
    
    val alarmUseCase: IAlarmUseCase by lazy {
        AlarmUseCase(
            alarmRepository = alarmRepository,
            alarmManagerService = alarmManagerService,
            shiftConfigRepository = shiftConfigRepository,
            shiftRecognitionEngine = shiftRecognitionEngine
        )
    }
    
    // ==============================
    // INITIALIZATION
    // ==============================
    
    /**
     * Initializes OAuth2 token storage repository.
     * Should be called during app startup.
     */
    suspend fun initializeTokenStorage() {
        tokenStorageRepository.initialize()
    }
    
    // ==============================
    // IMPORTANT: ViewModels sind NICHT hier!
    // ViewModels werden über ViewModelFactory erstellt
    // um korrekte Lifecycle-Bindung zu gewährleisten
    // ==============================
}
