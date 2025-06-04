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
import timber.log.Timber

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
            Timber.d("Stoppe Alarm-Medien")
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                    mediaPlayer = null
                }
            } catch (e: Exception) {
                Timber.e(e, "Fehler beim Stoppen des MediaPlayers")
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
                Timber.e(e, "Fehler beim Stoppen der Vibration")
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            Timber.e("AlarmReceiver: Context ist null")
            return
        }
        
        Timber.i("🚨 ALARM EMPFANGEN! 🚨")
        
        // Stoppe vorherige Alarm-Medien falls vorhanden
        stopAlarmMedia()
        
        // Hole Schicht-Info aus Intent (falls verfügbar)
        val shiftName = intent?.getStringExtra("shift_name") ?: "Unbekannte Schicht"
        val alarmTime = intent?.getStringExtra("alarm_time") ?: "Jetzt"
        
        Timber.i("Alarm für Schicht: $shiftName um $alarmTime")
        
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
            val vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+): Use VibratorManager
                vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager?
                vibratorManager?.defaultVibrator?.let { defaultVibrator ->
                    if (defaultVibrator.hasVibrator()) {
                        val vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, 1)
                        vibratorManager?.vibrate(CombinedVibration.createParallel(vibrationEffect))
                        Timber.d("Vibration gestartet (VibratorManager)")
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ (API 26+): Use VibrationEffect with Vibrator
                @Suppress("DEPRECATION")
                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
                vibrator?.let {
                    if (it.hasVibrator()) {
                        val vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, 1)
                        it.vibrate(vibrationEffect)
                        Timber.d("Vibration gestartet (VibrationEffect)")
                    }
                }
            } else {
                // Android 7.1 and below (API < 26): Use legacy vibrate method
                @Suppress("DEPRECATION")
                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
                vibrator?.let {
                    if (it.hasVibrator()) {
                        @Suppress("DEPRECATION")
                        it.vibrate(vibrationPattern, 1) // Repeat from index 1
                        Timber.d("Vibration gestartet (Legacy)")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Starten der Vibration")
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
                        Timber.d("MediaPlayer vorbereitet, starte Wiedergabe")
                        try {
                            mp.start()
                        } catch (e: Exception) {
                            Timber.e(e, "Fehler beim Starten der Wiedergabe")
                        }
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Timber.e("MediaPlayer Fehler: what=$what, extra=$extra")
                        stopAlarmMedia()
                        true
                    }
                    
                    prepareAsync()
                }
                Timber.d("Alarmton wird vorbereitet...")
            } else {
                Timber.e("Kein Alarmton verfügbar")
            }
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Starten des Alarmtons")
        }
    }
    
    private fun showFullScreenAlarm(context: Context, shiftName: String, alarmTime: String) {
        try {
            val fullScreenIntent = Intent(context, AlarmFullScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("shift_name", shiftName)
                putExtra("alarm_time", alarmTime)
            }
            context.startActivity(fullScreenIntent)
            Timber.d("Vollbild-Alarm Activity gestartet")
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Starten der Vollbild-Activity")
        }
    }
    
    private fun showAlarmNotification(context: Context, shiftName: String, alarmTime: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Erstelle Channel falls nötig
            createAlarmNotificationChannel(notificationManager)
            
            // Stoppt-Intent für Notification-Action
            val stopIntent = Intent(context, AlarmStopService::class.java)
            val stopPendingIntent = PendingIntent.getService(
                context, 0, stopIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Vollbild-Intent für Tap auf Notification
            val fullScreenIntent = Intent(context, AlarmFullScreenActivity::class.java).apply {
                putExtra("shift_name", shiftName)
                putExtra("alarm_time", alarmTime)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 0, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Besseres Icon
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
            Timber.d("Alarm-Notification angezeigt")
            
        } catch (e: Exception) {
            Timber.e(e, "Fehler beim Anzeigen der Notification")
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
                Timber.d("Alarm NotificationChannel erstellt")
            }
        }
    }
}
