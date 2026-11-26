package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CarCrash
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.CurrencyPound
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NoCrash
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WrongLocation
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.service.ExternalIntentHelpers
import com.example.ui.theme.ANYROLLER_DARK_MAP_STYLE_JSON
import com.example.ui.theme.AnyRollerBgPrimary
import com.example.ui.theme.AnyRollerBgSecondary
import com.example.ui.theme.AnyRollerBorder
import com.example.ui.theme.AnyRollerBlue
import com.example.ui.theme.AnyRollerGreen
import com.example.ui.theme.AnyRollerGreenDeep
import com.example.ui.theme.AnyRollerOrange
import com.example.ui.theme.AnyRollerRed
import com.example.ui.theme.AnyRollerTextMuted
import com.example.ui.theme.AnyRollerTextPrimary
import com.example.ui.theme.LockedHudBlueText
import com.example.ui.theme.LockedHudWhiteShadow
import com.example.ui.theme.SpaceGroteskFont
import com.example.viewmodel.DriverOverlayRoute
import com.example.viewmodel.DriverTab
import com.example.viewmodel.DriverTerminalUiState
import com.example.viewmodel.DriverTerminalViewModel
import com.example.viewmodel.TerminalRideState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DriverTerminalRoot(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onSignOut: () -> Unit,
    onExportGdpr: () -> Unit,
    onDeleteAccount: ((Boolean, String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Keep screen awake while driver is Online or on an active trip
    val keepScreenAwake = state.isOnline || state.activeRide != null
    DisposableEffect(context, keepScreenAwake) {
        val window = (context as? Activity)?.window
        if (keepScreenAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(state.customTabUrlToOpen) {
        val url = state.customTabUrlToOpen
        if (!url.isNullOrBlank()) {
            ExternalIntentHelpers.openCustomTab(context, url)
            viewModel.clearCustomTabUrl()
        }
    }

    LaunchedEffect(state.whatsAppUrlToOpen) {
        val waUrl = state.whatsAppUrlToOpen
        if (!waUrl.isNullOrBlank()) {
            ExternalIntentHelpers.launchValidatedWhatsAppUrl(context, waUrl)
            viewModel.clearWhatsAppUrl()
        }
    }

    val hasLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission.value = granted
        if (granted) {
            viewModel.toggleOnline(true)
        }
    }

    val onToggleOnlineClicked = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        hasLocationPermission.value = granted
        if (!state.isOnline && !granted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.toggleOnline(granted)
        }
    }

    // Handle Android system Back button across overlays & tabs
    BackHandler(enabled = state.showChatSheet || state.showHazardModal || state.overlayRoute != DriverOverlayRoute.NONE || state.activeTab != DriverTab.TERMINAL) {
        when {
            state.showChatSheet -> viewModel.setChatSheetVisible(false)
            state.showHazardModal -> viewModel.setHazardModalVisible(false)
            state.overlayRoute != DriverOverlayRoute.NONE -> viewModel.closeOverlay()
            state.activeTab != DriverTab.TERMINAL -> viewModel.selectTab(DriverTab.TERMINAL)
        }
    }

    Scaffold(
        containerColor = AnyRollerBgPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.overlayRoute == DriverOverlayRoute.NONE) {
                DriverBottomNavigationBar(
                    activeTab = state.activeTab,
                    unreadMessagesCount = state.unreadNotificationsCount,
                    onSelectTab = viewModel::selectTab
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (state.activeTab) {
                DriverTab.TERMINAL -> TerminalMapCockpit(
                    state = state,
                    viewModel = viewModel,
                    hasLocationPermission = hasLocationPermission.value,
                    onToggleOnline = onToggleOnlineClicked
                )
                DriverTab.EARNINGS -> DriverEarningsScreen(
                    state = state,
                    viewModel = viewModel
                )
                DriverTab.MESSAGES -> DriverInboxScreen(
                    state = state,
                    viewModel = viewModel
                )
                DriverTab.MY_JOBS -> DriverJobsScreen(
                    state = state,
                    viewModel = viewModel
                )
            }

            // Full-screen overlay sub-routes (Menu & its sub-screens, Expenses, Trip History)
            when (state.overlayRoute) {
                DriverOverlayRoute.NONE -> Unit
                DriverOverlayRoute.MENU -> DriverMenuScreen(
                    state = state,
                    viewModel = viewModel,
                    onToggleOnline = onToggleOnlineClicked,
                    onClose = { viewModel.closeOverlay() },
                    onSignOut = onSignOut
                )
                DriverOverlayRoute.DOCUMENTS -> DriverDocumentsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.closeOverlay() }
                )
                DriverOverlayRoute.INSURANCE -> InsuranceMarketplaceScreen(
                    state = state,
                    onBack = { viewModel.closeOverlay() }
                )
                DriverOverlayRoute.AVAILABILITY -> DriverAvailabilityScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.closeOverlay() }
                )
                DriverOverlayRoute.ZONES -> DriverZonesScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.closeOverlay() }
                )
                DriverOverlayRoute.ANALYTICS -> DriverAnalyticsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.closeOverlay() }
                )
                DriverOverlayRoute.PROFILE -> DriverProfileScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.closeOverlay() },
                    onExportGdpr = onExportGdpr,
                    onDeleteAccount = onDeleteAccount,
                    onSignOut = onSignOut
                )
                DriverOverlayRoute.EXPENSES -> DriverExpensesScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.closeOverlay() }
                )
                DriverOverlayRoute.TRIP_HISTORY -> DriverTripHistoryScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { viewModel.closeOverlay() }
                )
            }

            // Modals & Chat Sheet
            if (state.showHazardModal) {
                ReportHazardAndSosModal(
                    onDismiss = { viewModel.setHazardModalVisible(false) },
                    onSubmitHazard = { viewModel.reportHazard(it) },
                    onEmergencySos = {
                        viewModel.setHazardModalVisible(false)
                        ExternalIntentHelpers.dialPhoneNumber(context, "999")
                    }
                )
            }

            if (state.showChatSheet && state.activeRide != null) {
                RideChatModalSheet(
                    state = state,
                    viewModel = viewModel,
                    onDismiss = { viewModel.setChatSheetVisible(false) }
                )
            }
        }
    }
}

