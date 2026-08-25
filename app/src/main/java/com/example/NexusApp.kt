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
import coil.decode.SvgDecoder
import okhttp3.OkHttpClient
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

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NexusApp", "Prevented app crash on thread: ${thread.name}", throwable)
            try {
                // If it's a non-fatal or player/network/render exception, prevent crash
                val msg = throwable.message ?: ""
                if (msg.contains("vlc") || msg.contains("mediaplayer") || msg.contains("libvlc") || msg.contains("surface") || msg.contains("texture")) {
                    Log.w("NexusApp", "Ignored non-fatal player exception to prevent closure")
                    return@setDefaultUncaughtExceptionHandler
                }
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (_: Throwable) {}
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

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val loader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(SvgDecoder.Factory())
            }
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
