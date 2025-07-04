package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*

import androidx.core.app.NotificationCompat
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.UIConstants

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val ALARM_CHANNEL_ID = "CF_ALARM_CHANNEL"
        private const val ALARM_CHANNEL_NAME = "CF-Alarm Wecker"
        private const val ALARM_NOTIFICATION_ID = 1001
        
        // Static references für Alarm-Medien (um sie stoppen zu können)
        private var mediaPlayer: MediaPlayer? = null
        private var vibrator: Vibrator? = null
        private var vibratorManager: VibratorManager? = null
        
        fun stopAlarmMedia() {
            Logger.d(LogTags.ALARM, "Stopping alarm media")
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                    mediaPlayer = null
                }
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "Error stopping MediaPlayer", e)
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    vibratorManager?.cancel()
                    vibratorManager = null
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.cancel()
                    vibrator = null
                }
            } catch (e: Exception) {
                Logger.e(LogTags.ALARM, "Error stopping vibration", e)
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            Logger.e(LogTags.ALARM_RECEIVER, "AlarmReceiver: Context is null")
            return
        }
        
        Logger.business(LogTags.ALARM_RECEIVER, "🚨 ALARM RECEIVED! 🚨")
        
        // Stoppe vorherige Alarm-Medien falls vorhanden
        stopAlarmMedia()
        
        // Hole Schicht-Info aus Intent (falls verfügbar)
        val shiftName = intent?.getStringExtra("shift_name") ?: "Unbekannte Schicht"
        val alarmTime = intent?.getStringExtra("alarm_time") ?: "Jetzt"
        
        Logger.business(LogTags.ALARM_RECEIVER, "Alarm for shift", "$shiftName at $alarmTime")
        
        // Starte Vibration
        startVibration(context)
        
        // Starte Alarmton
        startAlarmSound(context)
        
        // Zeige Vollbild-Activity
        showFullScreenAlarm(context, shiftName, alarmTime)
        
        // Zeige Notification
        showAlarmNotification(context, shiftName, alarmTime)
    }
    
    private fun startVibration(context: Context) {
        try {
            val vibrationPattern = UIConstants.ALARM_VIBRATION_PATTERN
            
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // Android 12+ (API 31+): Use VibratorManager
                    vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager?
                    vibratorManager?.defaultVibrator?.let { defaultVibrator ->
                        if (defaultVibrator.hasVibrator()) {
                            val vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, 1)
                            vibratorManager?.vibrate(CombinedVibration.createParallel(vibrationEffect))
                            Logger.d(LogTags.ALARM, "Vibration started (VibratorManager)")
                        }
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    // Android 8.0+ (API 26-30): Use VibrationEffect with Vibrator
                    vibrator =
                        context.getSystemService(Vibrator::class.java)
                    vibrator?.let { vib ->
                        if (vib.hasVibrator()) {
                            val vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, 1)
                            vib.vibrate(vibrationEffect)
                            Logger.d(LogTags.ALARM, "Vibration started (VibrationEffect)")
                        }
                    }
                }
                else -> {
                    // Android 7.1 and below (API 24-25): Use legacy vibrate method
                    vibrator =
                        context.getSystemService(Vibrator::class.java)
                    vibrator?.let { vib ->
                        if (vib.hasVibrator()) {
                            @Suppress("DEPRECATION")
                            vib.vibrate(vibrationPattern, 1) // Repeat from index 1
                            Logger.d(LogTags.ALARM, "Vibration started (Legacy)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error starting vibration", e)
        }
    }
    
    private fun startAlarmSound(context: Context) {
        try {
            // Nutze Standard-Alarmton oder fallback zu Notification
            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            
            if (alarmUri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    
                    setOnPreparedListener { mp ->
                        Logger.d(LogTags.ALARM, "MediaPlayer prepared, starting playback")
                        try {
                            mp.start()
                        } catch (e: Exception) {
                            Logger.e(LogTags.ALARM, "Error starting playback", e)
                        }
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Logger.e(LogTags.ALARM, "MediaPlayer error: what=$what, extra=$extra")
                        stopAlarmMedia()
                        true
                    }
                    
                    prepareAsync()
                }
                Logger.d(LogTags.ALARM, "Alarm sound being prepared")
            } else {
                Logger.e(LogTags.ALARM, "No alarm sound available")
            }
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error starting alarm sound", e)
        }
    }
    
    private fun showFullScreenAlarm(context: Context, shiftName: String, alarmTime: String) {
        try {
            val fullScreenIntent = Intent(context, AlarmFullScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("shift_name", shiftName)
                putExtra("alarm_time", alarmTime)
                setPackage(context.packageName)
            }
            context.startActivity(fullScreenIntent)
            Logger.d(LogTags.ALARM, "Full-screen alarm activity started")
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error starting full-screen activity", e)
        }
    }
    
    private fun showAlarmNotification(context: Context, shiftName: String, alarmTime: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Erstelle Channel falls nötig
            createAlarmNotificationChannel(notificationManager)
            
            // Stoppt-Intent für Notification-Action
            val stopIntent = Intent(context, com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmStopService::class.java).apply {
                setPackage(context.packageName)
            }
            val stopPendingIntent = PendingIntent.getService(
                context, 0, stopIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Vollbild-Intent für Tap auf Notification
            val fullScreenIntent = Intent(context, AlarmFullScreenActivity::class.java).apply {
                putExtra("shift_name", shiftName)
                putExtra("alarm_time", alarmTime)
                setPackage(context.packageName)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 0, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Standard alarm icon
                .setContentTitle("⏰ CF-Alarm: $shiftName")
                .setContentText("Zeit aufzustehen! Schicht um $alarmTime")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSilent(true) // Wir spielen eigenen Sound
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    "Stopp",
                    stopPendingIntent
                )
                .build()
            
            notificationManager.notify(ALARM_NOTIFICATION_ID, notification)
            Logger.d(LogTags.ALARM, "Alarm notification displayed")
            
        } catch (e: Exception) {
            Logger.e(LogTags.ALARM, "Error showing notification", e)
        }
    }
    
    private fun createAlarmNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    ALARM_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Benachrichtigungen für CF-Alarm Wecker"
                    setSound(null, null) // Kein Standard-Sound
                    enableVibration(false) // Keine Standard-Vibration
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setBypassDnd(true) // Umgeht "Nicht stören" Modus
                }
                notificationManager.createNotificationChannel(channel)
                Logger.d(LogTags.ALARM, "Alarm NotificationChannel created")
            }
        }
    }
}
