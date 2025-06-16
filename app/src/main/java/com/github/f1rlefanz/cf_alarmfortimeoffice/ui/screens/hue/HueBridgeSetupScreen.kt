package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.hue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.DiscoveryMethod
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.DiscoveryStatusCard
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.HueViewModel
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.SetupStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueBridgeSetupScreen(
    viewModel: HueViewModel,
    onSetupComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showManualInput by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Hue Bridge Setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        when (uiState.setupStep) {
            SetupStep.DISCOVERY -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Bridge Suche",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Show discovery status card when loading
                            if (uiState.isLoading && uiState.discoveryStatus.currentMethod != DiscoveryMethod.IDLE) {
                                DiscoveryStatusCard(
                                    discoveryStatus = uiState.discoveryStatus,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Text(
                                    text = if (uiState.isLoading) 
                                        "Suche nach Philips Hue Bridges in Ihrem Netzwerk..." 
                                    else if (uiState.discoveredBridges.isEmpty()) 
                                        "Keine Bridges gefunden. Starten Sie eine neue Suche oder geben Sie die IP manuell ein."
                                    else 
                                        "Wählen Sie eine Bridge aus der Liste unten.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            

                            
                            if (uiState.isLoading && uiState.discoveryStatus.currentMethod == DiscoveryMethod.IDLE) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                    
                                    OutlinedButton(
                                        onClick = { viewModel.stopDiscovery() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Suche stoppen")
                                    }
                                }
                            } else if (uiState.isLoading) {
                                OutlinedButton(
                                    onClick = { viewModel.stopDiscovery() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Suche stoppen")
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.discoverBridges() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Nach Bridges suchen")
                                }
                            }
                        }
                    }
                }
                
                // Discovered bridges
                if (uiState.discoveredBridges.isNotEmpty()) {
                    item {
                        Text(
                            text = "Gefundene Bridges:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    items(uiState.discoveredBridges) { bridge ->
                        Card(
                            onClick = { viewModel.connectToBridge(bridge.internalipaddress) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Hue Bridge",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = bridge.internalipaddress,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Select"
                                )
                            }
                        }
                    }
                }
                
                // Manual IP input option
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Manuelle Eingabe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (showManualInput) {
                                OutlinedTextField(
                                    value = manualIp,
                                    onValueChange = { manualIp = it },
                                    label = { Text("Bridge IP-Adresse") },
                                    placeholder = { Text("z.B. 192.168.1.100") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { 
                                            showManualInput = false
                                            manualIp = ""
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Abbrechen")
                                    }
                                    Button(
                                        onClick = { 
                                            if (manualIp.isNotBlank()) {
                                                viewModel.connectToBridge(manualIp)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = manualIp.isNotBlank()
                                    ) {
                                        Text("Verbinden")
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showManualInput = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("IP-Adresse manuell eingeben")
                                }
                            }
                        }
                    }
                }
            }
            
            SetupStep.LINK_BUTTON -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                text = "Link-Button drücken",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            
                            Text(
                                text = "Bitte drücken Sie den Link-Button auf Ihrer Hue Bridge und klicken Sie dann auf 'Verbinden'.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            
                            if (uiState.isLoading) {
                                CircularProgressIndicator()
                            } else {
                                Button(
                                    onClick = { viewModel.createUser() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Verbinden")
                                }
                            }
                        }
                    }
                }
            }
            
            SetupStep.COMPLETE -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                text = "Verbindung hergestellt!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Text(
                                text = "Ihre Hue Bridge wurde erfolgreich verbunden.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            
                            Button(
                                onClick = onSetupComplete,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Fertig")
                            }
                        }
                    }
                }
            }
        }
        
        // Error display
        uiState.error?.let { error ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
