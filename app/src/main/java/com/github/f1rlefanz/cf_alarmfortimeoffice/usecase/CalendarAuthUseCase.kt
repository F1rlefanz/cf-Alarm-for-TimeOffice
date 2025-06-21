package com.github.f1rlefanz.cf_alarmfortimeoffice.usecase

import android.app.Activity
import androidx.activity.result.ActivityResult
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarItem
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.data.AuthDataStoreRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.Scopes
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Extrahierte Calendar Authorization Logic aus AuthViewModel
 * Vereinfacht die Kalender-Autorisierung und verbessert Testbarkeit
 */
class CalendarAuthUseCase(
    private val calendarRepository: CalendarRepository,
    private val authDataStoreRepository: AuthDataStoreRepository
) {
    
    private val targetCalendarScopeString = CalendarScopes.CALENDAR_READONLY
    
    sealed class CalendarAuthResult {
        object NeedsPermissionGrant : CalendarAuthResult()
        data class TokenReceived(val token: String) : CalendarAuthResult()
        data class Error(val message: String) : CalendarAuthResult()
        data class RequiresUserConsent(val pendingIntent: android.app.PendingIntent) : CalendarAuthResult()
    }
    
    sealed class CalendarLoadResult {
        data class Success(val calendars: List<CalendarItem>) : CalendarLoadResult()
        data class Error(val message: String, val shouldRetryAuth: Boolean = false) : CalendarLoadResult()
    }
    
    fun requestCalendarScopes(activity: MainActivity, userEmail: String): CalendarAuthResult {
        Timber.d("CalendarAuthUseCase: requestCalendarScopes für $userEmail")
        
        if (userEmail.isBlank()) {
            return CalendarAuthResult.Error("User-E-Mail ist leer")
        }
        
        val scopesToRequestObjects = listOf(
            Scope(Scopes.EMAIL),
            Scope(Scopes.PROFILE),
            Scope(targetCalendarScopeString)
        )
        
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopesToRequestObjects)
            .build()
        
        return try {
            var result: CalendarAuthResult = CalendarAuthResult.Error("Unbekannter Fehler")
            
            val successListener = OnSuccessListener<AuthorizationResult> { authResult ->
                result = when {
                    authResult.hasResolution() -> {
                        Timber.i("Zustimmung des Nutzers erforderlich")
                        CalendarAuthResult.RequiresUserConsent(authResult.pendingIntent!!)
                    }
                    authResult.accessToken != null -> {
                        Timber.i("Zugriffstoken direkt erhalten")
                        saveAccessToken(authResult.accessToken)
                        CalendarAuthResult.TokenReceived(authResult.accessToken)
                    }
                    checkCalendarScopeGranted(authResult.grantedScopes) -> {
                        Timber.w("Scopes erteilt, aber kein direkter AccessToken")
                        CalendarAuthResult.Error("Kalenderberechtigung erteilt, aber Token-Abruf benötigt weitere Schritte")
                    }
                    else -> {
                        Timber.w("Autorisierung nicht erfolgreich. Granted: ${authResult.grantedScopes}")
                        CalendarAuthResult.Error("Kalenderberechtigung nicht vollständig erteilt")
                    }
                }
            }
            
            val failureListener = OnFailureListener { exception ->
                Timber.e(exception, "Fehler bei der Autorisierungsanfrage")
                result = CalendarAuthResult.Error("Fehler bei Kalender-Autorisierung: ${exception.localizedMessage}")
            }
            
            Identity.getAuthorizationClient(activity)
                .authorize(request)
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener)
            
            result
            
        } catch (e: Exception) {
            Timber.e(e, "Exception beim Anfordern der Calendar Scopes")
            CalendarAuthResult.Error("Unerwarteter Fehler: ${e.localizedMessage}")
        }
    }
    
    fun handleAuthorizationResult(
        activityResult: ActivityResult, 
        activity: MainActivity
    ): CalendarAuthResult {
        val intentData = activityResult.data
        
        val authResult: AuthorizationResult? = try {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intentData)
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Extrahieren des AuthorizationResult")
            return CalendarAuthResult.Error("Fehler beim Verarbeiten der Autorisierungsantwort")
        }
        
        return when {
            activityResult.resultCode == Activity.RESULT_OK && authResult?.accessToken != null -> {
                Timber.i("Zugriffstoken nach Intent-Auflösung erhalten")
                saveAccessToken(authResult.accessToken)
                CalendarAuthResult.TokenReceived(authResult.accessToken)
            }
            activityResult.resultCode == Activity.RESULT_OK && checkCalendarScopeGranted(authResult?.grantedScopes) -> {
                Timber.w("Scopes nach Intent erteilt, aber kein direkter AccessToken")
                CalendarAuthResult.Error("Kalenderberechtigung erteilt, aber Token-Abruf benötigt weitere Schritte")
            }
            else -> {
                Timber.w("Autorisierung abgebrochen oder fehlgeschlagen. ResultCode: ${activityResult.resultCode}")
                val errorMsg = if (authResult == null && activityResult.resultCode == Activity.RESULT_OK) {
                    "Fehler beim Verarbeiten der Autorisierungsantwort"
                } else {
                    "Kalender-Autorisierung abgebrochen oder fehlgeschlagen"
                }
                CalendarAuthResult.Error(errorMsg)
            }
        }
    }
    
    suspend fun loadCalendarsWithToken(token: String): CalendarLoadResult {
        if (token.isBlank()) {
            return CalendarLoadResult.Error("Access Token ist leer")
        }
        
        return try {
            Timber.d("Lade Kalender mit Access Token")
            val calendars = calendarRepository.getCalendarsWithToken(token)
            
            if (calendars.isEmpty()) {
                Timber.w("Keine Kalender gefunden")
                CalendarLoadResult.Error("Keine Kalender in Ihrem Google-Account gefunden oder Zugriff nicht vollständig gewährt")
            } else {
                Timber.i("${calendars.size} Kalender geladen")
                CalendarLoadResult.Success(calendars)
            }
            
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            Timber.e(e, "GoogleJsonResponseException. Status: ${e.statusCode}")
            when (e.statusCode) {
                401, 403 -> {
                    Timber.w("Access Token ungültig/abgelaufen")
                    CalendarLoadResult.Error(
                        "Kalender-Sitzung ungültig/verweigert (${e.statusCode}), bitte erneut versuchen",
                        shouldRetryAuth = true
                    )
                }
                else -> {
                    CalendarLoadResult.Error("Fehler (${e.statusCode}) beim Laden der Kalender: ${e.statusMessage}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Allgemeiner Fehler beim Laden der Kalender")
            CalendarLoadResult.Error("Unbekannter Fehler beim Laden der Kalender: ${e.localizedMessage}")
        }
    }
    
    suspend fun loadCalendarEvents(token: String, calendarId: String) = 
        calendarRepository.getCalendarEventsWithToken(token, calendarId)
    
    private fun checkCalendarScopeGranted(grantedScopeStrings: List<String>?): Boolean {
        return grantedScopeStrings?.contains(targetCalendarScopeString) == true
    }
    
    private fun saveAccessToken(token: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            authDataStoreRepository.saveAccessToken(token)
            // Token expires in 1 hour (3600 seconds)
            authDataStoreRepository.saveTokenExpiry(System.currentTimeMillis() + 3600000)
        }
    }
}
