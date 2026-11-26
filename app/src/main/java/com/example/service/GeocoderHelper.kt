package com.example.service

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GeocodedPlace(
    val formattedAddress: String,
    val lat: Double,
    val lng: Double
)

object GeocoderHelper {

    /**
     * Zero-cost UK address & postcode geocoder using Android's built-in Geocoder.
     */
    suspend fun geocodeUkAddress(context: Context, query: String): GeocodedPlace? {
        val trimmed = query.trim()
        if (trimmed.isBlank() || !Geocoder.isPresent()) return null
        val ukQuery = if (trimmed.contains("UK", ignoreCase = true) || trimmed.contains("United Kingdom", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed, UK"
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(context, Locale.UK)
                val first: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocationName(ukQuery, 1) { list ->
                            if (cont.isActive) cont.resume(list.firstOrNull())
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(ukQuery, 1)?.firstOrNull()
                }
                first?.let { addr ->
                    val label = addr.getAddressLine(0)?.takeIf { it.isNotBlank() } ?: trimmed
                    GeocodedPlace(
                        formattedAddress = label,
                        lat = addr.latitude,
                        lng = addr.longitude
                    )
                }
            }.getOrNull()
        }
    }
}
