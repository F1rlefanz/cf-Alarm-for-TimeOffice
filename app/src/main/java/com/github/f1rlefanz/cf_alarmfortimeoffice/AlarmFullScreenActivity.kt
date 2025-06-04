package com.github.f1rlefanz.cf_alarmfortimeoffice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class AlarmFullScreenActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.d("AlarmFullScreenActivity onCreate")
        
        // Zeige über Sperrbildschirm und schalte Bildschirm an
        setupWindowFlags()
        
        // Hole Alarm-Daten aus Intent
        val shiftName = intent.getStringExtra("shift_name") ?: "Unbekannte Schicht"
        val alarmTime = intent.getStringExtra("alarm_time") ?: "Jetzt"
        
        setContent {
            CFAlarmForTimeOfficeTheme {
                AlarmFullScreenContent(
                    shiftName = shiftName,
                    alarmTime = alarmTime,
                    onStopClicked = { 
                        performHapticFeedback()
                        stopAlarmAndFinish() 
                    }
                )
            }
        }
    }
    
    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }
    }
    
    private fun performHapticFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            Timber.w(e, "Haptic feedback failed")
        }
    }
    
    private fun stopAlarmAndFinish() {
        Timber.d("Stoppe Alarm und beende Activity")
        
        // Stoppe Alarm über Service
        val stopIntent = Intent(this, AlarmStopService::class.java)
        startService(stopIntent)
        
        // Activity beenden
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("AlarmFullScreenActivity onDestroy")
    }
}

@Composable
fun AlarmFullScreenContent(
    shiftName: String,
    alarmTime: String,
    onStopClicked: () -> Unit
) {
    // Aktuelle Zeit für Anzeige
    var currentTime by remember { mutableStateOf(getCurrentTimeString()) }
    
    // Timer für Zeit-Update
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            currentTime = getCurrentTimeString()
        }
    }
    
    // Animationen
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val alarmPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarm_pulse_scale"
    )
    
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.red < 0.5f
    
    // Optimierte Farben für Dark/Light Mode
    val backgroundGradient = if (isDarkTheme) {
        Brush.radialGradient(
            colors = listOf(
                colorScheme.errorContainer.copy(alpha = 0.3f),
                colorScheme.background
            ),
            radius = 800f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                colorScheme.errorContainer.copy(alpha = 0.4f),
                colorScheme.surface.copy(alpha = 0.8f)
            ),
            radius = 800f
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Wecker-Bildschirm für $shiftName" }
        ) {
            // Animiertes Alarm Icon mit Puls-Effekt
            AnimatedAlarmIcon(
                scale = alarmPulse,
                isDarkTheme = isDarkTheme
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Alarm Titel mit Einblend-Animation
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { -40 },
                    animationSpec = tween(800, delayMillis = 200)
                ) + fadeIn(animationSpec = tween(800, delayMillis = 200))
            ) {
                Text(
                    text = "WECKER",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ),
                    color = if (isDarkTheme) 
                        colorScheme.onBackground 
                    else 
                        colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { 
                        contentDescription = "Wecker-Alarm aktiv" 
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Moderne Zeit-Anzeige
            ModernTimeDisplay(
                currentTime = currentTime,
                isDarkTheme = isDarkTheme
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Verbesserte Schicht-Info mit Animation
            EnhancedShiftInfo(
                shiftName = shiftName,
                alarmTime = alarmTime,
                isDarkTheme = isDarkTheme
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Verbesserter Stopp-Button mit Hover-Effekt
            EnhancedStopButton(
                onStopClicked = onStopClicked,
                isDarkTheme = isDarkTheme
            )
        }
    }
}

@Composable
private fun AnimatedAlarmIcon(
    scale: Float,
    isDarkTheme: Boolean
) {
    Card(
        modifier = Modifier
            .size(140.dp)
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme)
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = "Wecker-Symbol",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ModernTimeDisplay(
    currentTime: String,
    isDarkTheme: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme)
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Aktuelle Zeit: $currentTime" }
            )
        }
    }
}

@Composable
private fun EnhancedShiftInfo(
    shiftName: String,
    alarmTime: String,
    isDarkTheme: Boolean
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { 60 },
            animationSpec = tween(600, delayMillis = 400)
        ) + fadeIn(animationSpec = tween(600, delayMillis = 400))
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Work,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Zeit für:",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = shiftName,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        contentDescription = "Schicht: $shiftName"
                    }
                )
                
                if (alarmTime != "Jetzt") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Geplant für: $alarmTime",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedStopButton(
    onStopClicked: () -> Unit,
    isDarkTheme: Boolean
) {
    var isPressed by remember { mutableStateOf(false) }
    
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { 80 },
            animationSpec = tween(700, delayMillis = 600)
        ) + fadeIn(animationSpec = tween(700, delayMillis = 600))
    ) {
        Button(
            onClick = {
                isPressed = true
                onStopClicked()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .scale(if (isPressed) 0.95f else 1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 12.dp,
                pressedElevation = 8.dp
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onError.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                }
                Text(
                    text = "ALARM STOPPEN",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.semantics {
                        contentDescription = "Alarm stoppen Button"
                    }
                )
            }
        }
    }
    
    // Reset pressed state after animation
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

private fun getCurrentTimeString(): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date())
}
