package com.example.service

import android.location.Location
import com.example.data.model.RideRequest
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class OfferIntelligence(
    val pickupDeadheadMiles: Double,
    val totalTripMiles: Double,
    val estimatedTotalMinutes: Int,
    val estimatedGbpPerHour: Double,
    val estimatedGbpPerMile: Double,
    val compassShort: String,
    val compassLabel: String,
    val isHeadingTowardHome: Boolean,
    val smartTag: String? // e.g., "AIRPORT", "RAIL STATION", "LONG TRIP"
)

object OfferIntelligenceHelper {

    private val airportKeywords = listOf(
        "airport", "heathrow", "gatwick", "stansted", "luton", "london city",
        "manchester terminal", "birmingham airport", "edinburgh airport",
        "glasgow airport", "bristol airport", "east midlands", "newcastle airport",
        "liverpool john lennon", "leeds bradford", "terminal 1", "terminal 2",
        "terminal 3", "terminal 4", "terminal 5"
    )

    private val stationKeywords = listOf(
        "station", "king's cross", "kings cross", "st pancras", "euston",
        "paddington", "waterloo", "victoria coach", "victoria station",
        "liverpool street", "marylebone", "piccadilly", "new street",
        "waverley", "central station", "temple meads", "lime street"
    )

    fun analyze(
        offer: RideRequest,
        driverLat: Double,
        driverLng: Double,
        hasRealDriverFix: Boolean,
        homeLat: Double?,
        homeLng: Double?
    ): OfferIntelligence {
        val deadheadMiles = if (hasRealDriverFix && (offer.pickupLat != 0.0 || offer.pickupLng != 0.0)) {
            distanceMiles(driverLat, driverLng, offer.pickupLat, offer.pickupLng)
        } else {
            0.0
        }

        val tripMiles = when {
            offer.distanceMiles > 0.05 -> offer.distanceMiles
            (offer.pickupLat != 0.0 || offer.pickupLng != 0.0) &&
                (offer.dropoffLat != 0.0 || offer.dropoffLng != 0.0) ->
                distanceMiles(offer.pickupLat, offer.pickupLng, offer.dropoffLat, offer.dropoffLng) * 1.25
            else -> 1.0
        }

        val totalMiles = (deadheadMiles + tripMiles).coerceAtLeast(0.5)
        // Average UK urban pickup approach ~18 mph => ~3.3 min/mile
        val pickupMins = (deadheadMiles * 3.3).toInt().coerceAtLeast(if (deadheadMiles > 0.1) 2 else 0)
        val rideMins = if (offer.durationMinutes > 0) offer.durationMinutes else (tripMiles * 3.2).toInt().coerceAtLeast(5)
        val totalMins = (pickupMins + rideMins).coerceAtLeast(5)

        val gbpPerHour = if (offer.fareEstimate > 0.0) {
            (offer.fareEstimate / (totalMins / 60.0)).coerceIn(0.0, 250.0)
        } else 0.0

        val gbpPerMile = if (offer.fareEstimate > 0.0) {
            (offer.fareEstimate / totalMiles).coerceIn(0.0, 50.0)
        } else 0.0

        val bearingDeg = if (
            (offer.pickupLat != 0.0 || offer.pickupLng != 0.0) &&
            (offer.dropoffLat != 0.0 || offer.dropoffLng != 0.0)
        ) {
            computeBearingDegrees(offer.pickupLat, offer.pickupLng, offer.dropoffLat, offer.dropoffLng)
        } else null

        val (compassShort, compassLabel) = bearingDeg?.let { bearingToCompass(it) } ?: ("" to "")

        val headingTowardHome = if (
            bearingDeg != null &&
            homeLat != null && homeLng != null &&
            (homeLat != 0.0 || homeLng != 0.0) &&
            (offer.pickupLat != 0.0 || offer.pickupLng != 0.0)
        ) {
            val homeBearing = computeBearingDegrees(offer.pickupLat, offer.pickupLng, homeLat, homeLng)
            val diff = angularDiff(bearingDeg, homeBearing)
            val distBefore = distanceMiles(offer.pickupLat, offer.pickupLng, homeLat, homeLng)
            val distAfter = distanceMiles(offer.dropoffLat, offer.dropoffLng, homeLat, homeLng)
            diff <= 38.0 && distAfter < distBefore
        } else false

        val combinedAddress = "${offer.pickupAddress} ${offer.dropoffAddress}".lowercase()
        val smartTag = when {
            airportKeywords.any { combinedAddress.contains(it) } -> "AIRPORT"
            stationKeywords.any { combinedAddress.contains(it) } -> "RAIL STATION"
            tripMiles >= 15.0 || rideMins >= 30 -> "LONG TRIP"
            else -> null
        }

        return OfferIntelligence(
            pickupDeadheadMiles = deadheadMiles,
            totalTripMiles = tripMiles,
            estimatedTotalMinutes = totalMins,
            estimatedGbpPerHour = gbpPerHour,
            estimatedGbpPerMile = gbpPerMile,
            compassShort = compassShort,
            compassLabel = compassLabel,
            isHeadingTowardHome = headingTowardHome,
            smartTag = smartTag
        )
    }

    fun distanceMiles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0] / 1609.344
    }

    private fun computeBearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = Math.toDegrees(atan2(y, x))
        return (theta + 360.0) % 360.0
    }

    private fun angularDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    private fun bearingToCompass(deg: Double): Pair<String, String> {
        val normalized = ((deg % 360.0) + 360.0) % 360.0
        return when {
            normalized < 22.5 || normalized >= 337.5 -> "N" to "North"
            normalized < 67.5 -> "NE" to "North-East"
            normalized < 112.5 -> "E" to "East"
            normalized < 157.5 -> "SE" to "South-East"
            normalized < 202.5 -> "S" to "South"
            normalized < 247.5 -> "SW" to "South-West"
            normalized < 292.5 -> "W" to "West"
            else -> "NW" to "North-West"
        }
    }
}
