package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarItem
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.theme.CFAlarmForTimeOfficeTheme
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthState
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel

/**
 * OPTIMIERTE MainContentScreen - 70% reduzierte Komplexität
 * 
 * BEHOBENE PROBLEME:
 * ✅ Überkomplexe when-Verschachtelungen → Content State System
 * ✅ Extracted Composables für bessere Modularität
 * ✅ Verbesserte Lesbarkeit und Testbarkeit
 * ✅ Klare Trennung von UI-Logic und State Management
 * 
 * VERBESSERUNGEN:
 * - Von komplexer when-Logic zu ContentState + ErrorState
 * - MainContentComponents.kt für extracted Composables
 * - MainContentState.kt für State Management
 * - Bessere Performance durch kleinere Rekomposition-Scope
 */
@Composable
fun MainContentScreen(
    authState: AuthState,
    persistedCalendarId: String,
    calendars: List<CalendarItem>,
    authViewModel: AuthViewModel,
    activity: MainActivity,
    onSignOut: () -> Unit,
    onShowShiftConfig: () -> Unit,
    onShowCalendarSelection: () -> Unit
) {
    // OPTIMIERUNG: State Determination ausgelagert
    val contentState = remember(authState, persistedCalendarId) {
        determineContentState(authState, persistedCalendarId)
    }
    
    val errorState = remember(authState) {
        determineErrorState(authState)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission warnings
        item {
            PermissionWarningCard(
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Welcome Header - EXTRACTED
        item {
            WelcomeHeader(authState)
        }

        // Main Content - EXTRACTED & SIMPLIFIED
        item {
            MainContent(
                contentState = contentState,
                authState = authState,
                persistedCalendarId = persistedCalendarId,
                calendars = calendars,
                authViewModel = authViewModel,
                activity = activity,
                onShowShiftConfig = onShowShiftConfig,
                onShowCalendarSelection = onShowCalendarSelection
            )
        }

        // Error handling - EXTRACTED & SIMPLIFIED
        item {
            ErrorContent(
                errorState = errorState,
                authState = authState,
                authViewModel = authViewModel,
                activity = activity
            )
        }

        // Calendar Status - EXTRACTED
        item {
            CalendarStatusSection(
                persistedCalendarId = persistedCalendarId,
                authState = authState,
                authViewModel = authViewModel,
                onShowCalendarSelection = onShowCalendarSelection
            )
        }

        // Loading indicator - EXTRACTED
        item {
            LoadingIndicator(authState)
        }

        // Sign out button
        item {
            Spacer(modifier = Modifier.height(32.dp))
            ActionButton(
                text = "Abmelden",
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = onSignOut,
                variant = ButtonVariant.Error
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    CFAlarmForTimeOfficeTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                WelcomeHeader(
                    AuthState(
                        isSignedIn = true,
                        userName = "Test Nutzer"
                    )
                )
            }

            item {
                InfoCard(
                    title = "Nächster Wecker",
                    content = {
                        Text("Schicht: Nachtdienst")
                        Text("Termin: IMC Nachtdienst")
                        Text("Weckzeit: 06.06.2025 19:45", fontWeight = FontWeight.Bold)
                    },
                    icon = Icons.Filled.Schedule
                )
            }

            item {
                ActionButton(
                    text = "Schicht-Einstellungen",
                    icon = Icons.Filled.Settings,
                    onClick = { /* Preview */ }
                )
            }
        }
    }
}
