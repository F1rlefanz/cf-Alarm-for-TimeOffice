package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarItem // Korrekter Import!
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class) // Für CenterAlignedTopAppBar, falls du es später hinzufügst
@Composable
fun CalendarSelectionScreen(
    calendars: List<CalendarItem>,
    selectedCalendarId: String,
    onCalendarSelected: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onCancelClicked: () -> Unit, // Hinzugefügt für bessere UX
    isLoading: Boolean // Um Ladezustand anzuzeigen
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Dienstplan-Kalender wählen") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // paddingValues vom Scaffold übernehmen
                .padding(16.dp) // Zusätzliches Padding für den Inhalt
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Timber.d("CalendarSelectionScreen: Zeige Ladeindikator.")
                }
            } else if (calendars.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine Kalender gefunden oder Zugriff verweigert. Bitte überprüfe die Berechtigungen und versuche es erneut.")
                    Timber.d("CalendarSelectionScreen: Keine Kalender zum Anzeigen.")
                }
            } else {
                Timber.d("CalendarSelectionScreen: Zeige ${calendars.size} Kalender. Ausgewählt: $selectedCalendarId")
                LazyColumn(modifier = Modifier.weight(1.0f)) { // weight damit die Buttons unten bleiben
                    items(calendars) { calendar ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (calendar.id == selectedCalendarId),
                                    onClick = {
                                        Timber.d("CalendarSelectionScreen: Kalender ausgewählt - ID: ${calendar.id}, Name: ${calendar.displayName}")
                                        onCalendarSelected(calendar.id)
                                    }
                                )
                                .padding(vertical = 8.dp), // Etwas mehr vertikales Padding
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (calendar.id == selectedCalendarId),
                                onClick = {
                                    Timber.d("CalendarSelectionScreen: RadioButton geklickt - ID: ${calendar.id}")
                                    onCalendarSelected(calendar.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = calendar.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End // Buttons rechtsbündig
                ) {
                    TextButton(onClick = {
                        Timber.d("CalendarSelectionScreen: Abbrechen geklickt.")
                        onCancelClicked()
                    }) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            Timber.d("CalendarSelectionScreen: Speichern geklickt mit ID: $selectedCalendarId")
                            onSaveClicked()
                        },
                        enabled = selectedCalendarId.isNotBlank() // Speichern nur aktiv, wenn etwas ausgewählt ist
                    ) {
                        Text("Auswahl speichern")
                    }
                }
            }
        }
    }
}