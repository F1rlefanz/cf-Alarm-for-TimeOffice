package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.HueBridge
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.DiscoveryStatus
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.BridgeConnectionInfo
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.ErrorMessage
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.hue.AnimatedDiscoveryCard
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.hue.BridgeSetupModal
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.hue.BridgeSuccessCard
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.HueViewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.ViewModelFactory
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.theme.SpacingConstants
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.timing.UIConstants

/**
 * Enhanced Hue Tab Content with modern UX
 * Features animated discovery, interactive bridge setup modal, and success celebrations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueTabContent(
    viewModelFactory: ViewModelFactory,
    modifier: Modifier = Modifier
) {
    val hueViewModel: HueViewModel = viewModel(factory = viewModelFactory)
    val uiState by hueViewModel.uiState.collectAsState()
    val discoveryStatus by hueViewModel.discoveryStatus.collectAsState()

    // Modal state management
    var selectedBridge by remember { mutableStateOf<HueBridge?>(null) }
    var showBridgeSetupModal by remember { mutableStateOf(false) }
    var showSuccessCard by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    // Handle successful bridge connection
    LaunchedEffect(uiState.bridgeConnectionInfo?.isConnected) {
        if (uiState.bridgeConnectionInfo?.isConnected == true && isConnecting) {
            isConnecting = false
            showBridgeSetupModal = false
            showSuccessCard = true
            // Clear discovered bridges when connected to avoid UI confusion
            hueViewModel.clearDiscoveredBridges()
            kotlinx.coroutines.delay(UIConstants.ERROR_MESSAGE_AUTO_DISMISS_MS) // Show success for 5 seconds
            showSuccessCard = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpacingConstants.SPACING_LARGE),
            verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE)
        ) {
            // Header
            Text(
                text = "Philips Hue Integration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Error Display
            uiState.error?.let { error ->
                ErrorMessage(
                    message = error,
                    onDismiss = { hueViewModel.clearError() }
                )
            }

            // Enhanced Discovery Card (replaces boring loading)
            AnimatedDiscoveryCard(
                discoveryStatus = discoveryStatus,
                onCancel = { 
                    hueViewModel.clearDiscoveredBridges()
                }
            )

            // Success Celebration Card ODER Connected Status ODER Discovery
            when {
                // 1. Success Card (temporär für 5 Sekunden)
                showSuccessCard -> {
                    uiState.bridgeConnectionInfo?.let { connectionInfo ->
                        BridgeSuccessCard(
                            connectionInfo = connectionInfo,
                            isVisible = showSuccessCard,
                            onDismiss = { showSuccessCard = false }
                        )
                    }
                }
                
                // 2. Connected Status Card (permanent wenn connected)
                uiState.bridgeConnectionInfo?.isConnected == true && !showSuccessCard -> {
                    uiState.bridgeConnectionInfo?.let { connectionInfo ->
                        ConnectedStatusCard(
                            connectionInfo = connectionInfo,
                            onDisconnect = { 
                                hueViewModel.clearDiscoveredBridges()
                            }
                        )
                    }
                }
                
                // 3. Discovery Card (nur wenn NICHT connected und NICHT discovering)
                discoveryStatus?.stage != "N_UPNP_SEARCH" && 
                discoveryStatus?.stage != "MDNS_SEARCH" && 
                discoveryStatus?.stage != "STARTING" &&
                uiState.bridgeConnectionInfo?.isConnected != true -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_LARGE)
                    ) {
                        item {
                            EnhancedBridgeConnectionCard(
                                connectionInfo = uiState.bridgeConnectionInfo,
                                discoveredBridges = uiState.discoveredBridges,
                                onDiscoverBridges = { hueViewModel.discoverBridges() },
                                onSetupBridge = { bridge -> 
                                    selectedBridge = bridge
                                    showBridgeSetupModal = true
                                },
                                onDirectConnect = { bridge ->
                                    isConnecting = true
                                    hueViewModel.setupBridge(bridge)
                                },
                                onValidateConnection = { hueViewModel.validateBridgeConnection() },
                                onClearBridges = { hueViewModel.clearDiscoveredBridges() }
                            )
                        }
                    }
                }
            }
        }

        // Interactive Bridge Setup Modal
        BridgeSetupModal(
            bridge = selectedBridge,
            isVisible = showBridgeSetupModal,
            isConnecting = isConnecting,
            error = if (uiState.error?.contains("link button", ignoreCase = true) == true) 
                uiState.error else null,
            onDismiss = { 
                showBridgeSetupModal = false
                selectedBridge = null
                hueViewModel.clearError()
            },
            onConnect = {
                isConnecting = true
                selectedBridge?.let { bridge ->
                    hueViewModel.setupBridge(bridge)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedBridgeConnectionCard(
    connectionInfo: BridgeConnectionInfo?,
    discoveredBridges: List<HueBridge>,
    onDiscoverBridges: () -> Unit,
    onSetupBridge: (HueBridge) -> Unit,
    onDirectConnect: (HueBridge) -> Unit,
    onValidateConnection: () -> Unit,
    onClearBridges: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpacingConstants.SPACING_LARGE),
        verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_EXTRA_LARGE)
    ) {
        // Simple Header
        Text(
            text = "Bridge-Suche",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Connection Status - Clean and simple
        if (connectionInfo != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
            ) {
                Icon(
                    imageVector = if (connectionInfo.isConnected) 
                        Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (connectionInfo.isConnected) 
                        Color.Green else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(SpacingConstants.ICON_SIZE_LARGE)
                )
                Column {
                    Text(
                        text = if (connectionInfo.isConnected) "✅ Verbunden" else "❌ Nicht verbunden",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    connectionInfo.bridgeIp?.let { ip ->
                        Text(
                            text = ip,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Keine Bridge verbunden",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Discovered Bridges - Simplified
        if (discoveredBridges.isNotEmpty()) {
            Text(
                text = "${discoveredBridges.size} Bridge${if (discoveredBridges.size != 1) "s" else ""} gefunden:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            discoveredBridges.forEach { bridge ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(SpacingConstants.CARD_CORNER_RADIUS),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(SpacingConstants.SPACING_LARGE),
                        verticalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
                    ) {
                        // Bridge info - larger, readable text
                        Text(
                            text = bridge.name ?: "Philips Hue Bridge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = bridge.internalipaddress,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        // Action buttons - CLEAR unterschied zwischen Connect und Direct
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SpacingConstants.SPACING_MEDIUM)
                        ) {
                            // DIRECT Connect - sofort verbinden (für Link Button bereits gedrückt)
                            Button(
                                onClick = { onDirectConnect(bridge) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(SpacingConstants.SURFACE_CORNER_RADIUS)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(SpacingConstants.ICON_SIZE_SMALL)
                                )
                                Spacer(modifier = Modifier.width(SpacingConstants.SPACING_SMALL))
                                Text(
                                    text = "Jetzt verbinden",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            // SETUP Connect - zeigt Modal mit Anleitung
                            OutlinedButton(
                                onClick = { onSetupBridge(bridge) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(SpacingConstants.SURFACE_CORNER_RADIUS)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Help,
                                    contentDescription = null,
                                    modifier = Modifier.size(SpacingConstants.ICON_SIZE_SMALL)
                                )
                                Spacer(modifier = Modifier.width(SpacingConstants.SPACING_SMALL))
                                Text(
                                    text = "Hilfe?",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // KLARE Anleitung - was der User tun soll
                        Text(
                            text = "🔗 Link-Taste an der Bridge drücken, dann 'Jetzt verbinden' antippen",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Simple Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (connectionInfo?.isConnected != true) {
                Button(
                    onClick = onDiscoverBridges,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bridges suchen",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (connectionInfo?.isConnected == true) {
                OutlinedButton(
                    onClick = onValidateConnection,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Prüfen",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (discoveredBridges.isNotEmpty()) {
                TextButton(
                    onClick = onClearBridges,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Löschen",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedStatusCard(
    connectionInfo: BridgeConnectionInfo,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Success Icon
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Verbunden",
            tint = Color.Green,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = "🎉 Bridge verbunden!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Deine Hue Bridge ist bereit für die smarte Lichtsteuerung",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        // Bridge Details - Clean and simple
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                connectionInfo.bridgeName?.let { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                connectionInfo.bridgeIp?.let { ip ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = ip,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                connectionInfo.lastValidated?.let { timestamp ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Verbunden: ${
                                java.text.SimpleDateFormat("HH:mm:ss").format(timestamp)
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Disconnect controls
        OutlinedButton(
            onClick = onDisconnect,
            shape = RoundedCornerShape(SpacingConstants.SURFACE_CORNER_RADIUS)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(SpacingConstants.ICON_SIZE_MEDIUM)
            )
            Spacer(modifier = Modifier.width(SpacingConstants.SPACING_SMALL))
            Text(
                text = "Zurücksetzen",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
