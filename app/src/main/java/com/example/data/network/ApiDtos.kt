package com.example.data.network

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class ApiErrorBody(
    val error: String? = null,
    val message: String? = null,
    val code: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String? = null,
    val service: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class MaintenanceStatusResponse(
    val maintenanceMode: Boolean = false,
    val message: String? = null
)

// --- Account Compliance & GDPR ---

@Keep
@JsonClass(generateAdapter = true)
data class AcceptTermsRequest(
    val termsVersion: String,
    val privacyVersion: String = termsVersion,
    val marketingOptIn: Boolean = false,
    val partnerDataSharingOptIn: Boolean = false
)

@Keep
@JsonClass(generateAdapter = true)
data class GenericSuccessResponse(
    val success: Boolean = false,
    val message: String? = null,
    val status: String? = null,
    val error: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class GdprExportResponse(
    val success: Boolean = false,
    val exportedAt: String? = null,
    val downloadUrl: String? = null,
    val data: Map<String, Any?>? = null
)

// --- Push Notifications, Presence & 4-Tier WhatsApp Offline Fallback ---

@Keep
@JsonClass(generateAdapter = true)
data class RegisterTokenRequest(
    val fcmToken: String,
    val deviceId: String,
    val platform: String = "android"
)

@Keep
@JsonClass(generateAdapter = true)
data class UnregisterTokenRequest(
    val deviceId: String,
    val token: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class ChatPushRequest(
    val rideId: String,
    val recipientId: String,
    val title: String,
    val message: String,
    val body: String = message,
    val type: String? = "chat" // "chat" | "ride_arrived"
)

@Keep
@JsonClass(generateAdapter = true)
data class NotificationPreferencesDto(
    val whatsappOptIn: Boolean = false,
    val whatsappPhoneMasked: String? = null,
    val whatsappHasPhone: Boolean = false,
    val whatsappVerified: Boolean = false,
    val whatsappWindowActive: Boolean = false,
    val whatsappWindowExpiresAt: String? = null,
    val emailNotifications: Boolean = true,
    val pushNotifications: Boolean = true,
    val activePushDevicesCount: Int = 0,
    val businessWhatsAppConfigured: Boolean = false,
    val metaApiConfigured: Boolean = false
)

@Keep
@JsonClass(generateAdapter = true)
data class NotificationPreferencesResponse(
    val success: Boolean = false,
    val preferences: NotificationPreferencesDto? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class UpdateNotificationPreferencesRequest(
    val whatsappPhone: String? = null,
    val whatsappOptIn: Boolean? = null,
    val pushNotifications: Boolean? = null,
    val emailNotifications: Boolean? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class WhatsAppActivationLinkRequest(
    val contextLabel: String = "AnyRoller Driver Alerts"
)

@Keep
@JsonClass(generateAdapter = true)
data class WhatsAppActivationLinkResponse(
    val success: Boolean = false,
    val activationCode: String? = null,
    val businessNumberConfigured: Boolean = false,
    val waMeUrl: String? = null,
    val prefilledMessage: String? = null,
    val expiresAt: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class PresenceHeartbeatRequest(
    val activeConversationId: String?,
    val markRead: Boolean = true
)

@Keep
@JsonClass(generateAdapter = true)
data class ChatBlocksResponse(
    val blockedUserIds: List<String> = emptyList(),
    val blockedByUserIds: List<String> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class ReportBlockRequest(
    val targetUserId: String,
    val rideId: String? = null,
    val reason: String,
    val blockUser: Boolean = true
)

// --- Driver Heartbeat, Demand Zones & Analytics Pulse ---

@Keep
@JsonClass(generateAdapter = true)
data class DriverHeartbeatResponse(
    val success: Boolean = false,
    val limitReached: Boolean = false,
    val maxDailyDriverHours: Double? = null,
    val onlineSecondsToday: Long? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class DemandZoneDto(
    val id: String? = null,
    val name: String? = null,
    val area: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val level: String? = null,
    val intensity: String? = null,
    val multiplier: Double? = null,
    val surgeFee: Double? = null,
    val radiusMeters: Double? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class DemandZonesResponse(
    val success: Boolean = false,
    val zones: List<DemandZoneDto> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class DriverStatsPayload(
    val rating: Double,
    val acceptance: Double,
    val cancellation: Double,
    val responseTime: Double
)

@Keep
@JsonClass(generateAdapter = true)
data class AnalyticsPulseRequest(
    val driverStats: DriverStatsPayload
)

@Keep
@JsonClass(generateAdapter = true)
data class AnalyticsPulseResponse(
    val success: Boolean = false,
    val insight: String? = null,
    val summary: String? = null,
    val tips: List<String>? = null
)

// --- Server-Authoritative Ride Lifecycle ---

@Keep
@JsonClass(generateAdapter = true)
data class RideAcceptRequest(
    val rideId: String
)

@Keep
@JsonClass(generateAdapter = true)
data class RideTransitionRequest(
    val rideId: String,
    val action: String, // arrived | in_progress | stop_progress | early_completion_reason
    val paidWaitSeconds: Int? = null,
    val currentStopIndex: Int? = null,
    val earlyCompletionReason: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class CreateTripPaymentRequest(
    val rideId: String,
    val paidWaitSeconds: Int? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class CreateTripPaymentResponse(
    val success: Boolean = false,
    val url: String? = null,
    val intentId: String? = null,
    val effectiveBaseFare: Double? = null,
    val sanitizedTip: Double? = null,
    val totalAmount: Double? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class RideCompleteRequest(
    val rideId: String,
    val action: String, // auto_card | qr_pending | qr_manual_close | cash_report | cash_finalize | abandonment
    val paidWaitSeconds: Int? = null,
    val cashCollected: Double? = null,
    val earlyCompletionReason: String? = null,
    val paymentUrl: String? = null,
    val abandonmentFee: Double? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class RideCancelRequest(
    val rideId: String,
    val cancelledBy: String = "driver"
)

// --- Stripe Connect, Fee Settlements & Gold Driver Subscription ---

@Keep
@JsonClass(generateAdapter = true)
data class StripeBalanceResponse(
    val available: Double = 0.0,
    val pending: Double = 0.0,
    val currency: String = "gbp",
    val connected: Boolean = false
)

@Keep
@JsonClass(generateAdapter = true)
data class StripePayoutRequest(
    val idempotencyKey: String,
    val amount: Double? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class StripeAccountStatusResponse(
    val connected: Boolean = false,
    val stripeAccountId: String? = null,
    val payoutsEnabled: Boolean = false,
    val chargesEnabled: Boolean = false,
    val detailsSubmitted: Boolean = false,
    val error: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class StripeUrlResponse(
    val success: Boolean = false,
    val url: String? = null,
    val error: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class FeeSettlementStatusResponse(
    val pendingPlatformFees: Double = 0.0,
    val hasPendingFees: Boolean = false,
    val currency: String = "gbp"
)

@Keep
@JsonClass(generateAdapter = true)
data class CreateCheckoutSessionRequest(
    val userId: String,
    val priceId: String = "price_driver_gold",
    val tierName: String = "Gold Driver Subscription",
    val mode: String = "subscription",
    val successUrl: String,
    val cancelUrl: String,
    val metadata: Map<String, String>
)

@Keep
@JsonClass(generateAdapter = true)
data class SubscriptionActionRequest(
    val subscriptionType: String = "driver_gold"
)
