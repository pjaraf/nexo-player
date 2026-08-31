package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
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

class NexusApp : Application(), ImageLoaderFactory {

    private var activeImageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NexusApp", "Uncaught exception caught safely on thread: ${thread.name}", throwable)
            // Suppress all unhandled exceptions to prevent the application from closing unexpectedly
        }

        // Install resilient Main Looper crash protection
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            while (true) {
                try {
                    android.os.Looper.loop()
                } catch (t: Throwable) {
                    Log.e("NexusApp", "Handled main looper exception to prevent app close: ${t.message}", t)
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = try {
            SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }
        } catch (_: Exception) {
            SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, SecureRandom())
            }
        }

        val modernTlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .build()

        val compatibleTlsSpec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .build()

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectionSpecs(listOf(modernTlsSpec, compatibleTlsSpec, ConnectionSpec.CLEARTEXT))
            .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val loader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .bitmapConfig(Bitmap.Config.RGB_565) // 50% less RAM usage per poster/logo
            .allowRgb565(true)
            .allowHardware(!isLowRam) // Prevent GPU texture OOM on older Android/TV devices
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.12 else 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.04)
                    .build()
            }
            .crossfade(false) // Disable heavy crossfade animation on low-end chipsets for maximum FPS
            .build()

        activeImageLoader = loader
        return loader
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= TRIM_MEMORY_RUNNING_LOW) {
                activeImageLoader?.memoryCache?.clear()
            }
        } catch (_: Exception) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            activeImageLoader?.memoryCache?.clear()
        } catch (_: Exception) {}
    }

    companion object {
        lateinit var instance: NexusApp
            private set
    }
}
