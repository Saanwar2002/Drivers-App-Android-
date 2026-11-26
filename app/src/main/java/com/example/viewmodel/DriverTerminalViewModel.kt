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
import com.example.data.repository.DriverFirestoreRepository
import com.example.data.repository.DriverPreferencesStore
import com.example.service.DriverLocationService
import com.example.service.DriverLocationSnapshot
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
    val incomingOffer: RideRequest? = null,
    val stackedRideOffer: RideRequest? = null,
    val reviewRide: RideRequest? = null,
    val completedRides: List<RideRequest> = emptyList(),
    val scheduledRides: List<RideRequest> = emptyList(),
    val offerSecondsLeft: Int = 15,
    val stackedOfferSecondsLeft: Int = 15,
    val waitSecondsElapsed: Int = 0,
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
    val isBusy: Boolean = false,
    val toastMessage: String? = null,
    val customTabUrlToOpen: String? = null,
    val whatsAppUrlToOpen: String? = null
) {
    val unreadNotificationsCount: Int
        get() = notifications.count { !it.read }

    val activeCommissionRate: Double
        get() = if (profile.isGoldDriver) platformConfig.goldCommissionRate else platformConfig.commissionRate

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

    private val _uiState = MutableStateFlow(DriverTerminalUiState())
    val uiState: StateFlow<DriverTerminalUiState> = _uiState.asStateFlow()

    private var boundUid: String? = null
    private var bindingJobs = mutableListOf<Job>()
    private var chatJob: Job? = null
    private var activeChatRideId: String? = null
    private var presenceJob: Job? = null
    private var waitingStartedAtMs: Long? = null
    private var declinedOfferIds: Set<String> = emptySet()

    init {
        startOneSecondTicker()
        observeSharedLocationAndPushEvents()
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
            repository.observePlatformRideConfig().collect { cfg ->
                _uiState.update { it.copy(platformConfig = cfg) }
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

    private fun startOneSecondTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                _uiState.update { state ->
                    var next = state
                    if (state.incomingOffer != null && state.rideState == TerminalRideState.OFFER) {
                        val secs = ((state.incomingOffer.expiresAtMs - now) / 1000L).toInt()
                        next = if (secs <= 0) {
                            next.copy(incomingOffer = null, rideState = TerminalRideState.IDLE, offerSecondsLeft = 0)
                        } else {
                            next.copy(offerSecondsLeft = secs)
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
                    next
                }
            }
        }
    }

    private fun reconcileRidesAndOffers(
        uid: String,
        driverRides: List<RideRequest>,
        pendingOffers: List<RideRequest>
    ) {
        val activeStatuses = setOf(
            "accepted", "en_route", "en_route_pickup", "arrived", "waiting", "in_progress", "awaiting_payment", "payment_pending"
        )
        val completedStatuses = setOf("completed", "paid", "rider_abandoned")

        val currentActive = driverRides.firstOrNull { it.status.lowercase() in activeStatuses }
        val completed = driverRides.filter { it.status.lowercase() in completedStatuses }
        val scheduled = driverRides.filter { !it.scheduledTime.isNullOrBlank() && it.status.lowercase() !in completedStatuses }

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

        val currentState = _uiState.value
        val validPending = pendingOffers.firstOrNull { offer ->
            offer.id !in declinedOfferIds &&
                (offer.assignedDriverId.isNullOrBlank() || offer.assignedDriverId == uid)
        }

        val derivedRideState = when {
            currentState.rideState == TerminalRideState.REVIEW && currentState.reviewRide != null ->
                TerminalRideState.REVIEW
            currentActive != null -> when (currentActive.status.lowercase()) {
                "arrived", "waiting" -> TerminalRideState.WAITING
                "in_progress" -> TerminalRideState.IN_PROGRESS
                "awaiting_payment", "payment_pending" -> TerminalRideState.COMPLETED_PAYMENT
                else -> TerminalRideState.EN_ROUTE_PICKUP
            }
            currentState.isOnline && validPending != null -> TerminalRideState.OFFER
            else -> TerminalRideState.IDLE
        }

        if (currentState.isOnline) {
            DriverLocationService.updateOnRide(
                context = context,
                driverName = currentState.profile.fullName,
                isOnRide = currentActive != null
            )
        }

        val stackedCandidate = if (
            currentActive != null &&
            derivedRideState == TerminalRideState.IN_PROGRESS &&
            currentState.isOnline &&
            currentState.profile.stackingEnabled &&
            !currentState.profile.lastJob &&
            validPending != null &&
            validPending.id != currentActive.id
        ) validPending else null

        _uiState.update { st ->
            st.copy(
                activeRide = currentActive,
                completedRides = completed,
                scheduledRides = scheduled,
                rideState = derivedRideState,
                incomingOffer = if (derivedRideState == TerminalRideState.OFFER) {
                    validPending?.let {
                        if (st.incomingOffer?.id == it.id) st.incomingOffer else it.copy(
                            expiresAtMs = System.currentTimeMillis() + 15_000L
                        )
                    }
                } else null,
                stackedRideOffer = stackedCandidate?.let {
                    if (st.stackedRideOffer?.id == it.id) st.stackedRideOffer else it.copy(
                        expiresAtMs = System.currentTimeMillis() + 15_000L
                    )
                },
                paymentQrUrl = currentActive?.paymentUrl ?: st.paymentQrUrl
            )
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
            DriverLocationService.start(context, state.profile.fullName, state.activeRide != null)
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
                    isPetFriendly = state.localPetFriendly
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
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (val res = ApiClient.safeCall { ApiClient.service.acceptRide(RideAcceptRequest(offer.id)) }) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            incomingOffer = if (!isStacked) null else it.incomingOffer,
                            stackedRideOffer = if (isStacked) null else it.stackedRideOffer,
                            toastMessage = if (isStacked) "Next ride queued!" else "Ride accepted! Navigating to pickup."
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
        viewModelScope.launch {
            prefsStore.addDeclinedOfferId(offer.id)
        }
        _uiState.update {
            if (isStacked) {
                it.copy(stackedRideOffer = null, toastMessage = "Queued ride declined.")
            } else {
                it.copy(incomingOffer = null, rideState = TerminalRideState.IDLE, toastMessage = "Ride offer declined.")
            }
        }
    }

    fun markArrivedAtPickup() {
        val ride = _uiState.value.activeRide ?: return
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
                            toastMessage = "Marked arrived. Passenger notified."
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
        val paidWait = (state.waitSecondsElapsed - state.platformConfig.freeWaitSeconds).coerceAtLeast(0)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.transitionRide(
                        RideTransitionRequest(
                            rideId = ride.id,
                            action = "in_progress",
                            paidWaitSeconds = paidWait
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            rideState = TerminalRideState.IN_PROGRESS,
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

    fun advanceMultiStop() {
        val ride = _uiState.value.activeRide ?: return
        val nextIndex = ride.currentStopIndex + 1
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
                    _uiState.update { it.copy(isBusy = false, toastMessage = "Stop completed — heading to next point.") }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
                }
            }
        }
    }

    fun initiatePaymentHandshake() {
        val state = _uiState.value
        val ride = state.activeRide ?: return
        val paidWait = (state.waitSecondsElapsed - state.platformConfig.freeWaitSeconds).coerceAtLeast(0)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            if (ride.paymentMethod.equals("cash", ignoreCase = true)) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        rideState = TerminalRideState.COMPLETED_PAYMENT
                    )
                }
                return@launch
            }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.createTripPayment(
                        CreateTripPaymentRequest(rideId = ride.id, paidWaitSeconds = paidWait)
                    )
                }
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            paymentQrUrl = res.data.url ?: ride.paymentUrl,
                            rideState = TerminalRideState.COMPLETED_PAYMENT
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            rideState = TerminalRideState.COMPLETED_PAYMENT,
                            toastMessage = res.message
                        )
                    }
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
        val paidWait = (state.waitSecondsElapsed - state.platformConfig.freeWaitSeconds).coerceAtLeast(0)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.completeRide(
                        RideCompleteRequest(
                            rideId = ride.id,
                            action = action,
                            paidWaitSeconds = paidWait,
                            cashCollected = cashCollected,
                            earlyCompletionReason = earlyReason,
                            paymentUrl = state.paymentQrUrl
                        )
                    )
                }
            ) {
                is ApiResult.Success -> {
                    if (state.profile.lastJob) {
                        DriverLocationService.stop(context)
                        repository.markDriverOfflineInFirestore(state.uid)
                    }
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            reviewRide = ride,
                            rideState = TerminalRideState.REVIEW,
                            isOnline = if (state.profile.lastJob) false else it.isOnline,
                            toastMessage = "Trip completed!"
                        )
                    }
                    refreshFinancials()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, toastMessage = res.message) }
                }
            }
        }
    }

    fun cancelActiveRide() {
        val ride = _uiState.value.activeRide ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            when (
                val res = ApiClient.safeCall {
                    ApiClient.service.cancelRide(RideCancelRequest(rideId = ride.id, cancelledBy = "driver"))
                }
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            activeRide = null,
                            rideState = TerminalRideState.IDLE,
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
            _uiState.update {
                it.copy(
                    isBusy = false,
                    reviewRide = null,
                    paymentQrUrl = null,
                    rideState = TerminalRideState.IDLE,
                    toastMessage = "Thanks for rating your passenger!"
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
        val uid = _uiState.value.uid.ifBlank { return }
        viewModelScope.launch {
            val res = repository.updateUserPreferences(uid, mapOf(key to value))
            if (res.isFailure) {
                _uiState.update { it.copy(toastMessage = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun saveHomeDestination(address: String, lat: Double?, lng: Double?) {
        val uid = _uiState.value.uid.ifBlank { return }
        viewModelScope.launch {
            repository.updateUserPreferences(
                uid,
                mapOf(
                    "homeAddress" to address.trim(),
                    "homeLat" to lat,
                    "homeLng" to lng
                )
            )
            _uiState.update { it.copy(toastMessage = "Home destination saved.") }
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
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = ApiClient.safeCall { ApiClient.service.getDemandZones(loc.lat, loc.lng) }) {
                is ApiResult.Success -> {
                    val mapped = res.data.zones.mapIndexedNotNull { idx, dto ->
                        val lat = dto.lat ?: return@mapIndexedNotNull null
                        val lng = dto.lng ?: return@mapIndexedNotNull null
                        DemandZone(
                            id = dto.id ?: "zone_$idx",
                            name = dto.name ?: dto.area ?: "Demand Zone ${idx + 1}",
                            lat = lat,
                            lng = lng,
                            level = (dto.level ?: dto.intensity ?: "moderate").lowercase(),
                            multiplier = dto.multiplier ?: 1.2,
                            surgeFee = dto.surgeFee ?: 1.5,
                            radiusMeters = dto.radiusMeters ?: 800.0
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
        super.onCleared()
    }
}
