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

data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val durationSeconds: Int = 0,
    val maneuver: String? = null,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val legIndex: Int = 0
)

data class ActiveRouteResult(
    val points: List<LatLng> = emptyList(),
    val etaMinutes: Int? = null,
    val totalDurationSeconds: Int = 0,
    val distanceText: String? = null,
    val totalDistanceMeters: Int = 0,
    val nextManeuver: String? = null,
    val steps: List<RouteStep> = emptyList()
)

/**
 * Cost-capped Google Directions REST client (strictly Basic SKU — never sets departure_time
 * or optimize:true):
 * - Single-call multi-stop support (up to 10 waypoints on the same Basic SKU request)
 * - Decodes per-step road-accurate polylines (falling back to overview_polyline)
 * - Extracts full step maneuvers, durations, and leg indices for live UK Heads-Up HUD & voice
 * - Gracefully falls back to a clean local estimate if MAPS_API_KEY is unconfigured or unavailable
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
        destLng: Double,
        waypoints: List<Pair<Double, Double>> = emptyList()
    ): ActiveRouteResult {
        if ((originLat == 0.0 && originLng == 0.0) || (destLat == 0.0 && destLng == 0.0)) {
            return ActiveRouteResult()
        }
        val validWaypoints = waypoints
            .filter { (lat, lng) -> lat != 0.0 || lng != 0.0 }
            .take(10) // Google Directions Basic SKU limit: <=10 non-optimized waypoints
        val fallbackPoints = buildList {
            add(LatLng(originLat, originLng))
            validWaypoints.forEach { (lat, lng) -> add(LatLng(lat, lng)) }
            add(LatLng(destLat, destLng))
        }
        val fallbackMeters = computePolylineLengthMeters(fallbackPoints)
        val fallbackMiles = fallbackMeters / 1609.344
        val fallbackMins = ((fallbackMiles / 22.0) * 60.0).toInt().coerceAtLeast(1)

        val apiKey = BuildConfig.MAPS_API_KEY.trim()
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            return ActiveRouteResult(
                points = fallbackPoints,
                etaMinutes = fallbackMins,
                totalDurationSeconds = fallbackMins * 60,
                distanceText = "%.1f mi".format(fallbackMiles),
                totalDistanceMeters = fallbackMeters
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val waypointsParam = if (validWaypoints.isNotEmpty()) {
                    "&waypoints=" + validWaypoints.joinToString("|") { (lat, lng) -> "$lat,$lng" }
                } else ""
                val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=$originLat,$originLng" +
                    "&destination=$destLat,$destLng" +
                    waypointsParam +
                    "&mode=driving&units=imperial&key=$apiKey"
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext ActiveRouteResult(
                            points = fallbackPoints,
                            etaMinutes = fallbackMins,
                            totalDurationSeconds = fallbackMins * 60,
                            distanceText = "%.1f mi".format(fallbackMiles),
                            totalDistanceMeters = fallbackMeters
                        )
                    }
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes")
                    if (routes == null || routes.length() == 0) {
                        return@withContext ActiveRouteResult(
                            points = fallbackPoints,
                            etaMinutes = fallbackMins,
                            totalDurationSeconds = fallbackMins * 60,
                            distanceText = "%.1f mi".format(fallbackMiles),
                            totalDistanceMeters = fallbackMeters
                        )
                    }
                    val route0 = routes.getJSONObject(0)
                    val legsArr = route0.optJSONArray("legs")

                    var totalDurSec = 0
                    var totalDistMeters = 0
                    val parsedSteps = mutableListOf<RouteStep>()
                    val detailedPoints = ArrayList<LatLng>()

                    if (legsArr != null && legsArr.length() > 0) {
                        for (legIdx in 0 until legsArr.length()) {
                            val legObj = legsArr.optJSONObject(legIdx) ?: continue
                            totalDurSec += legObj.optJSONObject("duration")?.optInt("value") ?: 0
                            totalDistMeters += legObj.optJSONObject("distance")?.optInt("value") ?: 0
                            val stepsArr = legObj.optJSONArray("steps") ?: continue
                            for (i in 0 until stepsArr.length()) {
                                val stepObj = stepsArr.optJSONObject(i) ?: continue
                                val stepEncoded = stepObj.optJSONObject("polyline")?.optString("points").orEmpty()
                                if (stepEncoded.isNotBlank()) {
                                    val stepPts = decodePolyline(stepEncoded)
                                    for (pt in stepPts) {
                                        val last = detailedPoints.lastOrNull()
                                        if (last == null || last.latitude != pt.latitude || last.longitude != pt.longitude) {
                                            detailedPoints.add(pt)
                                        }
                                    }
                                }
                                val cleanInstruction = stripHtml(stepObj.optString("html_instructions"))
                                if (cleanInstruction.isBlank()) continue
                                val stepDist = stepObj.optJSONObject("distance")?.optInt("value") ?: 0
                                val stepDur = stepObj.optJSONObject("duration")?.optInt("value") ?: 0
                                val maneuver = stepObj.optString("maneuver").takeIf { it.isNotBlank() }
                                val startLoc = stepObj.optJSONObject("start_location")
                                val endLoc = stepObj.optJSONObject("end_location")
                                parsedSteps += RouteStep(
                                    instruction = cleanInstruction,
                                    distanceMeters = stepDist,
                                    durationSeconds = stepDur,
                                    maneuver = maneuver,
                                    startLat = startLoc?.optDouble("lat") ?: originLat,
                                    startLng = startLoc?.optDouble("lng") ?: originLng,
                                    endLat = endLoc?.optDouble("lat") ?: destLat,
                                    endLng = endLoc?.optDouble("lng") ?: destLng,
                                    legIndex = legIdx
                                )
                            }
                        }
                    }

                    val finalPoints = if (detailedPoints.size >= 2) {
                        detailedPoints
                    } else {
                        val overview = route0.optJSONObject("overview_polyline")?.optString("points").orEmpty()
                        if (overview.isNotBlank()) decodePolyline(overview) else fallbackPoints
                    }
                    val resolvedDurSec = if (totalDurSec > 0) totalDurSec else (fallbackMins * 60)
                    val resolvedDistMeters = if (totalDistMeters > 0) totalDistMeters else fallbackMeters
                    val resolvedDistText = if (resolvedDistMeters >= 161) {
                        "%.1f mi".format(resolvedDistMeters / 1609.344)
                    } else {
                        val yds = (resolvedDistMeters * 1.09361).toInt().coerceAtLeast(10)
                        "$yds yd"
                    }

                    ActiveRouteResult(
                        points = finalPoints.ifEmpty { fallbackPoints },
                        etaMinutes = (resolvedDurSec / 60).coerceAtLeast(1),
                        totalDurationSeconds = resolvedDurSec,
                        distanceText = resolvedDistText,
                        totalDistanceMeters = resolvedDistMeters,
                        nextManeuver = parsedSteps.firstOrNull()?.instruction,
                        steps = parsedSteps
                    )
                }
            }.getOrElse {
                ActiveRouteResult(
                    points = fallbackPoints,
                    etaMinutes = fallbackMins,
                    totalDurationSeconds = fallbackMins * 60,
                    distanceText = "%.1f mi".format(fallbackMiles),
                    totalDistanceMeters = fallbackMeters
                )
            }
        }
    }

    private fun computePolylineLengthMeters(pts: List<LatLng>): Int {
        if (pts.size < 2) return 0
        var sum = 0.0
        val out = FloatArray(1)
        for (i in 0 until pts.lastIndex) {
            Location.distanceBetween(
                pts[i].latitude, pts[i].longitude,
                pts[i + 1].latitude, pts[i + 1].longitude,
                out
            )
            sum += out[0].toDouble()
        }
        return sum.toInt().coerceAtLeast(0)
    }

    private fun stripHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("<div[^>]*>", RegexOption.IGNORE_CASE), ". ")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\.\\s*\\."), ".")
            .trim()
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
