package com.github.f1rlefanz.cf_alarmfortimeoffice.usecase

import com.github.f1rlefanz.cf_alarmfortimeoffice.model.AuthData
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.interfaces.IAuthDataStoreRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.interfaces.IAuthUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.SafeExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * UseCase für alle Authentication-bezogenen Operationen - implementiert IAuthUseCase
 * 
 * REFACTORED:
 * ✅ Implementiert IAuthUseCase Interface für bessere Testbarkeit
 * ✅ Verwendet Repository-Interface statt konkrete Implementierung
 * ✅ Kapselt Business Logic von Infrastructure
 * ✅ Result-basierte API für konsistente Fehlerbehandlung
 * ✅ Clean Architecture Compliance
 * 
 * TESTING IMPROVEMENTS:
 * - Mock-fähige Repository-Abhängigkeit
 * - Interface-basierte Dependency Injection
 * - Entkoppelte Business Logic
 * - Comprehensive Error Handling
 */
class AuthUseCase(
    private val authDataStoreRepository: IAuthDataStoreRepository
) : IAuthUseCase {
    
    override val authData: Flow<AuthData> = authDataStoreRepository.authData
    
    override suspend fun updateAuthData(authData: AuthData): Result<Unit> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("AuthUseCase.updateAuthData") {
            authDataStoreRepository.updateAuthData(authData).getOrThrow()
            Logger.d(LogTags.AUTH, "Auth data updated successfully")
        }
    }
    
    override suspend fun clearAuthData(): Result<Unit> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("AuthUseCase.clearAuthData") {
            authDataStoreRepository.clearAuthData().getOrThrow()
            Logger.business(LogTags.AUTH, "Auth data cleared (logout)")
        }
    }
    
    override suspend fun isAuthenticated(): Result<Boolean> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("AuthUseCase.isAuthenticated") {
            authDataStoreRepository.isAuthenticated().getOrThrow()
        }
    }
    
    override suspend fun getCurrentAuthData(): Result<AuthData> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("AuthUseCase.getCurrentAuthData") {
            authDataStoreRepository.getCurrentAuthData().getOrThrow()
        }
    }
    
    override suspend fun migrateTokenExpiryIfNeeded(): Result<Unit> = withContext(Dispatchers.IO) {
        SafeExecutor.safeExecute("AuthUseCase.migrateTokenExpiryIfNeeded") {
            authDataStoreRepository.migrateTokenExpiryIfNeeded().getOrThrow()
            Logger.d(LogTags.DATASTORE, "Token expiry migration completed")
        }
    }
}
