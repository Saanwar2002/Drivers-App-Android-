package com.example.data.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Exact 1:1 Kotlin port of AnyTrader/AnyRoller's canonical ride financial summary resolver
 * (`src/lib/rideEarningsDisplay.ts`, AT-RIDE-PAY-08).
 *
 * Always prefers authoritative server-persisted fields (`driverEarnings`, `platformFee`,
 * `appliedCommissionRate`, `finalFare`, `tipAmount`, `totalFarePaid`) under `v2_excl_tip`
 * without double-counting tips or hardcoding commission percentages.
 */
data class FallbackFareConfig(
    val commissionRate: Double? = null,
    val fixedTripFee: Double? = null
)

data class RideFinancialSummary(
    val baseFare: Double,
    val tipAmount: Double,
    val totalFarePaid: Double,
    val platformFee: Double,
    val driverEarnings: Double,
    val driverBaseShare: Double,
    val appliedCommissionRate: Double,
    val appliedCommissionPct: Double,
    val appliedFixedTripFee: Double,
    val isServerVerified: Boolean,
    val hasEstimatedFallback: Boolean
)

private fun round2(v: Double): Double = round(v * 100.0) / 100.0
private fun round4(v: Double): Double = round(v * 10000.0) / 10000.0
private fun round1(v: Double): Double = round(v * 10.0) / 10.0

fun resolveRideFinancialSummary(
    ride: RideRequest,
    fallbackFareConfig: FallbackFareConfig? = null
): RideFinancialSummary {
    val tipAmount = max(0.0, round2(ride.tipAmount))

    val defaultRate = when {
        ride.appliedCommissionRate != null -> max(0.0, min(1.0, ride.appliedCommissionRate))
        fallbackFareConfig?.commissionRate != null -> max(0.0, min(1.0, fallbackFareConfig.commissionRate))
        else -> 0.12
    }

    val appliedFixedTripFee = when {
        ride.appliedFixedTripFee != null -> max(0.0, round2(ride.appliedFixedTripFee))
        fallbackFareConfig?.fixedTripFee != null -> max(0.0, round2(fallbackFareConfig.fixedTripFee))
        else -> 0.0
    }

    val serverDriverRaw = ride.driverEarnings ?: ride.calculatedDriverEarnings
    val serverFeeRaw = ride.platformFee ?: ride.calculatedPlatformFee ?: ride.commissionAmount
    val finalFareNum = ride.finalFare
    val totalFarePaidNum = ride.totalFarePaid
    val legacyTotalFareNum = ride.totalFare
    val estimateNum = ride.price ?: ride.quotedPrice ?: ride.fareEstimate.takeIf { it > 0.0 }

    val baseFare: Double
    val totalFarePaid: Double
    var hasEstimatedFallback = false

    if (ride.fareContractVersion == "v2_excl_tip" && finalFareNum != null) {
        baseFare = max(0.0, round2(finalFareNum))
        totalFarePaid = if (totalFarePaidNum != null) {
            max(0.0, round2(totalFarePaidNum))
        } else {
            round2(baseFare + tipAmount)
        }
    } else if (totalFarePaidNum != null) {
        totalFarePaid = max(0.0, round2(totalFarePaidNum))
        baseFare = if (finalFareNum != null) {
            max(0.0, round2(finalFareNum))
        } else {
            max(0.0, round2(totalFarePaid - tipAmount))
        }
    } else if (finalFareNum != null) {
        if (legacyTotalFareNum != null) {
            baseFare = max(0.0, round2(finalFareNum))
            totalFarePaid = max(0.0, round2(legacyTotalFareNum))
        } else if (serverDriverRaw != null && serverFeeRaw != null) {
            totalFarePaid = max(0.0, round2(serverDriverRaw + serverFeeRaw))
            baseFare = max(0.0, round2(totalFarePaid - tipAmount))
        } else {
            totalFarePaid = max(0.0, round2(finalFareNum))
            baseFare = max(0.0, round2(totalFarePaid - tipAmount))
        }
    } else if (legacyTotalFareNum != null) {
        baseFare = max(0.0, round2(legacyTotalFareNum))
        totalFarePaid = round2(baseFare + tipAmount)
    } else {
        hasEstimatedFallback = true
        var est = estimateNum?.let { max(0.0, round2(it)) } ?: 0.0
        if (ride.status == "rider_abandoned" && est <= 0.0) {
            est = 5.0
        }
        baseFare = est
        totalFarePaid = round2(baseFare + tipAmount)
    }

    val driverEarnings: Double
    val platformFee: Double
    val isServerVerified: Boolean

    if (serverDriverRaw != null) {
        isServerVerified = true
        driverEarnings = max(0.0, round2(serverDriverRaw))
        platformFee = if (serverFeeRaw != null) {
            max(0.0, round2(serverFeeRaw))
        } else {
            max(0.0, round2(totalFarePaid - driverEarnings))
        }
    } else if (serverFeeRaw != null) {
        isServerVerified = true
        platformFee = max(0.0, round2(serverFeeRaw))
        driverEarnings = max(0.0, round2(baseFare - platformFee + tipAmount))
    } else {
        isServerVerified = false
        val computedFee = min(baseFare, round2(baseFare * defaultRate + appliedFixedTripFee))
        platformFee = max(0.0, computedFee)
        driverEarnings = max(0.0, round2(baseFare - platformFee + tipAmount))
    }

    val appliedCommissionRate = when {
        ride.appliedCommissionRate != null -> max(0.0, min(1.0, ride.appliedCommissionRate))
        baseFare > 0.0 && isServerVerified -> {
            val percentagePortion = max(0.0, platformFee - appliedFixedTripFee)
            max(0.0, min(1.0, round4(percentagePortion / baseFare)))
        }
        else -> defaultRate
    }

    val appliedCommissionPct = round1(appliedCommissionRate * 100.0)
    val driverBaseShare = max(0.0, round2(driverEarnings - tipAmount))

    return RideFinancialSummary(
        baseFare = baseFare,
        tipAmount = tipAmount,
        totalFarePaid = totalFarePaid,
        platformFee = platformFee,
        driverEarnings = driverEarnings,
        driverBaseShare = driverBaseShare,
        appliedCommissionRate = appliedCommissionRate,
        appliedCommissionPct = appliedCommissionPct,
        appliedFixedTripFee = appliedFixedTripFee,
        isServerVerified = isServerVerified,
        hasEstimatedFallback = hasEstimatedFallback
    )
}
