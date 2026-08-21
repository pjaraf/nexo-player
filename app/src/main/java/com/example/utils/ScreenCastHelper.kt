package com.example.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object ScreenCastHelper {

    /**
     * Attempts to open the Android native Cast / Wireless Display / Smart View dialog or settings.
     */
    fun openCastSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_CAST_SETTINGS),
            Intent("com.samsung.wfd.LAUNCH_WFD_PICKER_DLG"),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent("com.huawei.android.airsharing.action.AIRSHARING_MAIN"),
            Intent("com.xiaomi.wirelessdisplay"),
            Intent("com.google.android.apps.chromecast.app"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Throwable) {
                // Try next candidate
            }
        }

        // Fallback: try opening Cast Settings directly
        return try {
            val fallback = Intent(Settings.ACTION_CAST_SETTINGS)
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            true
        } catch (_: Throwable) {
            try {
                val displayFallback = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                displayFallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(displayFallback)
                true
            } catch (e: Throwable) {
                Toast.makeText(
                    context,
                    "Abre 'Transmitir', 'Smart View' o 'Google Cast' desde la barra superior de tu teléfono.",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }

    /**
     * Opens the stream URL in an external video player / casting app (e.g. Web Video Caster, BubbleUPnP, VLC, MX Player).
     */
    fun openInExternalPlayer(context: Context, url: String, title: String = "Nexo Player"): Boolean {
        if (url.isBlank()) {
            Toast.makeText(context, "No hay URL de video activa para transmitir", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            val uri = Uri.parse(url.trim())
            val mimeType = when {
                url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                url.contains(".ts", ignoreCase = true) -> "video/mp2t"
                url.contains(".mp4", ignoreCase = true) -> "video/mp4"
                url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
                else -> "video/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                putExtra("title", title)
                putExtra("android.intent.extra.TITLE", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Transmitir o reproducir con...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Throwable) {
            Toast.makeText(context, "No se encontró una app para transmitir o reproducir", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Copies video stream URL to clipboard so user can use it anywhere.
     */
    fun copyStreamUrl(context: Context, url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "No hay URL disponible para copiar", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Nexo Stream URL", url.trim())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Enlace directo del video copiado al portapapeles", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            Toast.makeText(context, "No se pudo copiar el enlace", Toast.LENGTH_SHORT).show()
        }
    }
}
