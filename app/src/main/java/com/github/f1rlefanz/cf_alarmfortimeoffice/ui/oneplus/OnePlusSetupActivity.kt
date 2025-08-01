package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.f1rlefanz.cf_alarmfortimeoffice.BuildConfig
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.BatteryOptimizationManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.EnhancedOnePlusConfigStatus
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.validation.OnePlusValidationManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.validation.OnePlusValidationTester
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 🚀 ENHANCED: OnePlus Interactive Setup Activity with Device Validator
 * 
 * Modern MVVM-based OnePlus setup activity that follows Clean Architecture principles.
 * Provides step-by-step guidance for OnePlus users to configure their device for 
 * maximum alarm reliability using enhanced validation.
 * 
 * Features:
 * - Enhanced OnePlus device detection and validation
 * - Real-time configuration status with research-based insights
 * - Interactive step-by-step guidance with alternative paths
 * - Advanced reliability estimation and progress tracking
 * - Device-specific configuration recommendations
 * - Debug tools for development and troubleshooting
 */
class OnePlusSetupActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_FROM_ALARM_FAILURE = "from_alarm_failure"
        const val EXTRA_FIRST_TIME_SETUP = "first_time_setup"
        const val EXTRA_MANUAL_LAUNCH = "manual_launch"
        const val EXTRA_SHOW_DEBUG_INFO = "show_debug_info"
    }
    
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var validationManager: OnePlusValidationManager
    private var validationTester: OnePlusValidationTester? = null
    
    private lateinit var uiBuilder: OnePlusSetupUIBuilder
    
    // OnBackPressed Callback for modern Android
    private lateinit var backPressedCallback: OnBackPressedCallback
    
    // Setup Context
    private var isFirstTimeSetup = false
    private var isFromAlarmFailure = false
    private var isManualLaunch = false
    private var showDebugInfo = false
    
    // Configuration State
    private var currentConfigStatus: EnhancedOnePlusConfigStatus? = null
    private var isUIInitialized = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.business(LogTags.ALARM, "🔴 Enhanced OnePlus Setup Activity starting for ${Build.MODEL}")
        
        // Initialize managers
        initializeManagers()
        
        // Extract intent extras
        extractIntentExtras()
        
        // Initialize UI builder
        uiBuilder = OnePlusSetupUIBuilder(this, isFirstTimeSetup, isFromAlarmFailure, showDebugInfo)
        
        // Setup modern back button handling
        setupBackPressedHandler()
        
        // Validate OnePlus device asynchronously
        lifecycleScope.launch {
            validateOnePlusDevice()
        }
    }
    
    private fun setupBackPressedHandler() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Show confirmation dialog
                androidx.appcompat.app.AlertDialog.Builder(this@OnePlusSetupActivity)
                    .setTitle("OnePlus-Optimierung verlassen?")
                    .setMessage("Ohne diese Konfiguration können Alarme auf OnePlus-Geräten unzuverlässig sein.")
                    .setPositiveButton("Trotzdem verlassen") { _, _ ->
                        finishSetup(false)
                    }
                    .setNegativeButton("Weiter konfigurieren") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }
    
    private fun initializeManagers() {
        batteryOptimizationManager = BatteryOptimizationManager(this)
        validationManager = OnePlusValidationManager(this)
        
        // Initialize debug tester if needed
        if (BuildConfig.DEBUG) {
            validationTester = OnePlusValidationTester(this)
        }
    }
    
    private fun extractIntentExtras() {
        isFirstTimeSetup = intent.getBooleanExtra(EXTRA_FIRST_TIME_SETUP, false)
        isFromAlarmFailure = intent.getBooleanExtra(EXTRA_FROM_ALARM_FAILURE, false)
        isManualLaunch = intent.getBooleanExtra(EXTRA_MANUAL_LAUNCH, false)
        showDebugInfo = intent.getBooleanExtra(EXTRA_SHOW_DEBUG_INFO, false) || BuildConfig.DEBUG
        
        Logger.business(
            LogTags.ALARM, 
            "🔴 Enhanced OnePlus Setup Context", 
            "FirstTime: $isFirstTimeSetup, FromFailure: $isFromAlarmFailure, Manual: $isManualLaunch, Debug: $showDebugInfo"
        )
    }
    
    private suspend fun validateOnePlusDevice() {
        try {
            val isValidOnePlus = validationManager.isOnePlusDevice()
            if (!isValidOnePlus) {
                Logger.w(LogTags.ALARM, "⚠️ Enhanced OnePlus Setup opened on non-OnePlus device")
                finish()
                return
            }
            
            setupUI()
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ CRITICAL: OnePlus device validation failed", e)
            finish()
        }
    }
    
    private fun setupUI() {
        try {
            val rootView = uiBuilder.createEnhancedOnePlusUI(
                onFinishSetup = ::finishSetup,
                onShowDebugDialog = ::showDebugDialog
            )
            setContentView(rootView)
            isUIInitialized = true
            Logger.d(LogTags.ALARM, "✅ OnePlus UI successfully initialized")
            
            // Nach UI-Initialisierung können wir die Configuration laden
            loadConfiguration()
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ CRITICAL: Failed to setup OnePlus UI", e)
            // Fallback: Zeige einfache Error-Activity oder beende Activity
            finish()
        }
    }
    
    private fun loadConfiguration() {
        lifecycleScope.launch {
            try {
                Logger.d(LogTags.ALARM, "Loading enhanced OnePlus configuration...")
                
                // Kritische Sicherheitsprüfung: UI muss vollständig initialisiert sein
                if (!isUIInitialized) {
                    Logger.w(LogTags.ALARM, "⚠️ RACE CONDITION PREVENTED: UI not initialized, postponing configuration load")
                    return@launch
                }
                
                // Zusätzliche Prüfung: uiBuilder muss initialisiert sein
                if (!::uiBuilder.isInitialized) {
                    Logger.e(LogTags.ALARM, "❌ CRITICAL: uiBuilder not initialized, cannot proceed with configuration load")
                    return@launch
                }
                
                // Show loading state (defensive)
                try {
                    uiBuilder.updateLoadingState(true)
                } catch (e: Exception) {
                    Logger.e(LogTags.ALARM, "❌ Failed to show loading state", e)
                    // Continue anyway - loading state is not critical
                }
                
                // Get enhanced configuration status
                val configStatus = batteryOptimizationManager.getEnhancedOnePlusConfigurationStatus()
                
                if (configStatus == null) {
                    Logger.e(LogTags.ALARM, "❌ Failed to load enhanced OnePlus configuration - null response")
                    try {
                        uiBuilder.showErrorState("Fehler beim Laden der OnePlus-Konfiguration")
                    } catch (e: Exception) {
                        Logger.e(LogTags.ALARM, "❌ Failed to show error state", e)
                    }
                    return@launch
                }
                
                currentConfigStatus = configStatus
                
                // Update UI with enhanced data (defensive)
                try {
                    uiBuilder.updateConfigurationDisplay(configStatus, 
                        onStepAction = ::handleStepAction,
                        onStepConfirmation = ::handleStepConfirmation,
                        onStepHelp = ::handleStepHelp
                    )
                } catch (e: Exception) {
                    Logger.e(LogTags.ALARM, "❌ Failed to update configuration display", e)
                    try {
                        uiBuilder.showErrorState("UI-Update fehlgeschlagen: ${e.message}")
                    } catch (uiException: Exception) {
                        Logger.e(LogTags.ALARM, "❌ Failed to show UI error state", uiException)
                    }
                    return@launch
                }
                
                // Hide loading state (defensive)
                try {
                    uiBuilder.updateLoadingState(false)
                } catch (e: Exception) {
                    Logger.e(LogTags.ALARM, "❌ Failed to hide loading state", e)
                    // Continue anyway - this is not critical
                }
                
                Logger.business(
                    LogTags.ALARM, 
                    "📊 Enhanced OnePlus Configuration Status", 
                    "Confidence: ${String.format(Locale.US, "%.1f%%", configStatus.validationConfidence * 100)}, " +
                    "Reliability: ${configStatus.estimatedReliability.currentReliability}%, " +
                    "Steps: ${configStatus.estimatedReliability.completedSteps}/${configStatus.estimatedReliability.totalSteps}"
                )
                
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "💥 Enhanced OnePlus configuration loading failed", e)
                
                // Defensive error state update
                try {
                    if (::uiBuilder.isInitialized) {
                        uiBuilder.showErrorState("Unerwarteter Fehler: ${e.message}")
                        uiBuilder.updateLoadingState(false)
                    }
                } catch (uiException: Exception) {
                    Logger.e(LogTags.ALARM, "❌ Failed to handle error state in UI", uiException)
                }
            }
        }
    }
    
    private fun handleStepAction(stepId: String) {
        Logger.d(LogTags.ALARM, "Handling step action for: $stepId")
        
        try {
            val intent = batteryOptimizationManager.createOnePlusSetupIntent(stepId)
            
            if (intent != null) {
                startActivity(intent)
                Logger.business(LogTags.ALARM, "📱 Opened enhanced OnePlus settings for step: $stepId")
            } else {
                Logger.w(LogTags.ALARM, "❌ Failed to create intent for step: $stepId")
                // Show help dialog as fallback
                currentConfigStatus?.configurationSteps?.find { it.id.name.lowercase() == stepId }?.let { step ->
                    handleStepHelp(step)
                }
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Failed to handle step action for $stepId", e)
            currentConfigStatus?.configurationSteps?.find { it.id.name.lowercase() == stepId }?.let { step ->
                handleStepHelp(step)
            }
        }
    }
    
    private fun handleStepConfirmation(stepId: String) {
        Logger.d(LogTags.ALARM, "User confirmed completion of step: $stepId")
        
        // TODO: Record user confirmation in persistent storage
        // For now, just show feedback and refresh
        uiBuilder.showStepConfirmationFeedback()
        
        // Refresh configuration to update UI
        loadConfiguration()
    }
    
    private fun handleStepHelp(step: com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.OnePlusConfigurationStep) {
        Logger.d(LogTags.ALARM, "Showing help for step: ${step.id}")
        
        val helpDialog = OnePlusStepHelpDialog(this, step)
        helpDialog.show(
            onSettingsAction = { handleStepAction(step.id.name.lowercase()) }
        )
    }
    
    private fun showDebugDialog() {
        lifecycleScope.launch {
            try {
                val testReport = validationTester?.generateTestReport() ?: "Debug Tester nicht verfügbar"
                
                androidx.appcompat.app.AlertDialog.Builder(this@OnePlusSetupActivity)
                    .setTitle("🧪 OnePlus Validation Debug")
                    .setMessage(testReport)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setNeutralButton("Run Tests") { dialog, _ ->
                        dialog.dismiss()
                        runValidationTests()
                    }
                    .create()
                    .show()
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "Debug dialog error", e)
            }
        }
    }
    
    private fun runValidationTests() {
        lifecycleScope.launch {
            try {
                val testResult = validationTester?.runValidationTestSuite()
                val summary = testResult?.summary ?: "Test nicht verfügbar"
                
                Logger.business(LogTags.ALARM, "🧪 Validation tests completed: $summary")
                
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "Test error", e)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Robuste Lifecycle-Behandlung mit mehrfachen Sicherheitsprüfungen
        Logger.d(LogTags.ALARM, "OnePlusSetupActivity onResume() - UI initialized: $isUIInitialized")
        
        // Nur Configuration laden, wenn UI bereits vollständig initialisiert ist
        // Verhindert Race Condition zwischen onCreate() und onResume()
        if (isUIInitialized && ::uiBuilder.isInitialized) {
            // Refresh enhanced configuration status when returning from settings
            loadConfiguration()
            Logger.d(LogTags.ALARM, "♻️ Enhanced OnePlus setup refreshed on resume")
        } else {
            Logger.d(LogTags.ALARM, "⏳ Skipping configuration load in onResume() - prerequisites not met (UI: $isUIInitialized, Builder: ${::uiBuilder.isInitialized})")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        validationManager.cleanup()
        validationTester?.cleanup()
    }
    
    private fun finishSetup(completed: Boolean) {
        val message = when {
            completed && isFirstTimeSetup -> "Willkommen bei CF-Alarm! OnePlus-Optimierung abgeschlossen! 🎉"
            completed -> "OnePlus-Optimierung abgeschlossen! 🎉"
            isFromAlarmFailure -> "Alarm-Problem weiterhin vorhanden. Du kannst die Optimierung jederzeit in den Einstellungen nachholen."
            else -> "Du kannst die Optimierung später in den Einstellungen vornehmen"
        }
        
        // Show message to user via Toast
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        
        if (completed) {
            Logger.business(LogTags.ALARM, "✅ OnePlus setup completed by user (Context: FirstTime=$isFirstTimeSetup, FromFailure=$isFromAlarmFailure)")
        } else {
            Logger.w(LogTags.ALARM, "⚠️ OnePlus setup skipped by user (Context: FirstTime=$isFirstTimeSetup, FromFailure=$isFromAlarmFailure)")
        }
        
        // Return result to calling activity
        val resultIntent = Intent().apply {
            putExtra("setup_completed", completed)
            putExtra("from_oneplus_setup", true)
            putExtra("first_time_setup", isFirstTimeSetup)
            putExtra("from_alarm_failure", isFromAlarmFailure)
        }
        setResult(if (completed) RESULT_OK else RESULT_CANCELED, resultIntent)
        
        finish()
    }
}