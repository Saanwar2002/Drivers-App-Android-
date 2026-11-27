package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp

const val CURRENT_TERMS_VERSION = "2026.1"
const val NAMED_FIRESTORE_DB_ID = "ai-studio-anytrader-44dab8b3-bbc9-4352-b725-2cbe7a1dfd2a"

@Keep
data class VerificationDoc(
    val type: String = "",
    val status: String = "missing", // pending | approved | verified | rejected | expired | missing
    val fileUrl: String? = null,
    val expiryDate: String? = null,
    val rejectionReason: String? = null
)

@Keep
data class DaySchedule(
    val status: String = "anytime", // anytime | unavailable | specific
    val startTime: String? = "08:00",
    val endTime: String? = "18:00"
)

@Keep
data class EarningsGoals(
    val today: Double = 200.0,
    val week: Double = 1000.0,
    val month: Double = 4000.0
)

@Keep
data class DriverProfile(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val name: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val photoURL: String? = null,
    val role: String = "",
    val accountType: String = "",
    val capabilities: List<String> = emptyList(),
    val isDriverFlag: Boolean = false,
    val driverApproved: Boolean = false,
    val disabled: Boolean = false,
    val suspended: Boolean = false,
    val banned: Boolean = false,
    val deactivated: Boolean = false,
    val status: String = "",
    val driverStatus: String = "",
    val termsAcceptedVersion: String = "",
    val rating: Double = 4.9,
    val acceptanceRate: Double = 94.0,
    val cancellationRate: Double = 2.0,
    val avgResponseSeconds: Double = 4.2,
    val totalRides: Int = 0,
    val stripeAccountId: String? = null,
    val pendingPlatformFees: Double = 0.0,
    val subscriptionType: String? = null,
    val subscriptionStatus: String? = null,
    val earningsReportExport: Boolean = false,
    val referralCode: String = "",
    val vehicleCategory: String = "standard",
    val vehicleCategories: List<String> = listOf("standard"),
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val vehicleInfo: String = "",
    val vehiclePlate: String = "",
    val totalBusinessMileage: Double = 0.0,
    val proFeaturesEnabled: Boolean = false,
    val petFriendly: Boolean = false,
    val stackingEnabled: Boolean = true,
    val lastJob: Boolean = false,
    val destinationModeActive: Boolean = false,
    val homeAddress: String? = null,
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val zoneEnabled: Boolean = false,
    val zoneMaxDistance: Double = 10.0,
    val defaultNavApp: String = "Google Maps",
    val requirePasscode: Boolean = false,
    val muteHeadsUpVolume: Boolean = false,
    val muteRideOfferAlerts: Boolean = false,
    val documentsComplete: Boolean = false,
    val insuranceProvider: String = "Zego",
    val insuranceExpiry: String? = null,
    val verificationDocs: List<VerificationDoc> = emptyList(),
    val weeklySchedule: Map<String, DaySchedule> = defaultWeeklySchedule(),
    val earningsGoals: EarningsGoals = EarningsGoals()
) {
    val fullName: String
        get() = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { displayName.ifBlank { name.ifBlank { "AnyRoller Driver" } } }

    val initials: String
        get() = fullName.firstOrNull()?.uppercase() ?: "D"

    val isGoldDriver: Boolean
        get() = subscriptionType == "driver_gold" || earningsReportExport

    val hasProTaxAccess: Boolean
        get() = proFeaturesEnabled || isGoldDriver

    val resolvedVehicleTitle: String
        get() = vehicleInfo.takeIf { it.isNotBlank() }
            ?: listOf(vehicleColor, vehicleMake, vehicleModel)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Active PHV Vehicle" }

    fun hasDriverRole(): Boolean {
        val r = role.lowercase()
        val at = accountType.lowercase()
        val caps = capabilities.map { it.lowercase() }
        return r in setOf("driver", "fleet_driver", "taxi_driver") ||
            isDriverFlag ||
            at == "driver" ||
            caps.any { it in setOf("driver", "fleet_driver", "taxi_driver") }
    }

    fun isAccountSuspendedOrDisabled(): Boolean {
        val blockedStatuses = setOf("suspended", "banned", "disabled", "inactive", "deactivated")
        return disabled || suspended || banned || deactivated ||
            status.lowercase() in blockedStatuses ||
            driverStatus.lowercase() in blockedStatuses
    }
}

