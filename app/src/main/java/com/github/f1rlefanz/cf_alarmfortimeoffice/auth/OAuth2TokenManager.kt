package com.github.f1rlefanz.cf_alarmfortimeoffice.auth

import android.app.Activity
import android.content.Context
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.data.TokenData
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.storage.TokenStorageRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * OAuth2TokenManager - Manages long-lived token authentication for Google APIs.
 * 
 * Replaces CredentialAuthManager with proper OAuth2 flow including refresh tokens.
 * Follows architectural principles:
 * - Single Responsibility: Only handles OAuth2 token management
 * - Loose Coupling: Uses TokenStorageRepository for persistence
 * - Error Resilience: Comprehensive error handling with graceful degradation
 */
class OAuth2TokenManager(
    private val context: Context,
    private val tokenStorage: TokenStorageRepository
) {
    
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .build()
        
        GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Performs sign-in and gets access token using existing Google Sign-In.
     * Simplified approach that works with current Google Sign-In setup.
     */
    suspend fun signInWithAuthCode(activity: Activity): AuthResult = withContext(Dispatchers.IO) {
        try {
            Logger.d(LogTags.OAUTH, "Starting sign-in flow")
            
            // Get currently signed-in account
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                Logger.w(LogTags.OAUTH, "No Google account signed in")
                return@withContext AuthResult.Failure("No signed-in account found - please sign in first")
            }
            
            // Get access token for Calendar API
            val accessToken = try {
                GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    "oauth2:${CalendarScopes.CALENDAR_READONLY}"
                )
            } catch (e: Exception) {
                Logger.e(LogTags.OAUTH, "Failed to get access token", e)
                return@withContext AuthResult.Failure("Failed to get access token: ${e.localizedMessage}")
            }
            
            // Create token data (Google APIs handle refresh automatically)
            val tokenData = TokenData.fromOAuthResponse(
                accessToken = accessToken,
                refreshToken = "managed_by_google", // Placeholder - Google handles refresh
                expiresInSeconds = 3600L, // 1 hour default
                scope = CalendarScopes.CALENDAR_READONLY
            )
            
            // Store token
            val storeResult = tokenStorage.saveToken(tokenData)
            if (storeResult.isFailure) {
                Logger.e(LogTags.TOKEN, "Failed to store tokens: ${storeResult.exceptionOrNull()}")
                return@withContext AuthResult.Failure("Failed to store authentication tokens")
            }
            
            // Create user info
            val userInfo = UserInfo(
                email = account.email ?: "",
                displayName = account.displayName ?: "",
                id = account.id ?: ""
            )
            
            Logger.business(LogTags.OAUTH, "Sign-in successful", userInfo.email)
            AuthResult.Success(userInfo, tokenData)
            
        } catch (e: Exception) {
            Logger.e(LogTags.OAUTH, "Sign-in failed", e)
            AuthResult.Failure("Sign-in failed: ${e.localizedMessage}")
        }
    }
    
    /**
     * Gets current valid access token, automatically refreshing if needed.
     * This is the core method for API access.
     */
    suspend fun getCurrentValidToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentToken = tokenStorage.getCurrentToken()
            
            when {
                currentToken == null -> {
                    Logger.w(LogTags.TOKEN, "No token available")
                    Result.failure(Exception("No authentication token available"))
                }
                
                currentToken.isValid() -> {
                    // Token is still valid
                    Logger.dThrottled(LogTags.TOKEN, "Using valid access token")
                    Result.success(currentToken.accessToken)
                }
                
                currentToken.canRefresh() -> {
                    // Token expired, try to refresh
                    Logger.d(LogTags.TOKEN, "Access token expired, attempting refresh")
                    refreshAccessToken(currentToken.refreshToken!!)
                }
                
                else -> {
                    Logger.w(LogTags.TOKEN, "Token expired and cannot be refreshed")
                    Result.failure(Exception("Authentication expired - re-login required"))
                }
            }
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "Error getting valid token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Refreshes access token using Google's built-in token management.
     * Google APIs automatically handle token refresh, so we get a fresh token.
     */
    suspend fun refreshAccessToken(refreshToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Logger.d(LogTags.TOKEN, "Refreshing access token")
            
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                Logger.e(LogTags.TOKEN, "No Google account available for token refresh")
                return@withContext Result.failure(Exception("No Google account available"))
            }
            
            // Clear cached token to force fresh token request
            GoogleAuthUtil.clearToken(context, refreshToken)
            
            // Get fresh access token
            val newAccessToken = GoogleAuthUtil.getToken(
                context,
                account.account!!,
                "oauth2:${CalendarScopes.CALENDAR_READONLY}"
            )
            
            val newExpiresAt = System.currentTimeMillis() + (3600L * 1000) // 1 hour
            
            // Update stored token
            val updateResult = tokenStorage.updateAccessToken(
                newAccessToken = newAccessToken,
                newExpiresAt = newExpiresAt
            )
            
            if (updateResult.isFailure) {
                Logger.e(LogTags.TOKEN, "Failed to update stored token: ${updateResult.exceptionOrNull()}")
                return@withContext Result.failure(Exception("Failed to update stored token"))
            }
            
            Logger.business(LogTags.TOKEN, "Access token refreshed successfully")
            Result.success(newAccessToken)
            
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "Failed to refresh access token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Signs out user and clears all stored tokens.
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Logger.d(LogTags.AUTH, "Signing out")
            
            // Clear tokens from storage
            val clearResult = tokenStorage.clearToken()
            
            // Sign out from Google client
            try {
                googleSignInClient.signOut()
                Logger.d(LogTags.AUTH, "Google Sign-In client signed out")
            } catch (e: Exception) {
                Logger.w(LogTags.AUTH, "Error signing out from Google client (continuing anyway)", e)
            }
            
            if (clearResult.isSuccess) {
                Logger.business(LogTags.AUTH, "Sign-out successful")
                Result.success(Unit)
            } else {
                Logger.e(LogTags.AUTH, "Failed to clear tokens: ${clearResult.exceptionOrNull()}")
                Result.failure(Exception("Failed to clear authentication data"))
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.AUTH, "Error during sign-out", e)
            Result.failure(e)
        }
    }
    
    /**
     * Checks if user is currently authenticated with valid token.
     */
    suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentToken = tokenStorage.getCurrentToken()
            val isValid = currentToken?.isValid() ?: false
            Logger.dThrottled(LogTags.AUTH, "Authentication check: hasToken=${currentToken != null}, isValid=$isValid")
            isValid
        } catch (e: Exception) {
            Logger.e(LogTags.AUTH, "Error checking authentication status", e)
            false
        }
    }
    
    /**
     * Gets current user info if available.
     */
    fun getCurrentUserInfo(): UserInfo? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                UserInfo(
                    email = account.email ?: "",
                    displayName = account.displayName ?: "",
                    id = account.id ?: ""
                )
            } else null
        } catch (e: Exception) {
            Logger.e(LogTags.AUTH, "Error getting current user info", e)
            null
        }
    }
}

/**
 * Result of authentication operations
 */
sealed class AuthResult {
    data class Success(val userInfo: UserInfo, val tokenData: TokenData) : AuthResult()
    data class Failure(val error: String) : AuthResult()
}

/**
 * User information from authentication
 */
data class UserInfo(
    val email: String,
    val displayName: String,
    val id: String
)
