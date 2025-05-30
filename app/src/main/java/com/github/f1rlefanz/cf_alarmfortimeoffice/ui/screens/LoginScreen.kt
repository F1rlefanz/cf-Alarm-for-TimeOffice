package com.github.f1rlefanz.cf_alarmfortimeoffice.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current

    // Zeige Fehler als Toast an
    LaunchedEffect(authState.error) {
        authState.error?.let {
            if (it != "Sign-in was cancelled by the user.") { // Optional: Nutzerabbruch nicht als Fehler anzeigen
                Toast.makeText(context, "Fehler: $it", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (authState.isLoading) {
            CircularProgressIndicator()
        } else {
            // Der Button wird angezeigt, solange der Nutzer nicht eingeloggt ist.
            // MainActivity wechselt den Screen bei erfolgreichem Login.
            Text("Bitte melde dich an, um die App zu nutzen.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { authViewModel.startSignIn() }) {
                Text("Mit Google anmelden")
            }

            // Optional: Zeige Fehlermeldung dauerhaft an
            // authState.error?.let { errorMsg ->
            //     Spacer(modifier = Modifier.height(16.dp))
            //     Text(text = "Fehler: $errorMsg", color = MaterialTheme.colorScheme.error)
            // }
        }
    }
}