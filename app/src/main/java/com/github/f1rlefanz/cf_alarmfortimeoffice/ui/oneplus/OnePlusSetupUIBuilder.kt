package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.github.f1rlefanz.cf_alarmfortimeoffice.R
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import java.util.*

/**
 * 🎨 Modern OnePlus Setup UI Builder
 * 
 * Creates a beautiful, intuitive UI for OnePlus device configuration
 * with step-by-step guidance, progress indicators, and interactive elements.
 * 
 * Features:
 * - Material Design 3 inspired styling
 * - Progress indicators and success animations
 * - Device-specific recommendations
 * - Interactive help and guidance
 * - Debug information for developers
 */
class OnePlusSetupUIBuilder(
    private val context: Context,
    private val isFirstTimeSetup: Boolean,
    private val isFromAlarmFailure: Boolean,
    private val showDebugInfo: Boolean
) {
    
    // UI Constants for consistent styling
    private companion object {
        const val PADDING_STANDARD = 24
        const val PADDING_SMALL = 16
        const val PADDING_LARGE = 32
        const val CORNER_RADIUS = 16f
        const val ELEVATION = 8f
        
        // Colors (using standard Android colors as fallback)
        const val COLOR_PRIMARY = "#1976D2"
        const val COLOR_SUCCESS = "#4CAF50"
        const val COLOR_WARNING = "#FF9800"
        const val COLOR_ERROR = "#F44336"
        const val COLOR_SURFACE = "#FFFFFF"
        const val COLOR_ON_SURFACE = "#212121"
        const val COLOR_SECONDARY = "#757575"
    }
    
    // UI State
    private var loadingProgressBar: ProgressBar? = null
    private var mainContentLayout: LinearLayout? = null
    private var headerSection: LinearLayout? = null
    private var configStepsContainer: LinearLayout? = null
    private var reliabilitySection: LinearLayout? = null
    private var debugSection: LinearLayout? = null
    private var actionButtonsLayout: LinearLayout? = null
    
    /**
     * Creates the main OnePlus setup UI
     */
    fun createEnhancedOnePlusUI(
        onFinishSetup: (Boolean) -> Unit,
        onShowDebugDialog: () -> Unit
    ): ScrollView {
        val scrollView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            isFillViewport = true
        }
        
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING_STANDARD)
        }
        
        // Create UI sections
        createHeaderSection(rootLayout)
        createLoadingSection(rootLayout)
        createMainContentSection(rootLayout)
        createActionButtonsSection(rootLayout, onFinishSetup, onShowDebugDialog)
        
        scrollView.addView(rootLayout)
        return scrollView
    }
    
    private fun createHeaderSection(parent: LinearLayout) {
        headerSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(COLOR_PRIMARY))
            setPadding(PADDING_LARGE)
            
            // Title
            val title = TextView(context).apply {
                text = when {
                    isFromAlarmFailure -> "🔧 OnePlus Alarm-Problem beheben"
                    isFirstTimeSetup -> "🚀 OnePlus für CF-Alarm optimieren"
                    else -> "⚙️ OnePlus-Einstellungen prüfen"
                }
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, PADDING_SMALL)
            }
            
            // Subtitle
            val subtitle = TextView(context).apply {
                text = when {
                    isFromAlarmFailure -> "Ihr Alarm ist möglicherweise ausgefallen. Konfigurieren Sie diese Einstellungen für zuverlässige Alarme."
                    isFirstTimeSetup -> "Willkommen! Optimieren Sie Ihr OnePlus-Gerät in wenigen Schritten für maximale Alarm-Zuverlässigkeit."
                    else -> "Prüfen und aktualisieren Sie Ihre OnePlus-Einstellungen für optimale Alarm-Performance."
                }
                textSize = 16f
                setTextColor(Color.parseColor("#E3F2FD"))
                gravity = Gravity.CENTER
                lineHeight = (textSize * 1.4f).toInt()
            }
            
            addView(title)
            addView(subtitle)
        }
        
        parent.addView(headerSection)
    }
    
    private fun createLoadingSection(parent: LinearLayout) {
        val loadingLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(PADDING_LARGE)
            visibility = View.GONE
        }
        
        loadingProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            setPadding(0, 0, PADDING_STANDARD, 0)
        }
        
        val loadingText = TextView(context).apply {
            text = "OnePlus-Konfiguration wird analysiert..."
            textSize = 16f
            setTextColor(Color.parseColor(COLOR_ON_SURFACE))
        }
        
        loadingLayout.addView(loadingProgressBar)
        loadingLayout.addView(loadingText)
        parent.addView(loadingLayout)
    }
    
    private fun createMainContentSection(parent: LinearLayout) {
        mainContentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE // Initially hidden until configuration is loaded
        }
        
        // Configuration steps container
        configStepsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Reliability metrics section
        reliabilitySection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Debug section (only shown if enabled)
        if (showDebugInfo) {
            debugSection = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
        }
        
        mainContentLayout?.apply {
            addView(configStepsContainer)
            addView(reliabilitySection)
            debugSection?.let { addView(it) }
        }
        
        parent.addView(mainContentLayout)
    }
    
    private fun createActionButtonsSection(
        parent: LinearLayout,
        onFinishSetup: (Boolean) -> Unit,
        onShowDebugDialog: () -> Unit
    ) {
        actionButtonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING_STANDARD)
        }
        
        // Primary action button
        val completeButton = Button(context).apply {
            text = if (isFirstTimeSetup) "✅ Einrichtung abschließen" else "🔄 Konfiguration aktualisieren"
            textSize = 18f
            setBackgroundColor(Color.parseColor(COLOR_SUCCESS))
            setTextColor(Color.WHITE)
            setPadding(PADDING_STANDARD)
            setOnClickListener { onFinishSetup(true) }
        }
        
        // Secondary action button
        val skipButton = Button(context).apply {
            text = if (isFromAlarmFailure) "❌ Problem später lösen" else "⏭️ Überspringen"
            textSize = 16f
            setBackgroundColor(Color.parseColor(COLOR_SECONDARY))
            setTextColor(Color.WHITE)
            setPadding(PADDING_SMALL)
            setOnClickListener { onFinishSetup(false) }
        }
        
        actionButtonsLayout?.apply {
            addView(completeButton)
            if (!isFromAlarmFailure) { // Don't show skip for alarm failures
                addView(skipButton)
            }
            
            // Debug button
            if (showDebugInfo) {
                val debugButton = Button(context).apply {
                    text = "🧪 Debug Info"
                    textSize = 14f
                    setBackgroundColor(Color.parseColor(COLOR_WARNING))
                    setTextColor(Color.WHITE)
                    setPadding(PADDING_SMALL)
                    setOnClickListener { onShowDebugDialog() }
                }
                addView(debugButton)
            }
        }
        
        parent.addView(actionButtonsLayout)
    }
    
    /**
     * Updates the configuration display with enhanced OnePlus data
     */
    fun updateConfigurationDisplay(
        configStatus: EnhancedOnePlusConfigStatus,
        onStepAction: (String) -> Unit,
        onStepConfirmation: (String) -> Unit,
        onStepHelp: (OnePlusConfigurationStep) -> Unit
    ) {
        try {
            configStepsContainer?.removeAllViews()
            
            // Create device info section
            createDeviceInfoSection(configStatus)
            
            // Create configuration steps
            configStatus.configurationSteps.forEach { step ->
                createStepCard(step, onStepAction, onStepConfirmation, onStepHelp)
            }
            
            // Create reliability metrics
            createReliabilityMetrics(configStatus.estimatedReliability)
            
            // Create warnings section
            if (configStatus.criticalWarnings.isNotEmpty()) {
                createWarningsSection(configStatus.criticalWarnings)
            }
            
            // Show debug info if enabled
            if (showDebugInfo && debugSection != null) {
                createDebugSection(configStatus)
            }
            
            // Show main content
            mainContentLayout?.visibility = View.VISIBLE
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error updating configuration display", e)
            showErrorState("Fehler beim Anzeigen der Konfiguration: ${e.message}")
        }
    }
    
    private fun createDeviceInfoSection(configStatus: EnhancedOnePlusConfigStatus) {
        val deviceInfoCard = createCard("📱 Geräteinformation")
        
        val deviceInfo = """
            Gerät: ${configStatus.deviceInfo.model}
            Android: ${configStatus.deviceInfo.androidVersion}
            ${configStatus.deviceInfo.oxygenOSVersion?.let { "OxygenOS: $it" } ?: ""}
            Erkennungsgenauigkeit: ${String.format(Locale.US, "%.1f%%", configStatus.validationConfidence * 100)}
        """.trimIndent()
        
        val deviceText = TextView(context).apply {
            text = deviceInfo
            textSize = 14f
            setTextColor(Color.parseColor(COLOR_ON_SURFACE))
            setPadding(PADDING_STANDARD)
        }
        
        deviceInfoCard.addView(deviceText)
        configStepsContainer?.addView(deviceInfoCard)
    }
    
    private fun createStepCard(
        step: OnePlusConfigurationStep,
        onStepAction: (String) -> Unit,
        onStepConfirmation: (String) -> Unit,
        onStepHelp: (OnePlusConfigurationStep) -> Unit
    ) {
        val stepCard = createCard(getStepTitle(step))
        
        // Step status indicator
        val statusLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(PADDING_STANDARD)
        }
        
        val statusIcon = TextView(context).apply {
            text = if (step.isCompleted) "✅" else when (step.priority) {
                OnePlusConfigPriority.CRITICAL -> "🔴"
                OnePlusConfigPriority.HIGH -> "🟡"
                else -> "🔵"
            }
            textSize = 20f
            setPadding(0, 0, PADDING_SMALL, 0)
        }
        
        val statusText = TextView(context).apply {
            text = if (step.isCompleted) "Konfiguriert" else "Aktion erforderlich"
            textSize = 16f
            setTextColor(Color.parseColor(if (step.isCompleted) COLOR_SUCCESS else COLOR_WARNING))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        statusLayout.addView(statusIcon)
        statusLayout.addView(statusText)
        
        // Step description
        val descriptionText = TextView(context).apply {
            text = step.description
            textSize = 14f
            setTextColor(Color.parseColor(COLOR_SECONDARY))
            setPadding(PADDING_STANDARD, 0, PADDING_STANDARD, PADDING_SMALL)
        }
        
        // Action buttons
        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(PADDING_STANDARD)
        }
        
        if (!step.isCompleted) {
            val actionButton = Button(context).apply {
                text = "⚙️ Einstellungen öffnen"
                textSize = 14f
                setBackgroundColor(Color.parseColor(COLOR_PRIMARY))
                setTextColor(Color.WHITE)
                setPadding(PADDING_SMALL)
                setOnClickListener { onStepAction(step.id.name.lowercase()) }
            }
            buttonsLayout.addView(actionButton)
        }
        
        val helpButton = Button(context).apply {
            text = "❓ Hilfe"
            textSize = 14f
            setBackgroundColor(Color.parseColor(COLOR_SECONDARY))
            setTextColor(Color.WHITE)
            setPadding(PADDING_SMALL)
            setOnClickListener { onStepHelp(step) }
        }
        buttonsLayout.addView(helpButton)
        
        if (step.detectionMethod == ConfigDetectionMethod.USER_CONFIRMED_ONLY && !step.isCompleted) {
            val confirmButton = Button(context).apply {
                text = "✓ Erledigt"
                textSize = 14f
                setBackgroundColor(Color.parseColor(COLOR_SUCCESS))
                setTextColor(Color.WHITE)
                setPadding(PADDING_SMALL)
                setOnClickListener { onStepConfirmation(step.id.name.lowercase()) }
            }
            buttonsLayout.addView(confirmButton)
        }
        
        stepCard.apply {
            addView(statusLayout)
            addView(descriptionText)
            addView(buttonsLayout)
        }
        
        configStepsContainer?.addView(stepCard)
    }
    
    private fun createReliabilityMetrics(reliability: OnePlusReliabilityMetrics) {
        val reliabilityCard = createCard("📊 Zuverlässigkeits-Bewertung")
        
        // Progress bar for reliability
        val progressLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING_STANDARD)
        }
        
        val reliabilityText = TextView(context).apply {
            text = "Aktuelle Zuverlässigkeit: ${reliability.currentReliability}%"
            textSize = 16f
            setTextColor(Color.parseColor(COLOR_ON_SURFACE))
            gravity = Gravity.CENTER
        }
        
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = reliability.currentReliability
            setPadding(0, PADDING_SMALL, 0, PADDING_SMALL)
        }
        
        val levelText = TextView(context).apply {
            text = "Stufe: ${reliability.reliabilityLevel.displayName}"
            textSize = 14f
            setTextColor(Color.parseColor(getReliabilityColor(reliability.reliabilityLevel)))
            gravity = Gravity.CENTER
        }
        
        val stepsText = TextView(context).apply {
            text = "Konfigurierte Schritte: ${reliability.completedSteps}/${reliability.totalSteps}"
            textSize = 14f
            setTextColor(Color.parseColor(COLOR_SECONDARY))
            gravity = Gravity.CENTER
        }
        
        progressLayout.apply {
            addView(reliabilityText)
            addView(progressBar)
            addView(levelText)
            addView(stepsText)
        }
        
        reliabilityCard.addView(progressLayout)
        reliabilitySection?.addView(reliabilityCard)
    }
    
    private fun createWarningsSection(warnings: List<String>) {
        val warningsCard = createCard("⚠️ Wichtige Hinweise")
        
        warnings.forEach { warning ->
            val warningText = TextView(context).apply {
                text = warning
                textSize = 14f
                setTextColor(Color.parseColor(COLOR_ERROR))
                setPadding(PADDING_STANDARD, PADDING_SMALL, PADDING_STANDARD, PADDING_SMALL)
            }
            warningsCard.addView(warningText)
        }
        
        reliabilitySection?.addView(warningsCard)
    }
    
    private fun createDebugSection(configStatus: EnhancedOnePlusConfigStatus) {
        val debugCard = createCard("🧪 Debug-Information")
        
        val debugText = TextView(context).apply {
            text = """
                Validation Details: ${configStatus.validationDetails}
                Capabilities: ${configStatus.capabilities}
                Last Updated: ${configStatus.estimatedReliability.lastCalculated}
            """.trimIndent()
            textSize = 12f
            setTextColor(Color.parseColor(COLOR_SECONDARY))
            setPadding(PADDING_STANDARD)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        
        debugCard.addView(debugText)
        debugSection?.addView(debugCard)
    }
    
    private fun createCard(title: String): LinearLayout {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(COLOR_SURFACE))
            setPadding(0, 0, 0, PADDING_STANDARD)
            
            // Add some visual separation
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, PADDING_SMALL, 0, PADDING_SMALL)
            }
            layoutParams = params
        }
        
        // Card title
        val titleView = TextView(context).apply {
            text = title
            textSize = 18f
            setTextColor(Color.parseColor(COLOR_ON_SURFACE))
            setPadding(PADDING_STANDARD, PADDING_STANDARD, PADDING_STANDARD, PADDING_SMALL)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        cardLayout.addView(titleView)
        return cardLayout
    }
    
    private fun getStepTitle(step: OnePlusConfigurationStep): String {
        return "${getPriorityEmoji(step.priority)} ${step.title}"
    }
    
    private fun getPriorityEmoji(priority: OnePlusConfigPriority): String {
        return when (priority) {
            OnePlusConfigPriority.CRITICAL -> "🔴"
            OnePlusConfigPriority.HIGH -> "🟡"
            OnePlusConfigPriority.MEDIUM -> "🔵"
            OnePlusConfigPriority.LOW -> "⚪"
        }
    }
    
    private fun getReliabilityColor(level: OnePlusReliabilityLevel): String {
        return when (level) {
            OnePlusReliabilityLevel.EXCELLENT -> COLOR_SUCCESS
            OnePlusReliabilityLevel.GOOD -> COLOR_PRIMARY
            OnePlusReliabilityLevel.FAIR -> COLOR_WARNING
            OnePlusReliabilityLevel.POOR -> COLOR_ERROR
        }
    }
    
    /**
     * Updates the loading state
     */
    fun updateLoadingState(isLoading: Boolean) {
        loadingProgressBar?.parent?.let { loadingParent ->
            (loadingParent as LinearLayout).visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        mainContentLayout?.visibility = if (isLoading) View.GONE else View.VISIBLE
    }
    
    /**
     * Shows an error state
     */
    fun showErrorState(message: String) {
        configStepsContainer?.removeAllViews()
        
        val errorLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(PADDING_LARGE)
        }
        
        val errorIcon = TextView(context).apply {
            text = "❌"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, PADDING_STANDARD)
        }
        
        val errorText = TextView(context).apply {
            text = message
            textSize = 16f
            setTextColor(Color.parseColor(COLOR_ERROR))
            gravity = Gravity.CENTER
        }
        
        errorLayout.addView(errorIcon)
        errorLayout.addView(errorText)
        configStepsContainer?.addView(errorLayout)
        
        mainContentLayout?.visibility = View.VISIBLE
        updateLoadingState(false)
    }
    
    /**
     * Shows feedback when user confirms a step
     */
    fun showStepConfirmationFeedback() {
        Toast.makeText(context, "✅ Schritt als abgeschlossen markiert", Toast.LENGTH_SHORT).show()
    }
}
