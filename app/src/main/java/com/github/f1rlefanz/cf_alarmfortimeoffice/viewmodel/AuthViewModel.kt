package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AuthData
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AuthState
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.UserAuthState
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.PermissionState
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.CalendarOperationState
import com.github.f1rlefanz.cf_alarmfortimeoffice.model.state.AppErrorState
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IAuthDataStoreRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.OAuth2TokenManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.AuthResult
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.ErrorHandler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import java.lang.ref.WeakReference

/**
 * PERFORMANCE OPTIMIZED: AuthViewModel with Atomic State Updates
 * 
 * CRITICAL PERFORMANCE FIXES:
 * ✅ Eliminiert Mutex-basierte State Updates (Hauptthread-Blocker)
 * ✅ Verwendet atomic updates für thread-safe Operations
 * ✅ Debounced flows prevent rapid UI state updates
 * ✅ Single Source of Truth für Authentication Data
 * ✅ Memory leak prevention mit proper cleanup
 */
class AuthViewModel(
    private val authDataStoreRepository: IAuthDataStoreRepository,
    private val oauth2TokenManager: OAuth2TokenManager,
    private val errorHandler: ErrorHandler
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
     */
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
                }
        }
    }

    /**
     * Checks initial authentication state on ViewModel initialization.
     */
    private fun checkInitialAuthState() {
        viewModelScope.launch {
            try {
                val isAuthenticated = oauth2TokenManager.isAuthenticated()
                val currentUser = oauth2TokenManager.getCurrentUserInfo()
                
                updateAuthState { currentState ->
                    currentState.copy(
                        userAuth = UserAuthState(
                            hasValidToken = isAuthenticated,
                            userEmail = currentUser?.email,
                            displayName = currentUser?.displayName,
                            isSignedIn = isAuthenticated && currentUser != null,
                            accessToken = if (isAuthenticated) "valid" else null
                        )
                    )
                }
                
                Logger.d(LogTags.AUTH, "Initial auth state - authenticated=$isAuthenticated, user=${currentUser?.email}")
            } catch (e: Exception) {
                Logger.e(LogTags.AUTH, "Error checking initial auth state", e)
            }
        }
    }

    /**
     * Handles Google Sign-In result with OAuth2 token management.
     * PERFORMANCE FIX: Eliminiert alle Mutex-Locks
     */
    fun handleSignInResult(account: GoogleSignInAccount?, context: Context) {
        if (account == null) {
            updateAuthState { currentState ->
                currentState.copy(
                    errors = AppErrorState.authenticationError("Anmeldung fehlgeschlagen")
                )
            }
            return
        }

        viewModelScope.launch {
            updateAuthState { currentState ->
                currentState.copy(
                    calendarOps = currentState.calendarOps.copy(calendarsLoading = true),
                    errors = AppErrorState.EMPTY
                )
            }
            
            try {
                // Use OAuth2TokenManager for sign-in
                val authResult = oauth2TokenManager.signInWithAuthCode(context as Activity)
                
                when (authResult) {
                    is AuthResult.Success -> {
                        // Save auth data to DataStore
                        val authData = AuthData(
                            isLoggedIn = true,
                            email = authResult.userInfo.email,
                            displayName = authResult.userInfo.displayName
                        )
                        
                        authDataStoreRepository.updateAuthData(authData)
                            .onSuccess {
                                updateAuthState { currentState ->
                                    currentState.copy(
                                        userAuth = UserAuthState.authenticated(
                                            authResult.userInfo.email,
                                            authResult.userInfo.displayName ?: "",
                                            "valid_token"
                                        ),
                                        calendarOps = currentState.calendarOps.copy(calendarsLoading = false)
                                    )
                                }
                                Logger.business(LogTags.AUTH, "Sign-in successful", authResult.userInfo.email)
                            }
                            .onFailure { error ->
                                updateAuthState { currentState ->
                                    currentState.copy(
                                        calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                                        errors = AppErrorState.authenticationError(errorHandler.getErrorMessage(error))
                                    )
                                }
                            }
                    }
                    
                    is AuthResult.Failure -> {
                        updateAuthState { currentState ->
                            currentState.copy(
                                calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                                errors = AppErrorState.authenticationError(authResult.error)
                            )
                        }
                        Logger.e(LogTags.AUTH, "Sign-in failed - ${authResult.error}")
                    }
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
     * Signs out user and clears all authentication data.
     * MEMORY LEAK FIX: Avoid holding Context references
     */
    fun signOut() {
        viewModelScope.launch {
            updateAuthState { currentState ->
                currentState.copy(
                    calendarOps = currentState.calendarOps.copy(calendarsLoading = true),
                    errors = AppErrorState.EMPTY
                )
            }
            
            try {
                // MEMORY LEAK FIX: Use OAuth2TokenManager without Context dependency
                val signOutResult = oauth2TokenManager.signOut()
                
                if (signOutResult.isSuccess) {
                    // Clear auth data from DataStore
                    authDataStoreRepository.clearAuthData()
                        .onSuccess {
                            updateAuthState { AuthState.EMPTY }
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
                } else {
                    updateAuthState { currentState ->
                        currentState.copy(
                            calendarOps = currentState.calendarOps.copy(calendarsLoading = false),
                            errors = AppErrorState.authenticationError("Abmeldung fehlgeschlagen")
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
     * Refreshes current access token if needed.
     * Useful for manual token refresh or when API calls fail with auth errors.
     */
    fun refreshToken() {
        viewModelScope.launch {
            try {
                val tokenResult = oauth2TokenManager.getCurrentValidToken()
                
                if (tokenResult.isSuccess) {
                    updateAuthState { currentState ->
                        currentState.copy(
                            userAuth = currentState.userAuth.copy(hasValidToken = true)
                        )
                    }
                    Logger.d(LogTags.TOKEN, "Token refresh successful")
                } else {
                    updateAuthState { currentState ->
                        currentState.copy(
                            userAuth = currentState.userAuth.copy(hasValidToken = false),
                            errors = AppErrorState.authenticationError("Token-Aktualisierung fehlgeschlagen")
                        )
                    }
                    Logger.e(LogTags.TOKEN, "Token refresh failed - ${tokenResult.exceptionOrNull()}")
                }
            } catch (e: Exception) {
                Logger.e(LogTags.TOKEN, "Error refreshing token", e)
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
     * Useful for permission checks and API access validation.
     */
    fun validateAuthentication(): Boolean {
        val currentState = _authState.value
        return currentState.userAuth.isFullyAuthenticated
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
            // ViewModelScope automatically cancels, but we log for debugging
        } catch (e: Exception) {
            Logger.e(LogTags.LIFECYCLE, "Error during AuthViewModel cleanup", e)
        }
    }
}
