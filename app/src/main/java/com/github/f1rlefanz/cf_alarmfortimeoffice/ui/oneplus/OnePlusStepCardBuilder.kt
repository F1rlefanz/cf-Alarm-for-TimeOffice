package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus

import android.content.Context
import android.widget.*
import androidx.core.content.ContextCompat
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.*

/**
 * Builder for OnePlus configuration step cards
 * 
 * Creates interactive UI cards for each configuration step with appropriate
 * actions and feedback based on the step's detection method and completion status.
 */
class OnePlusStepCardBuilder(private val context: Context) {
    
    fun createStepCard(
        step: OnePlusConfigurationStep,
        onStepAction: (String) -> Unit,
        onStepConfirmation: (String) -> Unit,
        onStepHelp: (OnePlusConfigurationStep) -> Unit
    ): LinearLayout {
        
        val stepLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(ContextCompat.getColor(context, 
                if (step.isCompleted) android.R.color.background_light 
                else android.R.color.white
            ))
        }
        
        // Add all step components
        stepLayout.addView(createStepHeader(step))
        stepLayout.addView(createStepDescription(step))
        stepLayout.addView(createStepPath(step))
        stepLayout.addView(createTechnicalInfo(step))
        
        // Add optional sections
        addAlternativePaths(stepLayout, step)
        addOxygenOS15Changes(stepLayout, step)
        addWarnings(stepLayout, step)
        
        // Add action section
        if (step.isCompleted) {
            stepLayout.addView(createCompletionInfo(step))
        } else {
            stepLayout.addView(createActionSection(step, onStepAction, onStepConfirmation, onStepHelp))
        }
        
        // Add margin between steps
        val marginParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 16)
        }
        stepLayout.layoutParams = marginParams
        
        return stepLayout
    }
    
    private fun createStepHeader(step: OnePlusConfigurationStep): LinearLayout {
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        val priorityIcon = TextView(context).apply {
            text = getPriorityIcon(step.priority)
            textSize = 20f
            setPadding(0, 0, 8, 0)
        }
        
        val stepTitle = TextView(context).apply {
            text = step.title
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 8, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val statusIcon = TextView(context).apply {
            text = if (step.isCompleted) "✅" else "⚪"
            textSize = 16f
        }
        
        headerLayout.addView(priorityIcon)
        headerLayout.addView(stepTitle)
        headerLayout.addView(statusIcon)
        
        return headerLayout
    }
    
    private fun createStepDescription(step: OnePlusConfigurationStep): TextView {
        return TextView(context).apply {
            text = step.description
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(30, 4, 0, 4)
        }
    }
    
    private fun createStepPath(step: OnePlusConfigurationStep): TextView {
        return TextView(context).apply {
            text = "📍 ${step.settingsPath}"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setPadding(30, 4, 0, 8)
        }
    }
    
    private fun createTechnicalInfo(step: OnePlusConfigurationStep): TextView {
        return TextView(context).apply {
            text = "Impact: ${step.estimatedImpact}% • Detection: ${step.detectionMethod.name}"
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setPadding(30, 4, 0, 8)
        }
    }
    
    private fun addAlternativePaths(stepLayout: LinearLayout, step: OnePlusConfigurationStep) {
        if (step.alternativePaths.isEmpty()) return
        
        val altPathsTitle = TextView(context).apply {
            text = "🔄 Alternative Pfade:"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(30, 8, 0, 4)
        }
        stepLayout.addView(altPathsTitle)
        
        step.alternativePaths.forEach { altPath ->
            val altPathText = TextView(context).apply {
                text = "  • $altPath"
                textSize = 11f
                setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                setPadding(40, 2, 0, 2)
            }
            stepLayout.addView(altPathText)
        }
    }
    
    private fun addOxygenOS15Changes(stepLayout: LinearLayout, step: OnePlusConfigurationStep) {
        step.oxygenOS15Changes?.let { changes ->
            val os15ChangesTitle = TextView(context).apply {
                text = "📱 OxygenOS 15 Änderungen:"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
                setPadding(30, 8, 0, 4)
            }
            stepLayout.addView(os15ChangesTitle)
            
            val os15ChangesText = TextView(context).apply {
                text = changes
                textSize = 11f
                setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                setPadding(40, 2, 0, 8)
            }
            stepLayout.addView(os15ChangesText)
        }
    }
    
    private fun addWarnings(stepLayout: LinearLayout, step: OnePlusConfigurationStep) {
        // Add step warning
        step.warning?.let { warning ->
            val warningText = TextView(context).apply {
                text = "⚠️ $warning"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                setPadding(30, 8, 0, 8)
            }
            stepLayout.addView(warningText)
        }
        
        // Add reset warning
        if (step.resetsWithUpdates) {
            val resetWarning = TextView(context).apply {
                text = "🔄 WICHTIG: Diese Einstellung wird bei OnePlus-Updates automatisch zurückgesetzt!"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                setPadding(30, 8, 0, 8)
            }
            stepLayout.addView(resetWarning)
        }
    }
    
    private fun createActionSection(
        step: OnePlusConfigurationStep,
        onStepAction: (String) -> Unit,
        onStepConfirmation: (String) -> Unit,
        onStepHelp: (OnePlusConfigurationStep) -> Unit
    ): LinearLayout {
        
        val actionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }
        
        // Primary action button
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        
        val actionButton = Button(context).apply {
            text = "Einstellungen öffnen"
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_blue_bright))
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(24, 8, 24, 8)
            setOnClickListener { onStepAction(step.id.name.lowercase()) }
        }
        buttonLayout.addView(actionButton)
        actionContainer.addView(buttonLayout)
        
        // User confirmation buttons for steps that can't be auto-detected
        if (step.detectionMethod != ConfigDetectionMethod.API_RELIABLE) {
            val confirmLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 0)
            }
            
            val confirmLabel = TextView(context).apply {
                text = "Schritt abgeschlossen?"
                textSize = 12f
                setPadding(0, 0, 8, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            val yesButton = Button(context).apply {
                text = "✅ Ja"
                textSize = 12f
                setPadding(12, 6, 12, 6)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setOnClickListener { onStepConfirmation(step.id.name.lowercase()) }
            }
            
            val noButton = Button(context).apply {
                text = "❌ Hilfe"
                textSize = 12f
                setPadding(12, 6, 12, 6)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setOnClickListener { onStepHelp(step) }
            }
            
            confirmLayout.addView(confirmLabel)
            confirmLayout.addView(yesButton)
            confirmLayout.addView(noButton)
            
            actionContainer.addView(confirmLayout)
        }
        
        return actionContainer
    }
    
    private fun createCompletionInfo(step: OnePlusConfigurationStep): TextView {
        return TextView(context).apply {
            text = when (step.detectionMethod) {
                ConfigDetectionMethod.API_RELIABLE -> "✅ Automatisch erkannt"
                ConfigDetectionMethod.HEURISTIC_USER_CONFIRMED -> "✅ Intelligent erkannt"
                ConfigDetectionMethod.USER_CONFIRMED_ONLY -> "✅ Vom Benutzer bestätigt"
            }
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            setPadding(30, 4, 0, 0)
        }
    }
    
    private fun getPriorityIcon(priority: OnePlusConfigPriority): String {
        return when (priority) {
            OnePlusConfigPriority.CRITICAL -> "🔴"
            OnePlusConfigPriority.HIGH -> "🟡"
            OnePlusConfigPriority.MEDIUM -> "🟢"
            OnePlusConfigPriority.LOW -> "⚪"
        }
    }
}
