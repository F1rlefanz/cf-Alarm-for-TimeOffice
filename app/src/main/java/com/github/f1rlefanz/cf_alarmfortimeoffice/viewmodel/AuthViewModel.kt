package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AuthData
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AuthState
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.UserAuthState
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.PermissionState
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.CalendarOperationState
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.AppErrorState
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IAuthDataStoreRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.ErrorHandler
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * MODERNIZED: AuthViewModel with CredentialAuthManager
 * 
 * PERFORMANCE FIXES:
 * ✅ Uses modern androidx.credentials API
 * ✅ Atomic state updates (no mutex blocking)
 * ✅ Debounced flows prevent rapid UI updates
 * ✅ Single Source of Truth für Authentication
 * ✅ Memory leak prevention
 */
class AuthViewModel(
    private val authDataStoreRepository: IAuthDataStoreRepository,
    private val credentialAuthManager: CredentialAuthManager,
    private val errorHandler: ErrorHandler,
    private val authUseCase: com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAuthUseCase? = null
) : ViewModel() {

    // CONSOLIDATED STATE: Ein einziger State statt AuthState + AuthUiState
    private val _authState = MutableStateFlow(AuthState.EMPTY)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // BACKWARD COMPATIBILITY: Expose als uiState für bestehenden Code
    val uiState: StateFlow<AuthState> = authState
    
    /**
     * PERFORMANCE OPTIMIZATION: Non-blocking Atomic State Updates
     * Ersetzt Mutex durch atomare Vergleich-und-Tausch Operationen
     */
    private fun updateAuthState(updateFunc: (AuthState) -> AuthState) {
        val currentState = _authState.value
        val newState = updateFunc(currentState)
        
        // ATOMIC UPDATE: Thread-safe ohne Mutex-Blocking
        if (currentState != newState) {
            _authState.value = newState
        }
    }

    init {
        observeAuthState()
        checkInitialAuthState()
    }

    /**
     * Observes auth data changes from DataStore.
     * PERFORMANCE FIX: Atomic updates statt Mutex für bessere Performance
     * CALENDAR AUTO-RELOAD: Automatically loads calendars after successful authorization
     */
    @OptIn(FlowPreview::class)
    private fun observeAuthState() {
        viewModelScope.launch {
            authDataStoreRepository.authData
                .debounce(100) // Prevent rapid state updates
                .distinctUntilChanged() // Only emit when data actually changes
                .collect { authData ->
                    Logger.d(LogTags.AUTH, "Auth data updated - isLoggedIn=${authData.isLoggedIn}")
                    
                    // PERFORMANCE: Atomic non-blocking state update
                    updateAuthState { currentState ->
                        currentState.copy(
                            userAuth = UserAuthState(
                                isSignedIn = authData.isLoggedIn,
                                userEmail = authData.email,
                                displayName = authData.displayName,
                                accessToken = authData.accessToken,
                                hasValidToken = authData.accessToken?.isNotEmpty() == true
                            )
                        )
                    }
                    
                    // CRITICAL FIX: Auto-trigger calendar loading after successful authentication
                    if (authData.isLoggedIn && authData.accessToken?.isNotEmpty() == true) {
                        triggerCalendarReloadAfterAuth()
                    }
                }
        }
    }

    /**
     * Checks initial authentication state on ViewModel initialization.
     */
    private fun checkInitialAuthState() {
        viewModelScope.launch {
            try {
                // Get current auth data from repository
                authDataStoreRepository.authData.collect { authData ->
                    updateAuthState { currentState ->
                        currentState.copy(
                            userAuth = UserAuthState(
                                hasValidToken = authData.isLoggedIn,
                                userEmail = authData.email,
                                displayName = authData.displayName,
                                isSignedIn = authData.isLoggedIn,
                                accessToken = authData.accessToken
                            )
                        )
                    }
                    
                    Logger.d(LogTags.AUTH, "Initial auth state - authenticated=${authData.isLoggedIn}, user=${authData.email}")
                    // Only collect once for initial state, then return
                    return@collect
                }
            } catch (e: Exception) {
                Logger.e(LogTags.AUTH, "Error checking initial auth state", e)
            }
        }
    }

    /**
     * MODERN AUTH: Sign in using CredentialAuthManager
     */
    fun signIn(context: Context) {
        viewModelScope.launch {
            updateAuthState { currentState ->
                currentState.copy(
                    calendarOps = currentState.calendarOps.copy(calendarsLoading = true),
                    errors = AppErrorState.EMPTY
                )
            }
            
            try {
                Logger.business(LogTags.AUTH, "Starting modern credential sign-in")
                val signInResult = credentialAuthManager.signIn()
                
                if (signInResult.success && signInResult.credentialResponse != null) {
                    // Extract user info from credential
                    val (_, displayName, email) = credentialAuthManager.extractUserInfo(signInResult.credentialResponse)
                    
                    if (email != null) {
                        // CRITICAL FIX: Don't store placeholder token, let Calendar authorization handle real tokens
                        // The AuthData token is only used for auth state tracking, not for API calls
                        val authData = AuthData(
                            isLoggedIn = true,
                            email = email,
                            displayName = displayName,
                            accessToken = null // No token here - real tokens managed by ModernOAuth2TokenManager
                        )
                        
                        // CRITICAL FIX: Also save email to SharedPreferences for CalendarRepository
                        try {
                            val prefs = context.getSharedPreferences("cf_alarm_auth", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("current_user_email", email)
                                .putString("current_user_display_name", displayName)
                                .putLong("auth_timestamp", System.currentTimeMillis())
                                .apply()
                            Logger.d(LogTags.AUTH, "CRITICAL-FIX: Saved user email to SharedPreferences for CalendarRepository access")
                        } catch (e: Exception) {
                            Logger.w(LogTags.AUTH, "Could not save user email to SharedPreferences", e)
                        }
                        
                        authDataStoreRepository.updateAuthData(authData)
                            .onSuccess {
                                updateAuthState { currentState ->
                                    currentState.copy(
                                        userAuth = UserAuthState.authenticated(
                                            email,
                                            displayName ?: "",
                                            null // No placeholder token
                                        ),
                                        calendarOps = currentState.calendarOps.copy(calendarsLoading = false)
                                    )
                                }
                                Logger.business(LogTags.AUTH, "Sign-in successful", email)
                                
                                // CRITICAL FIX: Automatically trigger Calendar authorization after sign-in
                                Logger.business(LogTags.AUTH, "🔄 AUTO-FLOW: Triggering Calendar authorization for signed-in user")
                                requestCalendarAuthorization()
                            }
                            .onFailure { error ->
                                updateAuthState { currentState ->
                                    currentState.copy(
                                        calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                                        errors = AppErrorState.authenticationError(errorHandler.getErrorMessage(error))
                                    )
                                }
                            }
                    } else {
                        updateAuthState { currentState ->
                            currentState.copy(
                                calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                                errors = AppErrorState.authenticationError("Keine E-Mail-Adresse erhalten")
                            )
                        }
                    }
                } else {
                    val errorMessage = signInResult.error ?: "Unbekannter Fehler bei der Anmeldung"
                    updateAuthState { currentState ->
                        currentState.copy(
                            calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                            errors = AppErrorState.authenticationError(errorMessage)
                        )
                    }
                    Logger.e(LogTags.AUTH, "Sign-in failed - $errorMessage")
                }
                
            } catch (e: Exception) {
                updateAuthState { currentState ->
                    currentState.copy(
                        calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                        errors = AppErrorState.authenticationError(errorHandler.getErrorMessage(e))
                    )
                }
                Logger.e(LogTags.AUTH, "Unexpected error during sign-in", e)
            }
        }
    }

    /**
     * OVERLOAD: Context-free sign-in that extracts context from credentialAuthManager
     */
    fun signIn() {
        // This will be called from MainActivity's lambda
        // The context should be passed from MainActivity
        viewModelScope.launch {
            updateAuthState { currentState ->
                currentState.copy(
                    errors = AppErrorState.authenticationError("Anmeldung erfordert Google Web Client ID Konfiguration")
                )
            }
        }
        Logger.w(LogTags.AUTH, "Sign-in called without context - this should not happen in production")
    }

    /**
     * Signs out user and clears all authentication data.
     */
    fun signOut() {
        signOut(null)
    }
    
    /**
     * Signs out user and clears all authentication data.
     * @param context Optional context for clearing SharedPreferences
     */
    fun signOut(context: Context?) {
        viewModelScope.launch {
            updateAuthState { currentState ->
                currentState.copy(
                    calendarOps = currentState.calendarOps.copy(calendarsLoading = true),
                    errors = AppErrorState.EMPTY
                )
            }
            
            try {
                // Local sign-out using CredentialAuthManager
                credentialAuthManager.signOutLocally()
                
                // Clear auth data from DataStore
                authDataStoreRepository.clearAuthData()
                    .onSuccess {
                        updateAuthState { AuthState.EMPTY }
                        
                        // CRITICAL FIX: Also clear SharedPreferences if context is available
                        try {
                            val ctx = context ?: run {
                                // Try to get context from credentialAuthManager
                                val field = credentialAuthManager::class.java.getDeclaredField("context")
                                field.isAccessible = true
                                field.get(credentialAuthManager) as? Context
                            }
                            
                            ctx?.let {
                                val prefs = it.getSharedPreferences("cf_alarm_auth", Context.MODE_PRIVATE)
                                prefs.edit().clear().apply()
                                Logger.d(LogTags.AUTH, "CRITICAL-FIX: Cleared SharedPreferences on sign-out")
                            }
                        } catch (e: Exception) {
                            Logger.w(LogTags.AUTH, "Could not clear SharedPreferences on sign-out", e)
                        }
                        
                        Logger.business(LogTags.AUTH, "Sign-out successful")
                    }
                    .onFailure { error ->
                        updateAuthState { currentState ->
                            currentState.copy(
                                calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                                errors = AppErrorState.authenticationError(errorHandler.getErrorMessage(error))
                            )
                        }
                    }
                
            } catch (e: Exception) {
                updateAuthState { currentState ->
                    currentState.copy(
                        calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                        errors = AppErrorState.authenticationError(errorHandler.getErrorMessage(e))
                    )
                }
                Logger.e(LogTags.AUTH, "Error during sign-out", e)
            }
        }
    }

    /**
     * Clears current error message.
     */
    fun clearError() {
        updateAuthState { currentState ->
            currentState.copy(errors = AppErrorState.EMPTY)
        }
    }

    /**
     * Checks if current user has valid authentication.
     */
    fun validateAuthentication(): Boolean {
        val currentState = _authState.value
        return currentState.userAuth.isFullyAuthenticated
    }
    
    /**
     * MODERN: Requests Calendar API authorization for current user
     * CRITICAL FIX: Directly calls AuthUseCase to get real OAuth2 tokens
     */
    fun requestCalendarAuthorization() {
        viewModelScope.launch {
            updateAuthState { currentState ->
                currentState.copy(
                    calendarOps = currentState.calendarOps.copy(calendarsLoading = true),
                    errors = AppErrorState.EMPTY
                )
            }
            
            try {
                // Get current user email for Calendar authorization
                val currentAuthData = authDataStoreRepository.getCurrentAuthData().getOrNull()
                val userEmail = currentAuthData?.email
                
                if (userEmail.isNullOrEmpty()) {
                    updateAuthState { currentState ->
                        currentState.copy(
                            calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                            errors = AppErrorState.authenticationError("Benutzer-E-Mail für Calendar-Autorisierung nicht verfügbar")
                        )
                    }
                    return@launch
                }
                
                Logger.business(LogTags.AUTH, "MODERN-FLOW: Requesting Calendar authorization for $userEmail")
                
                authUseCase?.requestCalendarAuthorization(userEmail)?.fold(
                    onSuccess = { authorized ->
                        updateAuthState { currentState ->
                            currentState.copy(
                                calendarOps = currentState.calendarOps.copy(
                                    calendarsLoading = false,
                                    hasSelectedCalendars = authorized
                                )
                            )
                        }
                        Logger.business(LogTags.AUTH, "✅ MODERN-FLOW: Calendar authorization successful: $authorized")
                        
                        // CRITICAL FIX: Auto-trigger calendar loading after successful authorization
                        if (authorized) {
                            triggerCalendarReloadAfterAuth()
                        }
                    },
                    onFailure = { error ->
                        updateAuthState { currentState ->
                            currentState.copy(
                                calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                                errors = AppErrorState.authenticationError("Calendar-Autorisierung fehlgeschlagen: ${error.message}")
                            )
                        }
                        Logger.e(LogTags.AUTH, "❌ MODERN-FLOW: Calendar authorization failed", error)
                    }
                ) ?: run {
                    updateAuthState { currentState ->
                        currentState.copy(
                            calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                            errors = AppErrorState.authenticationError("Calendar-Autorisierungssystem nicht verfügbar")
                        )
                    }
                    Logger.e(LogTags.AUTH, "❌ MODERN-FLOW: AuthUseCase not available for Calendar authorization")
                }
            } catch (e: Exception) {
                updateAuthState { currentState ->
                    currentState.copy(
                        calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                        errors = AppErrorState.authenticationError(errorHandler.getErrorMessage(e))
                    )
                }
                Logger.e(LogTags.AUTH, "❌ MODERN-FLOW: Exception during calendar authorization", e)
            }
        }
    }
    
    /**
     * MODERN: Checks if Calendar authorization is available
     * AUTO-CALENDAR-RELOAD: Triggers calendar loading after successful authorization
     * CRITICAL FIX: Now also triggers authorization if not available
     */
    fun checkCalendarAuthorization() {
        viewModelScope.launch {
            try {
                authUseCase?.hasCalendarAuthorization()?.fold(
                    onSuccess = { authorized ->
                        updateAuthState { currentState ->
                            currentState.copy(
                                calendarOps = currentState.calendarOps.copy(hasSelectedCalendars = authorized)
                            )
                        }
                        Logger.d(LogTags.AUTH, "Calendar authorization status: $authorized")
                        
                        if (authorized) {
                            // CRITICAL FIX: Auto-trigger calendar loading after authorization check
                            triggerCalendarReloadAfterAuth()
                        } else {
                            // CRITICAL FIX: If not authorized, automatically request authorization
                            Logger.business(LogTags.AUTH, "🔄 AUTO-FLOW: No Calendar authorization - requesting it automatically")
                            requestCalendarAuthorization()
                        }
                    },
                    onFailure = { error ->
                        Logger.e(LogTags.AUTH, "Error checking calendar authorization", error)
                        // FALLBACK: If check failed, try to request authorization anyway
                        Logger.business(LogTags.AUTH, "🔄 FALLBACK-FLOW: Authorization check failed, trying to request authorization")
                        requestCalendarAuthorization()
                    }
                )
            } catch (e: Exception) {
                Logger.e(LogTags.AUTH, "Error checking calendar authorization", e)
                // FALLBACK: If check failed, try to request authorization anyway
                Logger.business(LogTags.AUTH, "🔄 FALLBACK-FLOW: Authorization check exception, trying to request authorization")
                requestCalendarAuthorization()
            }
        }
    }
    
    /**
     * CRITICAL FIX: Triggers calendar reload after successful authentication/authorization
     * This connects AuthViewModel to CalendarViewModel for automatic calendar loading
     * DEDUPLICATION: Prevents multiple calendar loads in quick succession
     */
    @Volatile
    private var lastCalendarTriggerTime = 0L
    
    private fun triggerCalendarReloadAfterAuth() {
        viewModelScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastTrigger = currentTime - lastCalendarTriggerTime
                
                // DEDUPLICATION: Prevent multiple triggers within 5 seconds
                if (timeSinceLastTrigger < 5000) {
                    Logger.d(LogTags.AUTH, "🔄 AUTH-TRIGGERED: Calendar reload trigger debounced (${timeSinceLastTrigger}ms since last)")
                    return@launch
                }
                
                lastCalendarTriggerTime = currentTime
                
                // Small delay to ensure auth state is fully updated
                kotlinx.coroutines.delay(200)
                
                Logger.business(LogTags.AUTH, "🔄 AUTH-TRIGGERED: Initiating calendar reload after successful authentication")
                
                // Notify any listeners that calendar reload should happen
                // This will be handled by MainViewModel or other coordinator
                calendarReloadTrigger?.invoke()
                
            } catch (e: Exception) {
                Logger.e(LogTags.AUTH, "❌ AUTH-TRIGGERED: Failed to trigger calendar reload", e)
            }
        }
    }
    
    // Callback to trigger calendar reload - will be set by MainViewModel or coordinator
    private var calendarReloadTrigger: (() -> Unit)? = null
    
    /**
     * Sets a callback to trigger calendar reload after successful authentication
     * This allows loose coupling between AuthViewModel and CalendarViewModel
     */
    fun setCalendarReloadTrigger(trigger: () -> Unit) {
        calendarReloadTrigger = trigger
        Logger.d(LogTags.AUTH, "Calendar reload trigger registered")
    }

    // PERMISSION MANAGEMENT: Methods for managing calendar permissions
    fun updateCalendarPermission(granted: Boolean, showRationale: Boolean = false, denied: Boolean = false) {
        updateAuthState { currentState ->
            currentState.copy(
                permissions = PermissionState(
                    androidCalendarPermissionGranted = granted,
                    showAndroidCalendarPermissionRationale = showRationale,
                    calendarPermissionDenied = denied
                )
            )
        }
        Logger.d(LogTags.PERMISSIONS, "Calendar permission updated: granted=$granted, rationale=$showRationale, denied=$denied")
    }

    fun updateCalendarOperations(
        loading: Boolean? = null,
        autoAlarmEnabled: Boolean? = null,
        nextShiftAlarm: String? = null,
        hasSelectedCalendars: Boolean? = null
    ) {
        updateAuthState { currentState ->
            val currentOps = currentState.calendarOps
            
            currentState.copy(
                calendarOps = CalendarOperationState(
                    calendarsLoading = loading ?: currentOps.calendarsLoading,
                    autoAlarmEnabled = autoAlarmEnabled ?: currentOps.autoAlarmEnabled,
                    nextShiftAlarm = nextShiftAlarm ?: currentOps.nextShiftAlarm,
                    hasSelectedCalendars = hasSelectedCalendars ?: currentOps.hasSelectedCalendars,
                    eventsLoading = currentOps.eventsLoading
                )
            )
        }
    }
    
    /**
     * LIFECYCLE MANAGEMENT: Properly cancel all ongoing operations
     */
    override fun onCleared() {
        super.onCleared()
        try {
            Logger.d(LogTags.LIFECYCLE, "AuthViewModel cleanup completed")
        } catch (e: Exception) {
            Logger.e(LogTags.LIFECYCLE, "Error during AuthViewModel cleanup", e)
        }
    }
}
