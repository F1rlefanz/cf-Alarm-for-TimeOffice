package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Modern animated countdown timer component
 */
@Composable
fun CountdownTimer(
    targetTime: LocalDateTime,
    modifier: Modifier = Modifier,
    onTimeUp: () -> Unit = {}
) {
    var timeRemaining by remember { mutableStateOf(Duration.ZERO) }
    var isExpired by remember { mutableStateOf(false) }
    
    // Update timer every second
    LaunchedEffect(targetTime) {
        while (true) {
            val now = LocalDateTime.now()
            val remaining = Duration.between(now, targetTime)
            
            timeRemaining = if (remaining.isNegative) {
                if (!isExpired) {
                    isExpired = true
                    onTimeUp()
                }
                Duration.ZERO
            } else {
                isExpired = false
                remaining
            }
            
            delay(1000)
        }
    }
    
    // Extract time components
    val days = timeRemaining.toDays()
    val hours = timeRemaining.toHours() % 24
    val minutes = (timeRemaining.toMinutes() % 60)
    
    // Animation states
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseScale = pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val urgencyColor = when {
        days > 0 -> MaterialTheme.colorScheme.primary
        hours > 12 -> MaterialTheme.colorScheme.primary
        hours > 6 -> MaterialTheme.colorScheme.secondary
        hours > 1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(pulseScale.value),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            urgencyColor.copy(alpha = 0.1f),
                            urgencyColor.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isExpired) Icons.Filled.NotificationsActive else Icons.Filled.Timer,
                        contentDescription = null,
                        tint = urgencyColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isExpired) "Zeit abgelaufen!" else "Zeit bis zum Wecker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Time display
                if (!isExpired) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (days > 0) {
                            TimeUnit(
                                value = days.toInt(),
                                unit = "Tage",
                                color = urgencyColor
                            )
                        }
                        
                        TimeUnit(
                            value = hours.toInt(),
                            unit = "Std",
                            color = urgencyColor,
                            isHighlighted = days == 0L
                        )
                        
                        TimeUnitSeparator()
                        
                        TimeUnit(
                            value = minutes.toInt(),
                            unit = "Min",
                            color = urgencyColor,
                            isHighlighted = days == 0L && hours == 0L
                        )
                    }
                } else {
                    // Expired state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val blink = rememberInfiniteTransition(label = "blink")
                        val alpha = blink.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "blinkAlpha"
                        )
                        
                        Text(
                            text = "ALARM AKTIV!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.alpha(alpha.value)
                        )
                    }
                }
                
                // Target time display
                Text(
                    text = targetTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TimeUnit(
    value: Int,
    unit: String,
    color: Color,
    isHighlighted: Boolean = false
) {
    val displayValue = value.toString().padStart(2, '0')
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (isHighlighted) 80.dp else 72.dp,
                    height = if (isHighlighted) 80.dp else 72.dp
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    color = if (isHighlighted) 
                        color.copy(alpha = 0.15f) 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .then(
                    if (isHighlighted) 
                        Modifier.border(2.dp, color, RoundedCornerShape(16.dp))
                    else 
                        Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayValue,
                fontSize = if (isHighlighted) 28.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) color else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TimeUnitSeparator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.height(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CountdownTimerPreview() {
    CFAlarmForTimeOfficeTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // In 2 days
            CountdownTimer(
                targetTime = LocalDateTime.now().plusDays(2).plusHours(5).plusMinutes(30)
            )
            
            // In 5 hours
            CountdownTimer(
                targetTime = LocalDateTime.now().plusHours(5).plusMinutes(15)
            )
            
            // In 45 minutes
            CountdownTimer(
                targetTime = LocalDateTime.now().plusMinutes(45).plusSeconds(30)
            )
            
            // In 30 seconds
            CountdownTimer(
                targetTime = LocalDateTime.now().plusSeconds(30)
            )
            
            // Expired
            CountdownTimer(
                targetTime = LocalDateTime.now().minusMinutes(5)
            )
        }
    }
}
