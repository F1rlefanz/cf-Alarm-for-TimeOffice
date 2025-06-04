package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.CalendarSelectionScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.LoginScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.ShiftConfigScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.*
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

    // Launcher für Notification-Berechtigung
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            Timber.d("Notification permission result: $isGranted")
            if (!isGranted) {
                Toast.makeText(this, "Benachrichtigungsberechtigung ist für Alarme erforderlich!", Toast.LENGTH_LONG).show()
            }
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

        // Prüfe und fordere Notification-Berechtigung an
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            CFAlarmForTimeOfficeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by authViewModel.authState.collectAsState()

                    if (authState.isLoading && !authState.isSignedIn) {
                        LoadingScreen()
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
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Lade Anmeldestatus...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CF-Alarm",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Willkommen, ${authState.userName ?: authState.userEmailOrId ?: "Nutzer"}!",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Next Shift Alarm Info
        authState.nextShiftAlarm?.let { nextShift ->
            item {
                InfoCard(
                    title = "Nächster Wecker",
                    content = {
                        Text(
                            text = "Schicht: ${nextShift.shiftDefinition.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Termin: ${nextShift.calendarEventTitle}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Weckzeit: ${nextShift.calculatedAlarmTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    icon = Icons.Filled.Schedule
                )
            }
        }
        
        // System Alarm Status
        if (authState.autoAlarmEnabled) {
            item {
                StatusCard(
                    title = "System-Alarm",
                    subtitle = authState.alarmStatusMessage ?: if (authState.systemAlarmSet) "Aktiv" else "Nicht gesetzt",
                    icon = if (authState.systemAlarmSet) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    isPositive = authState.systemAlarmSet,
                    actionButton = {
                        if (!authState.canScheduleExactAlarms && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            ActionButton(
                                text = "Alarm-Berechtigung erteilen",
                                icon = Icons.Filled.Settings,
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        "package:${activity.packageName}".toUri()
                                    )
                                    activity.startActivity(intent)
                                },
                                variant = ButtonVariant.Error
                            )
                        } else if (authState.systemAlarmSet) {
                            ActionButton(
                                text = "Alarm abbrechen",
                                icon = Icons.Filled.Cancel,
                                onClick = { authViewModel.cancelSystemAlarm() },
                                variant = ButtonVariant.Secondary
                            )
                        }
                    }
                )
            }
        }

        // Error handling cards
        item {
            when {
                !authState.androidCalendarPermissionGranted && authState.showAndroidCalendarPermissionRationale -> {
                    StatusCard(
                        title = "Kalenderberechtigung erforderlich",
                        subtitle = "Diese App benötigt Zugriff auf deine Kalender, um Dienstpläne zu lesen.",
                        icon = Icons.Filled.CalendarMonth,
                        isPositive = false,
                        actionButton = {
                            ActionButton(
                                text = "Kalenderberechtigung erneut anfordern",
                                icon = Icons.Filled.CalendarMonth,
                                onClick = { activity.checkAndRequestCalendarPermission() },
                                variant = ButtonVariant.Error
                            )
                        }
                    )
                }

                !authState.androidCalendarPermissionGranted && authState.calendarPermissionDenied && authState.error?.contains("Android Kalenderberechtigung") == true -> {
                    StatusCard(
                        title = "Kalenderberechtigung verweigert",
                        subtitle = authState.error,
                        icon = Icons.Filled.CalendarMonth,
                        isPositive = false,
                        actionButton = {
                            ActionButton(
                                text = "App-Einstellungen öffnen",
                                icon = Icons.Filled.Settings,
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    val uri = "package:${activity.packageName}".toUri()
                                    intent.data = uri
                                    activity.startActivity(intent)
                                },
                                variant = ButtonVariant.Error
                            )
                        }
                    )
                }

                authState.accessToken.isNullOrBlank() && !authState.calendarsLoading && authState.calendarPermissionDenied -> {
                    StatusCard(
                        title = "Google Kalender Autorisierung fehlgeschlagen",
                        subtitle = authState.error ?: "Autorisierung für Google Kalender fehlgeschlagen.",
                        icon = Icons.Filled.Error,
                        isPositive = false,
                        actionButton = {
                            ActionButton(
                                text = "Google Kalender Autorisierung erneut versuchen",
                                icon = Icons.Filled.Refresh,
                                onClick = { authViewModel.requestCalendarScopes(activity) },
                                variant = ButtonVariant.Error
                            )
                        }
                    )
                }

                authState.error != null && (authState.error.contains("Kalender") || authState.error.contains("Authentifizierung")) -> {
                    StatusCard(
                        title = "Fehler",
                        subtitle = authState.error,
                        icon = Icons.Filled.Error,
                        isPositive = false,
                        actionButton = {
                            ActionButton(
                                text = "Erneut versuchen",
                                icon = Icons.Filled.Refresh,
                                onClick = { authViewModel.retryCalendarAccessOrReAuth(activity) },
                                variant = ButtonVariant.Error
                            )
                        }
                    )
                }
            }
        }

        // Calendar Selection Status
        item {
            StatusCard(
                title = "Dienstplan-Kalender",
                subtitle = if (persistedCalendarId.isNotBlank()) persistedCalendarId else "Kein Kalender ausgewählt",
                icon = Icons.Outlined.CalendarMonth,
                isPositive = persistedCalendarId.isNotBlank()
            )
        }

        // Loading indicator
        if (authState.calendarsLoading) {
            item {
                InfoCard(
                    title = "Lade Kalenderliste...",
                    content = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Kalender werden geladen...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    icon = Icons.Default.Refresh
                )
            }
        }

        // Main action buttons
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "Schicht-Einstellungen",
                    icon = Icons.Filled.Settings,
                    onClick = onShowShiftConfig
                )

                ActionButton(
                    text = if (persistedCalendarId.isBlank()) "Dienstplan-Kalender auswählen" else "Dienstplan-Kalender ändern",
                    icon = Icons.Filled.CalendarMonth,
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
                    variant = ButtonVariant.Secondary
                )
            }
        }

        // Sign out button
        item {
            Spacer(modifier = Modifier.height(32.dp))
            ActionButton(
                text = "Abmelden",
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = onSignOut,
                variant = ButtonVariant.Error
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CFAlarmForTimeOfficeTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "CF-Alarm",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Willkommen, Test Nutzer!",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            item {
                InfoCard(
                    title = "Nächster Wecker",
                    content = {
                        Text("Schicht: Nachtdienst")
                        Text("Termin: IMC Nachtdienst")
                        Text("Weckzeit: 06.06.2025 19:45", fontWeight = FontWeight.Bold)
                    },
                    icon = Icons.Filled.Schedule
                )
            }
            
            item {
                StatusCard(
                    title = "System-Alarm",
                    subtitle = "Alarm gesetzt für 06.06.2025 19:45",
                    icon = Icons.Filled.NotificationsActive,
                    isPositive = true
                )
            }
            
            item {
                ActionButton(
                    text = "Schicht-Einstellungen",
                    icon = Icons.Filled.Settings,
                    onClick = { /* Preview */ }
                )
            }
        }
    }
}
