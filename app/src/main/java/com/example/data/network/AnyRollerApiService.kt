package com.example.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AnyRollerApiService {

    // --- System Health & Maintenance ---
    @GET("/api/health")
    suspend fun getHealth(): Response<HealthResponse>

    @GET("/api/maintenance/status")
    suspend fun getMaintenanceStatus(): Response<MaintenanceStatusResponse>

    // --- Account Compliance & GDPR ---
    @POST("/api/account/accept-terms")
    suspend fun acceptTerms(@Body body: AcceptTermsRequest): Response<GenericSuccessResponse>

    @POST("/api/account/sync-expired-docs")
    suspend fun syncExpiredDocs(@Body body: Map<String, String> = emptyMap()): Response<GenericSuccessResponse>

    @POST("/api/account/gdpr/export")
    suspend fun exportGdprData(@Body body: Map<String, String> = emptyMap()): Response<GdprExportResponse>

    @POST("/api/account/gdpr/delete-account")
    suspend fun deleteAccount(@Body body: Map<String, String> = emptyMap()): Response<GenericSuccessResponse>

    // --- Push Token Lifecycle, Inbox, Presence & 4-Tier WhatsApp ---
    @POST("/api/notifications/register-token")
    suspend fun registerPushToken(@Body body: RegisterTokenRequest): Response<GenericSuccessResponse>

    @POST("/api/notifications/unregister-token")
    suspend fun unregisterPushToken(@Body body: UnregisterTokenRequest): Response<GenericSuccessResponse>

    @POST("/api/chat-push")
    suspend fun sendChatPush(@Body body: ChatPushRequest): Response<GenericSuccessResponse>

    @GET("/api/notifications/preferences")
    suspend fun getNotificationPreferences(): Response<NotificationPreferencesResponse>

    @PATCH("/api/notifications/preferences")
    suspend fun updateNotificationPreferences(
        @Body body: UpdateNotificationPreferencesRequest
    ): Response<NotificationPreferencesResponse>

    @POST("/api/notifications/whatsapp-activation-link")
    suspend fun createWhatsAppActivationLink(
        @Body body: WhatsAppActivationLinkRequest
    ): Response<WhatsAppActivationLinkResponse>

    @POST("/api/notifications/presence")
    suspend fun sendPresenceHeartbeat(
        @Body body: PresenceHeartbeatRequest
    ): Response<GenericSuccessResponse>

    @PATCH("/api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Response<GenericSuccessResponse>

    @DELETE("/api/notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: String): Response<GenericSuccessResponse>

    // --- Chat Safety ---
    @GET("/api/chat/blocks")
    suspend fun getChatBlocks(): Response<ChatBlocksResponse>

    @POST("/api/chat/report-block")
    suspend fun reportAndBlockUser(@Body body: ReportBlockRequest): Response<GenericSuccessResponse>

    @DELETE("/api/chat/blocks/{targetUserId}")
    suspend fun unblockUser(@Path("targetUserId") targetUserId: String): Response<GenericSuccessResponse>

    // --- Driver Heartbeat, Demand Zones & Analytics ---
    @POST("/api/driver/metrics/heartbeat")
    suspend fun sendDriverHeartbeat(@Body body: Map<String, String> = emptyMap()): Response<DriverHeartbeatResponse>

    @GET("/api/rides/demand-zones")
    suspend fun getDemandZones(
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null
    ): Response<DemandZonesResponse>

    @POST("/api/driver/analytics-pulse")
    suspend fun getAnalyticsPulse(@Body body: AnalyticsPulseRequest): Response<AnalyticsPulseResponse>

    // --- Server-Authoritative Ride Lifecycle ---
    @POST("/api/rides/accept")
    suspend fun acceptRide(@Body body: RideAcceptRequest): Response<GenericSuccessResponse>

    @POST("/api/rides/transition")
    suspend fun transitionRide(@Body body: RideTransitionRequest): Response<GenericSuccessResponse>

    @POST("/api/rides/create-trip-payment")
    suspend fun createTripPayment(@Body body: CreateTripPaymentRequest): Response<CreateTripPaymentResponse>

    @POST("/api/rides/complete")
    suspend fun completeRide(@Body body: RideCompleteRequest): Response<GenericSuccessResponse>

    @POST("/api/rides/cancel")
    suspend fun cancelRide(@Body body: RideCancelRequest): Response<GenericSuccessResponse>

    // --- Stripe Connect, Fee Settlement & Gold Driver Subscription ---
    @GET("/api/driver/stripe-balance/{driverId}")
    suspend fun getStripeBalance(@Path("driverId") driverId: String): Response<StripeBalanceResponse>

    @POST("/api/driver/stripe-payout/{driverId}")
    suspend fun requestStripePayout(
        @Path("driverId") driverId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: StripePayoutRequest
    ): Response<GenericSuccessResponse>

    @GET("/api/stripe/account-status")
    suspend fun getStripeAccountStatus(): Response<StripeAccountStatusResponse>

    @POST("/api/stripe/create-connect-account")
    suspend fun createConnectAccount(
        @Body body: Map<String, String> = mapOf("returnTarget" to "driver-terminal")
    ): Response<StripeUrlResponse>

    @POST("/api/stripe/create-login-link")
    suspend fun createStripeLoginLink(
        @Body body: Map<String, String> = emptyMap()
    ): Response<StripeUrlResponse>

    @GET("/api/driver/fee-settlement-status")
    suspend fun getFeeSettlementStatus(): Response<FeeSettlementStatusResponse>

    @POST("/api/driver/settle-fees")
    suspend fun settleFees(@Body body: Map<String, String> = emptyMap()): Response<StripeUrlResponse>

    @POST("/api/create-checkout-session")
    suspend fun createCheckoutSession(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CreateCheckoutSessionRequest
    ): Response<StripeUrlResponse>

    @POST("/api/create-customer-portal-session")
    suspend fun createCustomerPortalSession(
        @Body body: Map<String, String> = emptyMap()
    ): Response<StripeUrlResponse>

    @POST("/api/cancel-subscription")
    suspend fun cancelSubscription(@Body body: SubscriptionActionRequest): Response<GenericSuccessResponse>

    @POST("/api/reactivate-subscription")
    suspend fun reactivateSubscription(@Body body: SubscriptionActionRequest): Response<GenericSuccessResponse>
}
