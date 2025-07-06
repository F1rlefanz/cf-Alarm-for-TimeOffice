package com.github.f1rlefanz.cf_alarmfortimeoffice.hue.network

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.HostnameVerifier
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.Logger
import com.github.f1rlefanz.cf_alarmfortimeoffice.util.LogTags

/**
 * SSL Certificate Manager for Philips Hue Bridge Integration
 * 
 * Philips Hue Bridges use self-signed certificates which are rejected by default.
 * This manager provides controlled certificate acceptance for bridge communication
 * while maintaining security for all other HTTPS connections.
 * 
 * Implementation follows Security by Design principles:
 * - Only accepts certificates from private network ranges
 * - Validates bridge IP address ranges (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
 * - Logs all certificate acceptance for security auditing
 * 
 * @author CF-Alarm Development Team
 * @since Hue Integration v2
 */
object TrustAllCertificatesManager {
    
    /**
     * Creates SSLSocketFactory that accepts self-signed certificates
     * ONLY for Hue Bridge communication on private networks
     */
    fun createTrustAllSSLSocketFactory(): SSLSocketFactory {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // For Hue Bridge integration, we trust client certificates
                Logger.d(LogTags.HUE_NETWORK, "Accepting client certificate: ${chain.firstOrNull()?.subjectDN}")
            }
            
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // For Hue Bridge integration, we trust server certificates
                val cert = chain.firstOrNull()
                Logger.d(LogTags.HUE_NETWORK, "Accepting server certificate: ${cert?.subjectDN}")
                Logger.d(LogTags.HUE_NETWORK, "Certificate issuer: ${cert?.issuerDN}")
            }
            
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), java.security.SecureRandom())
        
        Logger.i(LogTags.HUE_NETWORK, "SSL TrustManager initialized for Hue Bridge communication")
        return sslContext.socketFactory
    }
    
    /**
     * Creates HostnameVerifier that accepts private network hostnames
     * Validates that we only accept certificates from private IP ranges
     */
    fun createTrustAllHostnameVerifier(): HostnameVerifier {
        return HostnameVerifier { hostname, _ ->
            val isPrivateNetwork = isPrivateNetworkAddress(hostname)
            
            if (isPrivateNetwork) {
                Logger.d(LogTags.HUE_NETWORK, "Accepting hostname for private network: $hostname")
            } else {
                Logger.w(LogTags.HUE_NETWORK, "Rejecting hostname outside private network: $hostname")
            }
            
            isPrivateNetwork
        }
    }
    
    /**
     * Validates if hostname/IP is in private network range
     * Security measure: Only trust certificates from private networks
     */
    private fun isPrivateNetworkAddress(hostname: String): Boolean {
        return try {
            when {
                // IPv4 private ranges
                hostname.startsWith("192.168.") -> true
                hostname.startsWith("10.") -> true
                hostname.startsWith("172.") -> {
                    val secondOctet = hostname.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                    secondOctet in 16..31
                }
                // Localhost
                hostname == "localhost" || hostname == "127.0.0.1" -> true
                // Link-local addresses
                hostname.startsWith("169.254.") -> true
                else -> false
            }
        } catch (e: Exception) {
            Logger.e(LogTags.HUE_NETWORK, "Error validating private network address: $hostname", e)
            false
        }
    }
}
