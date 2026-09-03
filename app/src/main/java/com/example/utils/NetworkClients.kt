package com.example.utils

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Centralized HTTP clients.
 * - [strict]: default TLS validation for updates, GitHub and trusted APIs.
 * - [iptv]: permissive TLS for user-configured IPTV servers (often self-signed).
 */
object NetworkClients {

    val strict: OkHttpClient by lazy {
        buildClient(permissiveSsl = false)
    }

    val iptv: OkHttpClient by lazy {
        buildClient(permissiveSsl = true)
    }

    private fun buildClient(permissiveSsl: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionSpecs(
                listOf(
                    ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                        .build(),
                    ConnectionSpec.CLEARTEXT
                )
            )
            .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)

        if (permissiveSsl) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }

            builder
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    private val trustedUpdateHosts = setOf(
        "github.com",
        "raw.githubusercontent.com",
        "cdn.jsdelivr.net",
        "api.github.com"
    )

    fun isTrustedUpdateUrl(url: String): Boolean {
        return try {
            val host = java.net.URI(url.trim()).host?.lowercase() ?: return false
            trustedUpdateHosts.any { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }
}
