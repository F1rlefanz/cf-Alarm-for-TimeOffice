package com.github.f1rlefanz.cf_alarmfortimeoffice

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmStopService
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.SpacingConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.AnimationDurations
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import java.text.SimpleDateFormat
import java.util.*

class AlarmFullScreenActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.d(LogTags.ALARM, "AlarmFullScreenActivity onCreate")

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
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            Logger.w(LogTags.ALARM, "Haptic feedback failed: ${e.message}")
        }
    }
    
    private fun stopAlarmAndFinish() {
        Logger.d(LogTags.ALARM, "Stoppe Alarm und beende Activity")
        
        // Stoppe Alarm über Service
        val stopIntent = Intent(this, AlarmStopService::class.java)
        startService(stopIntent)
        
        // Activity beenden
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.d(LogTags.ALARM, "AlarmFullScreenActivity onDestroy")
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
            kotlinx.coroutines.delay(AnimationDurations.TIMER_UPDATE_MS)
            currentTime = getCurrentTimeString()
        }
    }
    
    // Animationen
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val alarmPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(AnimationDurations.PULSE_MS.toInt(), easing = EaseInOutSine),
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
            .padding(SpacingConstants.SPACING_EXTRA_LARGE),
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
            
            Spacer(modifier = Modifier.height(SpacingConstants.SPACING_XXL))
            
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
            
            Spacer(modifier = Modifier.height(SpacingConstants.SPACING_EXTRA_LARGE))
            
            // Moderne Zeit-Anzeige
            ModernTimeDisplay(
                currentTime = currentTime,
                isDarkTheme = isDarkTheme
            )
            
            Spacer(modifier = Modifier.height(SpacingConstants.SPACING_XXL))
            
            // Verbesserte Schicht-Info mit Animation
            EnhancedShiftInfo(
                shiftName = shiftName,
                alarmTime = alarmTime,
                isDarkTheme = isDarkTheme
            )
            
            Spacer(modifier = Modifier.height(SpacingConstants.SPACING_XXXL))
            
            // Verbesserter Stopp-Button mit Hover-Effekt
            EnhancedStopButton(
                onStopClicked = onStopClicked
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
            .size(SpacingConstants.FULLSCREEN_ELEMENT_SIZE)
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme)
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = SpacingConstants.CARD_ELEVATION * 4),
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = "Wecker-Symbol",
                modifier = Modifier.size(SpacingConstants.ICON_SIZE_GIANT - SpacingConstants.SPACING_LARGE),
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
        elevation = CardDefaults.cardElevation(defaultElevation = SpacingConstants.CARD_ELEVATION * 3),
        shape = RoundedCornerShape(SpacingConstants.FULLSCREEN_CORNER_RADIUS)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingConstants.SPACING_XXXL - SpacingConstants.SPACING_EXTRA_SMALL),
            horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(SpacingConstants.ICON_SIZE_XXL)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(SpacingConstants.SPACING_XXXL - SpacingConstants.SPACING_LARGE),
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
            elevation = CardDefaults.cardElevation(defaultElevation = SpacingConstants.SPACING_SMALL),
            shape = RoundedCornerShape(SpacingConstants.CARD_CORNER_RADIUS + SpacingConstants.SPACING_EXTRA_SMALL)
        ) {
            Column(
                modifier = Modifier.padding(SpacingConstants.SPACING_EXTRA_LARGE),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                        modifier = Modifier.size(SpacingConstants.ICON_SIZE_EXTRA_LARGE + SpacingConstants.SPACING_EXTRA_SMALL)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Work,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(SpacingConstants.ICON_SIZE_STANDARD)
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
                
                Spacer(modifier = Modifier.height(SpacingConstants.SPACING_MEDIUM))
                
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
                    Spacer(modifier = Modifier.height(SpacingConstants.SPACING_MEDIUM))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_SMALL)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(SpacingConstants.ICON_SIZE_MEDIUM)
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
    onStopClicked: () -> Unit
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
                .height(SpacingConstants.BUTTON_HEIGHT_FULLSCREEN)
                .scale(if (isPressed) 0.95f else 1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(SpacingConstants.FULLSCREEN_CORNER_RADIUS),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = SpacingConstants.CARD_ELEVATION * 3,
                pressedElevation = SpacingConstants.SPACING_SMALL
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onError.copy(alpha = 0.2f),
                    modifier = Modifier.size(SpacingConstants.ICON_SIZE_EXTRA_LARGE + SpacingConstants.SPACING_SMALL)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(SpacingConstants.ICON_SIZE_LARGE),
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
            kotlinx.coroutines.delay(AnimationDurations.QUICK_MS)
            isPressed = false
        }
    }
}

private fun getCurrentTimeString(): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date())
}
