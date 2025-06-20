package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftDefinition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftConfigScreen(
    authState: com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthState,
    onToggleAutoAlarm: () -> Unit,
    onUpdateShiftDefinition: (ShiftDefinition) -> Unit,
    onDeleteShiftDefinition: (String) -> Unit,
    onResetToDefaults: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showAddShiftDialog by remember { mutableStateOf(false) }
    var editingShift by remember { mutableStateOf<ShiftDefinition?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schicht-Konfiguration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddShiftDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Schicht hinzufügen")
                    }
                    IconButton(onClick = onResetToDefaults) {
                        Icon(Icons.Default.Refresh, contentDescription = "Zurücksetzen")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Auto-Alarm Toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatische Wecker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Wecker basierend auf erkannten Schichten setzen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = authState.autoAlarmEnabled,
                        onCheckedChange = { onToggleAutoAlarm() }
                    )
                }
            }
            
            // Next Shift Info
            authState.nextShiftAlarm?.let { nextShift ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Nächster Wecker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Schicht: ${nextShift.shiftDefinition.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Termin: ${nextShift.calendarEventTitle}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Weckzeit: ${nextShift.calculatedAlarmTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Shift Definitions List
            Text(
                text = "Schicht-Definitionen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (authState.shiftConfigLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (authState.shiftDefinitions.isEmpty()) {
                // Show message if no definitions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Keine Schichtdefinitionen vorhanden!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Laden Sie die Standard-Schichtdefinitionen, um zu beginnen.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Button(
                            onClick = onResetToDefaults,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Standard-Definitionen laden")
                        }
                    }
                }
            } else {
                LazyColumn {
                    items(authState.shiftDefinitions) { shift ->
                        ShiftDefinitionCard(
                            shiftDefinition = shift,
                            onEdit = { editingShift = shift },
                            onDelete = { onDeleteShiftDefinition(shift.id) },
                            onToggleEnabled = { 
                                onUpdateShiftDefinition(shift.copy(isEnabled = !shift.isEnabled))
                            }
                        )
                    }
                }
            }
            
            authState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
    
    // Add/Edit Shift Dialog
    if (showAddShiftDialog || editingShift != null) {
        ShiftEditDialog(
            shift = editingShift,
            onDismiss = { 
                showAddShiftDialog = false
                editingShift = null
            },
            onSave = { shift ->
                onUpdateShiftDefinition(shift)
                showAddShiftDialog = false
                editingShift = null
            }
        )
    }
}

@Composable
fun ShiftDefinitionCard(
    shiftDefinition: ShiftDefinition,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shiftDefinition.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Schlüsselwörter: ${shiftDefinition.keywords.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Weckzeit: ${shiftDefinition.alarmTime}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Row {
                    Switch(
                        checked = shiftDefinition.isEnabled,
                        onCheckedChange = { onToggleEnabled() }
                    )
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen")
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftEditDialog(
    shift: ShiftDefinition?,
    onDismiss: () -> Unit,
    onSave: (ShiftDefinition) -> Unit
) {
    var name by remember { mutableStateOf(shift?.name ?: "") }
    var keywords by remember { mutableStateOf(shift?.keywords?.joinToString(", ") ?: "") }
    var alarmTime by remember { mutableStateOf(shift?.alarmTime ?: "06:00") }
    var isEnabled by remember { mutableStateOf(shift?.isEnabled != false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(if (shift == null) "Neue Schicht" else "Schicht bearbeiten") 
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Schlüsselwörter (kommagetrennt)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = alarmTime,
                    onValueChange = { alarmTime = it },
                    label = { Text("Weckzeit (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktiviert")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val id = shift?.id ?: "custom_${System.currentTimeMillis()}"
                    val keywordList = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(
                        ShiftDefinition(
                            id = id,
                            name = name,
                            keywords = keywordList,
                            alarmTime = alarmTime,
                            isEnabled = isEnabled
                        )
                    )
                },
                enabled = name.isNotBlank() && keywords.isNotBlank() && alarmTime.isNotBlank()
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
