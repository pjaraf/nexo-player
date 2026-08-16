package com.example.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceUtils {
    /**
     * Accurately detects whether the current device is an Android TV / Google TV / Fire TV
     * or a mobile device (phone / tablet).
     */
    fun isTelevision(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager != null && uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                packageManager.hasSystemFeature("android.hardware.type.television") ||
                packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    }
}
