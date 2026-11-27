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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

                // Configure Android 12+ smooth auto-enter Picture-in-Picture when switching to Google Maps / Waze
                LaunchedEffect(
                    gateState is SessionGateState.Ready,
                    isCockpitActive,
                    terminalState.cockpitSettings.pipEnabled
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    ) {
                        val shouldAutoEnter = (gateState is SessionGateState.Ready) &&
                            isCockpitActive &&
                            terminalState.cockpitSettings.pipEnabled
                        runCatching {
                            setPictureInPictureParams(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 10))
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
        val shouldEnterPip = gateReady &&
            st.cockpitSettings.pipEnabled &&
            (st.activeRide != null || st.isOnline)
        if (shouldEnterPip &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 10))
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        // Clear active chat presence heartbeat when app goes to background
        // so server's 45s WhatsApp fallback activates cleanly if driver is away
        if (terminalViewModel.uiState.value.showChatSheet) {
            terminalViewModel.stopChatPresence()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
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
    val (badgeLabel, badgeColor) = when {
        state.incomingOffer != null -> "NEW OFFER" to AnyRollerOrange
        state.rideState == TerminalRideState.EN_ROUTE_PICKUP -> "TO PICKUP" to AnyRollerBlue
        state.rideState == TerminalRideState.WAITING -> "AT PICKUP" to AnyRollerOrange
        state.rideState == TerminalRideState.IN_PROGRESS -> "ON TRIP" to AnyRollerGreen
        state.rideState == TerminalRideState.COMPLETED_PAYMENT -> "PAYMENT" to AnyRollerOrange
        state.isOnBreak -> "ON BREAK" to AnyRollerOrange
        state.isOnline -> "ONLINE" to AnyRollerGreen
        else -> "OFFLINE" to AnyRollerTextMuted
    }
    val headline = when {
        state.incomingOffer != null -> "£%.2f Offer • %ds".format(
            state.incomingOffer.fareEstimate,
            state.offerSecondsLeft
        )
        active != null -> active.passengerName.ifBlank { "Passenger" }
        state.isOnBreak -> if (state.breakIsOpenEnded) "Break Paused" else "Break %dm".format((state.breakSecondsLeft + 59) / 60)
        else -> "AnyRoller Driver"
    }
    val subline = when {
        state.incomingOffer != null -> state.incomingOffer.pickupAddress
        state.rideState == TerminalRideState.EN_ROUTE_PICKUP && active != null -> active.pickupAddress
        active != null -> active.dropoffAddress
        else -> "Today £%.2f • %d mph".format(state.todayEarningsComputed, state.currentSpeedMph)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = badgeColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = badgeLabel,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = headline,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subline,
            color = AnyRollerTextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (active != null) {
            val etaPart = state.activeRoute.etaMinutes?.let { "$it min • " } ?: ""
            val distPart = state.activeRoute.distanceText?.takeIf { it.isNotBlank() }?.let { "$it • " } ?: ""
            Text(
                text = "$etaPart$distPart£%.2f".format(active.fareEstimate),
                color = AnyRollerGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
