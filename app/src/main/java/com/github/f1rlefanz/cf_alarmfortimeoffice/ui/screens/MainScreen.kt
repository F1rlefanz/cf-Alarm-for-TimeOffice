package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.f1rlefanz.cf_alarmfortimeoffice.MainActivity
import com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens.MainTabScreen
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel
import timber.log.Timber

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