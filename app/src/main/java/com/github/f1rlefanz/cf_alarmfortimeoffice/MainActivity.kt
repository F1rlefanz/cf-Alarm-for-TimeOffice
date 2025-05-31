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
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.CalendarSelectionScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.LoginScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.ShiftConfigScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModelFactory
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private lateinit var credentialAuthManager: CredentialAuthManager
    internal lateinit var authViewModel: AuthViewModel

    // Launcher für die Android Kalender-Berechtigung
    private val requestCalendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            authViewModel.onAndroidCalendarPermissionResult(
                isGranted = isGranted,
                showRationaleIfDenied = !isGranted && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
            )
        }

    // Launcher für das Ergebnis des AuthorizationClient Flows (Consent Screen)
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
                            activity = this@MainActivity
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
                authViewModel.onAndroidCalendarPermissionResult(
                    isGranted = false,
                    showRationaleIfDenied = true
                )
            }

            else -> {
                Timber.d("MainActivity: Fordere Kalenderberechtigung an...")
                requestCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        }
    }

    fun launchAuthorizationIntent(pendingIntent: PendingIntent) {
        Timber.d("MainActivity: Starte Authorization PendingIntent...")
        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
        authorizationLauncher.launch(intentSenderRequest)
        authViewModel.clearAuthorizationPendingIntent()
    }
}

