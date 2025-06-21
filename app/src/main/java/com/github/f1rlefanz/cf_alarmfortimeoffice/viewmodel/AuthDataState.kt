package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import android.app.PendingIntent
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch

// OPTIMIERUNG 1: Ersetzt Custom Tuple Classes (Quadruple, Quintuple, Sextuple)
// durch strukturierte Data Class für bessere Performance und Lesbarkeit
data class AuthDataFromStore(
    val isLoggedIn: Boolean,
    val email: String,
    val userId: String,
    val calendarId: String,
    val accessToken: String,
    val tokenExpiry: Long
)

data class AuthState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val userName: String? = null,
    val userEmailOrId: String? = null,
    val error: String? = null,
    val accessToken: String? = null,
    val authorizationPendingIntent: PendingIntent? = null,
    val calendarsLoading: Boolean = false,
    val calendarPermissionDenied: Boolean = false,
    val androidCalendarPermissionGranted: Boolean = false,
    val showAndroidCalendarPermissionRationale: Boolean = false,
    val nextShiftAlarm: ShiftMatch? = null,
    val calendarEventsLoaded: Boolean = false,
    // Schicht-Konfiguration
    val shiftDefinitions: List<com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftDefinition> = emptyList(),
    val autoAlarmEnabled: Boolean = true,
    val shiftConfigLoading: Boolean = false,
    // AlarmManager Status
    val systemAlarmSet: Boolean = false,
    val canScheduleExactAlarms: Boolean = false,
    val alarmStatusMessage: String? = null
)

// Extension function für cleaner State Updates
fun AuthState.updateWithAuthData(authData: AuthDataFromStore): AuthState {
    val isTokenValid = authData.accessToken.isNotBlank() && 
        authData.tokenExpiry > System.currentTimeMillis()
    
    return if (authData.isLoggedIn) {
        if (authData.email.isNotBlank()) {
            this.copy(
                isLoading = false, 
                isSignedIn = true,
                userName = authData.userId.ifBlank { authData.email },
                userEmailOrId = authData.email, 
                error = null,
                accessToken = if (isTokenValid) authData.accessToken else null
            )
        } else {
            this.copy(
                isLoading = false, 
                isSignedIn = true,
                userName = if (this.userName.isNullOrBlank() && authData.userId.isNotBlank()) authData.userId else this.userName,
                userEmailOrId = if (this.userEmailOrId.isNullOrBlank() && authData.email.isNotBlank()) authData.email else this.userEmailOrId,
                error = if (this.userEmailOrId.isNullOrBlank() && authData.email.isBlank() && this.error == null) "E-Mail-Verarbeitung nach Login..." else this.error,
                accessToken = if (isTokenValid) authData.accessToken else null
            )
        }
    } else {
        AuthState()
    }
}
