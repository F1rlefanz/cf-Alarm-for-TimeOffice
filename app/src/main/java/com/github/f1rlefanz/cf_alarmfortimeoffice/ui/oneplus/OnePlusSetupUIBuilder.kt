package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.util.OnePlusUIConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * UI Builder for OnePlus Setup Activity
 * 
 * Handles all UI creation and updates for the OnePlus setup process.
 * Separates UI concerns from business logic following Clean Architecture principles.
 */
class OnePlusSetupUIBuilder(
    private val context: Context,
    private val isFirstTimeSetup: Boolean,
    private val isFromAlarmFailure: Boolean,
    private val showDebugInfo: Boolean
) {
    
    // UI Components
    private lateinit var reliabilityProgressBar: ProgressBar
    private lateinit var reliabilityText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var deviceInfoLayout: LinearLayout
    private lateinit var stepsContainer: LinearLayout
    private lateinit var warningsContainer: LinearLayout
    private lateinit var debugContainer: LinearLayout
    
    private lateinit var stepCardBuilder: OnePlusStepCardBuilder
    
    fun createEnhancedOnePlusUI(
        onFinishSetup: (Boolean) -> Unit,
        onShowDebugDialog: () -> Unit
    ): ScrollView {
        
        stepCardBuilder = OnePlusStepCardBuilder(context)
        
        // Create programmatic UI for enhanced OnePlus setup
        val rootScrollView = ScrollView(context).apply {
            setPadding(24, 24, 24, 24)
        }
        
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        // Create all UI sections
        val headerLayout = createHeaderSection()
        stepsContainer = createStepsContainer()
        warningsContainer = createWarningsContainer()
        debugContainer = createDebugContainer()
        val buttonLayout = createButtonSection(onFinishSetup, onShowDebugDialog)
        
        // Assemble UI
        mainLayout.addView(headerLayout)
        mainLayout.addView(stepsContainer)
        mainLayout.addView(warningsContainer)
        if (showDebugInfo) {
            mainLayout.addView(debugContainer)
        }
        mainLayout.addView(buttonLayout)
        
        rootScrollView.addView(mainLayout)
        return rootScrollView
    }
    
    private fun createHeaderSection(): LinearLayout {
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 32)
        }
        
        val titleText = TextView(context).apply {
            text = when {
                isFirstTimeSetup -> "🔴 Willkommen bei CF-Alarm!"
                isFromAlarmFailure -> "🔴 OnePlus Alarm-Problem erkannt"
                else -> "🔴 Enhanced OnePlus Alarm-Optimierung"
            }
            textSize = 24f
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val subtitleText = TextView(context).apply {
            text = when {
                isFirstTimeSetup -> "Dein OnePlus ${Build.MODEL} braucht spezielle Konfiguration für 100% zuverlässige Alarme"
                isFromAlarmFailure -> "Wir haben ein Alarm-Problem erkannt. Lass uns das mit enhanced Detection beheben!"
                else -> "Enhanced OnePlus Detection mit research-basierten Konfigurationsempfehlungen"
            }
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        
        // Device Information Section
        deviceInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_light))
        }
        
        // Validation Confidence Section
        val confidenceLabel = TextView(context).apply {
            text = "Validierungs-Zuverlässigkeit:"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 4)
        }
        
        confidenceText = TextView(context).apply {
            text = "Wird validiert..."
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            setPadding(0, 0, 0, 12)
        }
        
        // Reliability Progress Section
        val reliabilityLabel = TextView(context).apply {
            text = "Geschätzte Alarm-Zuverlässigkeit:"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 8)
        }
        
        reliabilityProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            setPadding(0, 0, 0, 8)
        }
        
        reliabilityText = TextView(context).apply {
            text = "Wird berechnet..."
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 24)
        }
        
        // Assemble header
        headerLayout.addView(titleText)
        headerLayout.addView(subtitleText)
        headerLayout.addView(deviceInfoLayout)
        headerLayout.addView(confidenceLabel)
        headerLayout.addView(confidenceText)
        headerLayout.addView(reliabilityLabel)
        headerLayout.addView(reliabilityProgressBar)
        headerLayout.addView(reliabilityText)
        
        return headerLayout
    }
    
    private fun createStepsContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }
    }
    
    private fun createWarningsContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))
        }
    }
    
    private fun createDebugContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
            visibility = if (showDebugInfo) LinearLayout.VISIBLE else LinearLayout.GONE
        }
    }
    
    private fun createButtonSection(
        onFinishSetup: (Boolean) -> Unit,
        onShowDebugDialog: () -> Unit
    ): LinearLayout {
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        
        val continueButton = Button(context).apply {
            text = when {
                isFirstTimeSetup -> "Los geht's! 🚀"
                else -> "Optimierung abgeschlossen"
            }
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(24, 12, 24, 12)
            setOnClickListener { onFinishSetup(true) }
        }
        
        val skipButton = Button(context).apply {
            text = when {
                isFirstTimeSetup -> "Später konfigurieren"
                isFromAlarmFailure -> "Problem ignorieren"
                else -> "Später konfigurieren"
            }
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(24, 12, 24, 12)
            setOnClickListener { onFinishSetup(false) }
        }
        
        val debugButton = Button(context).apply {
            text = "🧪 Debug Info"
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_purple))
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(24, 12, 24, 12)
            visibility = if (showDebugInfo) Button.VISIBLE else Button.GONE
            setOnClickListener { onShowDebugDialog() }
        }
        
        buttonLayout.addView(continueButton)
        buttonLayout.addView(skipButton)
        if (showDebugInfo) {
            buttonLayout.addView(debugButton)
        }
        
        return buttonLayout
    }
    
    fun updateLoadingState(isLoading: Boolean) {
        // Defensive Programmierung: Prüfen ob UI-Komponenten initialisiert sind
        if (!::reliabilityText.isInitialized || !::confidenceText.isInitialized) {
            Logger.w(LogTags.BATTERY_OPTIMIZATION, "⚠️ UI components not initialized in updateLoadingState - skipping UI update")
            return
        }
        
        try {
            if (isLoading) {
                reliabilityText.text = "Analysiere OnePlus-Gerät..."
                confidenceText.text = "Validiere Geräteerkennung..."
                Logger.d(LogTags.BATTERY_OPTIMIZATION, "✅ Loading state updated successfully")
            } else {
                Logger.d(LogTags.BATTERY_OPTIMIZATION, "✅ Loading state cleared successfully")
            }
        } catch (e: Exception) {
            Logger.e(LogTags.BATTERY_OPTIMIZATION, "❌ Failed to update loading state UI", e)
        }
    }
    
    fun showErrorState(message: String) {
        // Defensive Programmierung: Prüfen ob UI-Komponenten initialisiert sind
        if (!::reliabilityText.isInitialized || !::confidenceText.isInitialized || !::reliabilityProgressBar.isInitialized) {
            Logger.w(LogTags.ALARM, "⚠️ UI components not initialized in showErrorState - skipping UI update")
            return
        }
        
        try {
            reliabilityText.text = "Fehler: $message"
            confidenceText.text = "Validierung fehlgeschlagen"
            reliabilityProgressBar.progress = 0
            Logger.d(LogTags.ALARM, "✅ Error state updated successfully")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Failed to update error state UI", e)
        }
    }
    
    fun updateConfigurationDisplay(
        configStatus: EnhancedOnePlusConfigStatus,
        onStepAction: (String) -> Unit,
        onStepConfirmation: (String) -> Unit,
        onStepHelp: (OnePlusConfigurationStep) -> Unit
    ) {
        // Defensive Programmierung: Prüfen ob UI-Komponenten initialisiert sind
        if (!::deviceInfoLayout.isInitialized || !::stepsContainer.isInitialized || 
            !::warningsContainer.isInitialized || !::reliabilityText.isInitialized ||
            !::reliabilityProgressBar.isInitialized || !::confidenceText.isInitialized) {
            Logger.w(LogTags.ALARM, "⚠️ UI components not initialized in updateConfigurationDisplay - skipping UI update")
            return
        }
        
        try {
            updateDeviceInfoDisplay(configStatus.deviceInfo, configStatus.capabilities, configStatus.validationConfidence)
            updateReliabilityDisplay(configStatus.estimatedReliability)
            updateStepsDisplay(configStatus.configurationSteps, onStepAction, onStepConfirmation, onStepHelp)
            updateWarningsDisplay(configStatus.criticalWarnings)
            
            if (showDebugInfo && ::debugContainer.isInitialized) {
                updateDebugDisplay(configStatus)
            }
            Logger.d(LogTags.ALARM, "✅ Configuration display updated successfully")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "❌ Failed to update configuration display", e)
        }
    }
    
    private fun updateDeviceInfoDisplay(
        deviceInfo: OnePlusDeviceInfo,
        capabilities: OnePlusDeviceCapabilities,
        confidence: Float
    ) {
        deviceInfoLayout.removeAllViews()
        
        val deviceTitle = TextView(context).apply {
            text = "📱 Geräteinformationen"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 8)
        }
        deviceInfoLayout.addView(deviceTitle)
        
        val deviceDetails = listOf(
            "Modell: ${deviceInfo.model}",
            "Hersteller: ${deviceInfo.manufacturer}",
            "Android: ${deviceInfo.androidVersion}",
            "OxygenOS: ${deviceInfo.oxygenOSVersion ?: "Nicht erkannt"}",
            "Enhanced Optimization: ${if (capabilities.hasEnhancedOptimization) "Unterstützt" else "Nicht verfügbar"}"
        )
        
        deviceDetails.forEach { detail ->
            val detailText = TextView(context).apply {
                text = "  • $detail"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                setPadding(0, 2, 0, 2)
            }
            deviceInfoLayout.addView(detailText)
        }
        
        confidenceText.text = "Geräteerkennung: ${String.format("%.1f%%", confidence * 100)} Zuverlässigkeit"
        confidenceText.setTextColor(ContextCompat.getColor(context, 
            if (confidence >= 0.9f) android.R.color.holo_green_dark
            else if (confidence >= 0.7f) android.R.color.holo_blue_dark
            else android.R.color.holo_orange_dark
        ))
    }
    
    private fun updateReliabilityDisplay(reliabilityMetrics: OnePlusReliabilityMetrics) {
        reliabilityProgressBar.progress = reliabilityMetrics.currentReliability
        
        val reliabilityColor = when (reliabilityMetrics.reliabilityLevel) {
            OnePlusReliabilityLevel.EXCELLENT -> android.R.color.holo_green_dark
            OnePlusReliabilityLevel.GOOD -> android.R.color.holo_blue_dark
            OnePlusReliabilityLevel.FAIR -> android.R.color.holo_orange_dark
            OnePlusReliabilityLevel.POOR -> android.R.color.holo_red_dark
        }
        
        reliabilityText.apply {
            text = buildString {
                append("${reliabilityMetrics.currentReliability}% Zuverlässigkeit ")
                append("(${reliabilityMetrics.completedSteps}/${reliabilityMetrics.totalSteps} Schritte)")
                appendLine()
                append("Level: ${reliabilityMetrics.reliabilityLevel.displayName}")
            }
            setTextColor(ContextCompat.getColor(context, reliabilityColor))
        }
    }
    
    private fun updateStepsDisplay(
        steps: List<OnePlusConfigurationStep>,
        onStepAction: (String) -> Unit,
        onStepConfirmation: (String) -> Unit,
        onStepHelp: (OnePlusConfigurationStep) -> Unit
    ) {
        stepsContainer.removeAllViews()
        
        val stepsTitle = TextView(context).apply {
            text = "🔧 Konfigurationsschritte"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 16)
        }
        stepsContainer.addView(stepsTitle)
        
        steps.forEach { step ->
            val stepCard = stepCardBuilder.createStepCard(step, onStepAction, onStepConfirmation, onStepHelp)
            stepsContainer.addView(stepCard)
        }
    }
    
    private fun updateWarningsDisplay(warnings: List<String>) {
        warningsContainer.removeAllViews()
        
        if (warnings.isEmpty()) {
            warningsContainer.visibility = LinearLayout.GONE
            return
        }
        
        warningsContainer.visibility = LinearLayout.VISIBLE
        
        val warningsTitle = TextView(context).apply {
            text = "⚠️ Kritische OnePlus-Hinweise (Research-basiert)"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 12)
        }
        warningsContainer.addView(warningsTitle)
        
        warnings.forEach { warning ->
            val warningText = TextView(context).apply {
                text = warning
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                setPadding(0, 4, 0, 4)
            }
            warningsContainer.addView(warningText)
        }
    }
    
    private fun updateDebugDisplay(configStatus: EnhancedOnePlusConfigStatus) {
        if (!showDebugInfo) return
        
        debugContainer.removeAllViews()
        
        val debugTitle = TextView(context).apply {
            text = "🧪 Debug Information"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(0, 0, 0, 12)
        }
        debugContainer.addView(debugTitle)
        
        val debugInfo = listOf(
            "Validation Confidence: ${String.format("%.3f", configStatus.validationConfidence)}",
            "Device Generation: ${configStatus.capabilities.recommendedConfigSteps.size} recommended steps",
            "Reset Risk: ${String.format("%.1f%%", configStatus.capabilities.batteryOptimizationResetRisk * 100)}",
            "Enhanced Optimization: ${configStatus.capabilities.hasEnhancedOptimization}",
            "Last Calculated: ${configStatus.estimatedReliability.lastCalculated}"
        )
        
        debugInfo.forEach { info ->
            val debugText = TextView(context).apply {
                text = "  • $info"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setPadding(0, 2, 0, 2)
            }
            debugContainer.addView(debugText)
        }
    }
    
    fun showStepConfirmationFeedback() {
        Toast.makeText(context, "✅ Schritt als abgeschlossen markiert!", Toast.LENGTH_SHORT).show()
    }
}
