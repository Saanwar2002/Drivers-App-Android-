package com.example.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

object ExternalIntentHelpers {

    /**
     * Opens Stripe Connect, Fee Settlement, Gold Checkout, Insurance Partner, or AnyTrader
     * links strictly in Chrome Custom Tabs (never WebView).
     */
    fun openCustomTab(context: Context, url: String) {
        val parsed = runCatching { Uri.parse(url.trim()) }.getOrNull()
        if (parsed == null || (parsed.scheme != "https" && parsed.scheme != "http")) {
            Toast.makeText(context, "Invalid link URL.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(0xFF0D0D0F.toInt())
                .build()
            val intent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setShowTitle(true)
                .build()
            intent.launchUrl(context, parsed)
        } catch (_: Exception) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, parsed).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                Toast.makeText(context, "No browser available to open link.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * One-tap turn-by-turn external navigation respecting driver's `defaultNavApp` preference
     * (`"Google Maps"` or `"Waze"`), with graceful fallback.
     */
    fun launchTurnByTurnNavigation(
        context: Context,
        lat: Double,
        lng: Double,
        preferredNavApp: String
    ) {
        if (preferredNavApp.equals("Waze", ignoreCase = true)) {
            val wazeUri = Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes")
            val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
                setPackage("com.waze")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(wazeIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Fall back to Google Maps or web Waze
            }
        }

        val gmapsUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val gmapsIntent = Intent(Intent.ACTION_VIEW, gmapsUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(gmapsIntent)
        } catch (_: ActivityNotFoundException) {
            openCustomTab(context, "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving")
        }
    }

    /**
     * Strictly validates `https://wa.me/` before launching WhatsApp 1-tap `AT-XXXXXX` activation.
     */
    fun launchValidatedWhatsAppUrl(context: Context, waMeUrl: String) {
        val trimmed = waMeUrl.trim()
        if (!trimmed.startsWith("https://wa.me/", ignoreCase = true)) {
            Toast.makeText(context, "Invalid WhatsApp activation link.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp is not installed on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    fun dialPhoneNumber(context: Context, phoneNumber: String) {
        val sanitized = phoneNumber.filter { it.isDigit() || it == '+' }
        if (sanitized.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun shareText(context: Context, subject: String, text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
