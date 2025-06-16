package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.LoadingScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.LoginScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.MainScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModelFactory
import timber.log.Timber
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import android.app.NotificationManager

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

        // Prüfe und fordere USE_FULL_SCREEN_INTENT Permission an (ab Android 14)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                // Zeige Dialog oder Toast
                Toast.makeText(
                    this,
                    "Bitte erlauben Sie Vollbild-Benachrichtigungen für Alarme in den App-Einstellungen",
                    Toast.LENGTH_LONG
                ).show()

                // Navigiere zu App-Einstellungen
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Zeige einen Dialog oder Toast, um den Nutzer zu informieren
                Toast.makeText(
                    this,
                    "Bitte erlauben Sie exakte Alarme in den App-Einstellungen",
                    Toast.LENGTH_LONG
                ).show()

                // Optional: Direkt zu den Einstellungen navigieren
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    "package:$packageName".toUri()
                )
                startActivity(intent)
            }
        }

        setContent {
            CFAlarmForTimeOfficeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by authViewModel.authState.collectAsState()
                    val needsCalendarScopeRequest by authViewModel.needsCalendarScopeRequest.collectAsState()

                    // React to needsCalendarScopeRequest flag
                    LaunchedEffect(needsCalendarScopeRequest) {
                        if (needsCalendarScopeRequest) {
                            Timber.d("MainActivity: needsCalendarScopeRequest flag detected, triggering Google API authorization...")
                            authViewModel.clearCalendarScopeRequestFlag()
                            authViewModel.continueCalendarAccessAfterPermission(this@MainActivity)
                        }
                    }
                    
                    // Check and load calendar on startup if signed in
                    LaunchedEffect(authState.isSignedIn) {
                        if (authState.isSignedIn) {
                            Timber.d("MainActivity: User is signed in, checking for calendar auto-load")
                            
                            // Check Android permission immediately
                            val hasAndroidPermission = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.READ_CALENDAR
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasAndroidPermission) {
                                // Permission already granted, update state and check calendar
                                authViewModel.onAndroidCalendarPermissionResult(true)
                            }
                            
                            // Always check and load calendar on startup
                            authViewModel.checkAndLoadCalendarOnStartup()
                        }
                    }

                    if (authState.isLoading && !authState.isSignedIn) {
                        LoadingScreen(message = "Lade Anmeldestatus...")
                    } else if (authState.isSignedIn) {
                        MainScreen(
                            authViewModel = authViewModel,
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