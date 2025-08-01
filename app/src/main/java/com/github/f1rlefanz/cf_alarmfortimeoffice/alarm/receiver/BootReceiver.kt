package com.github.f1rlefanz.cf_alarmfortimeoffice.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.AlarmRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmManagerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BroadcastReceiver der nach einem Geräte-Neustart alle aktiven Alarme wiederherstellt
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.tag("CFAlarm.Boot").i("📱 Device booted - restoring alarms")
            
            // Starte Wiederherstellung in Coroutine
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    restoreAlarms(context)
                } catch (e: Exception) {
                    Timber.tag("CFAlarm.Boot").e(e, "Error restoring alarms after boot")
                }
            }
        }
    }
    
    private suspend fun restoreAlarms(context: Context) {
        // TODO: Implementiere AlarmRepository zum Laden gespeicherter Alarme
        // Für jetzt loggen wir nur
        Timber.tag("CFAlarm.Boot").d("Alarm restoration would happen here")
        
        // In der finalen Implementierung:
        // 1. Lade alle aktiven Alarme aus der Datenbank
        // 2. Filtere vergangene Alarme aus
        // 3. Setze alle zukünftigen Alarme neu mit AlarmManagerService
    }
}
