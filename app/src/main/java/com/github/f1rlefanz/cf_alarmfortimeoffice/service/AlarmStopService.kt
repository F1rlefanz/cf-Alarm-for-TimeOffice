package com.github.f1rlefanz.cf_alarmfortimeoffice.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.github.f1rlefanz.cf_alarmfortimeoffice.AlarmReceiver
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmStopService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.business(LogTags.ALARM, "Stopping alarm via service")
        
        // Stoppe Alarm-Medien über AlarmAudioManager in einem Coroutine-Scope
        serviceScope.launch {
            try {
                // Note: Da wir in einem Service sind, können wir hier keine DI verwenden
                // Wir erstellen einen temporären WakeLockManager für diese Operation
                val wakeLockManager = WakeLockManager(this@AlarmStopService)
                val alarmAudioManager = AlarmAudioManager(this@AlarmStopService, wakeLockManager)
                alarmAudioManager.stopAlarmSound()
                Logger.d(LogTags.ALARM, "Alarm sound stopped successfully")
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "Error stopping alarm sound", e)
            }
        }
        
        // Entferne Notification
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1001) // ALARM_NOTIFICATION_ID aus AlarmReceiver
            Logger.d(LogTags.ALARM, "Alarm notification removed")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error removing notification", e)
        }
        
        // Service beenden
        stopSelf()
        
        return START_NOT_STICKY
    }
}
