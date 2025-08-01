package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.oneplus

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.oneplus.model.*

/**
 * Help dialog for OnePlus configuration steps
 * 
 * Provides detailed instructions for each configuration step with
 * step-specific guidance and alternative paths.
 */
class OnePlusStepHelpDialog(
    private val context: Context,
    private val step: OnePlusConfigurationStep
) {
    
    fun show(onSettingsAction: () -> Unit) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("${step.title} - Detaillierte Anleitung")
            .setMessage(buildDetailedInstructions())
            .setPositiveButton("Verstanden") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Einstellungen öffnen") { dialog, _ ->
                dialog.dismiss()
                onSettingsAction()
            }
            .setNegativeButton("Später") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            
        dialog.show()
    }
    
    private fun buildDetailedInstructions(): String {
        return buildString {
            appendLine("🎯 ${step.description}")
            appendLine()
            appendLine("📍 Hauptpfad: ${step.settingsPath}")
            appendLine()
            
            if (step.alternativePaths.isNotEmpty()) {
                appendLine("🔄 Alternative Pfade:")
                step.alternativePaths.forEach { altPath ->
                    appendLine("  • $altPath")
                }
                appendLine()
            }
            
            // Step-specific detailed instructions
            appendStepSpecificInstructions()
            
            step.oxygenOS15Changes?.let { changes ->
                appendLine("📱 OxygenOS 15 Änderungen:")
                appendLine(changes)
                appendLine()
            }
            
            step.warning?.let { warning ->
                appendLine("⚠️ Wichtiger Hinweis:")
                appendLine(warning)
                appendLine()
            }
            
            if (step.resetsWithUpdates) {
                appendLine("🔄 WICHTIG: Diese Einstellung wird bei OnePlus-Updates automatisch zurückgesetzt!")
            }
            
            appendLine()
            appendLine("✅ Nach der Konfiguration hier zurückkehren und auf '✅ Ja' drücken.")
        }
    }
    
    private fun StringBuilder.appendStepSpecificInstructions() {
        when (step.id) {
            OnePlusConfigurationStepId.ENHANCED_OPTIMIZATION -> {
                appendLine("🔍 Enhanced Optimization (OxygenOS 15+):")
                appendLine("1. Öffne Settings > Battery")
                appendLine("2. Tippe auf 'Battery optimization'")
                appendLine("3. Tippe auf die drei Punkte (⋮) oben rechts")
                appendLine("4. Wähle 'Advanced optimization'")
                appendLine("5. Schalte 'Deep optimization' AUS")
                appendLine("6. Bestätige die Änderung")
            }
            OnePlusConfigurationStepId.AUTO_STARTUP -> {
                appendLine("📋 Auto-Start Konfiguration:")
                appendLine("1. Settings > App Management")
                appendLine("2. Finde und tippe auf 'CF-Alarm'")
                appendLine("3. Suche nach 'Auto-start' oder 'Allow Auto Startup'")
                appendLine("4. Aktiviere den Schalter")
                appendLine()
                appendLine("🔍 Wenn nicht gefunden:")
                appendLine("• Settings > Privacy > Special app access > Auto-start")
                appendLine("• Settings > Battery > App Auto-start")
            }
            OnePlusConfigurationStepId.BACKGROUND_RUNNING -> {
                appendLine("⚡ Background Running:")
                appendLine("1. Settings > App Management > CF-Alarm")
                appendLine("2. Suche nach 'Power Saver' oder 'Battery'")
                appendLine("3. Stelle auf 'No restrictions' oder")
                appendLine("4. Aktiviere 'Allow Background Running'")
            }
            OnePlusConfigurationStepId.SLEEP_STANDBY_OPTIMIZATION -> {
                appendLine("🌙 Sleep Standby Optimization (OxygenOS 15+):")
                appendLine("1. Settings > Battery > Battery optimization")
                appendLine("2. Drei Punkte (⋮) > Advanced optimization")
                appendLine("3. Schalte 'Sleep standby optimization' AUS")
                appendLine("4. Dies verhindert Netzwerk-Abschaltung nachts")
            }
            OnePlusConfigurationStepId.RECENT_APPS_LOCK -> {
                appendLine("🔒 Recent Apps Lock:")
                appendLine("1. Drücke den Quadrat-Button (Recent Apps)")
                appendLine("2. Finde CF-Alarm in der App-Liste")
                appendLine("3. Tippe auf das Schloss-Symbol 🔒 oben rechts")
                appendLine("4. Das Symbol sollte geschlossen/gefüllt aussehen")
                appendLine()
                appendLine("⚠️ Hinweis: Nur 70% Erfolgsrate laut Community")
            }
            OnePlusConfigurationStepId.BATTERY_OPTIMIZATION -> {
                appendLine("🔋 Battery Optimization:")
                appendLine("1. Settings > Battery > Battery optimization")
                appendLine("2. Finde CF-Alarm in der Liste")
                appendLine("3. Tippe auf CF-Alarm")
                appendLine("4. Wähle 'Don't optimize'")
                appendLine("5. Bestätige mit 'Done'")
            }
            else -> {
                appendLine("Folge dem angegebenen Pfad in den Einstellungen.")
            }
        }
        appendLine()
    }
}
