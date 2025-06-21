package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.github.f1rlefanz.cf_alarmfortimeoffice.AlarmReceiver
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import timber.log.Timber

/**
 * Extrahierte AlarmManager-Logic aus AuthViewModel
 * Verbessert Modularität und Testbarkeit
 */
class AlarmManagerService(private val application: Application) {
    
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    data class AlarmStatus(
        val systemAlarmSet: Boolean,
        val canScheduleExactAlarms: Boolean,
        val alarmStatusMessage: String?
    )
    
    fun checkAlarmPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // API < 31 needs no permission
        }
    }
    
    fun setAlarmFromShiftMatch(shiftMatch: ShiftMatch, autoAlarmEnabled: Boolean): AlarmStatus {
        if (!autoAlarmEnabled) {
            Timber.d("Auto-Alarm deaktiviert, setze keinen Alarm")
            return AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = checkAlarmPermissions(),
                alarmStatusMessage = "Auto-Alarm deaktiviert"
            )
        }
        
        if (!checkAlarmPermissions()) {
            Timber.w("Keine Berechtigung für exakte Alarme")
            return AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = false,
                alarmStatusMessage = "Alarm-Berechtigung fehlt"
            )
        }
        
        return try {
            // Erstelle Intent für AlarmReceiver
            val alarmIntent = Intent(application, AlarmReceiver::class.java).apply {
                putExtra("shift_name", shiftMatch.shiftDefinition.name)
                putExtra("alarm_time", formatAlarmTime(shiftMatch.calculatedAlarmTime))
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                application,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Setze exakten Alarm
            val alarmTimeMillis = shiftMatch.calculatedAlarmTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent
            )
            
            val formattedTime = formatAlarmTime(shiftMatch.calculatedAlarmTime)
            Timber.i("System-Alarm gesetzt für ${shiftMatch.shiftDefinition.name} um $formattedTime")
            
            AlarmStatus(
                systemAlarmSet = true,
                canScheduleExactAlarms = true,
                alarmStatusMessage = "Alarm gesetzt für $formattedTime"
            )
            
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException beim Setzen des Alarms")
            AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = false,
                alarmStatusMessage = "Alarm-Berechtigung verweigert"
            )
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Setzen des System-Alarms")
            AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = checkAlarmPermissions(),
                alarmStatusMessage = "Fehler beim Alarm setzen: ${e.localizedMessage}"
            )
        }
    }
    
    fun cancelSystemAlarm(): AlarmStatus {
        return try {
            val alarmIntent = Intent(application, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                application,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            
            Timber.i("System-Alarm abgebrochen")
            AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = checkAlarmPermissions(),
                alarmStatusMessage = "Alarm abgebrochen"
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Abbrechen des System-Alarms")
            AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = checkAlarmPermissions(),
                alarmStatusMessage = "Fehler beim Alarm abbrechen: ${e.localizedMessage}"
            )
        }
    }
    
    private fun formatAlarmTime(alarmTime: java.time.LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        return alarmTime.format(formatter)
    }
    
    companion object {
        private const val ALARM_REQUEST_CODE = 1001
    }
}
