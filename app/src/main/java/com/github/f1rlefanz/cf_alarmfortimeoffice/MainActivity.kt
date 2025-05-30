package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult // Für den neuen Launcher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.CalendarSelectionScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.LoginScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModelFactory
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private lateinit var credentialAuthManager: CredentialAuthManager
    internal lateinit var authViewModel: AuthViewModel // internal für Zugriff aus checkAndRequest...

    // Launcher für die Android Kalender-Berechtigung
    private val requestCalendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            authViewModel.onAndroidCalendarPermissionResult(
                isGranted = isGranted,
                showRationaleIfDenied = !isGranted && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
            )
        }

    // NEU: Launcher für das Ergebnis des AuthorizationClient Flows (Consent Screen)
    private val authorizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult: ActivityResult ->
            Timber.d("AuthorizationClient: Ergebnis vom IntentSender erhalten. ResultCode: ${activityResult.resultCode}")
            authViewModel.handleAuthorizationResult(activityResult, this)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        credentialAuthManager = CredentialAuthManager(this)
        authViewModel = ViewModelProvider(
            this,
            AuthViewModelFactory(application, credentialAuthManager)
        )[AuthViewModel::class.java]

        setContent {
            CFAlarmForTimeOfficeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by authViewModel.authState.collectAsState()

                    if (authState.isLoading && !authState.isSignedIn) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Text("Lade Anmeldestatus...")
                        }
                    } else if (authState.isSignedIn) {
                        MainAppScreen(
                            authViewModel = authViewModel,
                            onSignOut = { authViewModel.signOut() },
                            activity = this // MainActivity-Instanz übergeben
                        )
                    } else {
                        LoginScreen(authViewModel = authViewModel)
                    }
                }
            }
        }
    }

    fun checkAndRequestCalendarPermission() {
        Timber.d("MainActivity: checkAndRequestCalendarPermission aufgerufen")
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED -> {
                Timber.d("MainActivity: Kalenderberechtigung bereits erteilt.")
                authViewModel.onAndroidCalendarPermissionResult(isGranted = true)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) -> {
                Timber.d("MainActivity: Erklärung für Kalenderberechtigung sollte angezeigt werden.")
                authViewModel.onAndroidCalendarPermissionResult(isGranted = false, showRationaleIfDenied = true)
            }
            else -> {
                Timber.d("MainActivity: Fordere Kalenderberechtigung an...")
                requestCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        }
    }

    // NEU: Methode zum Starten des Authorization PendingIntents
    fun launchAuthorizationIntent(pendingIntent: PendingIntent) {
        Timber.d("MainActivity: Starte Authorization PendingIntent...")
        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
        authorizationLauncher.launch(intentSenderRequest)
        authViewModel.clearAuthorizationPendingIntent() // Intent als gestartet markieren
    }
}

