package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Hue API Client with proper SSL handling for self-signed certificates
 */
object HueApiClient {
    
    private const val DISCOVERY_BASE_URL = "https://discovery.meethue.com/"
    
    /**
     * Create Retrofit instance for bridge discovery
     */
    fun createDiscoveryService(): HueBridgeDiscoveryService {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
            
        return Retrofit.Builder()
            .baseUrl(DISCOVERY_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HueBridgeDiscoveryService::class.java)
    }
    
    /**
     * Create Retrofit instance for Hue Bridge API
     * Handles self-signed certificates from Hue Bridge
     */
    fun createBridgeService(bridgeIp: String): HueApiService {
        val client = createUnsafeOkHttpClient()
            
        val gson = GsonBuilder()
            .setLenient()
            .create()
            
        return Retrofit.Builder()
            .baseUrl("https://$bridgeIp/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(HueApiService::class.java)
    }
    
    /**
     * Create OkHttpClient that accepts self-signed certificates
     * This is necessary for local Hue Bridge communication
     */
    private fun createUnsafeOkHttpClient(): OkHttpClient {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory
            
            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { hostname, session ->
                    // Accept any hostname for local network
                    hostname.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) ||
                    session.peerPrincipal.name.contains("CN=")
                }
                .addInterceptor(HttpLoggingInterceptor { message ->
                    Timber.d("HueAPI: $message")
                }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
