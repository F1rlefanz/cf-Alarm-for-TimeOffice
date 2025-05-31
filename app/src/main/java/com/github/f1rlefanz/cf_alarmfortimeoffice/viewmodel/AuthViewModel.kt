package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
// import android.content.Intent // Sicherstellen, dass dieser Import nicht benötigt wird
import androidx.activity.result.ActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarItem
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.data.AuthDataStoreRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.CalendarEvent
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftRecognitionEngine
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope // Wird für das Erstellen von Scope-Objekten für die Anfrage benötigt
import com.google.android.gms.common.Scopes // Enthält EMAIL, PROFILE Konstanten als Strings
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.api.services.calendar.CalendarScopes // Enthält CALENDAR_READONLY Konstante als String
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
    val shiftConfigLoading: Boolean = false
)

class AuthViewModel(
    application: Application,
    private val credentialAuthManager: CredentialAuthManager
) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authDataStoreRepository = AuthDataStoreRepository(application)
    private val calendarRepository = CalendarRepository()
    private val shiftConfigRepository = ShiftConfigRepository(application)
    private val shiftRecognitionEngine = ShiftRecognitionEngine(shiftConfigRepository)

    private val _calendars = MutableStateFlow<List<CalendarItem>>(emptyList())
    val calendars: StateFlow<List<CalendarItem>> = _calendars.asStateFlow()

    private val _temporarilySelectedCalendarId = MutableStateFlow("")
    val temporarilySelectedCalendarId: StateFlow<String> = _temporarilySelectedCalendarId.asStateFlow()

    val persistedCalendarId: Flow<String> = authDataStoreRepository.calendarId

    // Die String-Konstante des Scopes, den wir suchen
    private val targetCalendarScopeString = CalendarScopes.CALENDAR_READONLY

    init {
        Timber.d("AuthViewModel init")
        viewModelScope.launch {
            // Beobachte Schicht-Konfiguration
            launch {
                combine(
                    shiftConfigRepository.shiftDefinitions,
                    shiftConfigRepository.autoAlarmEnabled
                ) { definitions, autoEnabled ->
                    Pair(definitions, autoEnabled)
                }.collectLatest { (definitions, autoEnabled) ->
                    Timber.d("ShiftConfig updated: ${definitions.size} definitions, autoEnabled=$autoEnabled")
                    definitions.forEach { def ->
                        Timber.d("ShiftDefinition: ${def.name} (${def.id}) - enabled=${def.isEnabled}")
                    }
                    _authState.update { currentState ->
                        currentState.copy(
                            shiftDefinitions = definitions,
                            autoAlarmEnabled = autoEnabled
                        )
                    }
                }
            }
            
            // Beobachte Auth-Daten
            authDataStoreRepository.loginStatus
                .combine(authDataStoreRepository.userEmail) { isLoggedInDs, emailDs -> Pair(isLoggedInDs, emailDs) }
                .combine(authDataStoreRepository.userId) { loginEmailPair, userIdDs -> Triple(loginEmailPair.first, loginEmailPair.second, userIdDs) }
                .combine(authDataStoreRepository.calendarId) { loginEmailUserIdPair, calIdDs ->
                    Quadruple(loginEmailUserIdPair.first, loginEmailUserIdPair.second, loginEmailUserIdPair.third, calIdDs)
                }
                .collectLatest { (isLoggedInDataStore, emailFromDataStore, userIdFromDataStore, persistedCalIdFromDataStore) ->
                    Timber.d("AuthViewModel init collect: isLoggedIn=$isLoggedInDataStore, email='$emailFromDataStore', userId='$userIdFromDataStore', calId='$persistedCalIdFromDataStore'")
                    _authState.update { currentState ->
                        val newState = if (isLoggedInDataStore) {
                            if (emailFromDataStore.isNotBlank()) {
                                currentState.copy(
                                    isLoading = false, isSignedIn = true,
                                    userName = userIdFromDataStore.ifBlank { emailFromDataStore },
                                    userEmailOrId = emailFromDataStore, error = null
                                )
                            } else {
                                currentState.copy(
                                    isLoading = false, isSignedIn = true,
                                    userName = if (currentState.userName.isNullOrBlank() && userIdFromDataStore.isNotBlank()) userIdFromDataStore else currentState.userName,
                                    userEmailOrId = if (currentState.userEmailOrId.isNullOrBlank() && emailFromDataStore.isNotBlank()) emailFromDataStore else currentState.userEmailOrId,
                                    error = if (currentState.userEmailOrId.isNullOrBlank() && emailFromDataStore.isBlank() && currentState.error == null) "E-Mail-Verarbeitung nach Login..." else currentState.error
                                )
                            }
                        } else {
                            AuthState()
                        }
                        if (persistedCalIdFromDataStore.isNotBlank() && _temporarilySelectedCalendarId.value != persistedCalIdFromDataStore) {
                            _temporarilySelectedCalendarId.value = persistedCalIdFromDataStore
                        }
                        newState
                    }
                }
        }
    }

    fun onAndroidCalendarPermissionResult(isGranted: Boolean, showRationaleIfDenied: Boolean = false) {
        Timber.d("AuthViewModel: onAndroidCalendarPermissionResult. isGranted: $isGranted, showRationale: $showRationaleIfDenied")
        _authState.update { currentState ->
            currentState.copy(
                androidCalendarPermissionGranted = isGranted,
                calendarPermissionDenied = if (!isGranted && !showRationaleIfDenied) true else currentState.calendarPermissionDenied,
                error = if (!isGranted && !showRationaleIfDenied && currentState.error == null) "Android Kalenderberechtigung dauerhaft verweigert." else currentState.error,
                showAndroidCalendarPermissionRationale = showRationaleIfDenied && !isGranted,
                calendarsLoading = if (isGranted) currentState.calendarsLoading else false
            )
        }
        if (isGranted && _authState.value.isSignedIn && !_authState.value.userEmailOrId.isNullOrBlank()) {
            if (_authState.value.accessToken.isNullOrBlank()) {
                Timber.i("Android Berechtigung erteilt, AccessToken fehlt. UI sollte AuthorizationClient-Flow starten (via triggerCalendarAccess).")
            } else {
                loadCalendarsUsingToken()
            }
        } else if (!isGranted) {
            Timber.w("Android Kalenderberechtigung nicht erteilt.")
            _authState.update { it.copy(calendarsLoading = false) }
        }
    }

    fun triggerCalendarAccess(activity: MainActivity) {
        viewModelScope.launch {
            if (!_authState.value.isSignedIn || _authState.value.userEmailOrId.isNullOrBlank()) {
                Timber.w("AuthViewModel: Kalenderzugriff nicht möglich, Nutzer nicht eingeloggt oder E-Mail fehlt.")
                _authState.update { it.copy(error = "Bitte zuerst anmelden, um Kalender zu laden.") }
                return@launch
            }

            if (!_authState.value.androidCalendarPermissionGranted) {
                Timber.d("AuthViewModel triggerCalendarAccess: Android-Berechtigung fehlt, fordere an.")
                activity.checkAndRequestCalendarPermission()
            } else if (_authState.value.accessToken.isNullOrBlank()) {
                Timber.d("AuthViewModel triggerCalendarAccess: Android-Berechtigung vorhanden, aber AccessToken fehlt. Fordere Google API Auth an.")
                requestCalendarScopes(activity)
            } else {
                Timber.d("AuthViewModel triggerCalendarAccess: Android-Berechtigung und AccessToken vorhanden. Lade Kalender.")
                loadCalendarsUsingToken()
            }
        }
    }

    // KORREKTUR: Hilfsfunktion angepasst, um mit List<String>? zu arbeiten
    private fun checkScopeStringGranted(grantedScopeStrings: List<String>?, targetScopeString: String): Boolean {
        // KORREKTUR für "Equality check can be used instead of elvis for nullable boolean check"
        return grantedScopeStrings?.contains(targetScopeString) == true
    }

    fun requestCalendarScopes(activity: MainActivity) {
        Timber.d("AuthViewModel: requestCalendarScopes wird angefordert.")
        _authState.update { it.copy(calendarsLoading = true, error = null, calendarPermissionDenied = false, authorizationPendingIntent = null) }

        val currentUserEmail = _authState.value.userEmailOrId
        if (currentUserEmail == null) {
            Timber.e("AuthorizationClient: User-E-Mail nicht vorhanden (null). Kann Scopes nicht anfordern.")
            _authState.update { it.copy(calendarsLoading = false, error = "Anmeldeinformationen (E-Mail ist null) unvollständig für Kalenderzugriff.") }
            return
        }

        // Für die Anfrage werden Scope-Objekte benötigt
        val scopesToRequestObjects = listOf(
            Scope(Scopes.EMAIL), // Scopes.EMAIL ist ein String
            Scope(Scopes.PROFILE), // Scopes.PROFILE ist ein String
            Scope(targetCalendarScopeString) // targetCalendarScopeString ist CalendarScopes.CALENDAR_READONLY
        )

        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(scopesToRequestObjects) // Hier Scope-Objekte übergeben

        val request = requestBuilder.build()

        val successListener = OnSuccessListener<AuthorizationResult> { authResult ->
            if (authResult.hasResolution()) {
                Timber.i("AuthorizationClient: Zustimmung des Nutzers erforderlich. Setze PendingIntent.")
                _authState.update { currentState -> currentState.copy(authorizationPendingIntent = authResult.pendingIntent, calendarsLoading = false) }
            } else if (authResult.accessToken != null) {
                Timber.i("AuthorizationClient: Zugriffstoken direkt erhalten: ${authResult.accessToken?.take(10)}...")
                _authState.update { currentState -> currentState.copy(accessToken = authResult.accessToken, calendarsLoading = false, error = null, calendarPermissionDenied = false) }
                loadCalendarsUsingToken()
            } else if (checkScopeStringGranted(authResult.grantedScopes, targetCalendarScopeString)) { // KORREKTUR: `grantedScopes` sind Strings
                Timber.w("AuthorizationClient: Scopes erteilt, aber kein direkter AccessToken.")
                _authState.update { currentState -> currentState.copy(calendarsLoading = false, error = "Kalenderberechtigung erteilt, aber Token-Abruf benötigt weitere Schritte.") }
            } else {
                Timber.w("AuthorizationClient: Autorisierung scheinbar erfolgreich, aber weder Token noch Resolution? Scope nicht erteilt? Granted: ${authResult.grantedScopes}")
                _authState.update { currentState -> currentState.copy(calendarsLoading = false, calendarPermissionDenied = true, error = "Kalenderberechtigung nicht vollständig erteilt.") }
            }
        }

        val failureListener = OnFailureListener { exception ->
            Timber.e(exception, "AuthorizationClient: Fehler bei der Autorisierungsanfrage.")
            _authState.update { currentState -> currentState.copy(calendarsLoading = false, error = "Fehler bei Kalender-Autorisierung: ${exception.localizedMessage}", calendarPermissionDenied = true) }
        }

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener(successListener)
            .addOnFailureListener(failureListener)
    }

    fun handleAuthorizationResult(activityResult: ActivityResult, activity: MainActivity) {
        val intentData = activityResult.data
        _authState.update { it.copy(authorizationPendingIntent = null) }

        val authResult: AuthorizationResult? = try {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intentData)
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Extrahieren des AuthorizationResult aus dem Intent.")
            null
        }

        if (activityResult.resultCode == Activity.RESULT_OK && authResult != null) {
            if (authResult.accessToken != null) {
                Timber.i("AuthorizationClient: Zugriffstoken nach Intent-Auflösung erhalten: ${authResult.accessToken?.take(10)}...")
                _authState.update { currentState -> currentState.copy(accessToken = authResult.accessToken, error = null, calendarPermissionDenied = false, calendarsLoading = false) }
                loadCalendarsUsingToken()
            } else if (checkScopeStringGranted(authResult.grantedScopes, targetCalendarScopeString)) { // KORREKTUR: `grantedScopes` sind Strings
                Timber.w("AuthorizationClient: Scopes nach Intent erteilt, aber kein direkter AccessToken.")
                _authState.update { currentState -> currentState.copy(calendarsLoading = false, error = "Kalenderberechtigung erteilt, aber Token-Abruf benötigt weitere Schritte.") }
            } else {
                Timber.w("AuthorizationClient: Autorisierung nach Intent nicht erfolgreich oder Token fehlt. Result: hasResolution=${authResult.hasResolution()}, token=${authResult.accessToken != null}, Granted: ${authResult.grantedScopes}")
                _authState.update { currentState -> currentState.copy(calendarsLoading = false, error = "Kalender-Autorisierung nicht erfolgreich.", calendarPermissionDenied = true) }
            }
        } else {
            Timber.w("AuthorizationClient: Autorisierung vom Nutzer abgebrochen oder Fehler. ResultCode: ${activityResult.resultCode}, AuthResult vorhanden: ${authResult != null}")
            val errorMsg = if (authResult == null && activityResult.resultCode == Activity.RESULT_OK) {
                "Fehler beim Verarbeiten der Autorisierungsantwort."
            } else {
                "Kalender-Autorisierung abgebrochen oder fehlgeschlagen."
            }
            _authState.update { currentState -> currentState.copy(calendarsLoading = false, error = errorMsg, calendarPermissionDenied = true) }
        }
    }

    private fun clearAccessToken() {
        _authState.update { it.copy(accessToken = null) }
    }

    internal fun loadCalendarsUsingToken() {
        val token = _authState.value.accessToken
        if (token.isNullOrBlank()) {
            Timber.e("loadCalendarsUsingToken: Access Token ist null oder leer. Dies sollte nicht passieren.")
            _authState.update { it.copy(calendarsLoading = false, error = "Authentifizierungstoken fehlt für Kalender.", calendarPermissionDenied = true) }
            return
        }

        Timber.d("loadCalendarsUsingToken: Lade Kalender mit Access Token (erste 10 Zeichen: ${token.take(10)})...")
        _authState.update { it.copy(calendarsLoading = true, error = null, calendarPermissionDenied = false) }
        viewModelScope.launch {
            try {
                val fetchedCalendars = calendarRepository.getCalendarsWithToken(token)
                _calendars.value = fetchedCalendars
                Timber.i("loadCalendarsUsingToken: ${fetchedCalendars.size} Kalender geladen.")
                if (fetchedCalendars.isEmpty() && _authState.value.isSignedIn && !_authState.value.calendarPermissionDenied) {
                    Timber.w("loadCalendarsUsingToken: Keine Kalender gefunden (API-Call ok, aber Liste leer).")
                }
                _authState.update { it.copy(calendarsLoading = false) }
                
                // Load calendar events if a calendar is already selected
                val selectedCalendarId = persistedCalendarId.first()
                if (selectedCalendarId.isNotBlank()) {
                    loadCalendarEvents(selectedCalendarId)
                }
                
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                Timber.e(e, "loadCalendarsUsingToken: GoogleJsonResponseException. Status: ${e.statusCode}")
                if (e.statusCode == 401 || e.statusCode == 403) {
                    Timber.w("Access Token möglicherweise ungültig/abgelaufen oder Scopes nicht ausreichend. Lösche Token.")
                    clearAccessToken()
                    _authState.update { currentState -> currentState.copy(calendarsLoading = false, error = "Kalender-Sitzung ungültig/verweigert (${e.statusCode}), bitte erneut versuchen.", calendarPermissionDenied = true, accessToken = null) }
                } else {
                    _authState.update { currentState -> currentState.copy(calendarsLoading = false, error = "Fehler (${e.statusCode}) beim Laden der Kalender: ${e.statusMessage}", calendarPermissionDenied = true) }
                }
                _calendars.value = emptyList()
            }
            catch (e: Exception) {
                Timber.e(e, "loadCalendarsUsingToken: Allgemeiner Fehler beim Abrufen der Kalender.")
                _authState.update { currentState -> currentState.copy(calendarsLoading = false, error = "Unbekannter Fehler beim Laden der Kalender: ${e.localizedMessage}", calendarPermissionDenied = true) }
                _calendars.value = emptyList()
            }
        }
    }
    
    private fun loadCalendarEvents(calendarId: String) {
        val token = _authState.value.accessToken
        if (token.isNullOrBlank()) {
            Timber.e("loadCalendarEvents: Access Token ist null oder leer.")
            return
        }
        
        viewModelScope.launch {
            try {
                Timber.d("Loading calendar events for calendar: $calendarId")
                val events = calendarRepository.getCalendarEventsWithToken(token, calendarId)
                
                // Process shift recognition
                val nextShift = shiftRecognitionEngine.findNextShiftAlarm(events)
                
                _authState.update { currentState ->
                    currentState.copy(
                        nextShiftAlarm = nextShift,
                        calendarEventsLoaded = true
                    )
                }
                
                Timber.i("Calendar events loaded: ${events.size} events, next shift: ${nextShift?.shiftDefinition?.name}")
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading calendar events")
                _authState.update { currentState ->
                    currentState.copy(
                        error = "Fehler beim Laden der Kalenderereignisse: ${e.localizedMessage}",
                        calendarEventsLoaded = false
                    )
                }
            }
        }
    }

    fun startSignIn() {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            Timber.d("AuthViewModel: Starte SignIn Prozess...")
            val result = credentialAuthManager.signIn()
            Timber.d("AuthViewModel: SignIn Ergebnis: success=${result.success}, credential vorhanden=${result.credentialResponse != null}, error=${result.error}, exception=${result.exception?.javaClass?.simpleName}")

            if (result.success && result.credentialResponse != null) {
                val (userIdFromCredential, displayName, emailFromCredential) = credentialAuthManager.extractUserInfo(result.credentialResponse)

                if (emailFromCredential.isNullOrBlank()) {
                    _authState.update { it.copy(isLoading = false, isSignedIn = false, error = "E-Mail nicht in Anmeldeantwort gefunden oder ist leer.") }
                    Timber.e("AuthViewModel: E-Mail ist null oder leer nach erfolgreichem SignIn.")
                    return@launch
                }
                val finalUserName = displayName ?: userIdFromCredential ?: emailFromCredential

                authDataStoreRepository.saveLoginStatus(true)
                authDataStoreRepository.saveUserId(finalUserName)
                authDataStoreRepository.saveUserEmail(emailFromCredential)

                _authState.update {
                    it.copy(
                        isLoading = false, isSignedIn = true,
                        userName = finalUserName, userEmailOrId = emailFromCredential,
                        error = null,
                        accessToken = null, authorizationPendingIntent = null,
                        calendarPermissionDenied = false, showAndroidCalendarPermissionRationale = false
                    )
                }
                Timber.d("AuthViewModel: Nutzer '$finalUserName' ('$emailFromCredential') angemeldet. AuthState aktualisiert.")
            } else {
                val errorMessage = result.error ?: "Unbekannter Fehler bei der Anmeldung (${result.exception?.javaClass?.simpleName})"
                _authState.update { it.copy(isLoading = false, isSignedIn = false, error = errorMessage) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            Timber.d("AuthViewModel: signOut aufgerufen.")
            credentialAuthManager.signOutLocally()
            authDataStoreRepository.clearAuthData()
            _calendars.value = emptyList()
            _temporarilySelectedCalendarId.value = ""
            _authState.value = AuthState()
            Timber.i("AuthViewModel: SignOut durchgeführt, AuthState und DataStore zurückgesetzt.")
        }
    }

    fun onCalendarTemporarilySelected(calendarId: String) {
        _temporarilySelectedCalendarId.value = calendarId
        Timber.d("AuthViewModel: Kalender temporär ausgewählt: '$calendarId'")
    }

    fun persistSelectedCalendar() {
        viewModelScope.launch {
            val idToSave = _temporarilySelectedCalendarId.value
            if (idToSave.isNotBlank()) {
                authDataStoreRepository.saveCalendarId(idToSave)
                Timber.i("AuthViewModel: Kalender-ID '$idToSave' persistent gespeichert.")
                
                // Load calendar events for the selected calendar
                loadCalendarEvents(idToSave)
            } else {
                Timber.w("AuthViewModel: Versuch, eine leere Kalender-ID zu speichern.")
            }
        }
    }

    fun clearTemporaryCalendarSelection() {
        viewModelScope.launch {
            val persistedId = persistedCalendarId.first()
            _temporarilySelectedCalendarId.value = persistedId
            Timber.d("AuthViewModel: Temporäre Kalenderauswahl auf '$persistedId' zurückgesetzt.")
        }
    }

    fun retryCalendarAccessOrReAuth(activity: MainActivity) {
        viewModelScope.launch {
            val userEmail = _authState.value.userEmailOrId
            Timber.d("AuthViewModel: retryCalendarAccessOrReAuth aufgerufen. Aktueller User im State: '$userEmail'")
            _authState.update { currentState -> currentState.copy(
                error = null,
                calendarPermissionDenied = false,
                showAndroidCalendarPermissionRationale = false,
                authorizationPendingIntent = null,
                accessToken = null,
                calendarsLoading = true
            )}
            if (authState.value.isSignedIn && !userEmail.isNullOrBlank()) {
                Timber.d("AuthViewModel: Erneuter Versuch für Kalenderzugriff für '$userEmail'")
                if (!authState.value.androidCalendarPermissionGranted) {
                    activity.checkAndRequestCalendarPermission()
                } else {
                    requestCalendarScopes(activity)
                }
            } else {
                Timber.e("AuthViewModel: retryCalendarAccessOrReAuth nicht möglich, Nutzer nicht eingeloggt oder E-Mail fehlt (aus AuthState).")
                if (!authState.value.isSignedIn) {
                    _authState.update { it.copy(calendarsLoading = false) }
                    startSignIn()
                } else {
                    _authState.update{it.copy(error = "Kritischer Fehler: Eingeloggt ohne E-Mail für erneuten Versuch (aus AuthState).", calendarsLoading = false)}
                }
            }
        }
    }

    fun clearAuthorizationPendingIntent() {
        _authState.update { it.copy(authorizationPendingIntent = null) }
    }

    // Schicht-Konfiguration Methoden
    fun toggleAutoAlarm() {
        viewModelScope.launch {
            val newState = !_authState.value.autoAlarmEnabled
            shiftConfigRepository.saveAutoAlarmEnabled(newState)
            Timber.d("Auto alarm toggled to: $newState")
        }
    }
    
    fun updateShiftDefinition(definition: com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftDefinition) {
        viewModelScope.launch {
            val currentDefinitions = _authState.value.shiftDefinitions.toMutableList()
            val index = currentDefinitions.indexOfFirst { it.id == definition.id }
            
            if (index >= 0) {
                currentDefinitions[index] = definition
            } else {
                currentDefinitions.add(definition)
            }
            
            shiftConfigRepository.saveShiftDefinitions(currentDefinitions)
            Timber.d("Updated shift definition: ${definition.name}")
            
            // Refresh shift recognition if calendar events are loaded
            val selectedCalendarId = persistedCalendarId.first()
            if (selectedCalendarId.isNotBlank() && _authState.value.accessToken != null) {
                loadCalendarEvents(selectedCalendarId)
            }
        }
    }
    
    fun deleteShiftDefinition(definitionId: String) {
        viewModelScope.launch {
            val currentDefinitions = _authState.value.shiftDefinitions.toMutableList()
            currentDefinitions.removeAll { it.id == definitionId }
            shiftConfigRepository.saveShiftDefinitions(currentDefinitions)
            Timber.d("Deleted shift definition: $definitionId")
            
            // Refresh shift recognition if calendar events are loaded
            val selectedCalendarId = persistedCalendarId.first()
            if (selectedCalendarId.isNotBlank() && _authState.value.accessToken != null) {
                loadCalendarEvents(selectedCalendarId)
            }
        }
    }
    
    fun resetShiftConfigToDefaults() {
        viewModelScope.launch {
            shiftConfigRepository.resetToDefaults()
            Timber.i("Shift configuration reset to defaults")
            
            // Refresh shift recognition if calendar events are loaded
            val selectedCalendarId = persistedCalendarId.first()
            if (selectedCalendarId.isNotBlank() && _authState.value.accessToken != null) {
                loadCalendarEvents(selectedCalendarId)
            }
        }
    }
}