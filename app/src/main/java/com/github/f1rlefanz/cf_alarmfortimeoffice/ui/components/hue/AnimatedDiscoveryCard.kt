package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.hue

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.DiscoveryStatus
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.SpacingConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.UIConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.AlphaValues

/**
 * Enhanced animated discovery card with visual progress indicators
 * Replaces boring circular progress with engaging animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedDiscoveryCard(
    discoveryStatus: DiscoveryStatus?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation states
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(UIConstants.ANIMATION_DURATION_MS.toInt(), easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    AnimatedVisibility(
        visible = discoveryStatus != null && !discoveryStatus.isComplete,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        discoveryStatus?.let { status ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpacingConstants.SPACING_LARGE),
                shape = RoundedCornerShape(SpacingConstants.FULLSCREEN_CORNER_RADIUS),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = SpacingConstants.CARD_CORNER_RADIUS
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpacingConstants.SPACING_EXTRA_LARGE),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_EXTRA_LARGE)
                ) {
                    // Animated Icon Section
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer pulsing circle
                        Box(
                            modifier = Modifier
                                .size(SpacingConstants.APP_ICON_SIZE)
                                .scale(pulseScale)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = AlphaValues.STRONG),
                                            MaterialTheme.colorScheme.primary.copy(alpha = AlphaValues.LIGHT),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        // Main discovery icon
                        Card(
                            modifier = Modifier.size(SpacingConstants.ICON_SIZE_GIANT),
                            shape = CircleShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            elevation = CardDefaults.cardElevation(SpacingConstants.SPACING_SMALL)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                when (status.stage) {
                                    "STARTING" -> Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .size(SpacingConstants.ICON_SIZE_EXTRA_LARGE)
                                            .scale(pulseScale * 0.8f)
                                    )
                                    "N_UPNP_SEARCH" -> Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(SpacingConstants.ICON_SIZE_EXTRA_LARGE)
                                    )
                                    "MDNS_SEARCH" -> Icon(
                                        imageVector = Icons.Default.Router,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(SpacingConstants.ICON_SIZE_EXTRA_LARGE)
                                    )
                                    "VALIDATING" -> Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(SpacingConstants.ICON_SIZE_EXTRA_LARGE)
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(SpacingConstants.ICON_SIZE_EXTRA_LARGE)
                                    )
                                }
                            }
                        }

                        // Scanning radar effect for mDNS
                        if (status.stage == "MDNS_SEARCH") {
                            Canvas(modifier = Modifier.size(SpacingConstants.FULLSCREEN_ELEMENT_SIZE)) {
                                // Add radar sweep animation here if needed
                            }
                        }
                    }

                    // Status Text with Typing Animation
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_SMALL)
                    ) {
                        Text(
                            text = getDiscoveryTitle(status.stage),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )

                        AnimatedTypingText(
                            text = status.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Progress Indicator
                    if (status.progress > 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_SMALL)
                        ) {
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SpacingConstants.SPACING_SMALL),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = AlphaValues.STRONG)
                            )
                            
                            Text(
                                text = "${(status.progress * 100).toInt()}% complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = AlphaValues.SURFACE_VARIANT)
                            )
                        }
                    }

                    // Bridge Counter with Animation
                    status.currentMethod?.let { method ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(SpacingConstants.SPACING_LARGE)
                        ) {
                            Row(
                                modifier = Modifier.padding(SpacingConstants.SPACING_MEDIUM),
                                horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_SMALL),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getMethodIcon(method),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(SpacingConstants.ICON_SIZE_SMALL)
                                )
                                Text(
                                    text = "Using $method",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Cancel Button
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SpacingConstants.ICON_SIZE_SMALL)
                        )
                        Spacer(modifier = Modifier.width(SpacingConstants.SPACING_SMALL))
                        Text("Cancel Discovery")
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedTypingText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    var displayedText by remember(text) { mutableStateOf("") }
    
    LaunchedEffect(text) {
        displayedText = ""
        for (i in text.indices) {
            displayedText = text.substring(0, i + 1)
            kotlinx.coroutines.delay(UIConstants.INPUT_DEBOUNCE_MS / 10) // Typing speed
        }
    }
    
    Text(
        text = displayedText,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier
    )
}

private fun getDiscoveryTitle(stage: String): String = when (stage) {
    "STARTING" -> "🔍 Starting Discovery"
    "N_UPNP_SEARCH" -> "🌐 Searching Online"
    "MDNS_SEARCH" -> "📡 Scanning Network"
    "VALIDATING" -> "✅ Validating Bridges"
    "COMPLETED" -> "🎉 Discovery Complete"
    "FAILED" -> "❌ Discovery Failed"
    else -> "🔍 Discovering Bridges"
}

private fun getMethodIcon(method: String): ImageVector = when (method.lowercase()) {
    "n-upnp" -> Icons.Default.Cloud
    "mdns" -> Icons.Default.Wifi
    "validation" -> Icons.Default.VerifiedUser
    else -> Icons.Default.Search
}