fun defaultWeeklySchedule(): Map<String, DaySchedule> = linkedMapOf(
    "Monday" to DaySchedule("anytime"),
    "Tuesday" to DaySchedule("anytime"),
    "Wednesday" to DaySchedule("anytime"),
    "Thursday" to DaySchedule("anytime"),
    "Friday" to DaySchedule("anytime"),
    "Saturday" to DaySchedule("anytime"),
    "Sunday" to DaySchedule("anytime")
)

@Keep
data class RideStop(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

@Keep
data class RideRequest(
    val id: String = "",
    val status: String = "pending",
    val passengerId: String = "",
    val passengerName: String = "Passenger",
    val passengerRating: Double = 4.9,
    val passengerPhone: String? = null,
    val riderNotes: String? = null,
    val driverId: String? = null,
    val assignedDriverId: String? = null,
    val pickupAddress: String = "",
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val dropoffAddress: String = "",
    val dropoffLat: Double = 0.0,
    val dropoffLng: Double = 0.0,
    val passengerLat: Double? = null,
    val passengerLng: Double? = null,
    val stops: List<RideStop> = emptyList(),
    val currentStopIndex: Int = 0,
    val fareEstimate: Double = 0.0,
    val price: Double? = null,
    val quotedPrice: Double? = null,
    val surgeMultiplier: Double = 1.0,
    val surgeModel: String = "multiplier",
    val surgeFixedAmount: Double = 0.0,
    val vehicleCategory: String = "standard",
    val distanceMiles: Double = 0.0,
    val durationMinutes: Int = 0,
    val paymentMethod: String = "card", // card | cash | qr
    val hasCardOnFile: Boolean = false,
    val handshakeCode: String? = null,
    val driverRequirePasscode: Boolean = false,
    val fareContractVersion: String? = null,
    val finalFare: Double? = null,
    val totalFare: Double? = null,
    val totalFarePaid: Double? = null,
    val tipAmount: Double = 0.0,
    val platformFee: Double? = null,
    val calculatedPlatformFee: Double? = null,
    val commissionAmount: Double? = null,
    val driverEarnings: Double? = null,
    val calculatedDriverEarnings: Double? = null,
    val appliedCommissionRate: Double? = null,
    val appliedFixedTripFee: Double? = null,
    val paymentStatus: String? = null,
    val paymentUrl: String? = null,
    val paidWaitSeconds: Int = 0,
    val createdAtMs: Long = 0L,
    val completedAtMs: Long = 0L,
    val scheduledTime: String? = null,
    val offeredAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val isModifiedByPassenger: Boolean = false,
    val cancelledBy: String? = null,
    val cancellationFee: Double = 0.0,
    val priorityFee: Double = 0.0,
    val abandonmentFee: Double = 0.0,
    val cashCollected: Double? = null,
    val unpaidBalance: Double = 0.0
)

@Keep
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String? = null,
    val imageUrl: String? = null,
    val createdAtMs: Long = 0L
)

@Keep
data class DriverMetrics(
    val date: String = "",
    val onlineSecondsToday: Long = 0L,
    val ridesToday: Int = 0,
    val earningsToday: Double = 0.0,
    val lifetimeRides: Int = 0,
    val lifetimeEarnings: Double = 0.0,
    val acceptanceRate: Double = 95.0,
    val rating: Double = 4.9
)

@Keep
data class DriverExpense(
    val id: String = "",
    val driverId: String = "",
    val amount: Double = 0.0,
    val category: String = "Fuel",
    val date: String = "",
    val notes: String = "",
    val receiptImage: String? = null,
    val createdAt: Timestamp? = null
)

