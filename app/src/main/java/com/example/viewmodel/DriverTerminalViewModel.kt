package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.DaySchedule
import com.example.data.model.DemandZone
import com.example.data.model.DriverExpense
import com.example.data.model.DriverMetrics
import com.example.data.model.DriverNotification
import com.example.data.model.DriverProfile
import com.example.data.model.FallbackFareConfig
import com.example.data.model.GlobalPlatformStatus
import com.example.data.model.InsurancePartner
import com.example.data.model.RidePlatformConfig
import com.example.data.model.RideRequest
import com.example.data.model.RoadHazard
import com.example.data.model.resolveRideFinancialSummary
import com.example.data.network.AnalyticsPulseRequest
import com.example.data.network.ApiClient
import com.example.data.network.ApiResult
import com.example.data.network.ChatPushRequest
import com.example.data.network.CreateCheckoutSessionRequest
import com.example.data.network.CreateTripPaymentRequest
import com.example.data.network.DriverStatsPayload
import com.example.data.network.FeeSettlementStatusResponse
import com.example.data.network.NotificationPreferencesDto
import com.example.data.network.PresenceHeartbeatRequest
import com.example.data.network.RatePassengerRequest
import com.example.data.network.ReportBlockRequest
import com.example.data.network.RideAcceptRequest
import com.example.data.network.RideCancelRequest
import com.example.data.network.RideCompleteRequest
import com.example.data.network.RideDeclineRequest
import com.example.data.network.RideTransitionRequest
import com.example.data.network.StripeAccountStatusResponse
import com.example.data.network.StripeBalanceResponse
import com.example.data.network.StripePayoutRequest
import com.example.data.network.UpdateNotificationPreferencesRequest
import com.example.data.network.WhatsAppActivationLinkRequest
import com.example.data.network.WhatsAppActivationLinkResponse
import com.example.data.repository.ActiveRouteResult
import com.example.data.repository.CockpitLocalSettings
import com.example.data.repository.DirectionsRepository
import com.example.data.repository.DriverFirestoreRepository
import com.example.data.repository.DriverPreferencesStore
import com.example.service.AnyRollerMessagingService
import com.example.service.DriverAudioFeedback
import com.example.service.DriverLocationService
import com.example.service.DriverLocationSnapshot
import com.example.service.ExternalIntentHelpers
import com.example.service.FloatingRideOverlayController
import com.example.service.GeocoderHelper
import com.example.service.NetworkMonitor
import com.example.service.OfferIntelligenceHelper
import com.example.service.ScheduledRideReminderScheduler
import com.example.service.UrgentPushEvent
import com.example.service.UrgentPushEventBus
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class DriverTab {
    TERMINAL,
    EARNINGS,
    MESSAGES,
    MY_JOBS
}

enum class DriverOverlayRoute {
    NONE,
    MENU,
    DOCUMENTS,
    INSURANCE,
    AVAILABILITY,
    ZONES,
    ANALYTICS,
    PROFILE,
    EXPENSES,
    TRIP_HISTORY
}

enum class TerminalRideState {
    IDLE,
    OFFER,
    EN_ROUTE_PICKUP,
    WAITING,
    IN_PROGRESS,
    COMPLETED_PAYMENT,
    REVIEW
}

data class ExternalNavLaunchEvent(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val waypoints: List<Pair<Double, Double>> = emptyList()
)

data class DriverTerminalUiState(
    val uid: String = "",
    val profile: DriverProfile = DriverProfile(),
    val activeTab: DriverTab = DriverTab.TERMINAL,
    val overlayRoute: DriverOverlayRoute = DriverOverlayRoute.NONE,
    val isOnline: Boolean = false,
    val rideState: TerminalRideState = TerminalRideState.IDLE,
    val driverLocation: DriverLocationSnapshot = DriverLocationSnapshot(),
    val platformConfig: RidePlatformConfig = RidePlatformConfig(),
    val metrics: DriverMetrics = DriverMetrics(),
    val activeRide: RideRequest? = null,
    val queuedStackedRide: RideRequest? = null,
    val incomingOffer: RideRequest? = null,
    val stackedRideOffer: RideRequest? = null,
    val reviewRide: RideRequest? = null,
    val completedRides: List<RideRequest> = emptyList(),
    val scheduledRides: List<RideRequest> = emptyList(),
    val offerSecondsLeft: Int = 15,
    val stackedOfferSecondsLeft: Int = 15,
    val waitSecondsElapsed: Int = 0,
    val isWaitingAtStop: Boolean = false,
    val currentStopWaitSeconds: Int = 0,
    val accumulatedPaidWaitSeconds: Int = 0,
    val abandonmentWarningSent: Boolean = false,
    val isAwaitingCashConfirm: Boolean = false,
    val cashConfirmSecondsLeft: Int = 0,
    val showEarlyArrivalDialog: Boolean = false,
    val showCompleteConfirmDialog: Boolean = false,
    val isEarlyDropoff: Boolean = false,
    val showStartTripReminder: Boolean = false,
    val showLeaveStopReminder: Boolean = false,
    val lastRideUpdateNotice: String? = null,
    val pickupZoomEventId: Long = 0L,
    val paymentQrUrl: String? = null,
    val showHazardModal: Boolean = false,
    val showChatSheet: Boolean = false,
    val chatMessages: List<ChatMessage> = emptyList(),
    val unreadChatCount: Int = 0,
    val quickMessageCooldownSeconds: Int = 0,
    val passengerLiveLocation: Pair<Double, Double>? = null,
    val blockedUserIds: Set<String> = emptySet(),
    val notifications: List<DriverNotification> = emptyList(),
    val expenses: List<DriverExpense> = emptyList(),
    val hazards: List<RoadHazard> = emptyList(),
    val demandZones: List<DemandZone> = emptyList(),
    val insurancePartners: List<InsurancePartner> = emptyList(),
    val localVehicleCategories: Set<String> = setOf("standard"),
    val localPetFriendly: Boolean = false,
    val stripeBalance: StripeBalanceResponse = StripeBalanceResponse(),
    val stripeAccountStatus: StripeAccountStatusResponse = StripeAccountStatusResponse(),
    val feeStatus: FeeSettlementStatusResponse = FeeSettlementStatusResponse(),
    val notificationPrefs: NotificationPreferencesDto = NotificationPreferencesDto(),
    val whatsAppActivation: WhatsAppActivationLinkResponse? = null,
    val analyticsInsight: String? = null,
    val analyticsTips: List<String> = emptyList(),
    val activeRoute: ActiveRouteResult = ActiveRouteResult(),
    val remainingRoutePoints: List<com.google.android.gms.maps.model.LatLng> = emptyList(),
    val liveRemainingDistanceMeters: Int? = null,
    val liveRemainingEtaMinutes: Int? = null,
    val snappedRouteBearing: Float? = null,
    val isAutoNavHeadUp: Boolean = true,
    val isAutoNavPaused: Boolean = false,
    val autoNavPausedAtMs: Long? = null,
    val routeRecalcLimitReached: Boolean = false,
    val currentNavStepIndex: Int = 0,
    val currentStepDistanceMeters: Int? = null,
    val isActiveCardCollapsed: Boolean = true,
    val incomingChatPopup: ChatMessage? = null,
    val isNetworkConnected: Boolean = true,
    val globalPlatformStatus: GlobalPlatformStatus = GlobalPlatformStatus(),
    val isBusy: Boolean = false,
    val toastMessage: String? = null,
    val customTabUrlToOpen: String? = null,
    val whatsAppUrlToOpen: String? = null,
    // Part 2 Cockpit Features
    val cockpitSettings: CockpitLocalSettings = CockpitLocalSettings(),
    val pendingExternalNav: ExternalNavLaunchEvent? = null,
    val isOnBreak: Boolean = false,
    val breakSecondsLeft: Int = 0,
    val breakIsOpenEnded: Boolean = false,
    val currentSpeedMph: Int = 0,
    val isOverSpeedAlert: Boolean = false,
    val shiftStartedAtMs: Long? = null,
    val shiftOnlineSeconds: Long = 0L,
    val shiftTripsCompleted: Int = 0,
    val shiftEarnings: Double = 0.0,
    val shiftPaidMiles: Double = 0.0,
    val shiftDeadheadMiles: Double = 0.0,
    val fatigueReminderActive: Boolean = false
) {
    val shiftHourlyRate: Double
        get() = if (shiftOnlineSeconds >= 60L) {
            (shiftEarnings / (shiftOnlineSeconds / 3600.0)).coerceAtLeast(0.0)
        } else 0.0

    val totalPaidWaitSeconds: Int
        get() {
            val pickupPaid = if (rideState == TerminalRideState.WAITING) {
                (waitSecondsElapsed - platformConfig.freeWaitSeconds).coerceAtLeast(0)
            } else 0
            return accumulatedPaidWaitSeconds + pickupPaid + currentStopWaitSeconds
        }

    val unreadNotificationsCount: Int
        get() = notifications.count { !it.read }

    val activeCommissionRate: Double
        get() = if (profile.isGoldDriver) platformConfig.goldCommissionRate else platformConfig.commissionRate

    val pendingFeesAmount: Double
        get() = maxOf(profile.pendingPlatformFees, feeStatus.pendingPlatformFees)

    val needsStripeSetup: Boolean
        get() = !stripeAccountStatus.connected && profile.stripeAccountId.isNullOrBlank()

    val hasDocumentActionRequired: Boolean
        get() {
            val requiredTypes = setOf("dvla_license", "phv_license", "insurance", "mot", "logbook")
            val byType = profile.verificationDocs.associateBy { it.type }
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.UK)
            val soonThresholdMs = System.currentTimeMillis() + 28L * 24L * 3600L * 1000L
            return requiredTypes.any { key ->
                val doc = byType[key] ?: return@any true
                if (doc.status.lowercase() !in setOf("approved", "verified", "pending")) return@any true
                val exp = doc.expiryDate?.takeIf { it.isNotBlank() }?.let {
                    runCatching { dateFmt.parse(it)?.time }.getOrNull()
                }
                exp != null && exp <= soonThresholdMs
            }
        }

    val unreadBroadcast: DriverNotification?
        get() = notifications.firstOrNull { !it.read && it.type.lowercase() in setOf("broadcast", "alert", "system") }

    val hasMenuAttentionDot: Boolean
        get() = hasDocumentActionRequired || needsStripeSetup || pendingFeesAmount > 0.0 || unreadBroadcast != null

    val todayEarningsComputed: Double
        get() {
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sum = completedRides
                .filter { maxOf(it.completedAtMs, it.createdAtMs) >= startOfDay }
                .sumOf {
                    resolveRideFinancialSummary(
                        it,
                        FallbackFareConfig(activeCommissionRate, platformConfig.fixedTripFee)
                    ).driverEarnings
                }
            return maxOf(sum, metrics.earningsToday)
        }

    val weekEarningsComputed: Double
        get() {
            val weekAgo = System.currentTimeMillis() - 7L * 24L * 3600L * 1000L
            return completedRides
                .filter { maxOf(it.completedAtMs, it.createdAtMs) >= weekAgo }
                .sumOf {
                    resolveRideFinancialSummary(
                        it,
                        FallbackFareConfig(activeCommissionRate, platformConfig.fixedTripFee)
                    ).driverEarnings
                }
        }

    val monthEarningsComputed: Double
        get() {
            val monthAgo = System.currentTimeMillis() - 30L * 24L * 3600L * 1000L
            return completedRides
                .filter { maxOf(it.completedAtMs, it.createdAtMs) >= monthAgo }
                .sumOf {
                    resolveRideFinancialSummary(
                        it,
                        FallbackFareConfig(activeCommissionRate, platformConfig.fixedTripFee)
                    ).driverEarnings
                }
        }

    val yearEarningsComputed: Double
        get() {
            val yearAgo = System.currentTimeMillis() - 365L * 24L * 3600L * 1000L
            return completedRides
                .filter { maxOf(it.completedAtMs, it.createdAtMs) >= yearAgo }
                .sumOf {
                    resolveRideFinancialSummary(
                        it,
                        FallbackFareConfig(activeCommissionRate, platformConfig.fixedTripFee)
                    ).driverEarnings
                }
        }
}

class DriverTerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val repository = DriverFirestoreRepository(context)
    private val prefsStore = DriverPreferencesStore(context)
    private val directionsRepo = DirectionsRepository()
    private val audioFeedback = DriverAudioFeedback(context)
    private val networkMonitor = NetworkMonitor(context)
    private val floatingOverlay = FloatingRideOverlayController(
        context = context,
        onAcceptOffer = { isStacked -> acceptOffer(isStacked = isStacked) },
        onDeclineOffer = { isStacked -> declineOffer(isStacked = isStacked) }
    )

    private val _uiState = MutableStateFlow(DriverTerminalUiState())
    val uiState: StateFlow<DriverTerminalUiState> = _uiState.asStateFlow()

    private var boundUid: String? = null
    private var bindingJobs = mutableListOf<Job>()
    private var chatJob: Job? = null
    private var activeChatRideId: String? = null
    private var lastAlertedChatMsgId: String? = null
    private var passengerLocationJob: Job? = null
    private var activePassengerLocId: String? = null
    private var presenceJob: Job? = null
    private var waitingStartedAtMs: Long? = null
    private var stopWaitStartMs: Long? = null
    private var stopWaitOriginLat: Double? = null
    private var stopWaitOriginLng: Double? = null
    private var pickupProximityStartMs: Long? = null
    private var hasAutoZoomedPickupRideId: String? = null
    private var hasReachedCurrentStopKey: String? = null
    private var dismissedStartTripReminderRideId: String? = null
    private var consecutiveMissedOffers: Int = 0
    private var lastTrackedActiveRide: RideRequest? = null
    private var lastActiveRideId: String? = null
    private var lastQueuedStackedRideId: String? = null
    private var lastAlertedCancelledQueuedId: String? = null
    private var declinedOfferIds: Set<String> = emptySet()
    private var lastAlertedOfferId: String? = null
    private var lastRouteLegKey: String? = null
    private var routeRecalcCount: Int = 0
    private var lastRouteFetchLat: Double = 0.0
    private var lastRouteFetchLng: Double = 0.0
    private var lastRecalcTimestampMs: Long = 0L
    private var consecutiveOffRouteFixes: Int = 0
    private var lastEtaSyncMs: Long = 0L
    private var incomingChatPopupJob: Job? = null
    private var lastSpokenStepIndex: Int = -1
    private val spokenStepThresholds: MutableSet<String> = mutableSetOf()
    private var hasAnnouncedLegArrivalKey: String? = null
    private var lastCollapsedRideState: TerminalRideState? = null
    // Part 2 Cockpit tracking fields
    private var breakEndsAtMs: Long? = null
    private var lastOverSpeedAlertMs: Long = 0L
    private var lastAutoAcceptedOfferId: String? = null
    private var lastScheduledReminderSig: String = ""
    private var lastDeadheadLat: Double? = null
    private var lastDeadheadLng: Double? = null
    private var fatigueReminderTriggeredForShift: Boolean = false

    init {
        startOneSecondTicker()
        observeSharedLocationAndPushEvents()
        viewModelScope.launch {
            networkMonitor.observeIsOnline().collect { online ->
                _uiState.update { it.copy(isNetworkConnected = online) }
            }
        }
        viewModelScope.launch {
            prefsStore.cockpitSettingsFlow.collect { settings ->
                _uiState.update { it.copy(cockpitSettings = settings) }
            }
        }
        viewModelScope.launch {
            combine(
                _uiState,
                UrgentPushEventBus.isFullScreenVisible,
                UrgentPushEventBus.isSplitScreenMode
            ) { state, fullScreen, splitScreen ->
                Triple(state, fullScreen, splitScreen)
            }.collect { (state, fullScreen, splitScreen) ->
                floatingOverlay.syncState(
                    state = state,
                    isFullScreenVisible = fullScreen,
                    isSplitScreen = splitScreen
                )
            }
        }
    }

    fun bindToDriver(uid: String, initialProfile: DriverProfile) {
        if (boundUid == uid) {
            _uiState.update { it.copy(profile = initialProfile) }
            return
        }
        unbindAll()
        boundUid = uid
        _uiState.update {
            it.copy(
                uid = uid,
                profile = initialProfile,
                isOnline = UrgentPushEventBus.isLocationServiceRunning.value
            )
        }

        bindingJobs += viewModelScope.launch {
            repository.observeDriverProfile(uid).collect { profile ->
                if (profile != null) {
                    _uiState.update { it.copy(profile = profile) }
                }
            }
        }
        bindingJobs += viewModelScope.launch {
            combine(
                repository.observePlatformRideConfig(),
                repository.observeGlobalTiersConfig()
            ) { rideCfg, tiersCfg ->
                rideCfg.copy(
                    commissionRate = tiersCfg.standardCommissionRate ?: rideCfg.commissionRate,
                    goldCommissionRate = tiersCfg.goldCommissionRate ?: rideCfg.goldCommissionRate,
                    fixedTripFee = tiersCfg.standardFixedTripFee ?: rideCfg.fixedTripFee
                )
            }.collect { effectiveCfg ->
                _uiState.update { it.copy(platformConfig = effectiveCfg) }
                // Immediately refresh live demand zones whenever Admin changes surge rules/model
                refreshDemandZones()
            }
        }
        bindingJobs += viewModelScope.launch {
            repository.observeGlobalPlatformStatus().collect { status ->
                _uiState.update { it.copy(globalPlatformStatus = status) }
            }
        }
        bindingJobs += viewModelScope.launch {
            // Exact mirror of DriverTerminal.tsx 30-second live demand zone polling
            while (isActive) {
                delay(30_000L)
                refreshDemandZones()
            }
        }
        bindingJobs += viewModelScope.launch {
            repository.observeDriverMetrics(uid).collect { metrics ->
                _uiState.update { it.copy(metrics = metrics) }
            }
        }
        bindingJobs += viewModelScope.launch {
            repository.observeDriverNotifications(uid).collect { list ->
                _uiState.update { it.copy(notifications = list) }
            }
        }
        bindingJobs += viewModelScope.launch {
            repository.observeDriverExpenses(uid).collect { list ->
                _uiState.update { it.copy(expenses = list) }
            }
        }
        bindingJobs += viewModelScope.launch {
            repository.observeRoadHazards().collect { list ->
                _uiState.update { it.copy(hazards = list) }
            }
        }
        bindingJobs += viewModelScope.launch {
            repository.observeInsurancePartners().collect { list ->
                _uiState.update { it.copy(insurancePartners = list) }
            }
        }
        bindingJobs += viewModelScope.launch {
            prefsStore.declinedOfferIdsFlow.collect { set ->
                declinedOfferIds = set
            }
        }
        bindingJobs += viewModelScope.launch {
            combine(
                prefsStore.localVehicleCategoriesFlow,
                prefsStore.localPetFriendlyFlow
            ) { cats, pet -> cats to pet }.collect { (cats, pet) ->
                _uiState.update {
                    it.copy(localVehicleCategories = cats, localPetFriendly = pet)
                }
            }
        }
        bindingJobs += viewModelScope.launch {
            combine(
                repository.observeDriverRides(uid),
                repository.observePendingRideOffers()
            ) { driverRides, pendingOffers ->
                driverRides to pendingOffers
            }.collect { (driverRides, pendingOffers) ->
                reconcileRidesAndOffers(uid, driverRides, pendingOffers)
            }
        }

        refreshFinancials()
        loadNotificationPreferences()
        loadChatBlocks()
        refreshDemandZones()
    }

    private fun unbindAll() {
        bindingJobs.forEach { it.cancel() }
        bindingJobs.clear()
        chatJob?.cancel()
        passengerLocationJob?.cancel()
        activePassengerLocId = null
        presenceJob?.cancel()
        boundUid = null
    }

    private fun observeSharedLocationAndPushEvents() {
        viewModelScope.launch {
            UrgentPushEventBus.lastLocation.collect { snap ->
                val prev = _uiState.value
                val speedMph = if (snap.hasRealFix) {
                    (snap.speedMps * 2.23694f).roundToInt().coerceAtLeast(0)
                } else 0
                val threshold = prev.cockpitSettings.speedAlertMph
                val isOverSpeed = threshold > 0 && speedMph > threshold
                if (isOverSpeed) {
                    val now = System.currentTimeMillis()
                    if (now - lastOverSpeedAlertMs >= 45_000L) {
                        lastOverSpeedAlertMs = now
                        ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 90, 70, 90))
                        audioFeedback.speakHeadsUp(
                            "Speed alert: $speedMph miles per hour.",
                            prev.profile.muteHeadsUpVolume
                        )
                    }
                }
                // Accumulate live deadhead mileage while heading to pickup
                var deadheadDeltaMiles = 0.0
                if (snap.hasRealFix && prev.rideState == TerminalRideState.EN_ROUTE_PICKUP) {
                    val prevLat = lastDeadheadLat
                    val prevLng = lastDeadheadLng
                    if (prevLat != null && prevLng != null) {
                        val movedMeters = distanceMeters(prevLat, prevLng, snap.lat, snap.lng)
                        if (movedMeters in 5f..250f) {
                            deadheadDeltaMiles = movedMeters / 1609.344
                        }
                    }
                    lastDeadheadLat = snap.lat
                    lastDeadheadLng = snap.lng
                } else {
                    lastDeadheadLat = null
                    lastDeadheadLng = null
                }

                _uiState.update {
                    it.copy(
                        driverLocation = snap,
                        currentSpeedMph = speedMph,
                        isOverSpeedAlert = isOverSpeed,
                        shiftDeadheadMiles = it.shiftDeadheadMiles + deadheadDeltaMiles
                    )
                }
                evaluateLocationGeofences(snap)
            }
        }
        viewModelScope.launch {
            UrgentPushEventBus.events.collect { event ->
                when (event) {
                    is UrgentPushEvent.RideOffer -> {
                        val offer = event.offer
                        val stNow = _uiState.value
                        if (offer.rideId !in declinedOfferIds && stNow.isOnline && !stNow.isOnBreak) {
                            val synthetic = RideRequest(
                                id = offer.rideId,
                                status = "pending",
                                pickupAddress = offer.pickupAddress,
                                pickupLat = offer.pickupLat,
                                pickupLng = offer.pickupLng,
                                dropoffAddress = offer.dropoffAddress,
                                dropoffLat = offer.dropoffLat,
                                dropoffLng = offer.dropoffLng,
                                fareEstimate = offer.fareEstimate,
                                surgeMultiplier = offer.surgeMultiplier,
                                vehicleCategory = offer.vehicleCategory,
                                distanceMiles = offer.distanceMiles,
                                offeredAtMs = offer.offeredAtMs,
                                expiresAtMs = offer.expiresAtMs
                            )
                            _uiState.update { state ->
                                if (state.activeRide != null && state.profile.stackingEnabled && !state.profile.lastJob) {
                                    state.copy(stackedRideOffer = synthetic)
                                } else if (state.activeRide == null) {
                                    state.copy(
                                        incomingOffer = synthetic,
                                        rideState = TerminalRideState.OFFER
                                    )
                                } else state
                            }
                        }
                    }
                    is UrgentPushEvent.RideCancelled -> {
                        AnyRollerMessagingService.cancelOfferNotification(context, event.rideId)
                        val before = _uiState.value
                        val isQueuedStackedCancelled = before.queuedStackedRide?.id == event.rideId ||
                            lastQueuedStackedRideId == event.rideId
                        if (isQueuedStackedCancelled && lastAlertedCancelledQueuedId != event.rideId) {
                            notifyQueuedStackedRideCancelled(event.rideId, 0.0)
                        }
                        _uiState.update { state ->
                            val isActiveCancelled = state.activeRide?.id == event.rideId
                            val isOfferCancelled = state.incomingOffer?.id == event.rideId
                            val isStackedCancelled = state.stackedRideOffer?.id == event.rideId
                            state.copy(
                                activeRide = if (isActiveCancelled) null else state.activeRide,
                                queuedStackedRide = if (isQueuedStackedCancelled) null else state.queuedStackedRide,
                                incomingOffer = if (isOfferCancelled) null else state.incomingOffer,
                                stackedRideOffer = if (isStackedCancelled) null else state.stackedRideOffer,
                                rideState = if (isActiveCancelled || isOfferCancelled) TerminalRideState.IDLE else state.rideState,
                                toastMessage = when {
                                    isActiveCancelled -> "Ride was cancelled by the passenger."
                                    isQueuedStackedCancelled -> "Your queued next ride was cancelled by the passenger."
                                    else -> state.toastMessage
                                }
                            )
                        }
                    }
                    is UrgentPushEvent.RideAcceptedElsewhere -> {
                        AnyRollerMessagingService.cancelOfferNotification(context, event.rideId)
                        _uiState.update { state ->
                            val clearedOffer = if (state.incomingOffer?.id == event.rideId) null else state.incomingOffer
                            val clearedStacked = if (state.stackedRideOffer?.id == event.rideId) null else state.stackedRideOffer
                            state.copy(
                                incomingOffer = clearedOffer,
                                stackedRideOffer = clearedStacked,
                                rideState = if (clearedOffer == null && state.rideState == TerminalRideState.OFFER) {
                                    TerminalRideState.IDLE
                                } else state.rideState
                            )
                        }
                    }
                    is UrgentPushEvent.DailyLimitReached -> {
                        _uiState.update {
                            it.copy(
                                isOnline = false,
                                toastMessage = "Daily driving limit (${event.maxHours.toInt()}h) reached. You have been switched Offline for safety."
                            )
                        }
                    }
                    is UrgentPushEvent.DeepLinkReturned -> {
                        refreshFinancials()
                        _uiState.update { it.copy(toastMessage = "Synced latest Stripe status.") }
                    }
                    is UrgentPushEvent.ChatMessageReceived -> {
                        _uiState.update { it.copy(toastMessage = "${event.title}: ${event.body}") }
                    }
                    UrgentPushEvent.AuthRevoked -> Unit
                }
            }
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        if ((lat1 == 0.0 && lng1 == 0.0) || (lat2 == 0.0 && lng2 == 0.0)) return Float.MAX_VALUE
        val out = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0]
    }

    private fun evaluateLocationGeofences(loc: DriverLocationSnapshot) {
        if (!loc.hasRealFix) return
        val state = _uiState.value
        val ride = state.activeRide ?: return

        // 1. 200m Pickup Proximity -> Auto zoom to street level & track 30s auto-arrival
        if (state.rideState == TerminalRideState.EN_ROUTE_PICKUP && (ride.pickupLat != 0.0 || ride.pickupLng != 0.0)) {
            val distToPickup = distanceMeters(loc.lat, loc.lng, ride.pickupLat, ride.pickupLng)
            if (distToPickup <= 200f) {
                if (pickupProximityStartMs == null) {
                    pickupProximityStartMs = System.currentTimeMillis()
                }
                if (hasAutoZoomedPickupRideId != ride.id) {
                    hasAutoZoomedPickupRideId = ride.id
                    _uiState.update {
                        it.copy(
                            pickupZoomEventId = System.currentTimeMillis(),
                            toastMessage = "Approaching pickup — zooming to street level."
                        )
                    }
                }
            } else {
                pickupProximityStartMs = null
            }
        } else {
            pickupProximityStartMs = null
        }

        // 2. >300m "Forgot to Start Trip" reminder while WAITING at pickup
        if (state.rideState == TerminalRideState.WAITING && dismissedStartTripReminderRideId != ride.id) {
            val distFromPickup = distanceMeters(loc.lat, loc.lng, ride.pickupLat, ride.pickupLng)
            val shouldRemind = distFromPickup > 300f && distFromPickup < Float.MAX_VALUE
            if (shouldRemind != state.showStartTripReminder) {
                _uiState.update { it.copy(showStartTripReminder = shouldRemind) }
            }
        } else if (state.showStartTripReminder) {
            _uiState.update { it.copy(showStartTripReminder = false) }
        }

        // 3. Mid-trip "Wait at Stop" auto-pause safeguard if driver drives >200m away
        if (state.isWaitingAtStop && stopWaitOriginLat != null && stopWaitOriginLng != null) {
            val moved = distanceMeters(loc.lat, loc.lng, stopWaitOriginLat!!, stopWaitOriginLng!!)
            if (moved > 200f && moved < Float.MAX_VALUE) {
                val added = state.currentStopWaitSeconds
                stopWaitStartMs = null
                stopWaitOriginLat = null
                stopWaitOriginLng = null
                _uiState.update {
                    it.copy(
                        isWaitingAtStop = false,
                        accumulatedPaidWaitSeconds = it.accumulatedPaidWaitSeconds + added,
                        currentStopWaitSeconds = 0,
                        abandonmentWarningSent = false,
                        toastMessage = "Trip Auto-Resumed — Stop wait timer paused to protect passenger fare."
                    )
                }
            }
        }

        // 4. Multi-stop "Left Stop without pressing Next Stop" reminder (>300m after reaching within 200m)
        if (state.rideState == TerminalRideState.IN_PROGRESS && ride.currentStopIndex < ride.stops.size) {
            val stop = ride.stops[ride.currentStopIndex]
            val stopKey = "${ride.id}:${ride.currentStopIndex}"
            if (stop.lat != 0.0 || stop.lng != 0.0) {
                val distToStop = distanceMeters(loc.lat, loc.lng, stop.lat, stop.lng)
                if (distToStop <= 200f) {
                    hasReachedCurrentStopKey = stopKey
                    if (state.showLeaveStopReminder) {
                        _uiState.update { it.copy(showLeaveStopReminder = false) }
                    }
                } else if (distToStop > 300f && distToStop < Float.MAX_VALUE && hasReachedCurrentStopKey == stopKey) {
                    if (!state.showLeaveStopReminder) {
                        _uiState.update { it.copy(showLeaveStopReminder = true) }
                    }
                }
            }
        } else if (state.showLeaveStopReminder) {
            _uiState.update { it.copy(showLeaveStopReminder = false) }
        }

        // 5. Throttled authoritative live ETA + stacked delay sync (every ~25s during active travel)
        val nowMs = System.currentTimeMillis()
        if ((state.rideState == TerminalRideState.EN_ROUTE_PICKUP || state.rideState == TerminalRideState.IN_PROGRESS) &&
            (nowMs - lastEtaSyncMs >= 25_000L)
        ) {
            val targetLat = if (state.rideState == TerminalRideState.EN_ROUTE_PICKUP) ride.pickupLat else ride.dropoffLat
            val targetLng = if (state.rideState == TerminalRideState.EN_ROUTE_PICKUP) ride.pickupLng else ride.dropoffLng
            if (targetLat != 0.0 || targetLng != 0.0) {
                val distM = distanceMeters(loc.lat, loc.lng, targetLat, targetLng)
                if (distM < Float.MAX_VALUE) {
                    lastEtaSyncMs = nowMs
                    // Estimate road ETA using current speed (clamped to realistic urban/arterial 6-22 m/s, ~13-50 mph)
                    val effectiveSpeedMps = loc.speedMps.coerceIn(7.5f, 22.0f)
                    val liveEtaSec = ((distM * 1.25f) / effectiveSpeedMps).toInt().coerceIn(30, 14_400)
                    val stackedDelayMins = if (state.rideState == TerminalRideState.IN_PROGRESS && state.queuedStackedRide != null) {
                        ((liveEtaSec + 59) / 60).coerceIn(1, 60)
                    } else null
                    viewModelScope.launch(Dispatchers.IO) {
                        ApiClient.safeCall {
                            ApiClient.service.transitionRide(
                                RideTransitionRequest(
                                    rideId = ride.id,
                                    action = "update_eta",
                                    liveEtaSeconds = liveEtaSec
                                )
                            )
                        }
                        if (stackedDelayMins != null && state.queuedStackedRide != null) {
                            ApiClient.safeCall {
                                ApiClient.service.transitionRide(
                                    RideTransitionRequest(
                                        rideId = state.queuedStackedRide.id,
                                        action = "update_eta",
                                        stackedDriverDelay = stackedDelayMins
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Live Route Projection (zero-cost polyline trimming, live remaining distance/ETA, route-snapped bearing)
        // & True Segment Off-Route Check (GPS accuracy filter + 2 consecutive fixes + 12s cooldown + 3/3 hard cap)
        val isDrivingLeg = state.rideState == TerminalRideState.EN_ROUTE_PICKUP ||
            state.rideState == TerminalRideState.IN_PROGRESS
        if (isDrivingLeg && state.activeRoute.points.size >= 2) {
            val proj = projectOntoPolyline(loc.lat, loc.lng, state.activeRoute.points)
            val trimmed = buildList(state.activeRoute.points.size - proj.segmentIndex) {
                add(proj.projectedPoint)
                for (i in (proj.segmentIndex + 1) until state.activeRoute.points.size) {
                    add(state.activeRoute.points[i])
                }
            }
            val totalRouteMeters = state.activeRoute.totalDistanceMeters.coerceAtLeast(1)
            val ratio = (proj.remainingDistanceMeters.toDouble() / totalRouteMeters.toDouble()).coerceIn(0.0, 1.2)
            val baselineDurSec = state.activeRoute.totalDurationSeconds.takeIf { it > 0 }
                ?: ((state.activeRoute.etaMinutes ?: 1) * 60)
            val scaledSec = (baselineDurSec * ratio).toInt()
            val liveEtaMins = if (loc.speedMps >= 3.0f && proj.remainingDistanceMeters > 200) {
                val speedEtaSec = ((proj.remainingDistanceMeters * 1.15f) / loc.speedMps.coerceIn(5.0f, 30.0f)).toInt()
                (((scaledSec * 0.65) + (speedEtaSec * 0.35)) / 60.0).toInt().coerceAtLeast(1)
            } else {
                (scaledSec / 60).coerceAtLeast(1)
            }
            val snappedBearing = if (proj.distanceMeters <= 28f) proj.segmentBearing else null
            _uiState.update {
                it.copy(
                    remainingRoutePoints = trimmed,
                    liveRemainingDistanceMeters = proj.remainingDistanceMeters,
                    liveRemainingEtaMinutes = liveEtaMins,
                    snappedRouteBearing = snappedBearing
                )
            }

            // Off-route check: ignore noisy GPS fixes (>35m accuracy), require 2 consecutive off-route fixes & 12s cooldown
            val hasReliableAccuracy = loc.accuracyMeters <= 0f || loc.accuracyMeters <= 35f
            if (hasReliableAccuracy) {
                if (proj.distanceMeters > 70f) {
                    consecutiveOffRouteFixes++
                    val nowRecalcMs = System.currentTimeMillis()
                    if (consecutiveOffRouteFixes >= 2 && (nowRecalcMs - lastRecalcTimestampMs) >= 12_000L) {
                        consecutiveOffRouteFixes = 0
                        lastRecalcTimestampMs = nowRecalcMs
                        refreshActiveRouteIfNeeded(ride, state.rideState)
                    }
                } else if (proj.distanceMeters <= 50f) {
                    consecutiveOffRouteFixes = 0
                }
            }
        }

        // 7. In-App Turn-by-Turn Voice Guidance & Live Step Progression (UK yards/miles)
        evaluateTurnByTurnNavigation(loc, _uiState.value, ride)
    }

    private data class PolylineProjection(
        val distanceMeters: Float,
        val segmentIndex: Int,
        val projectedPoint: com.google.android.gms.maps.model.LatLng,
        val segmentBearing: Float,
        val remainingDistanceMeters: Int
    )

    private fun projectOntoPolyline(
        lat: Double,
        lng: Double,
        points: List<com.google.android.gms.maps.model.LatLng>
    ): PolylineProjection {
        val cosLat = kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        val metersPerDegLat = 110_540.0
        val metersPerDegLng = 111_320.0 * cosLat

        var bestDist = Float.MAX_VALUE
        var bestSegIdx = 0
        var bestProjPt = points.first()
        var bestBearing = 0f

        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.longitude - lng) * metersPerDegLng
            val ay = (a.latitude - lat) * metersPerDegLat
            val bx = (b.longitude - lng) * metersPerDegLng
            val by = (b.latitude - lat) * metersPerDegLat
            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq > 1e-6) {
                ((-(ax * dx + ay * dy)) / lenSq).coerceIn(0.0, 1.0)
            } else 0.0
            val px = ax + t * dx
            val py = ay + t * dy
            val dist = kotlin.math.hypot(px, py).toFloat()
            if (dist < bestDist) {
                bestDist = dist
                bestSegIdx = i
                bestProjPt = com.google.android.gms.maps.model.LatLng(
                    a.latitude + t * (b.latitude - a.latitude),
                    a.longitude + t * (b.longitude - a.longitude)
                )
                bestBearing = if (lenSq > 1e-6) {
                    ((Math.toDegrees(kotlin.math.atan2(dx, dy)) + 360.0) % 360.0).toFloat()
                } else bestBearing
            }
        }

        // Compute exact remaining distance along polyline from projectedPoint -> end
        var remMeters = distanceMeters(
            bestProjPt.latitude, bestProjPt.longitude,
            points[bestSegIdx + 1].latitude, points[bestSegIdx + 1].longitude
        ).toDouble()
        for (i in (bestSegIdx + 1) until points.lastIndex) {
            remMeters += distanceMeters(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            ).toDouble()
        }

        return PolylineProjection(
            distanceMeters = bestDist,
            segmentIndex = bestSegIdx,
            projectedPoint = bestProjPt,
            segmentBearing = bestBearing,
            remainingDistanceMeters = remMeters.toInt().coerceAtLeast(0)
        )
    }

    private fun buildChainedStepInstruction(
        steps: List<com.example.data.repository.RouteStep>,
        index: Int
    ): String {
        val current = steps.getOrNull(index) ?: return ""
        val next = steps.getOrNull(index + 1)
        return if (next != null && next.distanceMeters in 1..50 && next.instruction.isNotBlank()) {
            "${current.instruction}, then ${next.instruction}"
        } else {
            current.instruction
        }
    }

    private fun evaluateTurnByTurnNavigation(
        loc: DriverLocationSnapshot,
        state: DriverTerminalUiState,
        ride: RideRequest
    ) {
        val isDrivingLeg = state.rideState == TerminalRideState.EN_ROUTE_PICKUP ||
            state.rideState == TerminalRideState.IN_PROGRESS
        if (!isDrivingLeg) return

        // Target leg destination for arrival voice announcement & zoom
        val hasStop = state.rideState == TerminalRideState.IN_PROGRESS && ride.currentStopIndex < ride.stops.size
        val activeStop = if (hasStop) ride.stops[ride.currentStopIndex] else null
        val (targetLat, targetLng) = when {
            state.rideState == TerminalRideState.EN_ROUTE_PICKUP -> ride.pickupLat to ride.pickupLng
            activeStop != null && (activeStop.lat != 0.0 || activeStop.lng != 0.0) -> activeStop.lat to activeStop.lng
            else -> ride.dropoffLat to ride.dropoffLng
        }

        val voiceEnabled = !state.profile.muteHeadsUpVolume && state.isAutoNavHeadUp

        // Leg arrival announcement (<=100m to pickup / stop / destination)
        if (targetLat != 0.0 || targetLng != 0.0) {
            val distToTarget = distanceMeters(loc.lat, loc.lng, targetLat, targetLng)
            val arrivalKey = "${ride.id}:${state.rideState.name}:${ride.currentStopIndex}"
            if (distToTarget <= 100f && hasAnnouncedLegArrivalKey != arrivalKey) {
                hasAnnouncedLegArrivalKey = arrivalKey
                if (voiceEnabled) {
                    val arrivalPhrase = when {
                        state.rideState == TerminalRideState.EN_ROUTE_PICKUP ->
                            "You have arrived at the pickup location."
                        hasStop ->
                            "You have arrived at stop ${ride.currentStopIndex + 1}."
                        else ->
                            "You have arrived at your destination."
                    }
                    audioFeedback.speakHeadsUp(arrivalPhrase, muteHeadsUp = false)
                }
                _uiState.update { it.copy(pickupZoomEventId = System.currentTimeMillis()) }
            }
        }

        val steps = state.activeRoute.steps
        if (steps.isEmpty()) return

        var stepIdx = state.currentNavStepIndex.coerceIn(0, steps.lastIndex)
        var distToEnd = distanceMeters(loc.lat, loc.lng, steps[stepIdx].endLat, steps[stepIdx].endLng)

        // Advance past completed maneuvers (within 35m of step end or already closer to a subsequent step end)
        while (stepIdx < steps.lastIndex && distToEnd <= 35f) {
            stepIdx++
            distToEnd = distanceMeters(loc.lat, loc.lng, steps[stepIdx].endLat, steps[stepIdx].endLng)
        }
        if (stepIdx + 1 < steps.size) {
            val lookaheadEnd = minOf(steps.lastIndex, stepIdx + 2)
            for (cand in (stepIdx + 1)..lookaheadEnd) {
                val candStartDist = distanceMeters(loc.lat, loc.lng, steps[cand].startLat, steps[cand].startLng)
                if (candStartDist <= 30f) {
                    stepIdx = cand
                    distToEnd = distanceMeters(loc.lat, loc.lng, steps[stepIdx].endLat, steps[stepIdx].endLng)
                    break
                }
            }
        }

        val roundedDist = distToEnd.takeIf { it < Float.MAX_VALUE }?.toInt()?.coerceAtLeast(0)
        if (stepIdx != state.currentNavStepIndex || roundedDist != state.currentStepDistanceMeters) {
            _uiState.update {
                it.copy(
                    currentNavStepIndex = stepIdx,
                    currentStepDistanceMeters = roundedDist
                )
            }
        }

        if (!voiceEnabled) return

        // Speak current step once upon entering it
        if (stepIdx != lastSpokenStepIndex) {
            lastSpokenStepIndex = stepIdx
            val spoken = buildChainedStepInstruction(steps, stepIdx)
            if (spoken.isNotBlank()) {
                audioFeedback.speakHeadsUp(spoken, muteHeadsUp = false)
            }
        }

        // Prep announcements for the upcoming maneuver in natural UK road units (half mile, 400 yards, 100 yards)
        val nextIdx = stepIdx + 1
        if (nextIdx < steps.size && distToEnd < Float.MAX_VALUE) {
            val nextPhrase = buildChainedStepInstruction(steps, nextIdx)
            val currentStepLen = steps[stepIdx].distanceMeters
            if (nextPhrase.isNotBlank()) {
                when {
                    distToEnd <= 105f && spokenStepThresholds.add("$nextIdx:100yd") -> {
                        audioFeedback.speakHeadsUp("In 100 yards, $nextPhrase", muteHeadsUp = false)
                    }
                    distToEnd in 160f..400f && currentStepLen >= 420 && spokenStepThresholds.add("$nextIdx:400yd") -> {
                        audioFeedback.speakHeadsUp("In 400 yards, $nextPhrase", muteHeadsUp = false)
                    }
                    distToEnd in 500f..860f && currentStepLen >= 950 && spokenStepThresholds.add("$nextIdx:halfmi") -> {
                        audioFeedback.speakHeadsUp("In half a mile, $nextPhrase", muteHeadsUp = false)
                    }
                }
            }
        }
    }

    private fun startOneSecondTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                var triggerMissedOfferTimeout = false
                var triggerAutoArrive = false
                var triggerAutoResumeBreak = false
                var triggerFatigueVoiceNudge = false
                var expiredOfferId: String? = null

                _uiState.update { state ->
                    var next = state
                    if (state.isOnline && !state.isOnBreak) {
                        val nextOnlineSecs = state.shiftOnlineSeconds + 1L
                        next = next.copy(shiftOnlineSeconds = nextOnlineSecs)
                        val fatigueHours = state.cockpitSettings.fatigueReminderHours
                        if (fatigueHours > 0 &&
                            !fatigueReminderTriggeredForShift &&
                            nextOnlineSecs >= fatigueHours * 3600L
                        ) {
                            fatigueReminderTriggeredForShift = true
                            next = next.copy(fatigueReminderActive = true)
                            if (state.activeRide == null) {
                                triggerFatigueVoiceNudge = true
                            }
                        }
                    }
                    if (state.isOnBreak && !state.breakIsOpenEnded) {
                        val endsAt = breakEndsAtMs
                        if (endsAt != null) {
                            val rem = ((endsAt - now) / 1000L).toInt()
                            if (rem <= 0) {
                                triggerAutoResumeBreak = true
                                next = next.copy(breakSecondsLeft = 0)
                            } else {
                                next = next.copy(breakSecondsLeft = rem)
                            }
                        }
                    }
                    if (state.incomingOffer != null && state.rideState == TerminalRideState.OFFER) {
                        val secs = ((state.incomingOffer.expiresAtMs - now) / 1000L).toInt()
                        if (secs <= 0) {
                            expiredOfferId = state.incomingOffer.id
                            AnyRollerMessagingService.cancelOfferNotification(context, state.incomingOffer.id)
                            triggerMissedOfferTimeout = true
                            next = next.copy(incomingOffer = null, rideState = TerminalRideState.IDLE, offerSecondsLeft = 0)
                        } else {
                            next = next.copy(offerSecondsLeft = secs)
                        }
                    }
                    if (state.stackedRideOffer != null) {
                        val secs = ((state.stackedRideOffer.expiresAtMs - now) / 1000L).toInt()
                        next = if (secs <= 0) {
                            AnyRollerMessagingService.cancelOfferNotification(context, state.stackedRideOffer.id)
                            next.copy(stackedRideOffer = null, stackedOfferSecondsLeft = 0)
                        } else {
                            next.copy(stackedOfferSecondsLeft = secs)
                        }
                    }
                    if (state.rideState == TerminalRideState.WAITING) {
                        val start = waitingStartedAtMs ?: now.also { waitingStartedAtMs = it }
                        val elapsed = ((now - start) / 1000L).toInt().coerceAtLeast(0)
                        next = next.copy(waitSecondsElapsed = elapsed)
                    } else {
                        waitingStartedAtMs = null
                    }
                    if (state.isWaitingAtStop && stopWaitStartMs != null) {
                        val stopElapsed = ((now - stopWaitStartMs!!) / 1000L).toInt().coerceAtLeast(0)
                        next = next.copy(currentStopWaitSeconds = stopElapsed)
                    }
                    if (state.isAwaitingCashConfirm && state.cashConfirmSecondsLeft > 0) {
                        next = next.copy(cashConfirmSecondsLeft = (state.cashConfirmSecondsLeft - 1).coerceAtLeast(0))
                    }
                    if (state.quickMessageCooldownSeconds > 0) {
                        next = next.copy(quickMessageCooldownSeconds = (state.quickMessageCooldownSeconds - 1).coerceAtLeast(0))
                    }
                    // Auto-resume 3D Head-Up follow 15 seconds after manual map drag so driver stays hands-free
                    val pausedAt = state.autoNavPausedAtMs
                    if (state.isAutoNavPaused && pausedAt != null && (now - pausedAt) >= 15_000L) {
                        next = next.copy(isAutoNavPaused = false, autoNavPausedAtMs = null)
                    }
                    // Check 30s stationary auto-arrival within 200m of pickup
                    val proxStart = pickupProximityStartMs
                    if (state.rideState == TerminalRideState.EN_ROUTE_PICKUP && proxStart != null && (now - proxStart) >= 30_000L && !state.isBusy) {
                        pickupProximityStartMs = null
                        triggerAutoArrive = true
                    }
                    next
                }

                if (triggerMissedOfferTimeout && expiredOfferId != null) {
                    handleOfferTimedOut(expiredOfferId!!)
                }
                if (triggerAutoArrive) {
                    markArrivedAtPickup(isAuto = true)
                }
                if (triggerAutoResumeBreak) {
                    endBreak(isAuto = true)
                }
                if (triggerFatigueVoiceNudge) {
                    val hrs = _uiState.value.cockpitSettings.fatigueReminderHours
                    audioFeedback.speakHeadsUp(
                        "You have been driving for $hrs hours. Consider taking a short coffee break.",
                        _uiState.value.profile.muteHeadsUpVolume
                    )
                }
            }
        }
    }

    private fun handleOfferTimedOut(offerId: String) {
        val state = _uiState.value
        viewModelScope.launch {
            prefsStore.addDeclinedOfferId(offerId)
            if (state.uid.isNotBlank()) {
                repository.recordDriverDeclineStatus(state.uid)
            }
        }
        consecutiveMissedOffers += 1
        if (consecutiveMissedOffers >= 3) {
            consecutiveMissedOffers = 0
            forceOfflineForMissedOffers()
        } else {
            val remaining = 3 - consecutiveMissedOffers
            _uiState.update {
                it.copy(
                    toastMessage = "Ride offer missed. Miss $remaining more in a row and you'll be switched Offline."
                )
            }
        }
    }

    private fun forceOfflineForMissedOffers() {
        val uid = _uiState.value.uid
        DriverLocationService.stop(context)
        _uiState.update {
            it.copy(
                isOnline = false,
                incomingOffer = null,
                rideState = TerminalRideState.IDLE,
                toastMessage = "Auto-Offline Activated — 3 consecutive ride offers missed."
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            prefsStore.setOnlineFlag(false)
            if (uid.isNotBlank()) {
                repository.markDriverOfflineInFirestore(uid)
            }
        }
    }

    private fun activeStatusPriority(status: String): Int = when (status.lowercase()) {
        "awaiting_payment", "payment_pending", "awaiting_cash_confirm", "cash_confirmed", "rider_abandoned" -> 0
        "in_progress" -> 1
        "arrived", "waiting" -> 2
        "accepted", "en_route", "en_route_pickup" -> 3
        else -> 99
    }

    private fun reconcileRidesAndOffers(
        uid: String,
        driverRides: List<RideRequest>,
        pendingOffers: List<RideRequest>
    ) {
        val activeStatuses = setOf(
            "accepted", "en_route", "en_route_pickup", "arrived", "waiting",
            "in_progress", "awaiting_payment", "payment_pending",
            "awaiting_cash_confirm", "cash_confirmed"
        )
        val completedStatuses = setOf("completed", "paid", "rider_abandoned")

        // CRITICAL: Priority-sort active rides so an `in_progress` / `awaiting_payment` trip
        // is NEVER hijacked by a newly accepted stacked ride (`accepted`).
        val sortedActiveRides = driverRides
            .filter { it.status.lowercase() in activeStatuses }
            .sortedWith(compareBy<RideRequest> { activeStatusPriority(it.status) }.thenBy { it.createdAtMs })

        val currentActive = sortedActiveRides.firstOrNull()
        val queuedStacked = sortedActiveRides.drop(1).firstOrNull { it.status.lowercase() == "accepted" }
        val completed = driverRides.filter { it.status.lowercase() in completedStatuses }
        val scheduled = driverRides.filter { !it.scheduledTime.isNullOrBlank() && it.status.lowercase() !in completedStatuses }

        val currentState = _uiState.value

        // Detect passenger cancellation OR Stripe QR webhook completion OR cash_confirmed on previously active ride!
        val prevRideId = lastActiveRideId
        if (prevRideId != null) {
            val updatedPrev = driverRides.firstOrNull { it.id == prevRideId }
            if (updatedPrev != null) {
                val prevStatus = updatedPrev.status.lowercase()
                if (prevStatus == "cancelled" && currentState.rideState != TerminalRideState.IDLE) {
                    audioFeedback.playCancellationAlert(currentState.profile.muteRideOfferAlerts)
                    ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 300, 150, 300))
                    val feeMsg = if (updatedPrev.cancellationFee > 0.0) {
                        "Ride cancelled by passenger. £%.2f cancellation fee credited.".format(updatedPrev.cancellationFee)
                    } else {
                        "The passenger has cancelled the ride."
                    }
                    lastActiveRideId = null
                    lastTrackedActiveRide = null
                    resetPerRideTransientState()
                    viewModelScope.launch(Dispatchers.IO) { repository.markDriverIdleStatus(uid) }
                    _uiState.update {
                        it.copy(
                            activeRide = null,
                            rideState = TerminalRideState.IDLE,
                            isAwaitingCashConfirm = false,
                            cashConfirmSecondsLeft = 0,
                            showCompleteConfirmDialog = false,
                            showEarlyArrivalDialog = false,
                            lastRideUpdateNotice = null,
                            toastMessage = feeMsg
                        )
                    }
                } else if (prevStatus == "cash_confirmed" && currentState.isAwaitingCashConfirm) {
                    _uiState.update {
                        it.copy(
                            isAwaitingCashConfirm = false,
                            cashConfirmSecondsLeft = 0,
                            toastMessage = "Passenger confirmed cash balance — finalizing trip!"
                        )
                    }
                    submitCashPayment(cashCollected = updatedPrev.cashCollected, forceComplete = true)
                } else if (
                    (prevStatus == "completed" || prevStatus == "paid") &&
                    currentState.rideState == TerminalRideState.COMPLETED_PAYMENT
                ) {
                    // Stripe QR webhook finalized payment on passenger's device -> seamlessly jump to Review!
                    lastActiveRideId = null
                    lastTrackedActiveRide = null
                    resetPerRideTransientState()
                    if (currentState.profile.lastJob) {
                        DriverLocationService.stop(context)
                        viewModelScope.launch(Dispatchers.IO) { repository.markDriverOfflineInFirestore(uid) }
                    }
                    _uiState.update {
                        it.copy(
                            activeRide = null,
                            reviewRide = updatedPrev,
                            paymentQrUrl = null,
                            isAwaitingCashConfirm = false,
                            rideState = TerminalRideState.REVIEW,
                            isOnline = if (currentState.profile.lastJob) false else it.isOnline,
                            toastMessage = "Payment confirmed! Trip finalized."
                        )
                    }
                    refreshFinancials()
                    return
                }
            }
        }

        // Detect passenger cancellation of an ALREADY ACCEPTED stacked (queued) ride without interrupting Ride A
        var stackedCancelNotice: String? = null
        val prevQueuedId = lastQueuedStackedRideId
        if (prevQueuedId != null && prevQueuedId != currentActive?.id && queuedStacked?.id != prevQueuedId) {
            val updatedQueued = driverRides.firstOrNull { it.id == prevQueuedId }
            if (updatedQueued != null &&
                updatedQueued.status.lowercase() == "cancelled" &&
                lastAlertedCancelledQueuedId != prevQueuedId
            ) {
                stackedCancelNotice = notifyQueuedStackedRideCancelled(
                    rideId = updatedQueued.id,
                    cancellationFee = updatedQueued.cancellationFee
                )
            }
        }
        lastQueuedStackedRideId = queuedStacked?.id

        // Detect mid-trip passenger modifications (extra stops, new destination, fare/tip change)
        var midTripNotice: String? = stackedCancelNotice ?: currentState.lastRideUpdateNotice
        var midTripRouteChanged = false
        if (currentActive != null && lastTrackedActiveRide != null && lastTrackedActiveRide!!.id == currentActive.id) {
            val prev = lastTrackedActiveRide!!
            val stopsChanged = prev.stops != currentActive.stops
            val dropoffChanged = prev.dropoffAddress != currentActive.dropoffAddress ||
                prev.dropoffLat != currentActive.dropoffLat ||
                prev.dropoffLng != currentActive.dropoffLng
            val pickupChanged = prev.pickupAddress != currentActive.pickupAddress ||
                prev.pickupLat != currentActive.pickupLat ||
                prev.pickupLng != currentActive.pickupLng
            val fareChanged = kotlin.math.abs(prev.fareEstimate - currentActive.fareEstimate) >= 0.05
            val tipChanged = currentActive.tipAmount > prev.tipAmount + 0.01
            midTripRouteChanged = stopsChanged || dropoffChanged || pickupChanged

            if (stopsChanged || dropoffChanged || pickupChanged || fareChanged || currentActive.isModifiedByPassenger) {
                audioFeedback.playRideUpdatedAlert(currentState.profile.muteRideOfferAlerts)
                ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 180, 100, 180))
                audioFeedback.speakHeadsUp("Job details updated by passenger", currentState.profile.muteHeadsUpVolume)

                val summaryParts = mutableListOf<String>()
                if (stopsChanged) summaryParts += "${currentActive.stops.size} stop(s)"
                if (dropoffChanged) summaryParts += "new dropoff"
                if (fareChanged) summaryParts += "fare £%.2f".format(currentActive.fareEstimate)
                val navSuffix = if (midTripRouteChanged && currentState.cockpitSettings.autoLaunchExternalNav) {
                    "Updating navigation."
                } else {
                    "Restart external nav if open."
                }
                val noticeText = if (summaryParts.isNotEmpty()) {
                    "Passenger updated trip: ${summaryParts.joinToString(" • ")}. $navSuffix"
                } else {
                    "Passenger updated journey details. Check updated route & fare."
                }
                midTripNotice = noticeText

                // Sync updated destination dropoffLat/Lng to live_tracking so passenger & dispatch have fresh dropoff coords
                if (dropoffChanged) {
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.syncActiveRideDropoff(uid, currentActive.dropoffLat, currentActive.dropoffLng)
                    }
                }

                // If dropoff shifted >1 mile and driver has a queued stacked ride, warn driver
                if (dropoffChanged && queuedStacked != null) {
                    val shiftMeters = distanceMeters(prev.dropoffLat, prev.dropoffLng, currentActive.dropoffLat, currentActive.dropoffLng)
                    if (shiftMeters > 1609f && shiftMeters < Float.MAX_VALUE) {
                        midTripNotice = "$noticeText Next queued ride may be delayed."
                    }
                }
            } else if (tipChanged) {
                midTripNotice = "Passenger added a £%.2f tip!".format(currentActive.tipAmount)
            }
        } else if (currentActive?.id != lastTrackedActiveRide?.id) {
            midTripNotice = null
        }

        lastTrackedActiveRide = currentActive
        lastActiveRideId = currentActive?.id

        // Bind chat stream to active ride if changed (with unread badge + chime + vibration when closed)
        if (currentActive?.id != activeChatRideId) {
            chatJob?.cancel()
            activeChatRideId = currentActive?.id
            lastAlertedChatMsgId = null
            if (currentActive != null) {
                var initialBatchLoaded = false
                chatJob = viewModelScope.launch {
                    repository.observeRideChat(currentActive.id).collect { msgs ->
                        val latestRemote = msgs.lastOrNull { it.senderId != uid }
                        val stateNow = _uiState.value
                        if (!initialBatchLoaded) {
                            initialBatchLoaded = true
                            lastAlertedChatMsgId = latestRemote?.id
                            _uiState.update { it.copy(chatMessages = msgs) }
                        } else if (latestRemote != null && latestRemote.id != lastAlertedChatMsgId) {
                            lastAlertedChatMsgId = latestRemote.id
                            val senderLabel = stateNow.activeRide?.passengerName
                                ?.takeIf { it.isNotBlank() }
                                ?: currentActive.passengerName
                            // Read incoming passenger message aloud hands-free (respects Mute Voice setting)
                            audioFeedback.speakChatMessage(
                                senderName = senderLabel,
                                text = latestRemote.text,
                                hasImage = !latestRemote.imageUrl.isNullOrBlank(),
                                muteHeadsUp = stateNow.profile.muteHeadsUpVolume
                            )
                            if (!stateNow.showChatSheet) {
                                audioFeedback.playChatMessageChime(stateNow.profile.muteRideOfferAlerts)
                                ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 90, 60, 90))
                                _uiState.update {
                                    it.copy(
                                        chatMessages = msgs,
                                        unreadChatCount = it.unreadChatCount + 1,
                                        incomingChatPopup = latestRemote
                                    )
                                }
                                scheduleIncomingChatPopupDismiss(latestRemote.id)
                            } else {
                                _uiState.update {
                                    it.copy(
                                        chatMessages = msgs,
                                        unreadChatCount = 0,
                                        incomingChatPopup = null
                                    )
                                }
                            }
                        } else {
                            _uiState.update { it.copy(chatMessages = msgs) }
                        }
                    }
                }
            } else {
                incomingChatPopupJob?.cancel()
                _uiState.update {
                    it.copy(
                        chatMessages = emptyList(),
                        unreadChatCount = 0,
                        showChatSheet = false,
                        incomingChatPopup = null
                    )
                }
            }
        }

        // Bind passenger live GPS tracking (`live_tracking/{passengerId}`) during pickup & waiting
        val needsPassengerGps = currentActive != null &&
            currentActive.passengerId.isNotBlank() &&
            currentActive.status.lowercase() in setOf("accepted", "en_route", "en_route_pickup", "arrived", "waiting")
        val targetPassengerId = if (needsPassengerGps) currentActive!!.passengerId else null
        if (targetPassengerId != activePassengerLocId) {
            passengerLocationJob?.cancel()
            activePassengerLocId = targetPassengerId
            if (targetPassengerId != null) {
                passengerLocationJob = viewModelScope.launch {
                    repository.observePassengerLiveLocation(targetPassengerId).collect { coords ->
                        _uiState.update { it.copy(passengerLiveLocation = coords) }
                    }
                }
            } else {
                _uiState.update { it.copy(passengerLiveLocation = null) }
            }
        }

        val latestState = _uiState.value
        val validPending = if (latestState.isOnBreak) {
            null
        } else {
            pendingOffers
                .filter { offer ->
                    offer.id !in declinedOfferIds &&
                        when (offer.status.lowercase()) {
                            "offered" -> offer.assignedDriverId == uid
                            else -> offer.assignedDriverId.isNullOrBlank() || offer.assignedDriverId == uid
                        }
                }
                // Prefer directed server-dispatched `offered` rides first
                .sortedBy { if (it.status.equals("offered", ignoreCase = true)) 0 else 1 }
                .firstOrNull()
        }

        val derivedRideState = when {
            latestState.rideState == TerminalRideState.REVIEW && latestState.reviewRide != null ->
                TerminalRideState.REVIEW
            currentActive != null -> when (currentActive.status.lowercase()) {
                "arrived", "waiting" -> TerminalRideState.WAITING
                "in_progress" -> TerminalRideState.IN_PROGRESS
                "awaiting_payment", "payment_pending", "awaiting_cash_confirm", "cash_confirmed" -> TerminalRideState.COMPLETED_PAYMENT
                else -> TerminalRideState.EN_ROUTE_PICKUP
            }
            latestState.isOnline && !latestState.isOnBreak && validPending != null -> TerminalRideState.OFFER
            else -> TerminalRideState.IDLE
        }

        if (latestState.isOnline) {
            DriverLocationService.updateOnRide(
                context = context,
                driverName = latestState.profile.fullName,
                isOnRide = currentActive != null
            )
        }

        val stackedCandidate = if (
            currentActive != null &&
            queuedStacked == null &&
            derivedRideState == TerminalRideState.IN_PROGRESS &&
            latestState.isOnline &&
            !latestState.isOnBreak &&
            latestState.profile.stackingEnabled &&
            !latestState.profile.lastJob &&
            validPending != null &&
            validPending.id != currentActive.id
        ) validPending else null

        val timeoutMs = (latestState.platformConfig.dispatchTimeoutSeconds.coerceIn(5, 60)) * 1000L
        val nowMs = System.currentTimeMillis()
        fun resolveOfferExpiry(offer: RideRequest): Long {
            return if (offer.expiresAtMs > nowMs + 1_000L) offer.expiresAtMs else nowMs + timeoutMs
        }

        val nextIncoming = if (derivedRideState == TerminalRideState.OFFER) {
            validPending?.let {
                if (latestState.incomingOffer?.id == it.id) latestState.incomingOffer else it.copy(
                    expiresAtMs = resolveOfferExpiry(it)
                )
            }
        } else null
        val nextStacked = stackedCandidate?.let {
            if (latestState.stackedRideOffer?.id == it.id) latestState.stackedRideOffer else it.copy(
                expiresAtMs = resolveOfferExpiry(it)
            )
        }

        // Play native chime + haptic + UK TTS heads-up + heads-up banner over external nav when a brand-new offer arrives
        val newlyArrivedOffer = nextIncoming ?: nextStacked
        if (newlyArrivedOffer != null && newlyArrivedOffer.id != lastAlertedOfferId) {
            lastAlertedOfferId = newlyArrivedOffer.id
            val isStackedOffer = (nextIncoming == null && nextStacked != null)
            audioFeedback.playRideOfferChime(latestState.profile.muteRideOfferAlerts)
            ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 200, 100, 200, 100, 450))
            val spokenOffer = if (isStackedOffer) {
                "Stacked next ride offer for £%.2f".format(newlyArrivedOffer.fareEstimate)
            } else {
                "New ride offer for £%.2f".format(newlyArrivedOffer.fareEstimate)
            }
            audioFeedback.speakHeadsUp(spokenOffer, latestState.profile.muteHeadsUpVolume)
            // Guarantee a high-priority heads-up notification banner when Google Maps/Waze is on-screen (even in PiP)
            if (!UrgentPushEventBus.isFullScreenVisible.value) {
                val remMs = (newlyArrivedOffer.expiresAtMs - System.currentTimeMillis()).coerceIn(5_000L, 60_000L)
                AnyRollerMessagingService.showUrgentOfferNotification(
                    context = context,
                    rideId = newlyArrivedOffer.id,
                    fareEstimate = newlyArrivedOffer.fareEstimate,
                    pickupAddress = newlyArrivedOffer.pickupAddress,
                    dropoffAddress = newlyArrivedOffer.dropoffAddress,
                    isStacked = isStackedOffer,
                    timeoutMs = remMs
                )
            }
        } else if (newlyArrivedOffer == null) {
            lastAlertedOfferId = null
        }

        // Sync scheduled ride 45m & 15m local reminder alarms
        val schedSig = "${latestState.cockpitSettings.scheduledRemindersEnabled}:" +
            scheduled.joinToString(",") { "${it.id}@${it.scheduledTime.orEmpty()}" }
        if (schedSig != lastScheduledReminderSig) {
            lastScheduledReminderSig = schedSig
            ScheduledRideReminderScheduler.syncReminders(
                context = context,
                scheduledRides = scheduled,
                enabled = latestState.cockpitSettings.scheduledRemindersEnabled
            )
        }

        // Compute live shift summary from completed rides since shift start (or start of day)
        val shiftFloorMs = latestState.shiftStartedAtMs ?: Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val shiftCompleted = completed.filter { maxOf(it.completedAtMs, it.createdAtMs) >= shiftFloorMs }
        val fallbackCfg = FallbackFareConfig(latestState.activeCommissionRate, latestState.platformConfig.fixedTripFee)
        val computedShiftEarnings = shiftCompleted.sumOf {
            resolveRideFinancialSummary(it, fallbackCfg).driverEarnings
        }
        val computedShiftPaidMiles = shiftCompleted.sumOf { it.distanceMiles.coerceAtLeast(0.0) }

        val shouldResetCollapsed = derivedRideState != lastCollapsedRideState &&
            derivedRideState in setOf(TerminalRideState.EN_ROUTE_PICKUP, TerminalRideState.IN_PROGRESS)
        if (derivedRideState != lastCollapsedRideState) {
            lastCollapsedRideState = derivedRideState
        }

        val midTripExternalNavEvent = if (midTripRouteChanged &&
            currentActive != null &&
            latestState.cockpitSettings.autoLaunchExternalNav
        ) {
            when (derivedRideState) {
                TerminalRideState.EN_ROUTE_PICKUP -> {
                    if (currentActive.pickupLat != 0.0 || currentActive.pickupLng != 0.0) {
                        ExternalNavLaunchEvent(
                            id = System.currentTimeMillis(),
                            lat = currentActive.pickupLat,
                            lng = currentActive.pickupLng
                        )
                    } else null
                }
                TerminalRideState.IN_PROGRESS -> {
                    if (currentActive.dropoffLat != 0.0 || currentActive.dropoffLng != 0.0) {
                        val remainingWaypoints = currentActive.stops
                            .drop(currentActive.currentStopIndex.coerceAtLeast(0))
                            .filter { it.lat != 0.0 || it.lng != 0.0 }
                            .map { it.lat to it.lng }
                        ExternalNavLaunchEvent(
                            id = System.currentTimeMillis(),
                            lat = currentActive.dropoffLat,
                            lng = currentActive.dropoffLng,
                            waypoints = remainingWaypoints
                        )
                    } else null
                }
                else -> null
            }
        } else null

        _uiState.update { st ->
            st.copy(
                activeRide = currentActive,
                queuedStackedRide = queuedStacked,
                completedRides = completed,
                scheduledRides = scheduled,
                rideState = derivedRideState,
                incomingOffer = nextIncoming,
                stackedRideOffer = nextStacked,
                paymentQrUrl = currentActive?.paymentUrl ?: st.paymentQrUrl,
                isAwaitingCashConfirm = if (currentActive?.status?.equals("awaiting_cash_confirm", true) == true) true else st.isAwaitingCashConfirm,
                lastRideUpdateNotice = midTripNotice,
                isActiveCardCollapsed = if (shouldResetCollapsed) true else st.isActiveCardCollapsed,
                isAutoNavPaused = if (shouldResetCollapsed) false else st.isAutoNavPaused,
                shiftTripsCompleted = shiftCompleted.size,
                shiftEarnings = computedShiftEarnings,
                shiftPaidMiles = computedShiftPaidMiles,
                pendingExternalNav = midTripExternalNavEvent ?: st.pendingExternalNav,
                toastMessage = if (midTripNotice != null && midTripNotice != st.lastRideUpdateNotice) midTripNotice else st.toastMessage
            )
        }

        // Auto-Accept Mode evaluation (OFF by default, gated by min fare & max pickup deadhead miles)
        val cockpit = latestState.cockpitSettings
        if (cockpit.autoAcceptEnabled &&
            !latestState.isOnBreak &&
            !latestState.isBusy &&
            nextIncoming != null &&
            nextIncoming.id != lastAutoAcceptedOfferId
        ) {
            val intel = OfferIntelligenceHelper.analyze(
                offer = nextIncoming,
                driverLat = latestState.driverLocation.lat,
                driverLng = latestState.driverLocation.lng,
                hasRealDriverFix = latestState.driverLocation.hasRealFix,
                homeLat = latestState.profile.homeLat,
                homeLng = latestState.profile.homeLng
            )
            val meetsFare = nextIncoming.fareEstimate >= cockpit.autoAcceptMinFare
            val meetsPickup = !latestState.driverLocation.hasRealFix || intel.pickupDeadheadMiles <= cockpit.autoAcceptMaxPickupMiles
            if (meetsFare && meetsPickup) {
                lastAutoAcceptedOfferId = nextIncoming.id
                audioFeedback.speakHeadsUp(
                    "Auto-accepting ride for £%.2f".format(nextIncoming.fareEstimate),
                    latestState.profile.muteHeadsUpVolume
                )
                acceptOffer(isStacked = false)
            }
        }

        refreshActiveRouteIfNeeded(currentActive, derivedRideState)
    }

    private fun scheduleIncomingChatPopupDismiss(messageId: String) {
        incomingChatPopupJob?.cancel()
        incomingChatPopupJob = viewModelScope.launch {
            delay(10_000L)
            _uiState.update { st ->
                if (st.incomingChatPopup?.id == messageId) st.copy(incomingChatPopup = null) else st
            }
        }
    }

    private fun resetPerRideTransientState() {
        waitingStartedAtMs = null
        stopWaitStartMs = null
        stopWaitOriginLat = null
        stopWaitOriginLng = null
        pickupProximityStartMs = null
        hasAutoZoomedPickupRideId = null
        hasReachedCurrentStopKey = null
        dismissedStartTripReminderRideId = null
        incomingChatPopupJob?.cancel()
        lastSpokenStepIndex = -1
        spokenStepThresholds.clear()
        hasAnnouncedLegArrivalKey = null
        routeRecalcCount = 0
        consecutiveOffRouteFixes = 0
        lastRecalcTimestampMs = 0L
        lastRouteLegKey = null
        _uiState.update {
            it.copy(
                incomingChatPopup = null,
                remainingRoutePoints = emptyList(),
                liveRemainingDistanceMeters = null,
                liveRemainingEtaMinutes = null,
                snappedRouteBearing = null,
                routeRecalcLimitReached = false,
                currentNavStepIndex = 0,
                currentStepDistanceMeters = null,
                isAutoNavPaused = false,
                autoNavPausedAtMs = null,
                isActiveCardCollapsed = true
            )
        }
    }

    private fun refreshActiveRouteIfNeeded(ride: RideRequest?, state: TerminalRideState) {
        // COST-SAVING FIX: Never call Directions API while parked in WAITING state at pickup!
        if (state == TerminalRideState.WAITING) {
            return
        }
        if (ride == null || state !in setOf(
                TerminalRideState.EN_ROUTE_PICKUP,
                TerminalRideState.IN_PROGRESS
            )
        ) {
            lastRouteLegKey = null
            routeRecalcCount = 0
            consecutiveOffRouteFixes = 0
            lastSpokenStepIndex = -1
            spokenStepThresholds.clear()
            if (_uiState.value.activeRoute.points.isNotEmpty() || _uiState.value.routeRecalcLimitReached) {
                _uiState.update {
                    it.copy(
                        activeRoute = ActiveRouteResult(),
                        remainingRoutePoints = emptyList(),
                        liveRemainingDistanceMeters = null,
                        liveRemainingEtaMinutes = null,
                        snappedRouteBearing = null,
                        routeRecalcLimitReached = false,
                        currentNavStepIndex = 0,
                        currentStepDistanceMeters = null
                    )
                }
            }
            return
        }

        // Single-call multi-stop fetch (Basic SKU <=10 non-optimized waypoints) so advancing stops costs £0 extra
        val (destLat, destLng, waypoints) = if (state == TerminalRideState.IN_PROGRESS) {
            val remainingStops = ride.stops
                .drop(ride.currentStopIndex.coerceAtLeast(0))
                .filter { it.lat != 0.0 || it.lng != 0.0 }
                .map { it.lat to it.lng }
            Triple(ride.dropoffLat, ride.dropoffLng, remainingStops)
        } else {
            Triple(ride.pickupLat, ride.pickupLng, emptyList())
        }

        // Key IN_PROGRESS by full stops signature + dropoff (NOT currentStopIndex) so advancing stops reuses the 1-call multi-leg route
        val stopsSig = if (state == TerminalRideState.IN_PROGRESS) {
            ride.stops.joinToString("|") { "${it.lat},${it.lng}" }
        } else ""
        val legKey = "${ride.id}:${state.name}:$destLat:$destLng:$stopsSig"

        val loc = _uiState.value.driverLocation
        val movedMeters = FloatArray(1)
        if (lastRouteFetchLat != 0.0 || lastRouteFetchLng != 0.0) {
            android.location.Location.distanceBetween(lastRouteFetchLat, lastRouteFetchLng, loc.lat, loc.lng, movedMeters)
        } else {
            movedMeters[0] = 999f
        }

        if (legKey != lastRouteLegKey) {
            lastRouteLegKey = legKey
            routeRecalcCount = 0
            consecutiveOffRouteFixes = 0
            lastSpokenStepIndex = -1
            spokenStepThresholds.clear()
            _uiState.update {
                it.copy(
                    routeRecalcLimitReached = false,
                    currentNavStepIndex = 0,
                    currentStepDistanceMeters = null
                )
            }
        } else if (routeRecalcCount >= 3) {
            if (!_uiState.value.routeRecalcLimitReached) {
                _uiState.update { it.copy(routeRecalcLimitReached = true) }
            }
            return
        } else if (movedMeters[0] < 50f) {
            return
        } else {
            routeRecalcCount++
            if (routeRecalcCount >= 3) {
                _uiState.update { it.copy(routeRecalcLimitReached = true) }
            }
        }

        lastRouteFetchLat = loc.lat
        lastRouteFetchLng = loc.lng
        lastRecalcTimestampMs = System.currentTimeMillis()
        viewModelScope.launch {
            val route = directionsRepo.fetchRoute(
                originLat = loc.lat,
                originLng = loc.lng,
                destLat = destLat,
                destLng = destLng,
                waypoints = waypoints
            )
            lastSpokenStepIndex = -1
            spokenStepThresholds.clear()
            _uiState.update {
                it.copy(
                    activeRoute = route,
                    remainingRoutePoints = route.points,
                    liveRemainingDistanceMeters = route.totalDistanceMeters.takeIf { d -> d > 0 },
                    liveRemainingEtaMinutes = route.etaMinutes,
                    currentNavStepIndex = 0,
                    currentStepDistanceMeters = route.steps.firstOrNull()?.distanceMeters
                )
            }
            val currentUi = _uiState.value
            val initialSpeech = if (route.steps.isNotEmpty()) {
                lastSpokenStepIndex = 0
                buildChainedStepInstruction(route.steps, 0)
            } else {
                route.nextManeuver.orEmpty()
            }
            if (initialSpeech.isNotBlank() &&
                currentUi.isAutoNavHeadUp &&
                (state == TerminalRideState.EN_ROUTE_PICKUP || state == TerminalRideState.IN_PROGRESS)
            ) {
                audioFeedback.speakHeadsUp(initialSpeech, currentUi.profile.muteHeadsUpVolume)
            }
        }
    }

    // --- Navigation & UI Toggles ---

    fun selectTab(tab: DriverTab) {
        _uiState.update { it.copy(activeTab = tab, overlayRoute = DriverOverlayRoute.NONE) }
        if (tab == DriverTab.EARNINGS) {
            refreshFinancials()
        }
    }

    fun openOverlay(route: DriverOverlayRoute) {
        _uiState.update { it.copy(overlayRoute = route) }
        when (route) {
            DriverOverlayRoute.MENU -> loadNotificationPreferences()
            DriverOverlayRoute.ANALYTICS -> fetchAnalyticsPulse()
            DriverOverlayRoute.ZONES -> refreshDemandZones()
            DriverOverlayRoute.EXPENSES, DriverOverlayRoute.TRIP_HISTORY -> refreshFinancials()
            else -> Unit
        }
    }

    fun closeOverlay() {
        val current = _uiState.value.overlayRoute
        val parent = when (current) {
            DriverOverlayRoute.DOCUMENTS,
            DriverOverlayRoute.AVAILABILITY,
            DriverOverlayRoute.ZONES,
            DriverOverlayRoute.ANALYTICS,
            DriverOverlayRoute.PROFILE -> DriverOverlayRoute.MENU
            DriverOverlayRoute.INSURANCE -> DriverOverlayRoute.DOCUMENTS
            else -> DriverOverlayRoute.NONE
        }
        _uiState.update { it.copy(overlayRoute = parent) }
    }

    fun setHazardModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showHazardModal = visible) }
    }

    fun setChatSheetVisible(visible: Boolean) {
        if (visible) {
            incomingChatPopupJob?.cancel()
        }
        _uiState.update {
            it.copy(
                showChatSheet = visible,
                unreadChatCount = if (visible) 0 else it.unreadChatCount,
                incomingChatPopup = if (visible) null else it.incomingChatPopup
            )
        }
        val rideId = _uiState.value.activeRide?.id
        if (visible && rideId != null) {
            startChatPresence(rideId)
        } else {
            stopChatPresence()
        }
    }

    fun dismissIncomingChatPopup() {
        incomingChatPopupJob?.cancel()
        _uiState.update { it.copy(incomingChatPopup = null) }
    }

    fun sendPopupQuickReply(text: String) {
        if (text.isBlank()) return
        incomingChatPopupJob?.cancel()
        _uiState.update {
            it.copy(
                incomingChatPopup = null,
                unreadChatCount = 0,
                toastMessage = "Quick reply sent: \"$text\""
            )
        }
        sendChatMessage(text)
    }

    fun toggleAutoNavHeadUp() {
        _uiState.update { st ->
            val next = !st.isAutoNavHeadUp
            st.copy(
                isAutoNavHeadUp = next,
                isAutoNavPaused = false,
                autoNavPausedAtMs = null,
                toastMessage = if (next) "3D Head-Up Navigation resumed" else "2D Map Overview mode"
            )
        }
    }

    fun pauseAutoNavFromGesture() {
        val now = System.currentTimeMillis()
        _uiState.update { it.copy(isAutoNavPaused = true, autoNavPausedAtMs = now) }
    }

    fun resumeAutoNav() {
        _uiState.update {
            it.copy(
                isAutoNavHeadUp = true,
                isAutoNavPaused = false,
                autoNavPausedAtMs = null
            )
        }
    }

    fun toggleActiveCardCollapsed() {
        _uiState.update { it.copy(isActiveCardCollapsed = !it.isActiveCardCollapsed) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun clearCustomTabUrl() {
        _uiState.update { it.copy(customTabUrlToOpen = null) }
    }

    fun clearWhatsAppUrl() {
        _uiState.update { it.copy(whatsAppUrlToOpen = null) }
    }

    fun clearPendingExternalNav() {
        _uiState.update { it.copy(pendingExternalNav = null) }
    }

    // --- Part 2 Cockpit Settings, Break Mode, Fatigue & Safety Toolkit ---

    fun updateCockpitSettings(transform: (CockpitLocalSettings) -> CockpitLocalSettings) {
        viewModelScope.launch {
            val updated = prefsStore.updateCockpitSettings(transform)
            _uiState.update { it.copy(cockpitSettings = updated) }
            ScheduledRideReminderScheduler.syncReminders(
                context = context,
                scheduledRides = _uiState.value.scheduledRides,
                enabled = updated.scheduledRemindersEnabled
            )
        }
    }

    fun startBreak(durationMinutes: Int?) {
        val state = _uiState.value
        if (!state.isOnline) {
            _uiState.update { it.copy(toastMessage = "Go Online first to take a shift break.") }
            return
        }
        if (state.activeRide != null) {
            _uiState.update { it.copy(toastMessage = "Finish your current ride before pausing requests.") }
            return
        }
        val uid = state.uid.ifBlank { return }
        val openEnded = durationMinutes == null || durationMinutes <= 0
        val totalSeconds = if (openEnded) 0 else durationMinutes!! * 60
        breakEndsAtMs = if (openEnded) null else System.currentTimeMillis() + totalSeconds * 1000L
        fatigueReminderTriggeredForShift = false

        _uiState.update {
            it.copy(
                isOnBreak = true,
                breakIsOpenEnded = openEnded,
                breakSecondsLeft = totalSeconds,
                incomingOffer = null,
                rideState = TerminalRideState.IDLE,
                fatigueReminderActive = false,
                toastMessage = if (openEnded) {
                    "Break started — new ride requests paused."
                } else {
                    "$durationMinutes-min break started — requests paused."
                }
            )
        }
        ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 60))
        viewModelScope.launch(Dispatchers.IO) {
            repository.setDriverBreakStatus(uid, onBreak = true)
        }
    }

    fun endBreak(isAuto: Boolean = false) {
        val state = _uiState.value
        if (!state.isOnBreak) return
        val uid = state.uid
        breakEndsAtMs = null
        _uiState.update {
            it.copy(
                isOnBreak = false,
                breakIsOpenEnded = false,
                breakSecondsLeft = 0,
                toastMessage = if (isAuto) {
                    "Break ended — you are back ONLINE for ride requests."
                } else {
                    "Break ended — ready for ride requests."
                }
            )
        }
        ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 70, 50, 70))
        if (isAuto) {
            audioFeedback.speakHeadsUp(
                "Your break has ended. You are back online for ride requests.",
                state.profile.muteHeadsUpVolume
            )
        }
        if (uid.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.setDriverBreakStatus(uid, onBreak = false)
            }
        }
    }

    fun dismissFatigueReminder() {
        _uiState.update { it.copy(fatigueReminderActive = false) }
    }

    fun logSafetyIncident(note: String) {
        val trimmed = note.trim()
        if (trimmed.isBlank()) return
        reportHazard("Safety Incident: $trimmed")
        _uiState.update { it.copy(toastMessage = "Safety incident logged with GPS & timestamp.") }
    }

    // --- Online / Offline Control ---

    fun toggleOnline(hasLocationPermission: Boolean) {
        val state = _uiState.value
        val uid = state.uid.ifBlank { return }
        if (!state.isOnline) {
            if (!hasLocationPermission) {
                _uiState.update { it.copy(toastMessage = "Location permission is required to go Online.") }
                return
            }
            // Hard safety check synced with Admin maxDailyDriverHours
            val drivenHours = state.metrics.onlineSecondsToday / 3600.0
            val maxHours = state.platformConfig.maxDailyDriverHours.coerceAtLeast(1.0)
            if (drivenHours >= maxHours) {
                _uiState.update {
                    it.copy(
                        toastMessage = "Safety Limit Reached — You have reached your daily limit of %.0f driving hours.".format(maxHours)
                    )
                }
                return
            }
            consecutiveMissedOffers = 0
            breakEndsAtMs = null
            fatigueReminderTriggeredForShift = false
            DriverLocationService.start(context, state.profile.fullName, state.activeRide != null)
            ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 80))
            _uiState.update {
                it.copy(
                    isOnline = true,
                    isOnBreak = false,
                    breakSecondsLeft = 0,
                    breakIsOpenEnded = false,
                    shiftStartedAtMs = System.currentTimeMillis(),
                    shiftOnlineSeconds = 0L,
                    shiftDeadheadMiles = 0.0,
                    fatigueReminderActive = false,
                    toastMessage = "You are now ONLINE — waiting for jobs."
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                prefsStore.setOnlineFlag(true)
                val loc = state.driverLocation
                repository.mergeLiveTracking(
                    uid = uid,
                    lat = loc.lat,
                    lng = loc.lng,
                    heading = loc.heading,
                    speedMps = loc.speedMps,
                    isOnline = true,
                    isOnRide = state.activeRide != null,
                    driverName = state.profile.fullName,
                    vehicleCategory = state.localVehicleCategories.firstOrNull() ?: "standard",
                    vehicleCategories = state.localVehicleCategories.toList(),
                    isPetFriendly = state.localPetFriendly,
                    isStackingEnabled = state.profile.stackingEnabled,
                    isLastJob = state.profile.lastJob,
                    destinationModeActive = state.profile.destinationModeActive,
                    homeLat = state.profile.homeLat,
                    homeLng = state.profile.homeLng,
                    zoneEnabled = state.profile.zoneEnabled,
                    zoneMaxDistance = state.profile.zoneMaxDistance,
                    dropoffLat = state.activeRide?.dropoffLat,
                    dropoffLng = state.activeRide?.dropoffLng
                )
            }
        } else {
            if (state.activeRide != null) {
                _uiState.update { it.copy(toastMessage = "Finish or cancel your active trip before going Offline.") }
                return
            }
            breakEndsAtMs = null
            fatigueReminderTriggeredForShift = false
            DriverLocationService.stop(context)
            _uiState.update {
                it.copy(
                    isOnline = false,
                    isOnBreak = false,
                    breakSecondsLeft = 0,
                    breakIsOpenEnded = false,
                    fatigueReminderActive = false,
                    incomingOffer = null,
                    rideState = TerminalRideState.IDLE,
                    toastMessage = "You are now OFFLINE."
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                prefsStore.setOnlineFlag(false)
                repository.markDriverOfflineInFirestore(uid)
            }
        }
    }

    private fun notifyQueuedStackedRideCancelled(rideId: String, cancellationFee: Double): String {
        lastAlertedCancelledQueuedId = rideId
        if (lastQueuedStackedRideId == rideId) {
            lastQueuedStackedRideId = null
        }
        val st = _uiState.value
        audioFeedback.playCancellationAlert(st.profile.muteRideOfferAlerts)
        ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 260, 120, 260))
        val msg = if (cancellationFee > 0.0) {
            "Queued next ride cancelled by passenger. £%.2f cancellation fee credited.".format(cancellationFee)
        } else {
            "Your queued next ride was cancelled by the passenger. Continue your current trip."
        }
        audioFeedback.speakHeadsUp(msg, st.profile.muteHeadsUpVolume)
        if (!UrgentPushEventBus.isFullScreenVisible.value) {
            AnyRollerMessagingService.showStackedCancelledNotification(context, rideId, msg)
        }
        return msg
    }

    private fun buildCurrentTripExternalNavEvent(state: DriverTerminalUiState): ExternalNavLaunchEvent? {
        if (!state.cockpitSettings.autoLaunchExternalNav) return null
        val active = state.activeRide ?: return null
        if (state.rideState != TerminalRideState.IN_PROGRESS) return null
        if (active.dropoffLat == 0.0 && active.dropoffLng == 0.0) return null
        val remainingWaypoints = active.stops
            .drop(active.currentStopIndex.coerceAtLeast(0))
            .filter { it.lat != 0.0 || it.lng != 0.0 }
            .map { it.lat to it.lng }
        return ExternalNavLaunchEvent(
            id = System.currentTimeMillis(),
            lat = active.dropoffLat,
            lng = active.dropoffLng,
            waypoints = remainingWaypoints
        )
    }

    // --- Server-Authoritative Ride Lifecycle ---

    fun acceptOffer(isStacked: Boolean = false) {
        val state = _uiState.value
        val offer = if (isStacked) state.stackedRideOffer else state.incomingOffer
        if (offer == null) return
        AnyRollerMessagingService.cancelOfferNotification(context, offer.id)
        val uid = state.uid
        consecutiveMissedOffers = 0
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (val res = ApiClient.safeCall { ApiClient.service.acceptRide(RideAcceptRequest(offer.id)) }) {
                is ApiResult.Success -> {
                    ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 70))
                    if (isStacked && uid.isNotBlank()) {
                        repository.syncActiveRideDropoff(uid, offer.dropoffLat, offer.dropoffLng)
                    }
                    val autoNavEvent = if (isStacked) {
                        // Return driver seamlessly back to Google Maps / Waze for their current active trip
                        buildCurrentTripExternalNavEvent(_uiState.value)
                    } else if (_uiState.value.cockpitSettings.autoLaunchExternalNav &&
                        (offer.pickupLat != 0.0 || offer.pickupLng != 0.0)
                    ) {
                        ExternalNavLaunchEvent(
                            id = System.currentTimeMillis(),
                            lat = offer.pickupLat,
                            lng = offer.pickupLng
                        )
                    } else null
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            incomingOffer = if (!isStacked) null else it.incomingOffer,
                            stackedRideOffer = if (isStacked) null else it.stackedRideOffer,
                            pendingExternalNav = autoNavEvent ?: it.pendingExternalNav,
                            toastMessage = if (isStacked) "Next ride queued! Finish current trip first." else "Ride accepted! Navigating to pickup."
                        )
                    }
                }
                is ApiResult.Error -> {
                    // Fallback for server/passenger-dispatched `"offered"` rides where `assignedDriverId == uid`
                    // causes `/api/rides/accept` to reject with 409 Conflict.
                    val isDirectOfferToMe = res.isConflict &&
                        uid.isNotBlank() &&
                        offer.status.equals("offered", ignoreCase = true) &&
                        offer.assignedDriverId == uid
                    if (isDirectOfferToMe) {
                        val txResult = repository.claimOfferedRideTransaction(
                            rideId = offer.id,
                            driverUid = uid,
                            passengerId = offer.passengerId.takeIf { it.isNotBlank() },
                            dropoffLat = offer.dropoffLat,
                            dropoffLng = offer.dropoffLng,
                            isStacked = isStacked
                        )
                        if (txResult.isSuccess) {
                            ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 70))
                            if (offer.passengerId.isNotBlank()) {
                                ApiClient.safeCall {
                                    ApiClient.service.sendChatPush(
                                        ChatPushRequest(
                                            rideId = offer.id,
                                            recipientId = offer.passengerId,
                                            title = if (isStacked) "Driver Assigned" else "Driver On The Way",
                                            message = if (isStacked) {
                                                "A driver has been assigned and will head to you after their current dropoff."
                                            } else {
                                                "${state.profile.fullName} has accepted your ride and is heading to pickup."
                                            },
                                            type = "ride_update"
                                        )
                                    )
                                }
                            }
                            val autoNavEvent = if (isStacked) {
                                buildCurrentTripExternalNavEvent(_uiState.value)
                            } else if (_uiState.value.cockpitSettings.autoLaunchExternalNav &&
                                (offer.pickupLat != 0.0 || offer.pickupLng != 0.0)
                            ) {
                                ExternalNavLaunchEvent(
                                    id = System.currentTimeMillis(),
                                    lat = offer.pickupLat,
                                    lng = offer.pickupLng
                                )
                            } else null
                            _uiState.update {
                                it.copy(
                                    isBusy = false,
                                    incomingOffer = if (!isStacked) null else it.incomingOffer,
                                    stackedRideOffer = if (isStacked) null else it.stackedRideOffer,
                                    pendingExternalNav = autoNavEvent ?: it.pendingExternalNav,
                                    toastMessage = if (isStacked) "Next ride queued! Finish current trip first." else "Ride accepted! Navigating to pickup."
                                )
                            }
                            return@launch
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            incomingOffer = if (!isStacked && res.isConflict) null else it.incomingOffer,
                            stackedRideOffer = if (isStacked && res.isConflict) null else it.stackedRideOffer,
                            rideState = if (!isStacked && res.isConflict) TerminalRideState.IDLE else it.rideState,
                            toastMessage = res.message
                        )
                    }
                }
            }
        }
    }

    fun declineOffer(isStacked: Boolean = false) {
        val currentState = _uiState.value
        val offer = if (isStacked) currentState.stackedRideOffer else currentState.incomingOffer
        if (offer == null) return
        AnyRollerMessagingService.cancelOfferNotification(context, offer.id)
        consecutiveMissedOffers = 0
        val uid = currentState.uid
        viewModelScope.launch(Dispatchers.IO) {
            prefsStore.addDeclinedOfferId(offer.id)
            // Call server-authoritative POST /api/rides/decline for instant re-dispatch; fall back safely if unavailable
            val res = ApiClient.safeCall {
                ApiClient.service.declineRide(RideDeclineRequest(rideId = offer.id))
            }
            if (res is ApiResult.Error && uid.isNotBlank()) {
                repository.recordDriverDeclineStatus(uid)
            }
        }
        val returnNavEvent = if (isStacked) buildCurrentTripExternalNavEvent(currentState) else null
        _uiState.update {
            if (isStacked) {
                it.copy(
                    stackedRideOffer = null,
                    pendingExternalNav = returnNavEvent ?: it.pendingExternalNav,
                    toastMessage = "Queued ride declined."
                )
            } else {
                it.copy(incomingOffer = null, rideState = TerminalRideState.IDLE, toastMessage = "Ride offer declined.")
            }
        }
    }

    fun requestArrivedAtPickup() {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        if (state.driverLocation.hasRealFix && (ride.pickupLat != 0.0 || ride.pickupLng != 0.0)) {
            val dist = distanceMeters(state.driverLocation.lat, state.driverLocation.lng, ride.pickupLat, ride.pickupLng)
            if (dist > 200f && dist < Float.MAX_VALUE) {
                _uiState.update { it.copy(showEarlyArrivalDialog = true) }
                return
            }
        }
        markArrivedAtPickup(isAuto = false)
    }

    fun dismissEarlyArrivalDialog() {
        _uiState.update { it.copy(showEarlyArrivalDialog = false) }
    }

    fun dismissStartTripReminder() {
        dismissedStartTripReminderRideId = _uiState.value.activeRide?.id
        _uiState.update { it.copy(showStartTripReminder = false) }
    }

    fun dismissRideUpdateNotice() {
        _uiState.update { it.copy(lastRideUpdateNotice = null) }
    }

    fun markArrivedAtPickup(isAuto: Boolean = false) {
        val ride = _uiState.value.activeRide ?: return
        _uiState.update { it.copy(showEarlyArrivalDialog = false) }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.transitionRide(
                        RideTransitionRequest(rideId = ride.id, action = "arrived")
                    )
                }
            ) {
                is ApiResult.Success -> {
                    val currentProfile = _uiState.value.profile
                    waitingStartedAtMs = System.currentTimeMillis()
                    audioFeedback.playArrivalConfirmChime(currentProfile.muteRideOfferAlerts)
                    ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 120, 60, 120))
                    audioFeedback.speakHeadsUp(
                        if (isAuto) "Auto-marked arrived outside. Passenger notified." else "Marked arrived outside. Passenger notified.",
                        currentProfile.muteHeadsUpVolume
                    )
                    // Trigger fast 5-second critical offline WhatsApp fallback + audible push on rider arrival!
                    if (ride.passengerId.isNotBlank()) {
                        ApiClient.safeCall {
                            ApiClient.service.sendChatPush(
                                ChatPushRequest(
                                    rideId = ride.id,
                                    recipientId = ride.passengerId,
                                    title = "Your AnyRoller driver has arrived outside",
                                    message = "${currentProfile.fullName} has arrived outside at your pickup point.",
                                    type = "ride_arrived"
                                )
                            )
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            rideState = TerminalRideState.WAITING,
                            pickupZoomEventId = System.currentTimeMillis(),
                            toastMessage = if (isAuto) "Auto-marked arrived outside. Passenger notified." else "Marked arrived outside. Passenger notified."
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
                }
            }
        }
    }

    fun startTrip(enteredPasscode: String = "") {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        val requiresCode = state.profile.requirePasscode || ride.driverRequirePasscode
        if (requiresCode) {
            val expected = ride.handshakeCode?.takeIf { it.isNotBlank() }
                ?: ride.passengerPhone?.filter { it.isDigit() }?.takeLast(4)
            if (!expected.isNullOrBlank() && enteredPasscode.trim() != expected) {
                _uiState.update { it.copy(toastMessage = "Incorrect passenger passcode. Please verify 4-digit PIN.") }
                return
            }
        }
        val pickupPaidWait = (state.waitSecondsElapsed - state.platformConfig.freeWaitSeconds).coerceAtLeast(0)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.transitionRide(
                        RideTransitionRequest(
                            rideId = ride.id,
                            action = "in_progress",
                            paidWaitSeconds = pickupPaidWait
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 100, 50, 100))
                    hasReachedCurrentStopKey = null
                    if (ride.passengerId.isNotBlank()) {
                        ApiClient.safeCall {
                            ApiClient.service.sendChatPush(
                                ChatPushRequest(
                                    rideId = ride.id,
                                    recipientId = ride.passengerId,
                                    title = "Trip Started",
                                    message = "Your AnyRoller journey to ${ride.dropoffAddress} has started.",
                                    type = "ride_update"
                                )
                            )
                        }
                    }
                    val remainingWaypoints = ride.stops
                        .drop(ride.currentStopIndex.coerceAtLeast(0))
                        .map { it.lat to it.lng }
                    val autoNavEvent = if (_uiState.value.cockpitSettings.autoLaunchExternalNav &&
                        (ride.dropoffLat != 0.0 || ride.dropoffLng != 0.0)
                    ) {
                        ExternalNavLaunchEvent(
                            id = System.currentTimeMillis(),
                            lat = ride.dropoffLat,
                            lng = ride.dropoffLng,
                            waypoints = remainingWaypoints
                        )
                    } else null
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            rideState = TerminalRideState.IN_PROGRESS,
                            accumulatedPaidWaitSeconds = pickupPaidWait,
                            showStartTripReminder = false,
                            showLeaveStopReminder = false,
                            isWaitingAtStop = false,
                            currentStopWaitSeconds = 0,
                            abandonmentWarningSent = false,
                            pendingExternalNav = autoNavEvent ?: it.pendingExternalNav,
                            toastMessage = "Trip started — drive safely."
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
                }
            }
        }
    }

    fun toggleWaitAtStop() {
        val state = _uiState.value
        if (state.rideState != TerminalRideState.IN_PROGRESS) return
        if (state.isWaitingAtStop) {
            val added = state.currentStopWaitSeconds
            stopWaitStartMs = null
            stopWaitOriginLat = null
            stopWaitOriginLng = null
            _uiState.update {
                it.copy(
                    isWaitingAtStop = false,
                    accumulatedPaidWaitSeconds = it.accumulatedPaidWaitSeconds + added,
                    currentStopWaitSeconds = 0,
                    abandonmentWarningSent = false,
                    toastMessage = "Stop wait paused."
                )
            }
        } else {
            stopWaitStartMs = System.currentTimeMillis()
            stopWaitOriginLat = state.driverLocation.lat
            stopWaitOriginLng = state.driverLocation.lng
            _uiState.update {
                it.copy(
                    isWaitingAtStop = true,
                    currentStopWaitSeconds = 0,
                    toastMessage = "Stop wait timer started (£%.2f/min).".format(state.platformConfig.waitRatePerMinute)
                )
            }
        }
    }

    fun sendAbandonmentWarning() {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        viewModelScope.launch {
            if (ride.passengerId.isNotBlank()) {
                ApiClient.safeCall {
                    ApiClient.service.sendChatPush(
                        ChatPushRequest(
                            rideId = ride.id,
                            recipientId = ride.passengerId,
                            title = "Please return to your AnyRoller vehicle",
                            message = "Your driver is waiting. Please return soon to avoid an abandonment fee.",
                            type = "ride_arrived"
                        )
                    )
                }
            }
            _uiState.update {
                it.copy(
                    abandonmentWarningSent = true,
                    toastMessage = "Warning sent to passenger's phone."
                )
            }
        }
    }

    fun processRiderAbandonment() {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        val totalWait = state.totalPaidWaitSeconds
        stopWaitStartMs = null
        stopWaitOriginLat = null
        stopWaitOriginLng = null
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isWaitingAtStop = false) }
            val payRes = ApiClient.safeCall {
                ApiClient.service.createTripPayment(
                    CreateTripPaymentRequest(
                        rideId = ride.id,
                        paidWaitSeconds = totalWait,
                        isAbandonment = true
                    )
                )
            }
            val payUrl = (payRes as? ApiResult.Success)?.data?.url ?: ride.paymentUrl
            when (
                val completeRes = ApiClient.safeCall {
                    ApiClient.service.completeRide(
                        RideCompleteRequest(
                            rideId = ride.id,
                            action = "abandonment",
                            paidWaitSeconds = totalWait,
                            paymentUrl = payUrl
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            abandonmentWarningSent = false,
                            paymentQrUrl = payUrl,
                            rideState = TerminalRideState.COMPLETED_PAYMENT,
                            toastMessage = "Rider abandonment recorded. Settlement QR ready."
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = completeRes.message) }
                }
            }
        }
    }

    fun advanceMultiStop() {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        val nextIndex = ride.currentStopIndex + 1
        val addedStopWait = if (state.isWaitingAtStop) state.currentStopWaitSeconds else 0
        stopWaitStartMs = null
        stopWaitOriginLat = null
        stopWaitOriginLng = null
        hasReachedCurrentStopKey = null

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.transitionRide(
                        RideTransitionRequest(
                            rideId = ride.id,
                            action = "stop_progress",
                            currentStopIndex = nextIndex
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 80))
                    if (ride.passengerId.isNotBlank()) {
                        val nextLabel = ride.stops.getOrNull(nextIndex)?.address?.takeIf { it.isNotBlank() }
                            ?: ride.dropoffAddress
                        ApiClient.safeCall {
                            ApiClient.service.sendChatPush(
                                ChatPushRequest(
                                    rideId = ride.id,
                                    recipientId = ride.passengerId,
                                    title = "Stop ${ride.currentStopIndex + 1} Completed",
                                    message = "Heading to $nextLabel.",
                                    type = "ride_update"
                                )
                            )
                        }
                    }
                    val remainingWaypoints = ride.stops
                        .drop(nextIndex.coerceAtLeast(0))
                        .filter { it.lat != 0.0 || it.lng != 0.0 }
                        .map { it.lat to it.lng }
                    val autoNavEvent = if (_uiState.value.cockpitSettings.autoLaunchExternalNav &&
                        (ride.dropoffLat != 0.0 || ride.dropoffLng != 0.0)
                    ) {
                        ExternalNavLaunchEvent(
                            id = System.currentTimeMillis(),
                            lat = ride.dropoffLat,
                            lng = ride.dropoffLng,
                            waypoints = remainingWaypoints
                        )
                    } else null
                    // Advance local step pointer into the next leg of our single-call multi-stop route (0 extra API calls!)
                    val nextLegFirstStep = _uiState.value.activeRoute.steps.indexOfFirst { it.legIndex >= nextIndex }
                    if (nextLegFirstStep >= 0) {
                        lastSpokenStepIndex = nextLegFirstStep
                        spokenStepThresholds.clear()
                        val nextLegSpeech = buildChainedStepInstruction(_uiState.value.activeRoute.steps, nextLegFirstStep)
                        if (nextLegSpeech.isNotBlank() && _uiState.value.isAutoNavHeadUp) {
                            audioFeedback.speakHeadsUp(nextLegSpeech, _uiState.value.profile.muteHeadsUpVolume)
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isWaitingAtStop = false,
                            accumulatedPaidWaitSeconds = it.accumulatedPaidWaitSeconds + addedStopWait,
                            currentStopWaitSeconds = 0,
                            showLeaveStopReminder = false,
                            currentNavStepIndex = if (nextLegFirstStep >= 0) nextLegFirstStep else it.currentNavStepIndex,
                            currentStepDistanceMeters = if (nextLegFirstStep >= 0) {
                                it.activeRoute.steps.getOrNull(nextLegFirstStep)?.distanceMeters
                            } else it.currentStepDistanceMeters,
                            pendingExternalNav = autoNavEvent ?: it.pendingExternalNav,
                            toastMessage = if (remainingWaypoints.isEmpty()) {
                                "Stop completed — heading to final destination."
                            } else {
                                "Stop completed — heading to next stop."
                            }
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
                }
            }
        }
    }

    fun requestCompleteTrip() {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        val isEarly = if (state.driverLocation.hasRealFix && (ride.dropoffLat != 0.0 || ride.dropoffLng != 0.0)) {
            val dist = distanceMeters(state.driverLocation.lat, state.driverLocation.lng, ride.dropoffLat, ride.dropoffLng)
            dist > 300f && dist < Float.MAX_VALUE
        } else false
        _uiState.update {
            it.copy(
                isEarlyDropoff = isEarly,
                showCompleteConfirmDialog = true
            )
        }
    }

    fun dismissCompleteConfirmDialog() {
        _uiState.update { it.copy(showCompleteConfirmDialog = false) }
    }

    fun confirmCompleteTrip(earlyReason: String? = null) {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        val cleanEarlyReason = earlyReason?.trim()?.takeIf { state.isEarlyDropoff && it.isNotBlank() }
        if (state.isEarlyDropoff && cleanEarlyReason.isNullOrBlank()) {
            _uiState.update { it.copy(toastMessage = "Please enter a brief reason for early drop-off.") }
            return
        }

        val addedStopWait = if (state.isWaitingAtStop) state.currentStopWaitSeconds else 0
        val totalWait = state.totalPaidWaitSeconds
        stopWaitStartMs = null
        stopWaitOriginLat = null
        stopWaitOriginLng = null

        _uiState.update {
            it.copy(
                showCompleteConfirmDialog = false,
                isWaitingAtStop = false,
                accumulatedPaidWaitSeconds = it.accumulatedPaidWaitSeconds + addedStopWait,
                currentStopWaitSeconds = 0,
                isBusy = true
            )
        }

        viewModelScope.launch {
            if (!cleanEarlyReason.isNullOrBlank()) {
                ApiClient.safeCall {
                    ApiClient.service.transitionRide(
                        RideTransitionRequest(
                            rideId = ride.id,
                            action = "early_completion_reason",
                            earlyCompletionReason = cleanEarlyReason
                        )
                    )
                }
            }

            // 1. Cash trips -> enter cash collection screen
            if (ride.paymentMethod.equals("cash", ignoreCase = true)) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        rideState = TerminalRideState.COMPLETED_PAYMENT
                    )
                }
                return@launch
            }

            // 2. Card on file -> auto-charge immediately and jump cleanly to Review!
            if (ride.hasCardOnFile) {
                when (
                    val res = ApiClient.safeCall {
                        ApiClient.service.completeRide(
                            RideCompleteRequest(
                                rideId = ride.id,
                                action = "auto_card",
                                paidWaitSeconds = totalWait,
                                earlyCompletionReason = cleanEarlyReason
                            )
                        )
                    }
                ) {
                    is ApiResult.Success -> {
                        val finalizedRide = applyCompleteResponse(ride, res.data)
                        finishIntoReview(finalizedRide, "Payment received — passenger's card charged automatically.")
                    }
                    is ApiResult.Error -> {
                        // Fall back to QR/manual settlement card on auto-charge failure
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                rideState = TerminalRideState.COMPLETED_PAYMENT,
                                toastMessage = res.message
                            )
                        }
                    }
                }
                return@launch
            }

            // 3. Direct-to-Driver QR payment -> create Stripe link AND mark `qr_pending` so passenger sees live payment prompt
            val payRes = ApiClient.safeCall {
                ApiClient.service.createTripPayment(
                    CreateTripPaymentRequest(
                        rideId = ride.id,
                        paidWaitSeconds = totalWait,
                        tipAmount = ride.tipAmount
                    )
                )
            }
            val qrUrl = (payRes as? ApiResult.Success)?.data?.url ?: ride.paymentUrl

            val qrPendingRes = ApiClient.safeCall {
                ApiClient.service.completeRide(
                    RideCompleteRequest(
                        rideId = ride.id,
                        action = "qr_pending",
                        paidWaitSeconds = totalWait,
                        earlyCompletionReason = cleanEarlyReason,
                        paymentUrl = qrUrl
                    )
                )
            }
            val updatedRide = (qrPendingRes as? ApiResult.Success)?.let { applyCompleteResponse(ride, it.data) } ?: ride
            ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 150, 80, 150))
            _uiState.update {
                it.copy(
                    isBusy = false,
                    activeRide = updatedRide,
                    paymentQrUrl = qrUrl,
                    rideState = TerminalRideState.COMPLETED_PAYMENT,
                    toastMessage = (payRes as? ApiResult.Error)?.message
                )
            }
        }
    }

    fun submitCashPayment(
        cashCollected: Double? = null,
        forceComplete: Boolean = false,
        forceReason: String? = null
    ) {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        val totalWait = state.totalPaidWaitSeconds
        val action = if (forceComplete) "cash_finalize" else "cash_report"

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.completeRide(
                        RideCompleteRequest(
                            rideId = ride.id,
                            action = action,
                            paidWaitSeconds = totalWait,
                            cashCollected = cashCollected,
                            forceComplete = forceComplete,
                            forceCompleteReason = forceReason?.trim()?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    if (res.data.awaitingCashConfirm) {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                isAwaitingCashConfirm = true,
                                cashConfirmSecondsLeft = 45,
                                toastMessage = "Awaiting passenger confirmation of unpaid balance on their phone (45s)."
                            )
                        }
                    } else {
                        val finalized = applyCompleteResponse(ride, res.data)
                        finishIntoReview(finalized, "Cash payment recorded — trip completed!")
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
                }
            }
        }
    }

    fun completeTripWithAction(
        action: String,
        cashCollected: Double? = null,
        earlyReason: String? = null
    ) {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        val totalWait = state.totalPaidWaitSeconds
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.completeRide(
                        RideCompleteRequest(
                            rideId = ride.id,
                            action = action,
                            paidWaitSeconds = totalWait,
                            cashCollected = cashCollected,
                            earlyCompletionReason = earlyReason,
                            paymentUrl = state.paymentQrUrl
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    val finalized = applyCompleteResponse(ride, res.data)
                    finishIntoReview(finalized, "Trip completed!")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
                }
            }
        }
    }

    private fun applyCompleteResponse(ride: RideRequest, data: com.example.data.network.CompleteRideResponse): RideRequest {
        return ride.copy(
            status = data.status ?: ride.status,
            finalFare = data.finalFare ?: ride.finalFare,
            tipAmount = data.tipAmount ?: ride.tipAmount,
            totalFarePaid = data.totalFarePaid ?: ride.totalFarePaid,
            driverEarnings = data.driverEarnings ?: ride.driverEarnings,
            platformFee = data.platformFee ?: ride.platformFee,
            fareContractVersion = "v2_excl_tip"
        )
    }

    private fun finishIntoReview(finalizedRide: RideRequest, toast: String) {
        val state = _uiState.value
        val shouldGoOffline = state.profile.lastJob && state.queuedStackedRide == null
        if (shouldGoOffline) {
            DriverLocationService.stop(context)
            viewModelScope.launch(Dispatchers.IO) {
                prefsStore.setOnlineFlag(false)
                repository.markDriverOfflineInFirestore(state.uid)
            }
        } else if (state.queuedStackedRide == null && state.uid.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.markDriverIdleStatus(state.uid)
            }
        }
        resetPerRideTransientState()
        lastActiveRideId = null
        lastTrackedActiveRide = null
        _uiState.update {
            it.copy(
                isBusy = false,
                reviewRide = finalizedRide,
                paymentQrUrl = null,
                isAwaitingCashConfirm = false,
                cashConfirmSecondsLeft = 0,
                rideState = TerminalRideState.REVIEW,
                isOnline = if (shouldGoOffline) false else it.isOnline,
                toastMessage = toast
            )
        }
        refreshFinancials()
    }

    fun cancelActiveRide() {
        val ride = _uiState.value.activeRide ?: return
        val uid = _uiState.value.uid
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.cancelRide(RideCancelRequest(rideId = ride.id, cancelledBy = "driver"))
                }
            ) {
                is ApiResult.Success -> {
                    resetPerRideTransientState()
                    lastActiveRideId = null
                    lastTrackedActiveRide = null
                    if (uid.isNotBlank()) {
                        repository.markDriverIdleStatus(uid)
                    }
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            activeRide = null,
                            rideState = TerminalRideState.IDLE,
                            isAwaitingCashConfirm = false,
                            lastRideUpdateNotice = null,
                            toastMessage = "Trip cancelled."
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
                }
            }
        }
    }

    fun submitPassengerReview(stars: Int, comment: String) {
        val state = _uiState.value
        val ride = state.reviewRide ?: run {
            _uiState.update { it.copy(rideState = TerminalRideState.IDLE, reviewRide = null) }
            return
        }
        if (stars < 5 && comment.trim().length < 3) {
            _uiState.update { it.copy(toastMessage = "Please enter a brief note when rating under 5 stars.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            if (ride.passengerId.isNotBlank()) {
                ApiClient.safeCall {
                    ApiClient.service.ratePassenger(
                        RatePassengerRequest(
                            rideId = ride.id,
                            rating = stars.coerceIn(1, 5),
                            comment = comment.trim().takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            val nextQueued = _uiState.value.queuedStackedRide ?: _uiState.value.activeRide?.takeIf { it.id != ride.id }
            if (nextQueued != null && state.uid.isNotBlank()) {
                repository.activateQueuedRideInLiveTracking(
                    uid = state.uid,
                    rideId = nextQueued.id,
                    passengerId = nextQueued.passengerId.takeIf { it.isNotBlank() },
                    dropoffLat = nextQueued.dropoffLat,
                    dropoffLng = nextQueued.dropoffLng,
                    isStackingEnabled = state.profile.stackingEnabled
                )
            }
            val stackedNavEvent = if (nextQueued != null &&
                _uiState.value.cockpitSettings.autoLaunchExternalNav &&
                (nextQueued.pickupLat != 0.0 || nextQueued.pickupLng != 0.0)
            ) {
                ExternalNavLaunchEvent(
                    id = System.currentTimeMillis(),
                    lat = nextQueued.pickupLat,
                    lng = nextQueued.pickupLng
                )
            } else null
            _uiState.update {
                it.copy(
                    isBusy = false,
                    reviewRide = null,
                    paymentQrUrl = null,
                    activeRide = nextQueued,
                    queuedStackedRide = null,
                    rideState = if (nextQueued != null) TerminalRideState.EN_ROUTE_PICKUP else TerminalRideState.IDLE,
                    pendingExternalNav = stackedNavEvent ?: it.pendingExternalNav,
                    toastMessage = if (nextQueued != null) {
                        "Starting your next queued trip!"
                    } else {
                        "Thanks for rating your passenger!"
                    }
                )
            }
        }
    }

    // --- Road Hazards ---

    fun reportHazard(type: String) {
        val state = _uiState.value
        if (state.uid.isBlank()) return
        viewModelScope.launch {
            repository.reportRoadHazard(
                reporterId = state.uid,
                type = type,
                lat = state.driverLocation.lat,
                lng = state.driverLocation.lng
            )
            _uiState.update {
                it.copy(
                    showHazardModal = false,
                    toastMessage = "$type reported to nearby AnyRoller drivers."
                )
            }
        }
    }

    // --- Ride Chat, 45s Presence Heartbeat & Safety ---

    fun sendChatMessage(text: String, imageUri: Uri? = null) {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        if (text.isBlank() && imageUri == null) return
        viewModelScope.launch {
            var uploadedUrl: String? = null
            if (imageUri != null) {
                val up = repository.uploadRideChatMedia(ride.id, imageUri)
                uploadedUrl = up.getOrElse { err ->
                    _uiState.update { it.copy(toastMessage = err.message ?: "Photo upload failed.") }
                    return@launch
                }
            }
            repository.sendRideChatMessage(
                rideId = ride.id,
                senderId = state.uid,
                text = text.takeIf { it.isNotBlank() },
                imageUrl = uploadedUrl
            )
            if (ride.passengerId.isNotBlank()) {
                ApiClient.safeCall {
                    ApiClient.service.sendChatPush(
                        ChatPushRequest(
                            rideId = ride.id,
                            recipientId = ride.passengerId,
                            title = "Message from ${state.profile.fullName}",
                            message = text.ifBlank { "Sent a photo" },
                            type = "chat"
                        )
                    )
                }
            }
        }
    }

    fun sendQuickChatMessage(text: String) {
        val state = _uiState.value
        if (state.quickMessageCooldownSeconds > 0 || text.isBlank()) return
        _uiState.update { it.copy(quickMessageCooldownSeconds = 120) }
        sendChatMessage(text)
    }

    private fun startChatPresence(rideId: String) {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                ApiClient.safeCall {
                    ApiClient.service.sendPresenceHeartbeat(
                        PresenceHeartbeatRequest(activeConversationId = rideId, markRead = true)
                    )
                }
                delay(45_000L)
            }
        }
    }

    fun stopChatPresence() {
        presenceJob?.cancel()
        presenceJob = null
        viewModelScope.launch(Dispatchers.IO) {
            ApiClient.safeCall {
                ApiClient.service.sendPresenceHeartbeat(
                    PresenceHeartbeatRequest(activeConversationId = null, markRead = false)
                )
            }
        }
    }

    private fun loadChatBlocks() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = ApiClient.safeCall { ApiClient.service.getChatBlocks() }) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(blockedUserIds = res.data.blockedUserIds.toSet()) }
                }
                else -> Unit
            }
        }
    }

    fun reportAndBlockActivePassenger(reason: String, blockUser: Boolean) {
        val ride = _uiState.value.activeRide ?: return
        if (ride.passengerId.isBlank()) return
        viewModelScope.launch {
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.reportAndBlockUser(
                        ReportBlockRequest(
                            targetUserId = ride.passengerId,
                            rideId = ride.id,
                            reason = reason,
                            blockUser = blockUser
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    if (blockUser) {
                        _uiState.update {
                            it.copy(
                                blockedUserIds = it.blockedUserIds + ride.passengerId,
                                toastMessage = "Safety report submitted and user blocked."
                            )
                        }
                    } else {
                        _uiState.update { it.copy(toastMessage = "Safety report submitted to AnyRoller team.") }
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(toastMessage = res.message) }
            }
        }
    }

    // --- Stripe Connect, Fee Settlement, Gold Subscription & Expenses ---

    fun refreshFinancials() {
        val uid = _uiState.value.uid.ifBlank { return }
        viewModelScope.launch(Dispatchers.IO) {
            when (val bal = ApiClient.safeCall { ApiClient.service.getStripeBalance(uid) }) {
                is ApiResult.Success -> _uiState.update { it.copy(stripeBalance = bal.data) }
                else -> Unit
            }
            when (val status = ApiClient.safeCall { ApiClient.service.getStripeAccountStatus() }) {
                is ApiResult.Success -> _uiState.update { it.copy(stripeAccountStatus = status.data) }
                else -> Unit
            }
            when (val fees = ApiClient.safeCall { ApiClient.service.getFeeSettlementStatus() }) {
                is ApiResult.Success -> _uiState.update { it.copy(feeStatus = fees.data) }
                else -> Unit
            }
        }
    }

    fun requestStripePayout() {
        val uid = _uiState.value.uid.ifBlank { return }
        val key = "drv-payout-$uid-${UUID.randomUUID()}"
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.requestStripePayout(
                        driverId = uid,
                        idempotencyKey = key,
                        body = StripePayoutRequest(idempotencyKey = key)
                    )
                }
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isBusy = false, toastMessage = res.data.message ?: "Payout requested successfully!")
                    }
                    refreshFinancials()
                }
                is ApiResult.Error -> _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
            }
        }
    }

    fun launchStripeConnectOnboarding() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (val res = ApiClient.safeCall { ApiClient.service.createConnectAccount() }) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            customTabUrlToOpen = res.data.url,
                            toastMessage = if (res.data.url.isNullOrBlank()) "Unable to create onboarding link." else null
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
            }
        }
    }

    fun launchStripeExpressDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (val res = ApiClient.safeCall { ApiClient.service.createStripeLoginLink() }) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            customTabUrlToOpen = res.data.url,
                            toastMessage = if (res.data.url.isNullOrBlank()) "Stripe dashboard unavailable." else null
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
            }
        }
    }

    fun launchSettlePlatformFees() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (val res = ApiClient.safeCall { ApiClient.service.settleFees() }) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            customTabUrlToOpen = res.data.url,
                            toastMessage = if (res.data.url.isNullOrBlank()) "No settlement checkout URL returned." else null
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
            }
        }
    }

    fun launchGoldDriverUpgrade() {
        val uid = _uiState.value.uid.ifBlank { return }
        val base = ApiClient.resolvedBaseUrl.trimEnd('/')
        val key = "drv-gold-$uid-${UUID.randomUUID()}"
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.createCheckoutSession(
                        idempotencyKey = key,
                        body = CreateCheckoutSessionRequest(
                            userId = uid,
                            priceId = "price_driver_gold",
                            tierName = "Gold Driver Subscription",
                            mode = "subscription",
                            successUrl = "$base/driver-terminal?gold=success",
                            cancelUrl = "$base/driver-terminal?gold=cancel",
                            metadata = mapOf(
                                "subscriptionType" to "driver_gold",
                                "type" to "driver_gold",
                                "userId" to uid
                            )
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            customTabUrlToOpen = res.data.url,
                            toastMessage = if (res.data.url.isNullOrBlank()) "Unable to open Gold Checkout." else null
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
            }
        }
    }

    fun launchBillingPortal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (val res = ApiClient.safeCall { ApiClient.service.createCustomerPortalSession() }) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isBusy = false, customTabUrlToOpen = res.data.url) }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
            }
        }
    }

    fun updateEarningsGoals(today: Double, week: Double, month: Double) {
        val uid = _uiState.value.uid.ifBlank { return }
        viewModelScope.launch {
            repository.updateUserPreferences(
                uid,
                mapOf(
                    "earningsGoals" to mapOf(
                        "today" to today.coerceAtLeast(10.0),
                        "week" to week.coerceAtLeast(50.0),
                        "month" to month.coerceAtLeast(200.0)
                    )
                )
            )
            _uiState.update { it.copy(toastMessage = "Earnings targets updated.") }
        }
    }

    fun addExpense(
        amount: Double,
        category: String,
        notes: String,
        receiptUri: Uri?,
        date: String? = null
    ) {
        val uid = _uiState.value.uid.ifBlank { return }
        if (amount <= 0.0) {
            _uiState.update { it.copy(toastMessage = "Please enter a valid expense amount.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            var receiptUrl: String? = null
            if (receiptUri != null) {
                val up = repository.uploadVerificationDoc(uid, "expense_receipt", receiptUri)
                receiptUrl = up.getOrElse { err ->
                    _uiState.update { it.copy(isBusy = false, toastMessage = err.message) }
                    return@launch
                }
            }
            val dateStr = date?.trim()?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) }
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
            repository.addDriverExpense(uid, amount, category, dateStr, notes, receiptUrl)
            _uiState.update { it.copy(isBusy = false, toastMessage = "Expense logged.") }
        }
    }

    fun updateExpense(
        expenseId: String,
        amount: Double,
        category: String,
        date: String,
        notes: String,
        newReceiptUri: Uri?,
        existingReceiptUrl: String?
    ) {
        val uid = _uiState.value.uid.ifBlank { return }
        if (amount <= 0.0) {
            _uiState.update { it.copy(toastMessage = "Please enter a valid expense amount.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            var receiptUrl = existingReceiptUrl
            if (newReceiptUri != null) {
                val up = repository.uploadVerificationDoc(uid, "expense_receipt", newReceiptUri)
                receiptUrl = up.getOrElse { err ->
                    _uiState.update { it.copy(isBusy = false, toastMessage = err.message) }
                    return@launch
                }
            }
            val dateStr = date.trim().takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) }
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
            val res = repository.updateDriverExpense(expenseId, amount, category, dateStr, notes, receiptUrl)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    toastMessage = if (res.isSuccess) "Expense updated." else (res.exceptionOrNull()?.message ?: "Unable to update expense.")
                )
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteDriverExpense(expenseId)
            _uiState.update { it.copy(toastMessage = "Expense deleted.") }
        }
    }

    // --- Inbox ---

    fun markNotificationRead(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ApiClient.safeCall { ApiClient.service.markNotificationRead(id) }
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch {
            when (val res = ApiClient.safeCall { ApiClient.service.deleteNotification(id) }) {
                is ApiResult.Success -> _uiState.update { it.copy(toastMessage = "Notification deleted.") }
                is ApiResult.Error -> _uiState.update { it.copy(toastMessage = res.message) }
            }
        }
    }

    // --- Driver Menu Preferences & 4-Tier WhatsApp Offline Fallback ---

    fun updateUserPref(key: String, value: Any?) {
        val state = _uiState.value
        val uid = state.uid.ifBlank { return }
        viewModelScope.launch {
            val res = repository.updateUserPreferences(uid, mapOf(key to value))
            if (res.isFailure) {
                _uiState.update { it.copy(toastMessage = res.exceptionOrNull()?.message) }
            } else if (key in setOf("isStackingEnabled", "isLastJob", "destinationModeActive", "zoneEnabled", "zoneMaxDistance")) {
                val p = state.profile
                repository.syncLiveTrackingDispatchPrefs(
                    uid = uid,
                    isStackingEnabled = if (key == "isStackingEnabled") (value as? Boolean ?: p.stackingEnabled) else p.stackingEnabled,
                    isLastJob = if (key == "isLastJob") (value as? Boolean ?: p.lastJob) else p.lastJob,
                    destinationModeActive = if (key == "destinationModeActive") (value as? Boolean ?: p.destinationModeActive) else p.destinationModeActive,
                    homeLat = p.homeLat,
                    homeLng = p.homeLng,
                    zoneEnabled = if (key == "zoneEnabled") (value as? Boolean ?: p.zoneEnabled) else p.zoneEnabled,
                    zoneMaxDistance = if (key == "zoneMaxDistance") ((value as? Number)?.toDouble() ?: p.zoneMaxDistance) else p.zoneMaxDistance
                )
            }
        }
    }

    fun saveHomeDestination(address: String, fallbackLat: Double?, fallbackLng: Double?) {
        val state = _uiState.value
        val uid = state.uid.ifBlank { return }
        val trimmed = address.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val geocoded = GeocoderHelper.geocodeUkAddress(context, trimmed)
            val finalAddress = geocoded?.formattedAddress ?: trimmed
            val finalLat = geocoded?.lat ?: fallbackLat
            val finalLng = geocoded?.lng ?: fallbackLng
            repository.updateUserPreferences(
                uid,
                mapOf(
                    "homeAddress" to finalAddress,
                    "homeLat" to finalLat,
                    "homeLng" to finalLng
                )
            )
            val p = state.profile
            repository.syncLiveTrackingDispatchPrefs(
                uid = uid,
                isStackingEnabled = p.stackingEnabled,
                isLastJob = p.lastJob,
                destinationModeActive = p.destinationModeActive,
                homeLat = finalLat,
                homeLng = finalLng,
                zoneEnabled = p.zoneEnabled,
                zoneMaxDistance = p.zoneMaxDistance
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    toastMessage = if (geocoded != null) {
                        "Home address verified: $finalAddress"
                    } else {
                        "Home destination saved."
                    }
                )
            }
        }
    }

    /**
     * CRITICAL RULE: `vehicleCategories` and `isPetFriendly` are NOT in `users/{uid}` update allowlist;
     * write ONLY to `live_tracking/{uid}` (with `SetOptions.merge()`) + local DataStore.
     */
    fun toggleVehicleCategory(category: String) {
        val uid = _uiState.value.uid.ifBlank { return }
        val current = _uiState.value.localVehicleCategories.toMutableSet()
        if (category in current && current.size > 1) {
            current.remove(category)
        } else {
            current.add(category)
        }
        viewModelScope.launch {
            prefsStore.setLocalVehicleCategories(current)
            repository.mergeLiveTrackingCapabilities(uid, current.toList(), _uiState.value.localPetFriendly)
        }
    }

    fun togglePetFriendly(enabled: Boolean) {
        val uid = _uiState.value.uid.ifBlank { return }
        viewModelScope.launch {
            prefsStore.setLocalPetFriendly(enabled)
            repository.mergeLiveTrackingCapabilities(
                uid,
                _uiState.value.localVehicleCategories.toList(),
                enabled
            )
        }
    }

    fun saveWeeklySchedule(schedule: Map<String, DaySchedule>) {
        val uid = _uiState.value.uid.ifBlank { return }
        val serialized = schedule.mapValues { (_, day) ->
            mapOf(
                "status" to day.status,
                "startTime" to (day.startTime ?: "08:00"),
                "endTime" to (day.endTime ?: "18:00")
            )
        }
        viewModelScope.launch {
            repository.updateUserPreferences(uid, mapOf("weeklySchedule" to serialized))
            _uiState.update { it.copy(toastMessage = "Weekly availability saved.") }
        }
    }

    fun loadNotificationPreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = ApiClient.safeCall { ApiClient.service.getNotificationPreferences() }) {
                is ApiResult.Success -> {
                    res.data.preferences?.let { prefs ->
                        _uiState.update { it.copy(notificationPrefs = prefs) }
                    }
                }
                else -> Unit
            }
        }
    }

    fun updateWhatsAppSettings(phone: String?, optIn: Boolean?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.updateNotificationPreferences(
                        UpdateNotificationPreferencesRequest(
                            whatsappPhone = phone?.takeIf { it.isNotBlank() },
                            whatsappOptIn = optIn
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    res.data.preferences?.let { prefs ->
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                notificationPrefs = prefs,
                                toastMessage = "Offline WhatsApp alert preferences saved."
                            )
                        }
                    } ?: _uiState.update { it.copy(isBusy = false) }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
            }
        }
    }

    fun activateWhatsAppFreeWindow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.createWhatsAppActivationLink(
                        WhatsAppActivationLinkRequest(contextLabel = "AnyRoller Driver Alerts")
                    )
                }
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            whatsAppActivation = res.data,
                            whatsAppUrlToOpen = res.data.waMeUrl
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
            }
        }
    }

    // --- Documents, Avatar, Demand Zones & Analytics ---

    fun uploadDriverAvatar(uri: Uri) {
        val uid = _uiState.value.uid.ifBlank { return }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val uploaded = repository.uploadAvatar(uid, uri)
            uploaded.onSuccess { url ->
                repository.updateUserPreferences(uid, mapOf("photoURL" to url))
                _uiState.update { it.copy(isBusy = false, toastMessage = "Profile photo updated.") }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false, toastMessage = err.message) }
            }
        }
    }

    fun uploadVerificationDocument(docType: String, expiryDate: String, uri: Uri) {
        val state = _uiState.value
        val uid = state.uid.ifBlank { return }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val uploaded = repository.uploadVerificationDoc(uid, docType, uri)
            uploaded.onSuccess { url ->
                val updatedList = state.profile.verificationDocs
                    .filterNot { it.type == docType }
                    .map {
                        mapOf(
                            "type" to it.type,
                            "status" to it.status,
                            "fileUrl" to it.fileUrl,
                            "expiryDate" to it.expiryDate,
                            "rejectionReason" to it.rejectionReason
                        )
                    } + mapOf(
                    "type" to docType,
                    "status" to "pending",
                    "fileUrl" to url,
                    "expiryDate" to expiryDate.trim(),
                    "rejectionReason" to null
                )
                repository.updateUserPreferences(uid, mapOf("verificationDocs" to updatedList))
                _uiState.update { it.copy(isBusy = false, toastMessage = "Document submitted for verification.") }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false, toastMessage = err.message) }
            }
        }
    }

    fun refreshDemandZones() {
        val loc = _uiState.value.driverLocation
        val cfg = _uiState.value.platformConfig
        if (!cfg.surgeEnabled) {
            _uiState.update { it.copy(demandZones = emptyList()) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = ApiClient.safeCall { ApiClient.service.getDemandZones(loc.lat, loc.lng) }) {
                is ApiResult.Success -> {
                    val mapped = res.data.zones.mapIndexedNotNull { idx, dto ->
                        val lat = dto.lat ?: return@mapIndexedNotNull null
                        val lng = dto.lng ?: return@mapIndexedNotNull null
                        val mult = dto.surgeMultiplier ?: dto.multiplier ?: 1.0
                        val extraFee = dto.extraFee ?: dto.surgeFee ?: 0.0
                        val isFixed = dto.isFixedModel ?: (cfg.surgeModel == "fixed")
                        val computedLabel = dto.label ?: if (isFixed) {
                            "+£%.2f Surge".format(extraFee)
                        } else {
                            "%.1fx Surge".format(mult)
                        }
                        DemandZone(
                            id = dto.id ?: "zone_$idx",
                            name = dto.name ?: dto.area ?: computedLabel,
                            label = computedLabel,
                            lat = lat,
                            lng = lng,
                            level = (dto.intensity ?: dto.level ?: "medium").lowercase(),
                            multiplier = mult,
                            surgeFee = extraFee,
                            isFixedModel = isFixed,
                            radiusMeters = dto.radius ?: dto.radiusMeters ?: 800.0,
                            uiColor = dto.uiColor ?: "amber",
                            uiOpacity = (dto.uiOpacity ?: cfg.surgeRules.surgeOpacity).coerceIn(10, 90),
                            waitWarning = dto.waitWarning ?: false
                        )
                    }
                    _uiState.update { it.copy(demandZones = mapped) }
                }
                else -> Unit
            }
        }
    }

    fun fetchAnalyticsPulse() {
        val p = _uiState.value.profile
        viewModelScope.launch {
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.getAnalyticsPulse(
                        AnalyticsPulseRequest(
                            driverStats = DriverStatsPayload(
                                rating = p.rating,
                                acceptance = p.acceptanceRate,
                                cancellation = p.cancellationRate,
                                responseTime = p.avgResponseSeconds
                            )
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            analyticsInsight = res.data.insight ?: res.data.summary,
                            analyticsTips = res.data.tips.orEmpty()
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    override fun onCleared() {
        unbindAll()
        floatingOverlay.destroy()
        audioFeedback.release()
        super.onCleared()
    }
}
