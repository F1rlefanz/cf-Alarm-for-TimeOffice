package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.github.f1rlefanz.cf_alarmfortimeoffice.di.AppContainer
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.LoadingScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.LoginScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.MainScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.DebugLogInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.BuildConfig
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus.OnePlusSetupActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.BatteryOptimizationManager

// Firebase Crashlytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MainActivity : ComponentActivity() {

    // Dependency Container
    private lateinit var appContainer: AppContainer
    
    // ViewModels werden by lazy initialisiert für bessere Performance
    private val viewModelFactory by lazy { ViewModelFactory(appContainer) }
    
    private val authViewModel by lazy { 
        ViewModelProvider(this, viewModelFactory)[AuthViewModel::class.java]
    }
    
    private val calendarViewModel by lazy { 
        ViewModelProvider(this, viewModelFactory)[CalendarViewModel::class.java]
    }
    
    private val shiftViewModel by lazy { 
        ViewModelProvider(this, viewModelFactory)[ShiftViewModel::class.java]
    }
    
    private val alarmViewModel by lazy { 
        ViewModelProvider(this, viewModelFactory)[AlarmViewModel::class.java]
    }
    
    private val mainViewModel by lazy { 
        ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
    }
    
    private val navigationViewModel by lazy { 
        ViewModelProvider(this, viewModelFactory)[NavigationViewModel::class.java]
    }

    // PERFORMANCE FIX: Deduplication für Calendar Permission Requests
    @Volatile
    private var isPermissionRequestInProgress = false
    @Volatile
    private var lastPermissionCheckTime = 0L

    // OnePlus Setup Tracking
    private var onePlusSetupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val setupCompleted = result.data?.getBooleanExtra("setup_completed", false) ?: false
        val fromOnePlusSetup = result.data?.getBooleanExtra("from_oneplus_setup", false) ?: false
        
        if (fromOnePlusSetup) {
            Logger.business(
                LogTags.BATTERY_OPTIMIZATION, 
                "🔴 OnePlus Setup Result", 
                "Completed: $setupCompleted, ResultCode: ${result.resultCode}"
            )
            
            // Update preferences to remember setup completion
            getSharedPreferences("oneplus_setup", MODE_PRIVATE).edit {
                putBoolean("setup_completed", setupCompleted)
                putLong("setup_timestamp", System.currentTimeMillis())
            }
                
            if (setupCompleted) {
                Toast.makeText(this, "✅ OnePlus-Optimierung abgeschlossen!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ OnePlus-Optimierung übersprungen - kann später in Einstellungen nachgeholt werden", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Permission Launchers
    private val requestCalendarPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
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
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            Logger.business(LogTags.PERMISSIONS, "Notification permission result", "granted: $isGranted")
            if (!isGranted) {
                Toast.makeText(this, "Benachrichtigungsberechtigung ist für Alarme erforderlich!", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase Crashlytics Setup
        setupFirebaseCrashlytics()

        // Dependency Container abrufen
        appContainer = (application as CFAlarmApplication).appContainer
        
        // CRITICAL DIAGNOSTIC: Check OAuth2 integration after container setup - BACKGROUND
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val diagnosticResults = appContainer.diagnoseOAuth2Integration()
                Logger.business(LogTags.AUTH, "🔍 OAUTH2-DIAGNOSTIC: System integration check:")
                diagnosticResults.split("\n").forEach { line ->
                    Logger.business(LogTags.AUTH, "  $line")
                }
            } catch (e: Exception) {
                Logger.e(LogTags.AUTH, "❌ OAUTH2-DIAGNOSTIC: Failed to run diagnostics", e)
            }
        }

        // Prüfe Notification-Berechtigung
        checkNotificationPermission()
        
        // 🚀 OnePlus Auto-Setup - OPTIMIZED BACKGROUND CHECK
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                checkAndTriggerOnePlusSetup()
            } catch (e: Exception) {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "OnePlus setup check failed", e)
            }
        }
        
        // Debug: Test File-Logging (nur im Debug-Build) - MOVED TO BACKGROUND
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    DebugLogInfo.addTestLogEntries()
                    DebugLogInfo.logFileInfo(this@MainActivity)
                } catch (e: Exception) {
                    Logger.w("MainActivity", "Debug logging failed", e)
                }
            }
        }

        setContent {
            CFAlarmForTimeOfficeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // MEMORY LEAK FIX: Consolidated State Collection für MainActivity
                    // Reduziert excessive Recompositions durch weniger collectAsState() calls
                    val authState by authViewModel.uiState.collectAsState()

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
                                viewModelFactory = viewModelFactory,
                                onRequestCalendarPermission = { checkCalendarPermission() }
                            )
                        }
                        "login" -> {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onSignIn = { 
                                    // MODERN AUTH: Use CredentialAuthManager with context
                                    authViewModel.signIn(this@MainActivity)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Firebase Crashlytics Setup mit Professional Best Practices
     */
    private fun setupFirebaseCrashlytics() {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            
            // User ID setzen (anonymisiert) 
            val userId = android.provider.Settings.Secure.getString(
                contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ).take(8) // Nur ersten 8 Zeichen für Datenschutz
            crashlytics.setUserId("user_$userId")
            
            // Custom Keys für App Context
            crashlytics.setCustomKey("app_version", packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown")
            crashlytics.setCustomKey("build_type", if (BuildConfig.DEBUG) "debug" else "release")
            crashlytics.setCustomKey("target_sdk", applicationInfo.targetSdkVersion)
            
            // Breadcrumb-Log
            crashlytics.log("MainActivity: Firebase Crashlytics initialized")
            
            Logger.business("Crashlytics", "Firebase Crashlytics setup completed with user context")
            
        } catch (e: Exception) {
            Logger.e("Crashlytics", "Failed to setup Crashlytics", e)
        }
    }

    /**
     * Test-Crash für Firebase Crashlytics Verifikation
     * NUR für Development/Testing verwenden!
     */
    fun triggerTestCrash() {
        if (BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().log("Test crash triggered by user")
            throw RuntimeException("Firebase Crashlytics Test Crash - This is intentional for testing!")
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
    
    /**
     * 🚀 NEUE FUNKTION: Automatische OnePlus-Setup-Erkennung und -Start
     * 
     * Überprüft beim ersten App-Start, ob es sich um ein OnePlus-Gerät handelt
     * und startet automatisch die OnePlus-spezifische Konfigurationsanleitung.
     */
    private fun checkAndTriggerOnePlusSetup() {
        // Prüfe, ob es ein OnePlus-Gerät ist
        if (!BatteryOptimizationManager.isOnePlusDevice()) {
            Logger.d(LogTags.BATTERY_OPTIMIZATION, "Not a OnePlus device - skipping auto-setup")
            return
        }
        
        Logger.business(LogTags.BATTERY_OPTIMIZATION, "🔴 OnePlus device detected: ${android.os.Build.MODEL}")
        
        // 🔄 INTEGRATION: OnePlus Configuration Monitoring
        try {
            val batteryManager = BatteryOptimizationManager(this)
            val configChecker = appContainer.onePlusConfigurationChecker
            configChecker.triggerImmediateCheck()
            Logger.business(LogTags.BATTERY_OPTIMIZATION, "✅ OnePlus configuration monitoring activated")
        } catch (e: Exception) {
            Logger.w(LogTags.BATTERY_OPTIMIZATION, "OnePlus configuration monitoring failed", e)
        }
        
        // Prüfe, ob Setup bereits durchgeführt wurde
        val preferences = getSharedPreferences("oneplus_setup", MODE_PRIVATE)
        val setupAlreadyShown = preferences.getBoolean("setup_shown", false)
        val setupCompleted = preferences.getBoolean("setup_completed", false)
        val lastSetupTime = preferences.getLong("setup_timestamp", 0)
        
        // Setup anzeigen, wenn:
        // 1. Noch nie gezeigt ODER
        // 2. Gezeigt aber nicht abgeschlossen UND mehr als 24h her ODER
        // 3. Geringe Zuverlässigkeit erkannt
        val currentTime = System.currentTimeMillis()
        val hoursSinceLastSetup = (currentTime - lastSetupTime) / (1000 * 60 * 60)
        
        val shouldShowSetup = when {
            !setupAlreadyShown -> {
                Logger.business(LogTags.BATTERY_OPTIMIZATION, "🚀 First OnePlus app start - showing setup")
                true
            }
            !setupCompleted && hoursSinceLastSetup > 24 -> {
                Logger.w(LogTags.BATTERY_OPTIMIZATION, "⚠️ OnePlus setup incomplete and 24h+ passed - showing again")
                true
            }
            setupCompleted -> {
                // Prüfe aktuelle Konfiguration auch wenn abgeschlossen
                val batteryManager = BatteryOptimizationManager(this)
                val configStatus = batteryManager.getOnePlusConfigurationStatus()
                val isLowReliability = (configStatus?.estimatedReliability?.currentReliability ?: 100) < 60
                
                if (isLowReliability && hoursSinceLastSetup > 7 * 24) { // 7 Tage
                    Logger.w(LogTags.BATTERY_OPTIMIZATION, "🔴 OnePlus low reliability detected - re-showing setup")
                    true
                } else {
                    Logger.d(LogTags.BATTERY_OPTIMIZATION, "✅ OnePlus setup completed and reliability good")
                    false
                }
            }
            else -> {
                Logger.d(LogTags.BATTERY_OPTIMIZATION, "⏭️ OnePlus setup check: too recent or conditions not met")
                false
            }
        }
        
        if (shouldShowSetup) {
            // Kurze Verzögerung, damit die MainActivity vollständig geladen ist
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1500) // 1.5 Sekunden Verzögerung
                
                try {
                    val setupIntent = android.content.Intent(this@MainActivity, OnePlusSetupActivity::class.java).apply {
                        putExtra(OnePlusSetupActivity.EXTRA_FROM_ALARM_FAILURE, false)
                        putExtra("first_time_setup", !setupAlreadyShown)
                    }
                    
                    onePlusSetupLauncher.launch(setupIntent)
                    
                    // Markiere als gezeigt
                    preferences.edit {
                        putBoolean("setup_shown", true)
                        putLong("last_shown_timestamp", currentTime)
                    }
                        
                    Logger.business(LogTags.BATTERY_OPTIMIZATION, "🚀 OnePlus Setup Activity launched automatically")
                    
                } catch (e: Exception) {
                    Logger.e(LogTags.BATTERY_OPTIMIZATION, "❌ Failed to launch OnePlus setup", e)
                }
            }
        }
    }
    
    /**
     * 🔧 Öffentliche Methode zum manuellen Starten der OnePlus-Konfiguration
     * Kann von anderen Teilen der App aufgerufen werden
     */
    fun launchOnePlusSetupManually() {
        if (BatteryOptimizationManager.isOnePlusDevice()) {
            try {
                val setupIntent = android.content.Intent(this, OnePlusSetupActivity::class.java).apply {
                    putExtra(OnePlusSetupActivity.EXTRA_FROM_ALARM_FAILURE, false)
                    putExtra("manual_launch", true)
                }
                onePlusSetupLauncher.launch(setupIntent)
                
                Logger.business(LogTags.BATTERY_OPTIMIZATION, "🔧 OnePlus Setup launched manually")
            } catch (e: Exception) {
                Logger.e(LogTags.BATTERY_OPTIMIZATION, "❌ Failed to launch OnePlus setup manually", e)
                Toast.makeText(this, "Fehler beim Öffnen der OnePlus-Konfiguration", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "OnePlus-Konfiguration ist nur auf OnePlus-Geräten verfügbar", Toast.LENGTH_SHORT).show()
        }
    }
}