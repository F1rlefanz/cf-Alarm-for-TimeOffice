package com.github.f1rlefanz.cf_alarmfortimeoffice.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import timber.log.Timber

data class SignInResult(
    val success: Boolean,
    val credentialResponse: GetCredentialResponse? = null,
    val error: String? = null,
    val exception: Throwable? = null
)

class CredentialAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val googleWebClientId = "931091152160-8s3nd7os2p61ac6ecm799gjhekkf0b4i.apps.googleusercontent.com"

    suspend fun signIn(): SignInResult {
        if (googleWebClientId.startsWith("ERSETZE_DIES") || googleWebClientId.isBlank()) {
            Timber.e("CredentialAuthManager: WICHTIG: Web Client ID wurde nicht korrekt in CredentialAuthManager.kt ersetzt!")
            return SignInResult(success = false, error = "Web Client ID nicht konfiguriert")
        }

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(googleWebClientId)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        Timber.d("CredentialAuthManager: Fordere Credentials an mit Scopes: EMAIL, PROFILE, calendar.readonly...")
        return try {
            val result = credentialManager.getCredential(context = context, request = request)
            Timber.d("CredentialAuthManager: Credential erfolgreich erhalten: ${result.credential.type}")
            SignInResult(success = true, credentialResponse = result)

        } catch (e: GetCredentialCancellationException) {
            Timber.w(e, "CredentialAuthManager: Fehler bei getCredential - Vom Nutzer abgebrochen.")
            SignInResult(success = false, error = "Sign-in was cancelled by the user.", exception = e)
        } catch (e: NoCredentialException) {
            Timber.w(e, "CredentialAuthManager: Fehler bei getCredential - Keine Credentials auf dem Gerät gefunden.")
            SignInResult(success = false, error = "No accounts found on this device. Please add a Google account.", exception = e)
        }
        catch (e: GetCredentialException) {
            Timber.e(e, "CredentialAuthManager: Fehler bei getCredential (Typ: ${e.type})")
            SignInResult(success = false, error = e.message ?: "Fehler bei der Anmeldung (${e.type})", exception = e)
        } catch (e: Exception) {
            Timber.e(e, "CredentialAuthManager: Allgemeiner Fehler bei der Anmeldung")
            SignInResult(success = false, error = e.message ?: "Allgemeiner Fehler bei der Anmeldung", exception = e)
        }
    }

    fun signOutLocally() {
        Timber.d("CredentialAuthManager: Lokaler Sign-Out (im ViewModel behandelt).")
    }

    fun extractUserInfo(response: GetCredentialResponse?): Triple<String?, String?, String?> {
        val credential = response?.credential
        if (credential is GoogleIdTokenCredential) {
            try {
                val userId = credential.id
                val displayName = credential.displayName
                val emailCandidate = credential.id
                Timber.d("CredentialAuthManager: UserInfo extrahiert: ID (oft Email)=${emailCandidate}, Name=$displayName")
                return Triple(userId, displayName, emailCandidate)
            } catch (e: Exception){
                Timber.e(e, "CredentialAuthManager: Allgemeiner Fehler beim Extrahieren von UserInfo aus GoogleIdTokenCredential")
            }
        }
        Timber.w("CredentialAuthManager: Konnte UserInfo nicht extrahieren, credential ist nicht vom Typ GoogleIdTokenCredential oder null.")
        return Triple(null, null, null)
    }
}