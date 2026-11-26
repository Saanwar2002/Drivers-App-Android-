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
import com.example.data.network.ReportBlockRequest
import com.example.data.network.RideAcceptRequest
import com.example.data.network.RideCancelRequest
import com.example.data.network.RideCompleteRequest
import com.example.data.network.RideTransitionRequest
import com.example.data.network.StripeAccountStatusResponse
import com.example.data.network.StripeBalanceResponse
import com.example.data.network.StripePayoutRequest
import com.example.data.network.UpdateNotificationPreferencesRequest
import com.example.data.network.WhatsAppActivationLinkRequest
import com.example.data.network.WhatsAppActivationLinkResponse
import com.example.data.repository.ActiveRouteResult
import com.example.data.repository.DirectionsRepository
import com.example.data.repository.DriverFirestoreRepository
import com.example.data.repository.DriverPreferencesStore
import com.example.service.DriverAudioFeedback
import com.example.service.DriverLocationService
import com.example.service.DriverLocationSnapshot
import com.example.service.ExternalIntentHelpers
import com.example.service.GeocoderHelper
import com.example.service.NetworkMonitor
import com.example.service.UrgentPushEvent
import com.example.service.UrgentPushEventBus
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
    val isNetworkConnected: Boolean = true,
    val globalPlatformStatus: GlobalPlatformStatus = GlobalPlatformStatus(),
    val isBusy: Boolean = false,
    val toastMessage: String? = null,
    val customTabUrlToOpen: String? = null,
    val whatsAppUrlToOpen: String? = null
) {
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
}

class DriverTerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val repository = DriverFirestoreRepository(context)
    private val prefsStore = DriverPreferencesStore(context)
    private val directionsRepo = DirectionsRepository()
    private val audioFeedback = DriverAudioFeedback(context)
    private val networkMonitor = NetworkMonitor(context)

    private val _uiState = MutableStateFlow(DriverTerminalUiState())
    val uiState: StateFlow<DriverTerminalUiState> = _uiState.asStateFlow()

    private var boundUid: String? = null
    private var bindingJobs = mutableListOf<Job>()
    private var chatJob: Job? = null
    private var activeChatRideId: String? = null
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
    private var declinedOfferIds: Set<String> = emptySet()
    private var lastAlertedOfferId: String? = null
    private var lastRouteLegKey: String? = null
    private var routeRecalcCount: Int = 0
    private var lastRouteFetchLat: Double = 0.0
    private var lastRouteFetchLng: Double = 0.0

    init {
        startOneSecondTicker()
        observeSharedLocationAndPushEvents()
        viewModelScope.launch {
            networkMonitor.observeIsOnline().collect { online ->
                _uiState.update { it.copy(isNetworkConnected = online) }
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
        presenceJob?.cancel()
        boundUid = null
    }

    private fun observeSharedLocationAndPushEvents() {
        viewModelScope.launch {
            UrgentPushEventBus.lastLocation.collect { snap ->
                _uiState.update { it.copy(driverLocation = snap) }
                evaluateLocationGeofences(snap)
            }
        }
        viewModelScope.launch {
            UrgentPushEventBus.events.collect { event ->
                when (event) {
                    is UrgentPushEvent.RideOffer -> {
                        val offer = event.offer
                        if (offer.rideId !in declinedOfferIds && _uiState.value.isOnline) {
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
                        _uiState.update { state ->
                            val isActiveCancelled = state.activeRide?.id == event.rideId
                            val isOfferCancelled = state.incomingOffer?.id == event.rideId
                            val isStackedCancelled = state.stackedRideOffer?.id == event.rideId
                            state.copy(
                                activeRide = if (isActiveCancelled) null else state.activeRide,
                                incomingOffer = if (isOfferCancelled) null else state.incomingOffer,
                                stackedRideOffer = if (isStackedCancelled) null else state.stackedRideOffer,
                                rideState = if (isActiveCancelled || isOfferCancelled) TerminalRideState.IDLE else state.rideState,
                                toastMessage = if (isActiveCancelled) "Ride was cancelled by the passenger." else state.toastMessage
                            )
                        }
                    }
                    is UrgentPushEvent.RideAcceptedElsewhere -> {
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
    }

    private fun startOneSecondTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                var triggerMissedOfferTimeout = false
                var triggerAutoArrive = false
                var expiredOfferId: String? = null

                _uiState.update { state ->
                    var next = state
                    if (state.incomingOffer != null && state.rideState == TerminalRideState.OFFER) {
                        val secs = ((state.incomingOffer.expiresAtMs - now) / 1000L).toInt()
                        if (secs <= 0) {
                            expiredOfferId = state.incomingOffer.id
                            triggerMissedOfferTimeout = true
                            next = next.copy(incomingOffer = null, rideState = TerminalRideState.IDLE, offerSecondsLeft = 0)
                        } else {
                            next = next.copy(offerSecondsLeft = secs)
                        }
                    }
                    if (state.stackedRideOffer != null) {
                        val secs = ((state.stackedRideOffer.expiresAtMs - now) / 1000L).toInt()
                        next = if (secs <= 0) {
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

        // Detect mid-trip passenger modifications (extra stops, new destination, fare/tip change)
        var midTripNotice: String? = currentState.lastRideUpdateNotice
        if (currentActive != null && lastTrackedActiveRide != null && lastTrackedActiveRide!!.id == currentActive.id) {
            val prev = lastTrackedActiveRide!!
            val stopsChanged = prev.stops != currentActive.stops
            val dropoffChanged = prev.dropoffAddress != currentActive.dropoffAddress ||
                prev.dropoffLat != currentActive.dropoffLat ||
                prev.dropoffLng != currentActive.dropoffLng
            val pickupChanged = prev.pickupAddress != currentActive.pickupAddress
            val fareChanged = kotlin.math.abs(prev.fareEstimate - currentActive.fareEstimate) >= 0.05
            val tipChanged = currentActive.tipAmount > prev.tipAmount + 0.01

            if (stopsChanged || dropoffChanged || pickupChanged || fareChanged || currentActive.isModifiedByPassenger) {
                audioFeedback.playRideUpdatedAlert(currentState.profile.muteRideOfferAlerts)
                ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 180, 100, 180))
                audioFeedback.speakHeadsUp("Job details updated by passenger", currentState.profile.muteHeadsUpVolume)

                val summaryParts = mutableListOf<String>()
                if (stopsChanged) summaryParts += "${currentActive.stops.size} stop(s)"
                if (dropoffChanged) summaryParts += "new dropoff"
                if (fareChanged) summaryParts += "fare £%.2f".format(currentActive.fareEstimate)
                val noticeText = if (summaryParts.isNotEmpty()) {
                    "Passenger updated trip: ${summaryParts.joinToString(" • ")}. Restart external nav if open."
                } else {
                    "Passenger updated journey details. Check updated route & fare."
                }
                midTripNotice = noticeText

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

        // Bind chat stream to active ride if changed
        if (currentActive?.id != activeChatRideId) {
            chatJob?.cancel()
            activeChatRideId = currentActive?.id
            if (currentActive != null) {
                chatJob = viewModelScope.launch {
                    repository.observeRideChat(currentActive.id).collect { msgs ->
                        _uiState.update { it.copy(chatMessages = msgs) }
                    }
                }
            } else {
                _uiState.update { it.copy(chatMessages = emptyList(), showChatSheet = false) }
            }
        }

        val latestState = _uiState.value
        val validPending = pendingOffers
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

        val derivedRideState = when {
            latestState.rideState == TerminalRideState.REVIEW && latestState.reviewRide != null ->
                TerminalRideState.REVIEW
            currentActive != null -> when (currentActive.status.lowercase()) {
                "arrived", "waiting" -> TerminalRideState.WAITING
                "in_progress" -> TerminalRideState.IN_PROGRESS
                "awaiting_payment", "payment_pending", "awaiting_cash_confirm", "cash_confirmed" -> TerminalRideState.COMPLETED_PAYMENT
                else -> TerminalRideState.EN_ROUTE_PICKUP
            }
            latestState.isOnline && validPending != null -> TerminalRideState.OFFER
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

        // Play native chime + haptic + optional UK TTS heads-up when a brand-new offer arrives
        val newlyArrivedOffer = nextIncoming ?: nextStacked
        if (newlyArrivedOffer != null && newlyArrivedOffer.id != lastAlertedOfferId) {
            lastAlertedOfferId = newlyArrivedOffer.id
            audioFeedback.playRideOfferChime(latestState.profile.muteRideOfferAlerts)
            ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 200, 100, 200, 100, 450))
            audioFeedback.speakHeadsUp(
                "New ride offer for £%.2f".format(newlyArrivedOffer.fareEstimate),
                latestState.profile.muteHeadsUpVolume
            )
        } else if (newlyArrivedOffer == null) {
            lastAlertedOfferId = null
        }

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
                toastMessage = if (midTripNotice != null && midTripNotice != st.lastRideUpdateNotice) midTripNotice else st.toastMessage
            )
        }

        refreshActiveRouteIfNeeded(currentActive, derivedRideState)
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
    }

    private fun refreshActiveRouteIfNeeded(ride: RideRequest?, state: TerminalRideState) {
        if (ride == null || state !in setOf(
                TerminalRideState.EN_ROUTE_PICKUP,
                TerminalRideState.WAITING,
                TerminalRideState.IN_PROGRESS
            )
        ) {
            lastRouteLegKey = null
            routeRecalcCount = 0
            if (_uiState.value.activeRoute.points.isNotEmpty()) {
                _uiState.update { it.copy(activeRoute = ActiveRouteResult()) }
            }
            return
        }

        val (destLat, destLng) = if (state == TerminalRideState.IN_PROGRESS) {
            val stop = ride.stops.getOrNull(ride.currentStopIndex)
            if (stop != null && (stop.lat != 0.0 || stop.lng != 0.0)) {
                stop.lat to stop.lng
            } else {
                ride.dropoffLat to ride.dropoffLng
            }
        } else {
            ride.pickupLat to ride.pickupLng
        }

        val legKey = "${ride.id}:${state.name}:${ride.currentStopIndex}:$destLat:$destLng"
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
        } else if (movedMeters[0] < 50f || routeRecalcCount >= 3) {
            return
        } else {
            routeRecalcCount++
        }

        lastRouteFetchLat = loc.lat
        lastRouteFetchLng = loc.lng
        viewModelScope.launch {
            val route = directionsRepo.fetchRoute(loc.lat, loc.lng, destLat, destLng)
            _uiState.update { it.copy(activeRoute = route) }
            route.nextManeuver?.let { maneuver ->
                if (state == TerminalRideState.EN_ROUTE_PICKUP || state == TerminalRideState.IN_PROGRESS) {
                    audioFeedback.speakHeadsUp(maneuver, _uiState.value.profile.muteHeadsUpVolume)
                }
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
        _uiState.update { it.copy(showChatSheet = visible) }
        val rideId = _uiState.value.activeRide?.id
        if (visible && rideId != null) {
            startChatPresence(rideId)
        } else {
            stopChatPresence()
        }
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
            DriverLocationService.start(context, state.profile.fullName, state.activeRide != null)
            ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 80))
            _uiState.update { it.copy(isOnline = true, toastMessage = "You are now ONLINE — waiting for jobs.") }
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
            DriverLocationService.stop(context)
            _uiState.update {
                it.copy(
                    isOnline = false,
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

    // --- Server-Authoritative Ride Lifecycle ---

    fun acceptOffer(isStacked: Boolean = false) {
        val offer = if (isStacked) _uiState.value.stackedRideOffer else _uiState.value.incomingOffer
        if (offer == null) return
        consecutiveMissedOffers = 0
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (val res = ApiClient.safeCall { ApiClient.service.acceptRide(RideAcceptRequest(offer.id)) }) {
                is ApiResult.Success -> {
                    ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 70))
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            incomingOffer = if (!isStacked) null else it.incomingOffer,
                            stackedRideOffer = if (isStacked) null else it.stackedRideOffer,
                            toastMessage = if (isStacked) "Next ride queued! Finish current trip first." else "Ride accepted! Navigating to pickup."
                        )
                    }
                }
                is ApiResult.Error -> {
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
        val offer = if (isStacked) _uiState.value.stackedRideOffer else _uiState.value.incomingOffer
        if (offer == null) return
        consecutiveMissedOffers = 0
        val uid = _uiState.value.uid
        viewModelScope.launch {
            prefsStore.addDeclinedOfferId(offer.id)
            if (uid.isNotBlank()) {
                repository.recordDriverDeclineStatus(uid)
            }
        }
        _uiState.update {
            if (isStacked) {
                it.copy(stackedRideOffer = null, toastMessage = "Queued ride declined.")
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
                    waitingStartedAtMs = System.currentTimeMillis()
                    ExternalIntentHelpers.vibratePattern(context, longArrayOf(0, 120, 60, 120))
                    // Trigger fast 5-second critical offline WhatsApp fallback on rider arrival!
                    if (ride.passengerId.isNotBlank()) {
                        ApiClient.safeCall {
                            ApiClient.service.sendChatPush(
                                ChatPushRequest(
                                    rideId = ride.id,
                                    recipientId = ride.passengerId,
                                    title = "Your AnyRoller driver has arrived",
                                    message = "${_uiState.value.profile.fullName} is waiting at pickup.",
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
                            toastMessage = if (isAuto) "Auto-marked arrived at pickup. Passenger notified." else "Marked arrived. Passenger notified."
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
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isWaitingAtStop = false,
                            accumulatedPaidWaitSeconds = it.accumulatedPaidWaitSeconds + addedStopWait,
                            currentStopWaitSeconds = 0,
                            showLeaveStopReminder = false,
                            toastMessage = "Stop completed — heading to next point."
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
                repository.submitPassengerRating(
                    rideId = ride.id,
                    driverId = state.uid,
                    passengerId = ride.passengerId,
                    rating = stars,
                    comment = comment
                )
            }
            val nextQueued = _uiState.value.queuedStackedRide ?: _uiState.value.activeRide?.takeIf { it.id != ride.id }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    reviewRide = null,
                    paymentQrUrl = null,
                    activeRide = nextQueued,
                    queuedStackedRide = null,
                    rideState = if (nextQueued != null) TerminalRideState.EN_ROUTE_PICKUP else TerminalRideState.IDLE,
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

    fun addExpense(amount: Double, category: String, notes: String, receiptUri: Uri?) {
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
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
            repository.addDriverExpense(uid, amount, category, dateStr, notes, receiptUrl)
            _uiState.update { it.copy(isBusy = false, toastMessage = "Expense logged.") }
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
        audioFeedback.release()
        super.onCleared()
    }
}