@Keep
data class DriverNotification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "system", // ride | alert | anytrader | document | system
    val read: Boolean = false,
    val rideId: String? = null,
    val createdAtMs: Long = 0L
)

@Keep
data class RoadHazard(
    val id: String = "",
    val type: String = "Traffic",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val reporterId: String = "",
    val expiresAtMs: Long = 0L
)

@Keep
data class SurgeRules(
    val lowWaitMins: Int = 5,
    val mediumWaitMins: Int = 10,
    val highWaitMins: Int = 20,
    val lowFee: Double = 1.0,
    val mediumFee: Double = 2.0,
    val highFee: Double = 3.5,
    val lowMultiplier: Double = 1.1,
    val mediumMultiplier: Double = 1.3,
    val highMultiplier: Double = 1.6,
    val lowColor: String = "green",
    val mediumColor: String = "amber",
    val highColor: String = "red",
    val surgeOpacity: Int = 40
)

@Keep
data class VehicleTypeConfig(
    val id: String = "standard",
    val name: String = "AnyTrader Standard",
    val multiplier: Double = 1.0
)

fun defaultVehicleTypes(): List<VehicleTypeConfig> = listOf(
    VehicleTypeConfig("standard", "Standard", 1.0),
    VehicleTypeConfig("executive", "Executive", 1.5),
    VehicleTypeConfig("mpv", "XL (6-Seater)", 1.4),
    VehicleTypeConfig("van", "Pro Van", 1.8),
    VehicleTypeConfig("vip", "VIP Luxury", 2.2)
)

@Keep
data class RidePlatformConfig(
    val baseFare: Double = 2.5,
    val distanceRate: Double = 1.2,
    val timeRate: Double = 0.15,
    val waitRatePerMinute: Double = 0.25,
    val minFare: Double = 5.0,
    val priorityFee: Double = 3.0,
    val commissionRate: Double = 0.12,
    val goldCommissionRate: Double = 0.10,
    val fixedTripFee: Double = 0.0,
    val maxDailyDriverHours: Double = 10.0,
    val freeWaitSeconds: Int = 180,
    val dispatchRadiusMiles: Double = 15.0,
    val dispatchTimeoutSeconds: Int = 15,
    val autoDispatchEnabled: Boolean = true,
    val allowRiderAbandonment: Boolean = false,
    val surgeEnabled: Boolean = true,
    val surgeModel: String = "fixed", // fixed | multiplier
    val surgeFixedAmount: Double = 2.0,
    val surgeMultiplierValue: Double = 1.4,
    val surgeRules: SurgeRules = SurgeRules(),
    val vehicleTypes: List<VehicleTypeConfig> = defaultVehicleTypes()
)

@Keep
data class GlobalTiersConfig(
    val standardCommissionRate: Double? = null,
    val standardFixedTripFee: Double? = null,
    val goldCommissionRate: Double? = null,
    val goldFixedTripFee: Double? = null
)

@Keep
data class GlobalPlatformStatus(
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String? = null,
    val taxiComingSoon: Boolean = false,
    val taxiComingSoonTitle: String? = null,
    val taxiComingSoonMessage: String? = null
)

@Keep
data class DemandZone(
    val id: String = "",
    val name: String = "",
    val label: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val level: String = "medium", // high | medium | low
    val multiplier: Double = 1.0,
    val surgeFee: Double = 0.0,
    val isFixedModel: Boolean = true,
    val radiusMeters: Double = 800.0,
    val uiColor: String = "amber",
    val uiOpacity: Int = 40,
    val waitWarning: Boolean = false
)

@Keep
data class InsurancePartner(
    val id: String = "",
    val name: String = "",
    val tagline: String = "",
    val estimatedMonthlyGbp: Double = 0.0,
    val url: String = "",
    val badge: String? = null
)

@Keep
data class IncomingOfferPush(
    val rideId: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropoffLat: Double,
    val dropoffLng: Double,
    val fareEstimate: Double,
    val surgeMultiplier: Double,
    val vehicleCategory: String,
    val distanceMiles: Double,
    val offeredAtMs: Long,
    val expiresAtMs: Long
)
