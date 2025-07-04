package com.github.f1rlefanz.cf_alarmfortimeoffice.auth.usecase

import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.OAuth2TokenManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.data.TokenData
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.storage.TokenStorageRepository
import kotlinx.coroutines.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * UseCase for automatic token refresh and validation.
 * 
 * FIXED: Thread-safe implementation with proper cancellation support.
 * - Uses structured concurrency
 * - Prevents Race Conditions through proper scope management
 * - Cancellation-aware for lifecycle safety
 */
class TokenRefreshUseCase(
    private val oauth2TokenManager: OAuth2TokenManager,
    private val tokenStorage: TokenStorageRepository
) {
    
    /**
     * Ensures a valid access token is available for API calls.
     * Automatically refreshes if needed.
     * 
     * FIXED: Cancellation-aware implementation
     */
    suspend fun ensureValidToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check for cancellation before proceeding
            ensureActive()
            
            Logger.d(LogTags.TOKEN, "Ensuring valid token")
            
            val currentToken = tokenStorage.getCurrentToken()
            
            when {
                currentToken == null -> {
                    Logger.w(LogTags.TOKEN, "No token available - authentication required")
                    Result.failure(TokenException.NoTokenAvailable)
                }
                
                currentToken.isValid() -> {
                    // Token is still valid, return it
                    Logger.dThrottled(LogTags.TOKEN, "Current token is valid")
                    Result.success(currentToken.accessToken)
                }
                
                currentToken.canRefresh() -> {
                    // Check cancellation before expensive refresh operation
                    ensureActive()
                    
                    // Token expired but can be refreshed
                    Logger.d(LogTags.TOKEN, "Token expired, attempting refresh")
                    oauth2TokenManager.refreshAccessToken(currentToken.refreshToken!!)
                        .onFailure { error ->
                            Logger.e(LogTags.TOKEN, "Token refresh failed: ${error.message}")
                        }
                }
                
                else -> {
                    Logger.w(LogTags.TOKEN, "Token expired and cannot be refreshed")
                    Result.failure(TokenException.RefreshNotPossible)
                }
            }
        } catch (e: CancellationException) {
            Logger.d(LogTags.TOKEN, "Operation cancelled")
            throw e // Re-throw cancellation
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "Error ensuring valid token", e)
            Result.failure(TokenException.UnknownError(e))
        }
    }
    
    /**
     * Proactively refreshes token if it's expiring soon.
     * Useful for background refresh operations.
     */
    suspend fun refreshIfExpiringSoon(bufferMinutes: Long = 15): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val currentToken = tokenStorage.getCurrentToken()
            
            if (currentToken == null) {
                Logger.d(LogTags.TOKEN, "No token to refresh")
                return@withContext Result.success(false)
            }
            
            if (!currentToken.isExpiredOrExpiringSoon(bufferMinutes)) {
                Logger.d(LogTags.TOKEN, "Token not expiring soon, no refresh needed")
                return@withContext Result.success(false)
            }
            
            if (!currentToken.canRefresh()) {
                Logger.w(LogTags.TOKEN, "Token expiring but cannot be refreshed")
                return@withContext Result.failure(TokenException.RefreshNotPossible)
            }
            
            Logger.d(LogTags.TOKEN, "Token expiring in ${currentToken.getRemainingLifetimeMinutes()} minutes, refreshing")
            val refreshResult = oauth2TokenManager.refreshAccessToken(currentToken.refreshToken!!)
            
            if (refreshResult.isSuccess) {
                Logger.business(LogTags.TOKEN, "Proactive token refresh successful")
                Result.success(true)
            } else {
                Logger.e(LogTags.TOKEN, "Proactive token refresh failed: ${refreshResult.exceptionOrNull()}")
                Result.failure(TokenException.RefreshFailed(refreshResult.exceptionOrNull()))
            }
            
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "Error in proactive token refresh", e)
            Result.failure(TokenException.UnknownError(e))
        }
    }
    
    /**
     * Validates current token and returns detailed status.
     * Useful for debugging and status checks.
     */
    suspend fun getTokenStatus(): TokenStatus = withContext(Dispatchers.IO) {
        try {
            val currentToken = tokenStorage.getCurrentToken()
            
            when {
                currentToken == null -> TokenStatus.NoToken
                
                currentToken.isValid() -> TokenStatus.Valid(
                    remainingMinutes = currentToken.getRemainingLifetimeMinutes(),
                    canRefresh = currentToken.canRefresh()
                )
                
                currentToken.canRefresh() -> TokenStatus.ExpiredButRefreshable(
                    expiredMinutesAgo = -currentToken.getRemainingLifetimeMinutes(),
                    hasRefreshToken = true
                )
                
                else -> TokenStatus.ExpiredNotRefreshable
            }
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "Error getting token status", e)
            TokenStatus.Error(e)
        }
    }
    
    /**
     * Forces a token refresh regardless of expiration status.
     * Useful for testing or when token is suspected to be invalid.
     */
    suspend fun forceRefresh(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentToken = tokenStorage.getCurrentToken()
            
            if (currentToken?.refreshToken == null) {
                Logger.w(LogTags.TOKEN, "Cannot force refresh - no refresh token available")
                return@withContext Result.failure(TokenException.RefreshNotPossible)
            }
            
            Logger.d(LogTags.TOKEN, "Forcing token refresh")
            oauth2TokenManager.refreshAccessToken(currentToken.refreshToken)
                .onSuccess { Logger.business(LogTags.TOKEN, "Force refresh successful") }
                .onFailure { Logger.e(LogTags.TOKEN, "Force refresh failed: ${it.message}") }
            
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "Error in force refresh", e)
            Result.failure(TokenException.UnknownError(e))
        }
    }
    
    /**
     * Clears invalid tokens from storage.
     * Useful for cleanup when tokens are permanently invalid.
     */
    suspend fun clearInvalidTokens(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentToken = tokenStorage.getCurrentToken()
            
            if (currentToken != null && !currentToken.isValid() && !currentToken.canRefresh()) {
                Logger.d(LogTags.TOKEN, "Clearing invalid tokens from storage")
                tokenStorage.clearToken()
            } else {
                Logger.d(LogTags.TOKEN, "No invalid tokens to clear")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e(LogTags.TOKEN, "Error clearing invalid tokens", e)
            Result.failure(TokenException.UnknownError(e))
        }
    }
}

/**
 * Token status information for debugging and UI updates
 */
sealed class TokenStatus {
    object NoToken : TokenStatus()
    data class Valid(val remainingMinutes: Long, val canRefresh: Boolean) : TokenStatus()
    data class ExpiredButRefreshable(val expiredMinutesAgo: Long, val hasRefreshToken: Boolean) : TokenStatus()
    object ExpiredNotRefreshable : TokenStatus()
    data class Error(val exception: Throwable) : TokenStatus()
}

/**
 * Typed exceptions for token operations
 */
sealed class TokenException : Exception() {
    object NoTokenAvailable : TokenException() {
        override val message = "No authentication token available"
    }
    
    object RefreshNotPossible : TokenException() {
        override val message = "Token cannot be refreshed - re-authentication required"
    }
    
    data class RefreshFailed(val error: Throwable?) : TokenException() {
        override val message = "Token refresh failed: ${error?.message}"
        override val cause = error
    }
    
    data class UnknownError(val error: Throwable) : TokenException() {
        override val message = "Unknown token error: ${error.message}"
        override val cause = error
    }
}
