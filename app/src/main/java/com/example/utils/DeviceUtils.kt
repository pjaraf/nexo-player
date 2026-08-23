package com.example.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

object DeviceUtils {
    /**
     * Accurately detects whether the current device is an Android TV, Google TV, Fire TV,
     * or a generic / older AOSP TV Box (MXQ, TX, Tanix, X96, Amlogic, Rockchip, etc.)
     * versus a mobile device (phone / tablet).
     */
    fun isTelevision(context: Context): Boolean {
        // 1. Official UiMode check (Android TV & Google TV)
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager != null && uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }

        val packageManager = context.packageManager

        // 2. PackageManager standard & vendor TV features
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature("android.hardware.type.television") ||
            packageManager.hasSystemFeature("android.software.leanback") ||
            packageManager.hasSystemFeature("amazon.hardware.fire_tv") ||
            packageManager.hasSystemFeature("android.software.live_tv")
        ) {
            return true
        }

        // 3. Hardware feature heuristics for legacy/AOSP TV Boxes (no touchscreen, no telephony)
        val hasNoTouchScreen = !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val hasNoTelephony = !packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

        if (hasNoTouchScreen && hasNoTelephony) {
            return true
        }

        // 4. Heuristic signature check on system properties, model, hardware & SOC for older TV Boxes
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()

        val tvBoxKeywords = listOf(
            "tv", "box", "mbox", "stb", "smarttv", "google tv", "android tv", "firetv",
            "shield", "bravia", "chromecast", "rk322", "rk332", "rk339", "amlogic",
            "allwinner", "sunxi", "gxbb", "gxl", "g12a", "sm1", "droidlogic", "realtek"
        )

        if (hasNoTelephony && tvBoxKeywords.any { kw ->
                model.contains(kw) || device.contains(kw) || product.contains(kw) ||
                hardware.contains(kw) || board.contains(kw) || fingerprint.contains(kw)
            }
        ) {
            return true
        }

        return false
    }
}

