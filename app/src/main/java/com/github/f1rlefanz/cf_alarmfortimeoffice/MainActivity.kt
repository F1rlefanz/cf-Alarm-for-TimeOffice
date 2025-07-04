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
import com.github.f1rlefanz.cf_alarmfortimeoffice.di.AppContainer
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.LoadingScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.LoginScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.MainScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

class MainActivity : ComponentActivity() {

    // ViewModels mit manueller Injection
    private lateinit var authViewModel: AuthViewModel
    private lateinit var calendarViewModel: CalendarViewModel
    private lateinit var shiftViewModel: ShiftViewModel
    private lateinit var alarmViewModel: AlarmViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var navigationViewModel: NavigationViewModel

    // PERFORMANCE FIX: Deduplication für Calendar Permission Requests
    @Volatile
    private var isPermissionRequestInProgress = false
    @Volatile
    private var lastPermissionCheckTime = 0L

    // Permission Launchers
    private val requestCalendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            isPermissionRequestInProgress = false // RESET permission request flag
            
            if (isGranted) {
                Logger.business(LogTags.PERMISSIONS, "Calendar permission granted")
                // PERFORMANCE FIX: Only load if not already loaded recently
                val calendarState = calendarViewModel.uiState.value
                if (calendarState.availableCalendars.isEmpty() && !calendarState.isLoading) {
                    calendarViewModel.loadAvailableCalendars()
                }
            } else {
                Toast.makeText(this, "Kalenderberechtigung ist erforderlich!", Toast.LENGTH_LONG).show()
            }
        }

    // Launcher für Notification-Berechtigung
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            Logger.business(LogTags.PERMISSIONS, "Notification permission result", "granted: $isGranted")
            if (!isGranted) {
                Toast.makeText(this, "Benachrichtigungsberechtigung ist für Alarme erforderlich!", Toast.LENGTH_LONG).show()
            }
        }

    // Launcher für das Ergebnis des AuthorizationClient Flows (Consent Screen)
    private val authorizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult: ActivityResult ->
            Logger.d(LogTags.AUTH, "Authorization result: ${activityResult.resultCode}")
            handleAuthorizationResult(activityResult)
        }

    // Launcher für Google Sign-In
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    Logger.business(LogTags.AUTH, "Google Sign-In successful", account.email ?: "unknown")
                    authViewModel.handleSignInResult(account, this)
                } catch (e: ApiException) {
                    Logger.e(LogTags.AUTH, "Google Sign-In failed: ${e.statusCode}", e)
                    authViewModel.handleSignInResult(null, this)
                }
            } else {
                Logger.d(LogTags.AUTH, "Google Sign-In cancelled or failed")
                authViewModel.handleSignInResult(null, this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dependency Container abrufen
        val appContainer = (application as CFAlarmApplication).appContainer
        
        // ViewModels mit Factory erstellen
        val viewModelFactory = ViewModelFactory(appContainer)
        authViewModel = ViewModelProvider(this, viewModelFactory)[AuthViewModel::class.java]
        calendarViewModel = ViewModelProvider(this, viewModelFactory)[CalendarViewModel::class.java]
        shiftViewModel = ViewModelProvider(this, viewModelFactory)[ShiftViewModel::class.java]
        alarmViewModel = ViewModelProvider(this, viewModelFactory)[AlarmViewModel::class.java]
        mainViewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
        navigationViewModel = ViewModelProvider(this, viewModelFactory)[NavigationViewModel::class.java]

        // MEMORY LEAK FIX: Event-basiertes System statt Callback
        // Observiere daysAhead-Änderungen über StateFlow statt Memory-Leak Callback
        // Das wird in MainScreen.kt über LaunchedEffect gehandhabt

        // Prüfe Notification-Berechtigung
        checkNotificationPermission()

        setContent {
            CFAlarmForTimeOfficeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // MEMORY LEAK FIX: Consolidated State Collection für MainActivity
                    // Reduziert excessive Recompositions durch weniger collectAsState() calls
                    val authState by authViewModel.uiState.collectAsState()
                    val mainState by mainViewModel.uiState.collectAsState()

                    // PERFORMANCE OPTIMIZATION: Memoized Screen Selection
                    // Verhindert unnötige Recompositions bei State-Changes
                    val screenContent = remember(authState.isSignedIn, authState.calendarOps.calendarsLoading) {
                        when {
                            authState.calendarOps.calendarsLoading && !authState.isSignedIn -> "loading"
                            authState.isSignedIn -> "main"
                            else -> "login"
                        }
                    }

                    when (screenContent) {
                        "loading" -> {
                            LoadingScreen(message = "Lade Anmeldestatus...")
                        }
                        "main" -> {
                            MainScreen(
                                authViewModel = authViewModel,
                                calendarViewModel = calendarViewModel,
                                shiftViewModel = shiftViewModel,
                                alarmViewModel = alarmViewModel,
                                mainViewModel = mainViewModel,
                                navigationViewModel = navigationViewModel,
                                authDataStoreRepository = appContainer.authDataStoreRepository as com.github.f1rlefanz.cf_alarmfortimeoffice.data.AuthDataStoreRepository,
                                onRequestCalendarPermission = { checkCalendarPermission() }
                            )
                        }
                        "login" -> {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onSignIn = { launchSignIn() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun checkCalendarPermission() {
        // PERFORMANCE FIX: Deduplication für mehrfache Permission-Checks
        val currentTime = System.currentTimeMillis()
        val timeSinceLastCheck = currentTime - lastPermissionCheckTime
        
        if (isPermissionRequestInProgress || timeSinceLastCheck < 2000) {
            Logger.d(LogTags.PERMISSIONS, "Permission check throttled - in progress: $isPermissionRequestInProgress, time since last: ${timeSinceLastCheck}ms")
            return
        }
        
        lastPermissionCheckTime = currentTime
        
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED -> {
                Logger.business(LogTags.PERMISSIONS, "Calendar permission already granted")
                // PERFORMANCE FIX: Only load if not already loaded and not loading
                val calendarState = calendarViewModel.uiState.value
                if (calendarState.availableCalendars.isEmpty() && !calendarState.isLoading) {
                    calendarViewModel.loadAvailableCalendars()
                }
            }
            else -> {
                Logger.d(LogTags.PERMISSIONS, "Requesting calendar permission")
                isPermissionRequestInProgress = true
                requestCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        }
    }

    private fun launchSignIn() {
        Logger.business(LogTags.AUTH, "Starting Google Sign-In")
        
        // Get Google Sign-In options from CalendarAuthUseCase
        val appContainer = (application as CFAlarmApplication).appContainer
        val calendarAuthUseCase = appContainer.calendarAuthUseCase as com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.CalendarAuthUseCase
        val googleSignInOptions = calendarAuthUseCase.getGoogleSignInOptions()
        
        // Create GoogleSignInClient and start sign-in
        val googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    fun launchAuthorizationIntent(pendingIntent: PendingIntent) {
        Logger.d(LogTags.AUTH, "Starting Authorization PendingIntent")
        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
        authorizationLauncher.launch(intentSenderRequest)
    }

    private fun handleAuthorizationResult(result: ActivityResult) {
        // Authorization Result verarbeiten
        if (result.resultCode == RESULT_OK) {
            Logger.business(LogTags.AUTH, "Authorization successful")
            // Kalender neu laden nach erfolgreicher Autorisierung
            calendarViewModel.loadAvailableCalendars()
        } else {
            Logger.w(LogTags.AUTH, "Authorization failed or cancelled")
        }
    }
}