package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AuthState

/**
 * OPTIMIERUNG: MainContentScreen State Management
 * 
 * Extrahiert die komplexe when-Logic in einen eigenen State Handler
 * für bessere Lesbarkeit und Testbarkeit
 */
sealed class ContentState {
    object ShowAlarm : ContentState()
    object NoCalendarSelected : ContentState() 
    object AutoAlarmDisabled : ContentState()
    object LoadingShifts : ContentState()
    object NoShiftFound : ContentState()
}

sealed class ErrorState {
    object None : ErrorState()
    object CalendarPermissionRationale : ErrorState()
    object CalendarPermissionDenied : ErrorState()
    object GoogleAuthFailed : ErrorState()
    data class GeneralError(val message: String) : ErrorState()
}

/**
 * Bestimmt den Hauptinhaltszustand basierend auf AuthState und persistedCalendarId
 */
fun determineContentState(authState: AuthState, persistedCalendarId: String): ContentState {
    return when {
        authState.nextShiftAlarm != null -> ContentState.ShowAlarm
        persistedCalendarId.isBlank() -> ContentState.NoCalendarSelected
        !authState.autoAlarmEnabled -> ContentState.AutoAlarmDisabled
        authState.calendarsLoading -> ContentState.LoadingShifts
        else -> ContentState.NoShiftFound
    }
}

/**
 * Bestimmt den Fehlerzustand basierend auf AuthState
 */
fun determineErrorState(authState: AuthState): ErrorState {
    return when {
        !authState.androidCalendarPermissionGranted && authState.showAndroidCalendarPermissionRationale -> {
            ErrorState.CalendarPermissionRationale
        }
        !authState.androidCalendarPermissionGranted && authState.calendarPermissionDenied && 
        authState.error?.contains("Android Kalenderberechtigung") == true -> {
            ErrorState.CalendarPermissionDenied
        }
        authState.accessToken.isNullOrBlank() && !authState.calendarsLoading && authState.calendarPermissionDenied -> {
            ErrorState.GoogleAuthFailed
        }
        authState.error?.let { it.contains("Kalender") || it.contains("Authentifizierung") } == true -> {
            ErrorState.GeneralError(authState.error!!)
        }
        else -> ErrorState.None
    }
}