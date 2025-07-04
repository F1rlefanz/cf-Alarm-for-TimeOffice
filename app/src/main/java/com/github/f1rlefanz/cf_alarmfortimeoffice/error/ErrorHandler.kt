package com.github.f1rlefanz.cf_alarmfortimeoffice.error

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * Central error handler for the application
 */
class ErrorHandler {
    private val tag = "CFAlarm.ErrorHandler"
    
    /**
     * Handle error with appropriate logging and user messaging
     */
    fun handleError(error: Throwable, context: String = ""): AppError {
        val appError = error.toAppError()
        
        val logMessage = buildString {
            append("Error in $context: ")
            append(appError.message)
            appError.cause?.let { 
                append(" | Cause: ${it.message}")
            }
        }
        
        when (appError) {
            is AppError.NetworkError,
            is AppError.ApiError -> Log.w(tag, logMessage, appError)
            
            is AppError.FileSystemError,
            is AppError.DataStoreError,
            is AppError.PreferencesError -> Log.e(tag, logMessage, appError)
            
            is AppError.AuthenticationError,
            is AppError.PermissionError -> Log.e(tag, logMessage, appError)
            
            is AppError.CalendarAccessError,
            is AppError.CalendarNotFoundError -> Log.w(tag, logMessage, appError)
            
            is AppError.ValidationError,
            is AppError.SystemError -> Log.e(tag, logMessage, appError)
            
            is AppError.UnknownError -> Log.e(tag, logMessage, appError)
        }
        
        return appError
    }
    
    /**
     * Get user-friendly error message for any throwable
     */
    fun getErrorMessage(error: Throwable): String {
        val appError = if (error is AppError) error else error.toAppError()
        return getUserMessage(appError)
    }
    
    /**
     * Get user-friendly error message
     */
    fun getUserMessage(error: AppError): String = when (error) {
        is AppError.NetworkError -> "Keine Internetverbindung. Bitte überprüfen Sie Ihre Verbindung."
        is AppError.ApiError -> "Serverfehler. Bitte versuchen Sie es später erneut."
        is AppError.FileSystemError -> "Dateizugriff fehlgeschlagen. Bitte starten Sie die App neu."
        is AppError.DataStoreError -> "Einstellungen konnten nicht gespeichert werden."
        is AppError.PreferencesError -> "Konfiguration konnte nicht geladen werden."
        is AppError.AuthenticationError -> "Anmeldung fehlgeschlagen. Bitte erneut versuchen."
        is AppError.PermissionError -> when (error.permission) {
            "android.permission.READ_CALENDAR" -> "Kalenderzugriff verweigert. Bitte in den Einstellungen erlauben."
            else -> "Berechtigung verweigert. Bitte in den Einstellungen überprüfen."
        }
        is AppError.CalendarAccessError -> "Auf den Kalender konnte nicht zugegriffen werden."
        is AppError.CalendarNotFoundError -> "Der ausgewählte Kalender wurde nicht gefunden."
        is AppError.ValidationError -> "Ungültige Eingabe. Bitte überprüfen Sie Ihre Daten."
        is AppError.SystemError -> "Systemfehler. Bitte versuchen Sie es erneut."
        is AppError.UnknownError -> "Ein unerwarteter Fehler ist aufgetreten."
    }
    
    /**
     * Create a CoroutineExceptionHandler
     */
    fun createCoroutineExceptionHandler(
        context: String,
        onError: ((AppError) -> Unit)? = null
    ): CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val appError = handleError(throwable, context)
        onError?.invoke(appError)
    }
}
