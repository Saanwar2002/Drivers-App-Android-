package com.example.data.repository

import android.location.Location
import com.example.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ActiveRouteResult(
    val points: List<LatLng> = emptyList(),
    val etaMinutes: Int? = null,
    val distanceText: String? = null,
    val nextManeuver: String? = null
)

/**
 * Cost-capped Google Directions REST client mirroring DriverTerminal.tsx:
 * - Decodes encoded overview_polyline for crisp dark-map rendering
 * - Extracts live ETA, distance, and next turn instruction for the Locked Blue HUD
 * - Gracefully falls back to a clean direct route line if MAPS_API_KEY is unconfigured or restricted
 */
class DirectionsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): ActiveRouteResult {
        if ((originLat == 0.0 && originLng == 0.0) || (destLat == 0.0 && destLng == 0.0)) {
            return ActiveRouteResult()
        }
        val fallbackPoints = listOf(LatLng(originLat, originLng), LatLng(destLat, destLng))
        val results = FloatArray(1)
        Location.distanceBetween(originLat, originLng, destLat, destLng, results)
        val fallbackMiles = results[0] / 1609.344
        val fallbackMins = ((fallbackMiles / 22.0) * 60.0).toInt().coerceAtLeast(1)

        val apiKey = BuildConfig.MAPS_API_KEY.trim()
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            return ActiveRouteResult(
                points = fallbackPoints,
                etaMinutes = fallbackMins,
                distanceText = "%.1f mi".format(fallbackMiles)
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=$originLat,$originLng" +
                    "&destination=$destLat,$destLng" +
                    "&mode=driving&units=imperial&key=$apiKey"
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext ActiveRouteResult(
                            points = fallbackPoints,
                            etaMinutes = fallbackMins,
                            distanceText = "%.1f mi".format(fallbackMiles)
                        )
                    }
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes")
                    if (routes == null || routes.length() == 0) {
                        return@withContext ActiveRouteResult(
                            points = fallbackPoints,
                            etaMinutes = fallbackMins,
                            distanceText = "%.1f mi".format(fallbackMiles)
                        )
                    }
                    val route0 = routes.getJSONObject(0)
                    val encoded = route0.optJSONObject("overview_polyline")?.optString("points").orEmpty()
                    val decoded = if (encoded.isNotBlank()) decodePolyline(encoded) else fallbackPoints
                    val leg0 = route0.optJSONArray("legs")?.optJSONObject(0)
                    val durSec = leg0?.optJSONObject("duration")?.optInt("value") ?: (fallbackMins * 60)
                    val distText = leg0?.optJSONObject("distance")?.optString("text")
                        ?: "%.1f mi".format(fallbackMiles)
                    val rawStepHtml = leg0?.optJSONArray("steps")?.optJSONObject(0)?.optString("html_instructions")
                    val cleanManeuver = rawStepHtml
                        ?.replace(Regex("<[^>]*>"), " ")
                        ?.replace(Regex("\\s+"), " ")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    ActiveRouteResult(
                        points = decoded.ifEmpty { fallbackPoints },
                        etaMinutes = (durSec / 60).coerceAtLeast(1),
                        distanceText = distText,
                        nextManeuver = cleanManeuver
                    )
                }
            }.getOrElse {
                ActiveRouteResult(
                    points = fallbackPoints,
                    etaMinutes = fallbackMins,
                    distanceText = "%.1f mi".format(fallbackMiles)
                )
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}
