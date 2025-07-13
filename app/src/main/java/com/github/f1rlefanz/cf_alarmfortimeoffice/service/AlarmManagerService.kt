package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.github.f1rlefanz.cf_alarmfortimeoffice.AlarmReceiver
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftMatch
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.business.DateTimeFormats
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

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

    data class NextAlarmInfo(
        val triggerTime: Long,
        val formattedTime: String
    )

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // API < 31 needs no permission
        }
    }

    fun checkAlarmPermissions(): Boolean {
        return canScheduleExactAlarms()
    }

    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                application.startActivity(intent)
            }
        }
    }

    fun getNextAlarmInfo(): NextAlarmInfo? {
        return try {
            val nextAlarmClockInfo = alarmManager.nextAlarmClock
            if (nextAlarmClockInfo != null) {
                val triggerTime = nextAlarmClockInfo.triggerTime
                val formattedTime = formatAlarmTime(
                    java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(triggerTime),
                        java.time.ZoneId.systemDefault()
                    )
                )
                NextAlarmInfo(triggerTime, formattedTime)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_MANAGER, "Error getting next alarm info", e)
            null
        }
    }

    fun setAlarmFromShiftMatch(shiftMatch: ShiftMatch, autoAlarmEnabled: Boolean): AlarmStatus {
        Logger.business(
            LogTags.ALARM_MANAGER, "🔧 ALARM DEBUG: setAlarmFromShiftMatch called",
            "autoAlarmEnabled=$autoAlarmEnabled, shift=${shiftMatch.shiftDefinition.name}, alarmTime=${shiftMatch.calculatedAlarmTime}"
        )

        if (!autoAlarmEnabled) {
            Logger.d(LogTags.ALARM_MANAGER, "Auto-Alarm disabled, not setting alarm")
            return AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = checkAlarmPermissions(),
                alarmStatusMessage = "Auto-Alarm deaktiviert"
            )
        }

        val canSchedule = checkAlarmPermissions()
        Logger.business(
            LogTags.ALARM_MANAGER,
            "🔧 ALARM DEBUG: Permissions check",
            "canScheduleExactAlarms=$canSchedule"
        )

        if (!canSchedule) {
            Logger.w(LogTags.ALARM_MANAGER, "❌ No permission for exact alarms - requesting permission")
            // Auto-request permission for better UX
            requestExactAlarmPermission()
            return AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = false,
                alarmStatusMessage = "Alarm-Berechtigung fehlt - Berechtigung angefordert"
            )
        }

        return try {
            val alarmTimeMillis = shiftMatch.calculatedAlarmTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val currentTimeMillis = System.currentTimeMillis()
            val timeDifferenceMinutes = (alarmTimeMillis - currentTimeMillis) / (1000 * 60)

            Logger.business(
                LogTags.ALARM_MANAGER, "🔧 ALARM DEBUG: Timing",
                "currentTime=$currentTimeMillis, alarmTime=$alarmTimeMillis, differenceMinutes=$timeDifferenceMinutes"
            )

            if (alarmTimeMillis <= currentTimeMillis) {
                Logger.w(
                    LogTags.ALARM_MANAGER,
                    "🔧 ALARM DEBUG: Alarm time is in the past! alarmTime=${shiftMatch.calculatedAlarmTime}, current=${java.time.LocalDateTime.now()}"
                )
                return AlarmStatus(
                    systemAlarmSet = false,
                    canScheduleExactAlarms = true,
                    alarmStatusMessage = "Alarm-Zeit liegt in der Vergangenheit"
                )
            }

            // Erstelle Intent für AlarmReceiver
            val alarmIntent = Intent(application, AlarmReceiver::class.java).apply {
                putExtra("shift_name", shiftMatch.shiftDefinition.name)
                putExtra("alarm_time", formatAlarmTime(shiftMatch.calculatedAlarmTime))
                putExtra("shift_pattern", shiftMatch.shiftDefinition.id)
                putExtra("shift_start_time", shiftMatch.calendarEvent.startTime.toString())
                putExtra("shift_end_time", shiftMatch.calendarEvent.endTime.toString())
                // Fix AttributionTag issue
                setPackage(application.packageName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                application,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            Logger.business(
                LogTags.ALARM_MANAGER, "🔧 ALARM DEBUG: Setting alarm",
                "requestCode=$ALARM_REQUEST_CODE, pendingIntent=$pendingIntent"
            )

            // Setze exakten Alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent
            )

            val formattedTime = formatAlarmTime(shiftMatch.calculatedAlarmTime)
            Logger.business(
                LogTags.ALARM_MANAGER, "✅ ALARM DEBUG: System alarm set successfully",
                "${shiftMatch.shiftDefinition.name} at $formattedTime"
            )

            // Verify alarm was set by checking next alarm
            val nextAlarmInfo = getNextAlarmInfo()
            Logger.business(
                LogTags.ALARM_MANAGER, "🔧 ALARM DEBUG: Next alarm verification",
                "nextAlarm=${nextAlarmInfo?.formattedTime ?: "none"}"
            )

            AlarmStatus(
                systemAlarmSet = true,
                canScheduleExactAlarms = true,
                alarmStatusMessage = "Alarm gesetzt für $formattedTime"
            )

        } catch (e: SecurityException) {
            Logger.e(
                LogTags.ALARM_MANAGER,
                "🔧 ALARM DEBUG: SecurityException when setting alarm",
                e
            )
            AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = false,
                alarmStatusMessage = "Alarm-Berechtigung verweigert: ${e.message}"
            )
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_MANAGER, "🔧 ALARM DEBUG: Exception when setting alarm", e)
            AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = checkAlarmPermissions(),
                alarmStatusMessage = "Fehler beim Alarm setzen: ${e.localizedMessage}"
            )
        }
    }

    fun cancelSystemAlarm(): AlarmStatus {
        return try {
            val alarmIntent = Intent(application, AlarmReceiver::class.java).apply {
                // Fix AttributionTag issue
                setPackage(application.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                application,
                ALARM_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()

            Logger.business(LogTags.ALARM_MANAGER, "System alarm cancelled")
            AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = checkAlarmPermissions(),
                alarmStatusMessage = "Alarm abgebrochen"
            )

        } catch (e: Exception) {
            Logger.e(LogTags.ALARM_MANAGER, "Error cancelling system alarm", e)
            AlarmStatus(
                systemAlarmSet = false,
                canScheduleExactAlarms = checkAlarmPermissions(),
                alarmStatusMessage = "Fehler beim Alarm abbrechen: ${e.localizedMessage}"
            )
        }
    }

    private fun formatAlarmTime(alarmTime: java.time.LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern(DateTimeFormats.STANDARD_DATETIME)
        return alarmTime.format(formatter)
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 1001
    }

    /**
     * 🔍 DEBUG: Get current alarm status for debugging
     */
    fun getAlarmDebugInfo(): String {
        return buildString {
            appendLine("=== ALARM DEBUG INFO ===")
            appendLine("Can schedule exact alarms: ${canScheduleExactAlarms()}")
            appendLine("Next alarm info: ${getNextAlarmInfo()?.formattedTime ?: "None"}")
            appendLine("Alarm manager: $alarmManager")
            appendLine("App package: ${application.packageName}")
            appendLine("Request code: $ALARM_REQUEST_CODE")
            appendLine("========================")
        }
    }


}