@Composable
fun MainAppScreen(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    activity: MainActivity
) {
    val authState by authViewModel.authState.collectAsState()
    val calendars by authViewModel.calendars.collectAsState()
    val temporarilySelectedCalendarId by authViewModel.temporarilySelectedCalendarId.collectAsState()
    val persistedCalendarId by authViewModel.persistedCalendarId.collectAsState("")

    var showCalendarSelectionScreen by rememberSaveable { mutableStateOf(false) }
    var showShiftConfigScreen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Effekt für Berechtigungsanfragen
    LaunchedEffect(
        authState.isSignedIn,
        authState.androidCalendarPermissionGranted,
        authState.accessToken
    ) {
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

    // Kalender Auswahl Effekt
    LaunchedEffect(showCalendarSelectionScreen, persistedCalendarId) {
        if (showCalendarSelectionScreen) {
            authViewModel.onCalendarTemporarilySelected(persistedCalendarId)
        }
    }

    // Conditionally show different screens
    when {
        showShiftConfigScreen -> {
            ShiftConfigScreen(
                authState = authState,
                onToggleAutoAlarm = { authViewModel.toggleAutoAlarm() },
                onUpdateShiftDefinition = { authViewModel.updateShiftDefinition(it) },
                onDeleteShiftDefinition = { authViewModel.deleteShiftDefinition(it) },
                onResetToDefaults = { authViewModel.resetShiftConfigToDefaults() },
                onNavigateBack = { showShiftConfigScreen = false }
            )
        }

        showCalendarSelectionScreen && authState.androidCalendarPermissionGranted && !authState.accessToken.isNullOrBlank() -> {
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
                        Toast.makeText(context, "Kalenderauswahl gespeichert!", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(
                            context,
                            "Bitte einen Kalender auswählen.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onCancelClicked = {
                    authViewModel.clearTemporaryCalendarSelection()
                    showCalendarSelectionScreen = false
                },
                isLoading = authState.calendarsLoading
            )
        }

        else -> {
            MainContent(
                authState = authState,
                persistedCalendarId = persistedCalendarId,
                calendars = calendars,
                authViewModel = authViewModel,
                activity = activity,
                onSignOut = onSignOut,
                onShowShiftConfig = { showShiftConfigScreen = true },
                onShowCalendarSelection = { showCalendarSelectionScreen = true }
            )
        }
    }
}

@Composable
private fun MainContent(
    authState: com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthState,
    persistedCalendarId: String,
    calendars: List<com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarItem>,
    authViewModel: AuthViewModel,
    activity: MainActivity,
    onSignOut: () -> Unit,
    onShowShiftConfig: () -> Unit,
    onShowCalendarSelection: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Willkommen, ${authState.userName ?: authState.userEmailOrId ?: "Nutzer"}!")

        // Next Shift Alarm Info
        authState.nextShiftAlarm?.let { nextShift ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Nächster Wecker:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Schicht: ${nextShift.shiftDefinition.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Termin: ${nextShift.calendarEventTitle}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Weckzeit: ${nextShift.calculatedAlarmTime}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // System Alarm Status
        if (authState.autoAlarmEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (authState.systemAlarmSet) 
                        MaterialTheme.colorScheme.secondaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (authState.systemAlarmSet) "🔔" else "⚠️",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "System-Alarm:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        authState.alarmStatusMessage ?: if (authState.systemAlarmSet) "Aktiv" else "Nicht gesetzt",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (!authState.canScheduleExactAlarms && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    android.net.Uri.parse("package:${activity.packageName}")
                                )
                                activity.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Alarm-Berechtigung erteilen")
                        }
                    }
                    
                    if (authState.systemAlarmSet) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { authViewModel.cancelSystemAlarm() }
                        ) {
                            Text("Alarm abbrechen")
                        }
                    }
                }
            }
        }

        // Error handling UI
        if (authState.isSignedIn) {
            val currentError = authState.error

            when {
                !authState.androidCalendarPermissionGranted && authState.showAndroidCalendarPermissionRationale -> {
                    ErrorCard(
                        title = "Kalenderberechtigung erforderlich",
                        message = "Diese App benötigt Zugriff auf deine Kalender, um Dienstpläne zu lesen. Bitte erteile die Berechtigung.",
                        buttonText = "Kalenderberechtigung erneut anfordern",
                        onButtonClick = { activity.checkAndRequestCalendarPermission() }
                    )
                }

                !authState.androidCalendarPermissionGranted && authState.calendarPermissionDenied && currentError?.contains(
                    "Android Kalenderberechtigung"
                ) == true -> {
                    ErrorCard(
                        title = "Kalenderberechtigung verweigert",
                        message = currentError,
                        buttonText = "App-Einstellungen öffnen",
                        onButtonClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", activity.packageName, null)
                            intent.data = uri
                            activity.startActivity(intent)
                        }
                    )
                }

                authState.accessToken.isNullOrBlank() && !authState.calendarsLoading && authState.calendarPermissionDenied -> {
                    ErrorCard(
                        title = "Google Kalender Autorisierung fehlgeschlagen",
                        message = currentError
                            ?: "Autorisierung für Google Kalender fehlgeschlagen.",
                        buttonText = "Google Kalender Autorisierung erneut versuchen",
                        onButtonClick = { authViewModel.requestCalendarScopes(activity) }
                    )
                }

                currentError != null && (currentError.contains("Kalender") || currentError.contains(
                    "Authentifizierung"
                )) -> {
                    ErrorCard(
                        title = "Fehler",
                        message = currentError,
                        buttonText = "Erneut versuchen",
                        onButtonClick = { authViewModel.retryCalendarAccessOrReAuth(activity) }
                    )
                }
            }
        }

        Text("Ausgewählter Dienstplan: ${persistedCalendarId.ifBlank { "Keiner" }}")

        if (authState.calendarsLoading) {
            CircularProgressIndicator()
            Text("Lade Kalenderliste...")
        }

        // Main action buttons
        Button(
            onClick = onShowShiftConfig,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Schicht-Einstellungen")
        }

        Button(
            onClick = {
                if (authState.isSignedIn) {
                    authViewModel.triggerCalendarAccess(activity)
                    if (calendars.isNotEmpty() || authState.calendarsLoading) {
                        authViewModel.onCalendarTemporarilySelected(persistedCalendarId)
                        onShowCalendarSelection()
                        Timber.d("MainContent: 'Kalender auswählen' geklickt. Zeige Auswahl.")
                    } else {
                        Timber.d("MainContent: 'Kalender auswählen' geklickt, Flow wurde getriggert.")
                    }
                } else {
                    Toast.makeText(context, "Bitte zuerst anmelden.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (persistedCalendarId.isBlank()) "Dienstplan-Kalender auswählen" else "Dienstplan-Kalender ändern")
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abmelden")
        }
    }
}

@Composable
private fun ErrorCard(
    title: String,
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(buttonText)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CFAlarmForTimeOfficeTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Willkommen, Test Nutzer!")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* Preview Klick */ }) {
                Text("Schicht-Einstellungen")
            }
            Spacer(modifier = Modifier.height(8.dp))
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
