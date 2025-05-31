package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

class AlarmStopService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("AlarmStopService: Stoppe Alarm")
        
        // Stoppe Alarm-Medien
        AlarmReceiver.stopAlarmMedia()
        
        // Entferne Notification
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1001) // ALARM_NOTIFICATION_ID aus AlarmReceiver
            Timber.d("Alarm-Notification entfernt")
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Entfernen der Notification")
        }
        
        // Service beenden
        stopSelf()
        
        return START_NOT_STICKY
    }
}