@Composable
private fun TerminalMapCockpit(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    hasLocationPermission: Boolean,
    onToggleOnline: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val driverLatLng = LatLng(state.driverLocation.lat, state.driverLocation.lng)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(driverLatLng, 14f)
    }

    LaunchedEffect(state.driverLocation.hasRealFix) {
        if (state.driverLocation.hasRealFix) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(driverLatLng, 15f)
            )
        }
    }

    // Smooth street-level zoom (18f) when approaching within 200m of pickup or marking arrived
    LaunchedEffect(state.pickupZoomEventId) {
        val ride = state.activeRide
        if (state.pickupZoomEventId > 0L && ride != null && (ride.pickupLat != 0.0 || ride.pickupLng != 0.0)) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(ride.pickupLat, ride.pickupLng), 18f)
            )
        }
    }

    val darkMapStyle = remember { MapStyleOptions(ANYROLLER_DARK_MAP_STYLE_JSON) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Full-bleed Dark Google Map (1:1 mirror of DriverTerminal.tsx full-screen map)
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("terminal_google_map"),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapStyleOptions = darkMapStyle,
                isTrafficEnabled = true
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            // Demand Zones (live Admin surgeRules colors, opacity, and fixed/multiplier zones)
            if (state.platformConfig.surgeEnabled) {
                state.demandZones.forEach { zone ->
                    val baseColor = when (zone.uiColor.lowercase()) {
                        "red" -> AnyRollerRed
                        "amber", "orange", "yellow" -> AnyRollerOrange
                        "blue" -> AnyRollerBlue
                        "green" -> AnyRollerGreen
                        else -> when (zone.level) {
                            "high" -> AnyRollerRed
                            "medium", "moderate" -> AnyRollerOrange
                            else -> AnyRollerGreen
                        }
                    }
                    val fillAlpha = (zone.uiOpacity.coerceIn(10, 90) / 100f) * 0.65f
                    Circle(
                        center = LatLng(zone.lat, zone.lng),
                        radius = zone.radiusMeters,
                        fillColor = baseColor.copy(alpha = fillAlpha),
                        strokeColor = baseColor.copy(alpha = 0.75f),
                        strokeWidth = 2f
                    )
                }
            }

            // Road Hazards
            state.hazards.forEach { h ->
                Marker(
                    state = MarkerState(position = LatLng(h.lat, h.lng)),
                    title = h.type,
                    snippet = "Reported road hazard",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                )
            }

            // Active Ride Pins (Pickup/Passenger when en_route/waiting; Stops/Dropoff when in_progress)
            val ride = state.activeRide
            if (ride != null) {
                if (state.rideState == TerminalRideState.EN_ROUTE_PICKUP || state.rideState == TerminalRideState.WAITING) {
                    if (ride.pickupLat != 0.0 || ride.pickupLng != 0.0) {
                        Marker(
                            state = MarkerState(position = LatLng(ride.pickupLat, ride.pickupLng)),
                            title = "Pickup",
                            snippet = ride.pickupAddress,
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        )
                    }
                    if (ride.passengerLat != null && ride.passengerLng != null) {
                        Marker(
                            state = MarkerState(position = LatLng(ride.passengerLat, ride.passengerLng)),
                            title = ride.passengerName,
                            snippet = "Live passenger location",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                } else if (state.rideState == TerminalRideState.IN_PROGRESS) {
                    ride.stops.forEachIndexed { idx, stop ->
                        if (idx >= ride.currentStopIndex && (stop.lat != 0.0 || stop.lng != 0.0)) {
                            Marker(
                                state = MarkerState(position = LatLng(stop.lat, stop.lng)),
                                title = "Stop ${idx + 1}",
                                snippet = stop.address,
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                            )
                        }
                    }
                    if (ride.dropoffLat != 0.0 || ride.dropoffLng != 0.0) {
                        Marker(
                            state = MarkerState(position = LatLng(ride.dropoffLat, ride.dropoffLng)),
                            title = "Dropoff",
                            snippet = ride.dropoffAddress,
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    }
                }
                // Live Active Route Polyline (clean single stroke, zero map clutter)
                if (state.activeRoute.points.size >= 2) {
                    Polyline(
                        points = state.activeRoute.points,
                        color = if (state.rideState == TerminalRideState.IN_PROGRESS) AnyRollerGreen else AnyRollerBlue,
                        width = 12f
                    )
                }
            }
        }

        // 2. Owner-Locked Navigation HUD (Transparent background, #2563EB text, #FFFFFF 90% drop shadow, NO black box, NO stroke)
        if (state.activeRide != null && state.rideState in setOf(
                TerminalRideState.EN_ROUTE_PICKUP,
                TerminalRideState.WAITING,
                TerminalRideState.IN_PROGRESS
            )
        ) {
            val navHeading = when (state.rideState) {
                TerminalRideState.EN_ROUTE_PICKUP -> "HEAD TO PICKUP"
                TerminalRideState.WAITING -> "WAITING AT PICKUP"
                else -> {
                    val hasStop = state.activeRide.currentStopIndex < state.activeRide.stops.size
                    if (hasStop) "HEAD TO STOP ${state.activeRide.currentStopIndex + 1}" else "HEAD TO DROPOFF"
                }
            }
            val targetAddress = when (state.rideState) {
                TerminalRideState.IN_PROGRESS -> {
                    val stop = state.activeRide.stops.getOrNull(state.activeRide.currentStopIndex)
                    stop?.address?.takeIf { it.isNotBlank() } ?: state.activeRide.dropoffAddress
                }
                else -> state.activeRide.pickupAddress
            }
            val liveInstruction = state.activeRoute.nextManeuver?.takeIf { it.isNotBlank() } ?: targetAddress
            val liveDistanceLabel = buildString {
                val distText = state.activeRoute.distanceText
                val etaMins = state.activeRoute.etaMinutes
                if (!distText.isNullOrBlank() && etaMins != null) {
                    append("$distText • $etaMins min")
                } else if (state.activeRide.distanceMiles > 0.0) {
                    append("%.1f mi • %d min".format(state.activeRide.distanceMiles, state.activeRide.durationMinutes.coerceAtLeast(1)))
                } else {
                    append("Live GPS Active")
                }
            }
            LockedNavigationHud(
                heading = navHeading,
                instruction = liveInstruction,
                distanceLabel = liveDistanceLabel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 12.dp, end = 88.dp)
            )
        }

        // 3. Top-Right Column: BatteryStatus + Circular Menu (☰) Button (Exact DriverTerminal.tsx layout)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BatteryStatusPill(context = context)

            // Circular Menu (☰) Button -> opens DriverMenu (subtle 8dp red dot if menu action needed)
            Box(modifier = Modifier.size(50.dp)) {
                Surface(
                    onClick = { viewModel.openOverlay(DriverOverlayRoute.MENU) },
                    shape = CircleShape,
                    color = AnyRollerBgSecondary,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, AnyRollerBorder, CircleShape)
                        .testTag("driver_menu_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Open Driver Menu",
                            tint = AnyRollerTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                if (state.hasMenuAttentionDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 3.dp, end = 3.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AnyRollerRed)
                            .border(1.5.dp, AnyRollerBgPrimary, CircleShape)
                            .testTag("driver_menu_attention_dot")
                    )
                }
            }

            // Right Floating Hazard / SOS Button (⚡)
            Surface(
                onClick = { viewModel.setHazardModalVisible(true) },
                shape = CircleShape,
                color = AnyRollerBgSecondary,
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, AnyRollerOrange.copy(alpha = 0.6f), CircleShape)
                    .testTag("hazard_sos_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Report road hazard or Emergency SOS",
                        tint = AnyRollerOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Blue Circular Navigate (#007AFF) Button during active trip
            if (state.activeRide != null && state.rideState in setOf(
                    TerminalRideState.EN_ROUTE_PICKUP,
                    TerminalRideState.WAITING,
                    TerminalRideState.IN_PROGRESS
                )
            ) {
                Surface(
                    onClick = {
                        val ride = state.activeRide
                        if (state.rideState == TerminalRideState.IN_PROGRESS) {
                            val remainingWaypoints = ride.stops
                                .drop(ride.currentStopIndex)
                                .filter { it.lat != 0.0 || it.lng != 0.0 }
                                .map { it.lat to it.lng }
                            val (destLat, destLng, waypointsToPass) = if (ride.dropoffLat != 0.0 || ride.dropoffLng != 0.0) {
                                Triple(ride.dropoffLat, ride.dropoffLng, remainingWaypoints)
                            } else {
                                val firstStop = remainingWaypoints.firstOrNull()
                                Triple(firstStop?.first ?: ride.pickupLat, firstStop?.second ?: ride.pickupLng, emptyList())
                            }
                            ExternalIntentHelpers.launchTurnByTurnNavigation(
                                context = context,
                                lat = destLat,
                                lng = destLng,
                                preferredNavApp = state.profile.defaultNavApp,
                                waypoints = waypointsToPass
                            )
                        } else {
                            ExternalIntentHelpers.launchTurnByTurnNavigation(
                                context = context,
                                lat = ride.pickupLat,
                                lng = ride.pickupLng,
                                preferredNavApp = state.profile.defaultNavApp
                            )
                        }
                    },
                    shape = CircleShape,
                    color = AnyRollerBlue,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("floating_navigate_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Navigation,
                            contentDescription = "Launch External Navigation",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Recenter Map GPS Button
            Surface(
                onClick = {
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(driverLatLng, 15f)
                        )
                    }
                },
                shape = CircleShape,
                color = AnyRollerBgSecondary,
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, AnyRollerBorder, CircleShape)
                    .testTag("recenter_map_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Recenter Map",
                        tint = AnyRollerTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 4. Stacked Ride Offer Banner (Screen 3b) when in_progress & stackedRideOffer != null
        if (state.stackedRideOffer != null && state.rideState == TerminalRideState.IN_PROGRESS) {
            StackedRideOfferBanner(
                offer = state.stackedRideOffer,
                secondsLeft = state.stackedOfferSecondsLeft,
                isBusy = state.isBusy,
                onAccept = { viewModel.acceptOffer(isStacked = true) },
                onDecline = { viewModel.declineOffer(isStacked = true) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 72.dp)
            )
        }

        // 5. Floating Bottom Status Pill (Idle) OR Active Trip / Offer Overlays
        when (state.rideState) {
            TerminalRideState.IDLE -> {
                FloatingOnlineStatusPill(
                    isOnline = state.isOnline,
                    isNetworkConnected = state.isNetworkConnected,
                    todayEarnings = state.todayEarningsComputed,
                    ridesToday = state.metrics.ridesToday,
                    onToggleOnline = onToggleOnline,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
            TerminalRideState.OFFER -> {
                state.incomingOffer?.let { offer ->
                    IncomingRideOfferOverlay(
                        offer = offer,
                        secondsLeft = state.offerSecondsLeft,
                        totalSeconds = state.platformConfig.dispatchTimeoutSeconds,
                        isBusy = state.isBusy,
                        onAccept = { viewModel.acceptOffer(isStacked = false) },
                        onDecline = { viewModel.declineOffer(isStacked = false) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
            TerminalRideState.EN_ROUTE_PICKUP,
            TerminalRideState.WAITING,
            TerminalRideState.IN_PROGRESS -> {
                state.activeRide?.let { ride ->
                    ActiveTripSheetCard(
                        ride = ride,
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
            TerminalRideState.COMPLETED_PAYMENT -> {
                state.activeRide?.let { ride ->
                    PaymentHandshakeCard(
                        ride = ride,
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
            TerminalRideState.REVIEW -> {
                state.reviewRide?.let { ride ->
                    TripReviewOverlayCard(
                        ride = ride,
                        state = state,
                        onSubmitRating = { stars, comment ->
                            viewModel.submitPassengerReview(stars, comment)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Owner-Locked Navigation HUD:
 * Transparent background (NO black box), #2563EB text, #FFFFFF 90% drop shadow, zero text stroke.
 */
@Composable
private fun LockedNavigationHud(
    heading: String,
    instruction: String,
    distanceLabel: String,
    modifier: Modifier = Modifier
) {
    val hudShadow = Shadow(
        color = LockedHudWhiteShadow,
        offset = Offset(0f, 2f),
        blurRadius = 6f
    )
    Column(modifier = modifier) {
        Text(
            text = heading,
            style = TextStyle(
                fontFamily = SpaceGroteskFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = LockedHudBlueText,
                shadow = hudShadow
            )
        )
        Text(
            text = instruction,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = SpaceGroteskFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = LockedHudBlueText,
                shadow = hudShadow
            )
        )
        Text(
            text = distanceLabel,
            style = TextStyle(
                fontFamily = SpaceGroteskFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = LockedHudBlueText,
                shadow = hudShadow
            )
        )
    }
}

@Composable
private fun BatteryStatusPill(context: Context) {
    val bm = remember { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    var pct by remember { mutableIntStateOf(bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100) }
    var charging by remember { mutableStateOf(bm?.isCharging ?: false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: pct
            charging = bm?.isCharging ?: charging
        }
    }

    val tint = when {
        charging -> AnyRollerGreen
        pct <= 20 -> AnyRollerRed
        else -> AnyRollerTextPrimary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AnyRollerBgSecondary.copy(alpha = 0.92f))
            .border(1.dp, AnyRollerBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
            contentDescription = "Battery $pct percent",
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun FloatingOnlineStatusPill(
    isOnline: Boolean,
    isNetworkConnected: Boolean,
    todayEarnings: Double,
    ridesToday: Int,
    onToggleOnline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "onlinePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    val containerBg = when {
        !isNetworkConnected -> AnyRollerBgSecondary.copy(alpha = 0.96f)
        isOnline -> AnyRollerGreenDeep.copy(alpha = 0.88f)
        else -> AnyRollerBgSecondary.copy(alpha = 0.94f)
    }
    val borderColor = when {
        !isNetworkConnected -> AnyRollerOrange.copy(alpha = 0.85f)
        isOnline -> AnyRollerGreen.copy(alpha = 0.65f)
        else -> AnyRollerBorder
    }
    val dotColor = when {
        !isNetworkConnected -> AnyRollerOrange.copy(alpha = pulseAlpha)
        isOnline -> AnyRollerGreen.copy(alpha = pulseAlpha)
        else -> AnyRollerTextMuted
    }

    Surface(
        onClick = onToggleOnline,
        shape = RoundedCornerShape(28.dp),
        color = containerBg,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(28.dp))
            .testTag("online_status_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = when {
                            !isNetworkConnected -> "NO SIGNAL — RETRYING"
                            isOnline -> "WAITING FOR JOBS"
                            else -> "OFFLINE"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (!isNetworkConnected) AnyRollerOrange else AnyRollerTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (!isNetworkConnected) {
                            "Reconnecting to live dispatch • Today: £%.2f".format(todayEarnings)
                        } else {
                            "Today: £%.2f • %d trips • Tap to %s".format(
                                todayEarnings,
                                ridesToday,
                                if (isOnline) "Go Offline" else "Go Online"
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline && isNetworkConnected) AnyRollerTextPrimary.copy(alpha = 0.8f) else AnyRollerTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            !isNetworkConnected -> AnyRollerOrange.copy(alpha = 0.2f)
                            isOnline -> AnyRollerGreen
                            else -> AnyRollerBorder
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        !isNetworkConnected -> "OFFLINE"
                        isOnline -> "ACTIVE"
                        else -> "STANDBY"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        !isNetworkConnected -> AnyRollerOrange
                        isOnline -> AnyRollerBgPrimary
                        else -> AnyRollerTextPrimary
                    }
                )
            }
        }
    }
}

@Composable
private fun DriverBottomNavigationBar(
    activeTab: DriverTab,
    unreadMessagesCount: Int,
    onSelectTab: (DriverTab) -> Unit
) {
    Surface(
        color = AnyRollerBgSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = AnyRollerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavPillItem(
                label = "Terminal",
                icon = Icons.Rounded.Bolt,
                selected = activeTab == DriverTab.TERMINAL,
                badgeCount = 0,
                testTag = "nav_tab_terminal",
                onClick = { onSelectTab(DriverTab.TERMINAL) },
                modifier = Modifier.weight(1f)
            )
            BottomNavPillItem(
                label = "Earnings",
                icon = Icons.Rounded.CurrencyPound,
                selected = activeTab == DriverTab.EARNINGS,
                badgeCount = 0,
                testTag = "nav_tab_earnings",
                onClick = { onSelectTab(DriverTab.EARNINGS) },
                modifier = Modifier.weight(1f)
            )
            BottomNavPillItem(
                label = "Messages",
                icon = Icons.Rounded.ChatBubbleOutline,
                selected = activeTab == DriverTab.MESSAGES,
                badgeCount = unreadMessagesCount,
                testTag = "nav_tab_messages",
                onClick = { onSelectTab(DriverTab.MESSAGES) },
                modifier = Modifier.weight(1f)
            )
            BottomNavPillItem(
                label = "My Jobs",
                icon = Icons.Rounded.Schedule,
                selected = activeTab == DriverTab.MY_JOBS,
                badgeCount = 0,
                testTag = "nav_tab_jobs",
                onClick = { onSelectTab(DriverTab.MY_JOBS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavPillItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent
    val tint = if (selected) AnyRollerGreen else AnyRollerTextMuted

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (badgeCount > 0) {
            BadgedBox(badge = {
                Badge(containerColor = AnyRollerRed, contentColor = Color.White) {
                    Text(if (badgeCount > 9) "9+" else "$badgeCount")
                }
            }) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            }
        } else {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) AnyRollerTextPrimary else AnyRollerTextMuted,
            maxLines = 1
        )
    }
}

/**
 * Exact 1:1 mirror of `DriverTerminal.tsx` white rounded-3xl "Report road issue" modal:
 * 3x2 grid (Accident, Traffic, Construction, Road Closure, Speed Trap, Incorrect Route)
 * with 3-second fill countdown & tap-to-cancel + prominent red EMERGENCY SOS (999) button.
 */
@Composable
private fun ReportHazardAndSosModal(
    onDismiss: () -> Unit,
    onSubmitHazard: (String) -> Unit,
    onEmergencySos: () -> Unit
) {
    val options = remember {
        listOf(
            "Accident" to Icons.Rounded.CarCrash,
            "Traffic" to Icons.Rounded.Traffic,
            "Construction" to Icons.Rounded.Construction,
            "Road Closure" to Icons.Rounded.NoCrash,
            "Speed Trap" to Icons.Rounded.Speed,
            "Incorrect Route" to Icons.Rounded.WrongLocation
        )
    }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var countdownMs by remember { mutableIntStateOf(3000) }

    LaunchedEffect(selectedType) {
        val current = selectedType ?: return@LaunchedEffect
        countdownMs = 3000
        while (countdownMs > 0 && selectedType == current) {
            delay(50L)
            countdownMs = (countdownMs - 50).coerceAtLeast(0)
        }
        if (selectedType == current && countdownMs == 0) {
            onSubmitHazard(current)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Report road issue",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF111114),
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close hazard modal", tint = Color(0xFF111114))
                    }
                }

                if (selectedType != null) {
                    Text(
                        text = "Reporting $selectedType in ${(countdownMs / 1000) + 1}s — tap tile again to cancel",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { 1f - (countdownMs / 3000f) },
                        color = AnyRollerBlue,
                        trackColor = Color(0xFFE5E7EB),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                // 3x2 Grid
                for (rowIdx in 0 until 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (colIdx in 0 until 3) {
                            val (label, icon) = options[rowIdx * 3 + colIdx]
                            val isSelected = selectedType == label
                            Surface(
                                onClick = {
                                    selectedType = if (isSelected) null else label
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = if (isSelected) AnyRollerBlue.copy(alpha = 0.14f) else Color(0xFFF3F4F6),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(92.dp)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) AnyRollerBlue else Color(0xFFE5E7EB),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .testTag("hazard_tile_${label.lowercase().replace(" ", "_")}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) AnyRollerBlue else Color(0xFF1F2937),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF111827),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onEmergencySos,
                    colors = ButtonDefaults.buttonColors(containerColor = AnyRollerRed, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("emergency_sos_999_button")
                ) {
                    Icon(Icons.Rounded.Emergency, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("EMERGENCY SOS (999)", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
