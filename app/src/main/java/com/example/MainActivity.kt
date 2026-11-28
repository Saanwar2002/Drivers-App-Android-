package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AnyRollerMessagingService
import com.example.service.FloatingRideOverlayController
import com.example.service.UrgentPushEvent
import com.example.service.UrgentPushEventBus
import com.example.ui.screens.DriverSignInScreen
import com.example.ui.screens.DriverTerminalRoot
import com.example.ui.screens.LegalTermsGateScreen
import com.example.ui.screens.LoadingGateScreen
import com.example.ui.screens.MaintenanceGateScreen
import com.example.ui.screens.NonDriverGateScreen
import com.example.ui.screens.PendingApprovalGateScreen
import com.example.ui.screens.SuspendedGateScreen
import com.example.ui.theme.AnyRollerBgPrimary
import com.example.ui.theme.AnyRollerBlue
import com.example.ui.theme.AnyRollerDriverTheme
import com.example.ui.theme.AnyRollerGreen
import com.example.ui.theme.AnyRollerOrange
import com.example.ui.theme.AnyRollerRed
import com.example.ui.theme.AnyRollerTextMuted
import com.example.viewmodel.DriverTerminalUiState
import com.example.viewmodel.DriverTerminalViewModel
import com.example.viewmodel.SessionGateState
import com.example.viewmodel.SessionViewModel
import com.example.viewmodel.TerminalRideState

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels()
    private val terminalViewModel: DriverTerminalViewModel by viewModels()
    private val isInPipModeState = mutableStateOf(false)
    private val isInMultiWindowState = mutableStateOf(false)
    private var isActivityResumed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInMultiWindowState.value = isInMultiWindowMode
            UrgentPushEventBus.setSplitScreenMode(isInMultiWindowMode && !isInPictureInPictureMode)
        }
        AnyRollerMessagingService.createNotificationChannels(applicationContext)
        handleIncomingIntent(intent)

        setContent {
            AnyRollerDriverTheme {
                val context = LocalContext.current
                val gateState by sessionViewModel.gateState.collectAsStateWithLifecycle()
                val authBusy by sessionViewModel.authBusy.collectAsStateWithLifecycle()
                val sessionBanner by sessionViewModel.statusBanner.collectAsStateWithLifecycle()
                val terminalState by terminalViewModel.uiState.collectAsStateWithLifecycle()
                val inPipMode by isInPipModeState
                val inMultiWindow by isInMultiWindowState
                val isSplitScreenMode = inMultiWindow && !inPipMode

                // Request POST_NOTIFICATIONS on Android 13+ when driver enters Ready state
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    sessionViewModel.registerFcmTokenNow()
                }

                LaunchedEffect(sessionBanner) {
                    if (!sessionBanner.isNullOrBlank()) {
                        Toast.makeText(context, sessionBanner, Toast.LENGTH_LONG).show()
                        sessionViewModel.clearBanner()
                    }
                }

                // Keep cockpit screen awake + optional Auto-Brightness Boost while Online or on an Active Trip
                val isCockpitActive = terminalState.isOnline || terminalState.activeRide != null
                LaunchedEffect(
                    isCockpitActive,
                    terminalState.cockpitSettings.keepScreenAwake,
                    terminalState.cockpitSettings.autoBrightnessBoost
                ) {
                    if (isCockpitActive && terminalState.cockpitSettings.keepScreenAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    val attrs = window.attributes
                    val desiredBrightness = if (isCockpitActive && terminalState.cockpitSettings.autoBrightnessBoost) {
                        0.85f
                    } else {
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                    if (attrs.screenBrightness != desiredBrightness) {
                        attrs.screenBrightness = desiredBrightness
                        window.attributes = attrs
                    }
                }

                // Configure Android 12+ Wide 2.39:1 PiP automatic fallback (skipped when Floating Overlay is active & permitted)
                LaunchedEffect(
                    gateState is SessionGateState.Ready,
                    isCockpitActive,
                    isSplitScreenMode,
                    terminalState.cockpitSettings.pipEnabled,
                    terminalState.cockpitSettings.floatingOverlayEnabled,
                    terminalState.activeRide != null,
                    terminalState.stackedRideOffer != null,
                    terminalState.incomingOffer != null
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    ) {
                        val overlayActiveForTrip = terminalState.cockpitSettings.floatingOverlayEnabled &&
                            FloatingRideOverlayController.canDrawOverlays(this@MainActivity) &&
                            (terminalState.activeRide != null || terminalState.stackedRideOffer != null || terminalState.incomingOffer != null)
                        val shouldAutoEnter = (gateState is SessionGateState.Ready) &&
                            isCockpitActive &&
                            !isSplitScreenMode &&
                            terminalState.cockpitSettings.pipEnabled &&
                            !overlayActiveForTrip
                        runCatching {
                            setPictureInPictureParams(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(239, 100))
                                    .setAutoEnterEnabled(shouldAutoEnter)
                                    .build()
                            )
                        }
                    }
                }

                when (val gate = gateState) {
                    is SessionGateState.Loading -> LoadingGateScreen()

                    is SessionGateState.Maintenance -> MaintenanceGateScreen(
                        message = gate.message,
                        onRetry = { sessionViewModel.checkMaintenanceAndBootstrap() }
                    )

                    is SessionGateState.SignedOut -> DriverSignInScreen(
                        errorMessage = gate.errorMessage,
                        isBusy = authBusy,
                        onSignInEmail = { email, password ->
                            sessionViewModel.signInWithEmail(email, password)
                        },
                        onSignInGoogle = {
                            sessionViewModel.signInWithGoogle(this@MainActivity)
                        },
                        onForgotPassword = { email ->
                            sessionViewModel.sendPasswordReset(email)
                        }
                    )

                    is SessionGateState.NonDriverAccount -> NonDriverGateScreen(
                        email = gate.email,
                        onSignOut = { sessionViewModel.signOut() }
                    )

                    is SessionGateState.SuspendedAccount -> SuspendedGateScreen(
                        statusLabel = gate.statusLabel,
                        onSignOut = { sessionViewModel.signOut() }
                    )

                    is SessionGateState.TermsRequired -> LegalTermsGateScreen(
                        profile = gate.profile,
                        isSubmitting = gate.isSubmitting,
                        errorMessage = gate.error,
                        onAccept = { marketing, partner ->
                            sessionViewModel.acceptCurrentTerms(marketing, partner)
                        },
                        onSignOut = { sessionViewModel.signOut() }
                    )

                    is SessionGateState.PendingApproval -> PendingApprovalGateScreen(
                        profile = gate.profile,
                        onSignOut = { sessionViewModel.signOut() }
                    )

                    is SessionGateState.Ready -> {
                        key(gate.uid) {
                            LaunchedEffect(gate.uid) {
                                terminalViewModel.bindToDriver(gate.uid, gate.profile)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }

                            if (inPipMode) {
                                DriverPipMiniScreen(state = terminalState)
                            } else {
                                DriverTerminalRoot(
                                    state = terminalState,
                                    viewModel = terminalViewModel,
                                    isSplitScreenMode = isSplitScreenMode,
                                    onSignOut = { sessionViewModel.signOut() },
                                    onExportGdpr = {
                                        sessionViewModel.exportGdprData { }
                                    },
                                    onDeleteAccount = { cb ->
                                        sessionViewModel.deleteAccountPermanently(cb)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val gateReady = sessionViewModel.gateState.value is SessionGateState.Ready
        val st = terminalViewModel.uiState.value
        val inSplit = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode && !isInPipModeState.value
        val overlayActiveForTrip = st.cockpitSettings.floatingOverlayEnabled &&
            FloatingRideOverlayController.canDrawOverlays(this) &&
            (st.activeRide != null || st.stackedRideOffer != null || st.incomingOffer != null)
        val shouldEnterPip = gateReady &&
            !inSplit &&
            st.cockpitSettings.pipEnabled &&
            !overlayActiveForTrip &&
            (st.activeRide != null || st.isOnline)
        if (shouldEnterPip &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(239, 100))
                        .build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
        val splitNow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode && !isInPictureInPictureMode
        isInMultiWindowState.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode
        UrgentPushEventBus.setSplitScreenMode(splitNow)
        UrgentPushEventBus.setFullScreenVisible(isActivityResumed && !isInPictureInPictureMode)
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        isInMultiWindowState.value = isInMultiWindowMode
        UrgentPushEventBus.setSplitScreenMode(isInMultiWindowMode && !isInPipModeState.value)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInMultiWindowState.value = isInMultiWindowMode
            UrgentPushEventBus.setSplitScreenMode(isInMultiWindowMode && !isInPipModeState.value)
        }
        UrgentPushEventBus.setFullScreenVisible(!isInPipModeState.value)
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        UrgentPushEventBus.setFullScreenVisible(false)
        // Clear active chat presence heartbeat when app goes to background
        // so server's 45s WhatsApp fallback activates cleanly if driver is away
        if (terminalViewModel.uiState.value.showChatSheet) {
            terminalViewModel.stopChatPresence()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(FloatingRideOverlayController.EXTRA_OPEN_CHAT, false) == true) {
            intent.removeExtra(FloatingRideOverlayController.EXTRA_OPEN_CHAT)
            if (terminalViewModel.uiState.value.activeRide != null) {
                terminalViewModel.setChatSheetVisible(true)
            }
        }
        val data = intent?.data
        if (data != null) {
            val path = data.path ?: ""
            if (path.startsWith("/driver-return") ||
                path.startsWith("/platform-fee-success") ||
                path.startsWith("/driver-terminal") ||
                path.startsWith("/driver-dashboard")
            ) {
                UrgentPushEventBus.emit(UrgentPushEvent.DeepLinkReturned(path))
            }
        }
    }
}

@Composable
private fun DriverPipMiniScreen(state: DriverTerminalUiState) {
    val active = state.activeRide
    val stackedOffer = state.stackedRideOffer
    val isOfferAlert = state.incomingOffer != null || stackedOffer != null
    val hasActiveStop = state.rideState == TerminalRideState.IN_PROGRESS &&
        active != null && active.currentStopIndex < active.stops.size
    val activeStop = if (hasActiveStop) active?.stops?.getOrNull(active.currentStopIndex) else null

    val (badgeLabel, badgeColor) = when {
        stackedOffer != null -> "NEXT RIDE" to AnyRollerOrange
        state.incomingOffer != null -> "NEW OFFER" to AnyRollerOrange
        state.rideState == TerminalRideState.EN_ROUTE_PICKUP -> "TO PICKUP" to AnyRollerBlue
        state.rideState == TerminalRideState.WAITING -> "AT PICKUP" to AnyRollerOrange
        state.rideState == TerminalRideState.IN_PROGRESS -> if (hasActiveStop && active != null) {
            "STOP ${active.currentStopIndex + 1}" to AnyRollerGreen
        } else {
            "TO DROPOFF" to AnyRollerGreen
        }
        state.rideState == TerminalRideState.COMPLETED_PAYMENT -> "PAYMENT" to AnyRollerOrange
        state.isOnBreak -> "ON BREAK" to AnyRollerOrange
        state.isOnline -> "ONLINE" to AnyRollerGreen
        else -> "OFFLINE" to AnyRollerTextMuted
    }
    val headline = when {
        stackedOffer != null -> "£%.2f Stacked • %ds".format(
            stackedOffer.fareEstimate,
            state.stackedOfferSecondsLeft
        )
        state.incomingOffer != null -> "£%.2f Offer • %ds".format(
            state.incomingOffer.fareEstimate,
            state.offerSecondsLeft
        )
        active != null -> {
            val fare = active.finalFare?.takeIf { it > 0.0 } ?: active.fareEstimate
            "${active.passengerName.ifBlank { "Passenger" }} • £%.2f".format(fare)
        }
        state.isOnBreak -> if (state.breakIsOpenEnded) "Break Paused" else "Break %dm".format((state.breakSecondsLeft + 59) / 60)
        else -> "AnyRoller Driver"
    }
    val addressLine = when {
        stackedOffer != null -> "${stackedOffer.pickupAddress} → ${stackedOffer.dropoffAddress}"
        state.incomingOffer != null -> "${state.incomingOffer.pickupAddress} → ${state.incomingOffer.dropoffAddress}"
        (state.rideState == TerminalRideState.EN_ROUTE_PICKUP || state.rideState == TerminalRideState.WAITING) && active != null ->
            active.pickupAddress
        activeStop != null && activeStop.address.isNotBlank() ->
            "Stop ${active!!.currentStopIndex + 1}: ${activeStop.address}"
        active != null -> active.dropoffAddress
        else -> "Today £%.2f • Ready for jobs".format(state.todayEarningsComputed)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOfferAlert) Color(0xFF1F160C) else AnyRollerBgPrimary)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = badgeColor.copy(alpha = if (isOfferAlert) 0.30f else 0.20f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = headline,
                    color = if (isOfferAlert) AnyRollerOrange else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (state.unreadChatCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(AnyRollerRed, CircleShape)
                    )
                }
                Text(
                    text = "${state.currentSpeedMph} mph",
                    color = if (state.isOverSpeedAlert) AnyRollerRed else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Wide 2.39:1 readable 2-line destination / stop address + UK postcode
        Text(
            text = addressLine,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOfferAlert) {
                Text(
                    text = if (stackedOffer != null) "Queued after current trip" else "Incoming dispatch",
                    color = AnyRollerTextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else if (active != null) {
                val liveEta = state.liveRemainingEtaMinutes ?: state.activeRoute.etaMinutes
                val etaPart = liveEta?.let { "$it min" } ?: ""
                val liveDist = state.liveRemainingDistanceMeters?.takeIf { it > 0 }?.let { m ->
                    if (m >= 805) "%.1f mi".format(m / 1609.344) else "${(m * 1.09361).toInt().coerceAtLeast(10)} yd"
                } ?: state.activeRoute.distanceText
                val queuedSuffix = if (state.queuedStackedRide != null) " • +1 queued" else ""
                val summary = listOfNotNull(
                    etaPart.takeIf { it.isNotBlank() },
                    liveDist?.takeIf { it.isNotBlank() }
                ).joinToString(" • ") + queuedSuffix
                Text(
                    text = summary.ifBlank { "Active trip" },
                    color = AnyRollerGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = if (isOfferAlert) "Tap to Accept" else "Tap for slider",
                color = if (isOfferAlert) AnyRollerOrange else AnyRollerBlue,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
