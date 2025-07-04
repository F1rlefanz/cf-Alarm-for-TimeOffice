package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api

import com.github.f1rlefanz.cf_alarmfortimeoffice.hue.data.*
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * HTTP client for Hue API communication
 * Handles SSL certificates and network requests with proper error handling
 */
class HueApiClient {
    
    companion object {
        private const val DISCOVERY_URL = "https://discovery.meethue.com"
        private const val TIMEOUT_SECONDS = 10L
    }
    
    private val gson = Gson()
    private val client: OkHttpClient
    
    init {
        // Create OkHttpClient with custom SSL handling for self-signed certificates
        client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .sslSocketFactory(createTrustAllSslContext().socketFactory, TrustAllCerts())
            .hostnameVerifier { _, _ -> true } // Accept all hostnames
            .build()
    }
    
    /**
     * Discover bridges using Philips online service
     */
    suspend fun discoverBridgesOnline(): List<BridgeDiscoveryResponse> = withContext(Dispatchers.IO) {
        try {
            Logger.d(LogTags.HUE_DISCOVERY, "Attempting online bridge discovery")
            
            val request = Request.Builder()
                .url("$DISCOVERY_URL/api/nupnp")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<BridgeDiscoveryResponse>>() {}.type
                val bridges = gson.fromJson<List<BridgeDiscoveryResponse>>(responseBody, type)
                
                Logger.i(LogTags.HUE_DISCOVERY, "Online discovery successful: ${bridges.size} bridges")
                return@withContext bridges
            } else {
                throw IOException("Discovery service unavailable: ${response.code}")
            }
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_DISCOVERY, "Online discovery failed", e)
            throw e
        }
    }
    
    /**
     * Get bridge configuration
     */
    suspend fun getBridgeConfig(bridgeIp: String, username: String? = null): HueBridgeConfig = withContext(Dispatchers.IO) {
        val url = if (username != null) {
            "http://$bridgeIp/api/$username/config"
        } else {
            "http://$bridgeIp/api/config"
        }
        
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: "{}"
            return@withContext gson.fromJson(responseBody, HueBridgeConfig::class.java)
        } else {
            throw IOException("Failed to get bridge config: ${response.code}")
        }
    }
    
    /**
     * Create user on bridge (requires link button press)
     */
    suspend fun createUser(bridgeIp: String, appName: String): String = withContext(Dispatchers.IO) {
        val requestBody = mapOf("devicetype" to appName)
        val json = gson.toJson(requestBody)
        
        val request = Request.Builder()
            .url("http://$bridgeIp/api")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: "[]"
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val responseList = gson.fromJson<List<Map<String, Any>>>(responseBody, type)
            
            responseList.firstOrNull()?.let { firstResponse ->
                when {
                    firstResponse.containsKey("success") -> {
                        val successMap = firstResponse["success"] as? Map<String, Any>
                        val username = successMap?.get("username") as? String
                        return@withContext username ?: throw IOException("Username not found in response")
                    }
                    firstResponse.containsKey("error") -> {
                        val errorMap = firstResponse["error"] as? Map<String, Any>
                        val errorType = errorMap?.get("type") as? Double
                        if (errorType == 101.0) {
                            throw IOException("Link button not pressed. Please press the link button on your Hue bridge and try again.")
                        } else {
                            throw IOException("Bridge error: ${errorMap?.get("description")}")
                        }
                    }
                }
            }
        }
        
        throw IOException("Failed to create user")
    }
    
    /**
     * Get all lights from bridge
     */
    suspend fun getLights(bridgeIp: String, username: String): Map<String, HueLight> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$bridgeIp/api/$username/lights")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: "{}"
            val type = object : TypeToken<Map<String, HueLight>>() {}.type
            return@withContext gson.fromJson(responseBody, type)
        } else {
            throw IOException("Failed to get lights: ${response.code}")
        }
    }
    
    /**
     * Get all groups from bridge
     */
    suspend fun getGroups(bridgeIp: String, username: String): Map<String, HueGroup> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$bridgeIp/api/$username/groups")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: "{}"
            val type = object : TypeToken<Map<String, HueGroup>>() {}.type
            return@withContext gson.fromJson(responseBody, type)
        } else {
            throw IOException("Failed to get groups: ${response.code}")
        }
    }
    
    /**
     * Control a light
     */
    suspend fun controlLight(
        bridgeIp: String,
        username: String,
        lightId: String,
        update: LightStateUpdate
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(update)
            val request = Request.Builder()
                .url("http://$bridgeIp/api/$username/lights/$lightId/state")
                .put(json.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_LIGHTS, "Error controlling light $lightId", e)
            false
        }
    }
    
    /**
     * Control a group
     */
    suspend fun controlGroup(
        bridgeIp: String,
        username: String,
        groupId: String,
        update: GroupUpdate
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(update)
            val request = Request.Builder()
                .url("http://$bridgeIp/api/$username/groups/$groupId/action")
                .put(json.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_LIGHTS, "Error controlling group $groupId", e)
            false
        }
    }
    
    /**
     * Create SSL context that trusts all certificates
     */
    private fun createTrustAllSslContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(TrustAllCerts())
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext
    }
    
    /**
     * Trust manager that accepts all certificates
     */
    private class TrustAllCerts : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
}
