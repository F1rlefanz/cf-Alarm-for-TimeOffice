package com.github.f1rlefanz.cf_alarmfortimeoffice.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager

class AuthViewModelFactory(
    private val application: Application, // Application übergeben
    private val credentialAuthManager: CredentialAuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(application, credentialAuthManager) as T // Application übergeben
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}