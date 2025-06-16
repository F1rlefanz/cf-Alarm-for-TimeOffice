package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.components.LoadingScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel

@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    activity: MainActivity
) {
    val authState by authViewModel.authState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            authState.isLoading && !authState.isSignedIn -> {
                LoadingScreen(message = "Lade Anmeldestatus...")
            }

            authState.isSignedIn -> {
                MainAppNavigator(
                    authViewModel = authViewModel,
                    onSignOut = { authViewModel.signOut() },
                    activity = activity
                )
            }

            else -> {
                LoginScreen(authViewModel = authViewModel)
            }
        }
    }
}

@Composable
private fun MainAppNavigator(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    activity: MainActivity
) {
    MainTabScreen(
        authViewModel = authViewModel,
        activity = activity,
        onSignOut = onSignOut
    )
}