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
            } catch (_: Exception) {
                // Try next candidate
            }
        }

        // Fallback: try opening Cast Settings directly without resolveActivity check
        return try {
            val fallback = Intent(Settings.ACTION_CAST_SETTINGS)
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            true
        } catch (_: Exception) {
            try {
                val displayFallback = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                displayFallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(displayFallback)
                true
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "No se pudo abrir el menú de transmisión automáticamente. Usa 'Smart View' o 'Transmitir' en el panel superior de tu teléfono.",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }

    /**
     * Opens the stream URL in an external video player / casting app (e.g. Web Video Caster, BubbleUPnP, VLC).
     */
    fun openInExternalPlayer(context: Context, url: String, title: String = "Nexo Player"): Boolean {
        if (url.isBlank()) return false
        return try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                putExtra("title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Transmitir o reproducir con...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No se encontró una app compatible para reproducir", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Copies video stream URL to clipboard so user can use it anywhere.
     */
    fun copyStreamUrl(context: Context, url: String) {
        if (url.isBlank()) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Stream URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo copiar el enlace", Toast.LENGTH_SHORT).show()
        }
    }
}