@Composable
fun MainAppScreen(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    activity: MainActivity // MainActivity Instanz für Berechtigungsanfragen
) {
    val authState by authViewModel.authState.collectAsState()
    val calendars by authViewModel.calendars.collectAsState()
    val temporarilySelectedCalendarId by authViewModel.temporarilySelectedCalendarId.collectAsState()
    val persistedCalendarId by authViewModel.persistedCalendarId.collectAsState("")

    var showCalendarSelectionScreen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Effekt, um Berechtigung bei erstem Erscheinen anzufragen oder Authorization Flow zu starten
    LaunchedEffect(authState.isSignedIn, authState.androidCalendarPermissionGranted, authState.accessToken) {
        if (authState.isSignedIn && !authState.userEmailOrId.isNullOrBlank()) {
            if (!authState.androidCalendarPermissionGranted && !authState.showAndroidCalendarPermissionRationale && !authState.calendarPermissionDenied) {
                Timber.d("MainAppScreen Effect: SignedIn, Android-Berechtigung fehlt. Fordere an.")
                activity.checkAndRequestCalendarPermission()
            } else if (authState.androidCalendarPermissionGranted && authState.accessToken.isNullOrBlank() && !authState.calendarsLoading && !authState.calendarPermissionDenied) {
                Timber.d("MainAppScreen Effect: Android-Berechtigung vorhanden, AccessToken fehlt. Fordere Google API Auth an.")
                authViewModel.requestCalendarScopes(activity)
            }
        }
    }

    // Effekt zum Starten des Authorization PendingIntents
    LaunchedEffect(authState.authorizationPendingIntent) {
        authState.authorizationPendingIntent?.let { pendingIntent ->
            Timber.d("MainAppScreen Effect: authorizationPendingIntent vorhanden. Starte Launcher.")
            activity.launchAuthorizationIntent(pendingIntent)
        }
    }

    LaunchedEffect(showCalendarSelectionScreen, persistedCalendarId) {
        if (showCalendarSelectionScreen) {
            authViewModel.onCalendarTemporarilySelected(persistedCalendarId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Willkommen, ${authState.userName ?: authState.userEmailOrId ?: "Nutzer"}!")
        Spacer(modifier = Modifier.height(16.dp))

        // --- UI für Berechtigungs-Workflow und Fehler ---
        if (authState.isSignedIn) {
            // KORREKTUR 1 (Smart Cast): Wert von error in lokaler Variable speichern
            val currentError = authState.error
            if (!authState.androidCalendarPermissionGranted) {
                if (authState.showAndroidCalendarPermissionRationale) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            "Diese App benötigt Zugriff auf deine Kalender, um Dienstpläne zu lesen. Bitte erteile die Berechtigung.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(onClick = { activity.checkAndRequestCalendarPermission() }) {
                            Text("Kalenderberechtigung erneut anfordern")
                        }
                    }
                } else if (authState.calendarPermissionDenied && currentError?.contains("Android Kalenderberechtigung") == true) {// KORREKTUR 1 HIER ANGEWENDET
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            currentError ?: "Zugriff auf Kalender verweigert. Bitte erteile die Berechtigung in den App-Einstellungen.", // KORREKTUR 1 HIER ANGEWENDET
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", activity.packageName, null)
                            intent.data = uri
                            activity.startActivity(intent)
                        }) {
                            Text("App-Einstellungen öffnen")
                        }
                    }
                }
            } else if (authState.accessToken.isNullOrBlank() && !authState.calendarsLoading && authState.calendarPermissionDenied) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        currentError ?: "Autorisierung für Google Kalender fehlgeschlagen.", // KORREKTUR 1 HIER ANGEWENDET
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(onClick = { authViewModel.requestCalendarScopes(activity) }) {
                        Text("Google Kalender Autorisierung erneut versuchen")
                    }
                }
            } else if (currentError != null && (currentError.contains("Kalender") || currentError.contains("Authentifizierung"))) { // KORREKTUR 1 HIER ANGEWENDET
                Text("Fehler: $currentError", color = MaterialTheme.colorScheme.error) // KORREKTUR 1 HIER ANGEWENDET
                Button(onClick = { authViewModel.retryCalendarAccessOrReAuth(activity) }) {
                    Text("Erneut versuchen")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Text("Ausgewählter Dienstplan: ${persistedCalendarId.ifBlank { "Keiner" }}")
        Spacer(modifier = Modifier.height(16.dp))

        if (authState.calendarsLoading && !showCalendarSelectionScreen) {
            CircularProgressIndicator()
            Text("Lade Kalenderliste...")
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(onClick = {
            if (authState.isSignedIn) {
                authViewModel.triggerCalendarAccess(activity)
                if (calendars.isNotEmpty() || authState.calendarsLoading) {
                    authViewModel.onCalendarTemporarilySelected(persistedCalendarId)
                    showCalendarSelectionScreen = true
                    Timber.d("MainAppScreen: 'Kalender auswählen' geklickt. Zeige Auswahl, wenn Kalender vorhanden/laden.")
                } else if (authState.androidCalendarPermissionGranted && authState.accessToken != null) {
                    // KORREKTUR 2: Sichtbarkeit von loadCalendarsUsingToken() im AuthViewModel muss auf internal/public geändert werden.
                    // Annahme: Dies wird durch triggerCalendarAccess() oder requestCalendarScopes() intern gehandhabt,
                    // direkter Aufruf ist hier womöglich nicht mehr nötig oder sollte durch eine öffentliche Funktion im ViewModel erfolgen.
                    // authViewModel.loadCalendarsUsingToken() // Dieser direkte Aufruf war das Problem
                    // Stattdessen wird triggerCalendarAccess oder eine spezifischere Funktion im ViewModel aufgerufen,
                    // die dann intern loadCalendarsUsingToken (wenn es die Bedingungen erfüllt) aufrufen kann.
                    // Da triggerCalendarAccess bereits oben aufgerufen wird, sollte dieser Block ggf. anders gehandhabt werden
                    // oder loadCalendarsUsingToken() wird durch triggerCalendarAccess implizit gecovert.
                    // Der aktuelle Code ruft bereits authViewModel.triggerCalendarAccess(activity) auf,
                    // was den Kalenderlade- oder Auth-Prozess starten sollte.
                    // Die Logik, ob Kalender geladen werden müssen, ist bereits in triggerCalendarAccess.
                    Timber.d("MainAppScreen: 'Kalender auswählen' geklickt. Flow via triggerCalendarAccess gestartet.")
                } else {
                    Timber.d("MainAppScreen: 'Kalender auswählen' geklickt, aber Berechtigungen/Token fehlen noch. Flow wurde getriggert.")
                }
            } else {
                Toast.makeText(context, "Bitte zuerst anmelden.", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text(if (persistedCalendarId.isBlank()) "Dienstplan-Kalender auswählen" else "Dienstplan-Kalender ändern")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onSignOut) {
            Text("Abmelden")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Hier kommt die eigentliche App-Funktionalität hin.")

        if (showCalendarSelectionScreen) {
            if (authState.androidCalendarPermissionGranted && !authState.accessToken.isNullOrBlank()) {
                CalendarSelectionScreen(
                    calendars = calendars,
                    selectedCalendarId = temporarilySelectedCalendarId,
                    onCalendarSelected = { calendarId ->
                        authViewModel.onCalendarTemporarilySelected(calendarId)
                    },
                    onSaveClicked = {
                        if (temporarilySelectedCalendarId.isNotBlank()) {
                            authViewModel.persistSelectedCalendar()
                            showCalendarSelectionScreen = false
                            Toast.makeText(context, "Kalenderauswahl gespeichert!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Bitte einen Kalender auswählen.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancelClicked = {
                        authViewModel.clearTemporaryCalendarSelection()
                        showCalendarSelectionScreen = false
                    },
                    isLoading = authState.calendarsLoading
                )
            } else {
                LaunchedEffect(Unit) { showCalendarSelectionScreen = false }
                Timber.w("Versuch, CalendarSelectionScreen ohne alle Berechtigungen/Token anzuzeigen. Verstecke wieder.")
            }
        }
    }
}

// Preview bleibt gleich
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CFAlarmForTimeOfficeTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Willkommen, Test Nutzer!")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* Preview Klick */ }) {
                Text("Abmelden")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Ausgewählter Dienstplan: Keiner")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* Preview Klick */ }) {
                Text("Dienstplan-Kalender auswählen")
            }
        }
    }
}