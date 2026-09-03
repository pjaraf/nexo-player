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
import com.example.utils.NetworkClients

class NexusApp : Application(), ImageLoaderFactory {

    private var activeImageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.example.data.storage.AppStorage.init(this)
        } catch (e: Throwable) {
            Log.e("NexusApp", "Failed to initialize AppStorage: ${e.message}", e)
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(
                "NexusApp",
                "Uncaught exception on thread [${thread.name}]: ${throwable.message}",
                throwable
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)

        val loader = ImageLoader.Builder(this)
            .okHttpClient(NetworkClients.iptv)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowRgb565(true)
            .allowHardware(!isLowRam)
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
            .crossfade(false)
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
