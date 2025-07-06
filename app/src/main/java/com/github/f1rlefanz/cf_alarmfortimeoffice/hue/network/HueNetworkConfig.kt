package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * Network Configuration for Philips Hue Integration
 * 
 * Provides optimized HTTP client configuration specifically for Hue Bridge communication.
 * Follows Clean Architecture and Dependency Inversion principles.
 * 
 * Features:
 * - SSL Certificate handling for self-signed bridge certificates
 * - Optimized timeouts for local network communication
 * - Request/Response logging for debugging
 * - Connection pooling for performance
 * 
 * @author CF-Alarm Development Team
 * @since Hue Integration v2
 */
object HueNetworkConfig {
    
    // Network timeouts optimized for local Hue Bridge communication
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 15L
    private const val WRITE_TIMEOUT_SECONDS = 15L
    
    /**
     * Creates configured OkHttpClient for Hue Bridge communication
     * 
     * Implementation considerations:
     * - Accepts self-signed certificates (Hue Bridge requirement)
     * - Optimized timeouts for local network
     * - Connection pooling for performance
     * - Request logging for debugging
     */
    fun createHueHttpClient(): OkHttpClient {
        Logger.d(LogTags.HUE_NETWORK, "Creating optimized HTTP client for Hue Bridge communication")
        
        return OkHttpClient.Builder()
            // SSL Configuration for self-signed certificates
            .sslSocketFactory(
                TrustAllCertificatesManager.createTrustAllSSLSocketFactory(),
                TrustAllCertificatesManager.createTrustAllSSLSocketFactory().let { factory ->
                    object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                    }
                }
            )
            .hostnameVerifier(TrustAllCertificatesManager.createTrustAllHostnameVerifier())
            
            // Timeouts optimized for local network communication
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            
            // Connection pool optimization
            .connectionPool(okhttp3.ConnectionPool(maxIdleConnections = 5, keepAliveDuration = 2, TimeUnit.MINUTES))
            
            // Add logging interceptor for debugging (only in debug builds)
            .apply {
                if (com.github.f1rlefanz.cf_alarmfortimeoffice.BuildConfig.DEBUG) {
                    addInterceptor { chain ->
                        val request = chain.request()
                        Logger.d(LogTags.HUE_NETWORK, "HTTP Request: ${request.method} ${request.url}")
                        
                        val response = chain.proceed(request)
                        Logger.d(LogTags.HUE_NETWORK, "HTTP Response: ${response.code} for ${request.url}")
                        
                        response
                    }
                }
            }
            
            .build()
            .also {
                Logger.i(LogTags.HUE_NETWORK, "Hue HTTP Client initialized with SSL trust and optimized timeouts")
            }
    }
    
    /**
     * Creates configured Retrofit instance for Hue API communication
     * 
     * @param baseUrl Bridge IP address (e.g., "https://192.168.1.100/")
     * @return Configured Retrofit instance
     */
    fun createHueRetrofit(baseUrl: String): Retrofit {
        Logger.d(LogTags.HUE_NETWORK, "Creating Retrofit instance for Hue Bridge at: $baseUrl")
        
        return Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(baseUrl))
            .client(createHueHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .also {
                Logger.i(LogTags.HUE_NETWORK, "Hue Retrofit instance created for baseUrl: $baseUrl")
            }
    }
    
    /**
     * Ensures base URL has trailing slash for proper Retrofit operation
     * Follows defensive programming principles
     */
    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
    
    /**
     * Creates base URL from bridge IP address
     * Handles both HTTP and HTTPS protocols based on bridge capabilities
     */
    fun createBridgeBaseUrl(bridgeIp: String, useHttps: Boolean = true): String {
        val protocol = if (useHttps) "https" else "http"
        return "$protocol://$bridgeIp"
    }
}
