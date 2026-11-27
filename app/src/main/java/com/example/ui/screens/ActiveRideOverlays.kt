package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.FallbackFareConfig
import com.example.data.model.RideRequest
import com.example.data.model.RideStop
import com.example.data.model.resolveRideFinancialSummary
import com.example.service.ExternalIntentHelpers
import com.example.service.OfferIntelligenceHelper
import com.example.service.QrCodeGenerator
import com.example.ui.components.SwipeConfirmButton
import com.example.ui.theme.AnyRollerBgInput
import com.example.ui.theme.AnyRollerBgPrimary
import com.example.ui.theme.AnyRollerBgSecondary
import com.example.ui.theme.AnyRollerBgTertiary
import com.example.ui.theme.AnyRollerBlue
import com.example.ui.theme.AnyRollerBorder
import com.example.ui.theme.AnyRollerGreen
import com.example.ui.theme.AnyRollerGreenDeep
import com.example.ui.theme.AnyRollerOrange
import com.example.ui.theme.AnyRollerPurple
import com.example.ui.theme.AnyRollerRed
import com.example.ui.theme.AnyRollerTextMuted
import com.example.ui.theme.AnyRollerTextPrimary
import com.example.ui.theme.AnyRollerTextSecondary
import com.example.ui.theme.JetBrainsMonoFont
import com.example.viewmodel.DriverTerminalUiState
import com.example.viewmodel.DriverTerminalViewModel
import com.example.viewmodel.TerminalRideState
import kotlinx.coroutines.delay

/**
 * Exact 1:1 mirror of Incoming Ride Offer Overlay (Screen 3):
 * Highlight header -> Rider Details + Circular 15s Countdown Ring -> Fare Section -> Route Line -> Accept / Decline.
 */
@Composable
fun IncomingRideOfferOverlay(
    offer: RideRequest,
    secondsLeft: Int,
    totalSeconds: Int = 15,
    isBusy: Boolean,
    driverLat: Double = 0.0,
    driverLng: Double = 0.0,
    hasRealDriverFix: Boolean = false,
    homeLat: Double? = null,
    homeLng: Double? = null,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val intel = remember(offer.id, driverLat, driverLng, hasRealDriverFix, homeLat, homeLng) {
        OfferIntelligenceHelper.analyze(
            offer = offer,
            driverLat = driverLat,
            driverLng = driverLng,
            hasRealDriverFix = hasRealDriverFix,
            homeLat = homeLat,
            homeLng = homeLng
        )
    }

    Surface(
        color = AnyRollerBgSecondary,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, AnyRollerGreen.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Highlight Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AnyRollerGreen.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = offer.vehicleCategory.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AnyRollerGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val hasFixedSurge = offer.surgeModel == "fixed" && offer.surgeFixedAmount > 0.0
                    val hasMultSurge = offer.surgeModel != "fixed" && offer.surgeMultiplier > 1.0
                    if (hasFixedSurge || hasMultSurge) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AnyRollerOrange.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasFixedSurge) {
                                    "+£%.2f SURGE".format(offer.surgeFixedAmount)
                                } else {
                                    "%.1fx SURGE".format(offer.surgeMultiplier)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = AnyRollerOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    intel.smartTag?.let { tag ->
                        val (tagColor, tagIcon) = when (tag) {
                            "AIRPORT" -> AnyRollerBlue to Icons.Rounded.Flight
                            "RAIL STATION" -> AnyRollerPurple to Icons.Rounded.Train
                            else -> AnyRollerOrange to Icons.Rounded.TrendingUp
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(tagColor.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("offer_smart_tag")
                        ) {
                            Icon(tagIcon, contentDescription = null, tint = tagColor, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = tagColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = offer.paymentMethod.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = AnyRollerTextMuted
                )
            }

            Spacer(Modifier.height(14.dp))

            // Rider Details + Circular 15s Countdown Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AnyRollerBgTertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = offer.passengerName.firstOrNull()?.uppercase() ?: "P",
                            style = MaterialTheme.typography.titleMedium,
                            color = AnyRollerTextPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = offer.passengerName,
                            style = MaterialTheme.typography.titleMedium,
                            color = AnyRollerTextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = AnyRollerOrange, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "%.1f • %.1f mi • %d min".format(
                                    offer.passengerRating,
                                    offer.distanceMiles,
                                    offer.durationMinutes.coerceAtLeast(1)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = AnyRollerTextMuted
                            )
                        }
                    }
                }

                // Circular Countdown Ring (driven by Admin dispatchTimeoutSeconds)
                val safeTotal = totalSeconds.coerceAtLeast(5)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                    val sweep = (secondsLeft.coerceIn(0, safeTotal) / safeTotal.toFloat()) * 360f
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = AnyRollerBorder,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = if (secondsLeft <= 5) AnyRollerRed else AnyRollerGreen,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "${secondsLeft}s",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFont,
                        color = AnyRollerTextPrimary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Fare + Live Profitability & Direction-of-Travel Intelligence
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AnyRollerBgPrimary)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Estimated Fare", style = MaterialTheme.typography.bodySmall, color = AnyRollerTextMuted)
                        if (intel.estimatedGbpPerHour > 0.0) {
                            Text(
                                text = "Est. £%.0f/hr • £%.2f/mi".format(
                                    intel.estimatedGbpPerHour,
                                    intel.estimatedGbpPerMile
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFont,
                                color = AnyRollerGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("offer_profitability_label")
                            )
                        }
                    }
                    Text(
                        text = "£%.2f".format(offer.fareEstimate),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = JetBrainsMonoFont,
                        color = AnyRollerGreen,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Direction-of-Travel Compass Pill & Pickup Distance
                if (intel.compassShort.isNotBlank() || intel.pickupDeadheadMiles > 0.05) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (intel.isHeadingTowardHome) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AnyRollerGreen.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("offer_toward_home_pill")
                            ) {
                                Icon(Icons.Rounded.Home, contentDescription = null, tint = AnyRollerGreen, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Toward Home (${intel.compassShort})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnyRollerGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (intel.compassShort.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AnyRollerBlue.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("offer_compass_pill")
                            ) {
                                Icon(Icons.Rounded.Explore, contentDescription = null, tint = AnyRollerBlue, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Heading ${intel.compassShort} • %.1f mi".format(intel.totalTripMiles),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnyRollerBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (intel.pickupDeadheadMiles > 0.05) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AnyRollerBgSecondary)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Pickup %.1f mi away".format(intel.pickupDeadheadMiles),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnyRollerTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Route Line (with intermediate stops if present)
            RouteAddressBlock(
                pickup = offer.pickupAddress,
                dropoff = offer.dropoffAddress,
                stops = offer.stops
            )

            Spacer(Modifier.height(16.dp))

            // Accept / 2-Tap Confirm Decline buttons (mirrors DriverTerminal.tsx declineConfirmActive)
            var confirmDecline by remember(offer.id) { mutableStateOf(false) }
            LaunchedEffect(confirmDecline, offer.id) {
                if (confirmDecline) {
                    delay(4_000L)
                    confirmDecline = false
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (confirmDecline) {
                    Button(
                        onClick = {
                            confirmDecline = false
                            onDecline()
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AnyRollerRed, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("confirm_decline_offer_button")
                    ) {
                        Text("CONFIRM", fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmDecline = true },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("decline_offer_button")
                    ) {
                        Text("Decline", color = AnyRollerTextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = {
                        confirmDecline = false
                        onAccept()
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.7f)
                        .height(52.dp)
                        .testTag("accept_offer_button")
                ) {
                    Text("ACCEPT RIDE", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun StackedRideOfferBanner(
    offer: RideRequest,
    secondsLeft: Int,
    isBusy: Boolean,
    driverLat: Double = 0.0,
    driverLng: Double = 0.0,
    hasRealDriverFix: Boolean = false,
    homeLat: Double? = null,
    homeLng: Double? = null,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val intel = remember(offer.id, driverLat, driverLng, hasRealDriverFix, homeLat, homeLng) {
        OfferIntelligenceHelper.analyze(
            offer = offer,
            driverLat = driverLat,
            driverLng = driverLng,
            hasRealDriverFix = hasRealDriverFix,
            homeLat = homeLat,
            homeLng = homeLng
        )
    }
    var confirmDecline by remember(offer.id) { mutableStateOf(false) }
    LaunchedEffect(confirmDecline, offer.id) {
        if (confirmDecline) {
            delay(4_000L)
            confirmDecline = false
        }
    }

    Surface(
        color = AnyRollerBgSecondary,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, AnyRollerPurple, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEXT QUEUED RIDE • £%.2f".format(offer.fareEstimate),
                    style = MaterialTheme.typography.labelMedium,
                    color = AnyRollerPurple,
                    fontWeight = FontWeight.Bold
                )
                Text("${secondsLeft}s", style = MaterialTheme.typography.labelMedium, color = AnyRollerOrange)
            }
            val metaLine = buildList {
                if (intel.estimatedGbpPerHour > 0.0) add("£%.0f/hr".format(intel.estimatedGbpPerHour))
                if (intel.isHeadingTowardHome) add("Toward Home")
                else if (intel.compassShort.isNotBlank()) add("Heading ${intel.compassShort}")
                intel.smartTag?.let { add(it) }
            }.joinToString(" • ")
            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = AnyRollerGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${offer.pickupAddress} → ${offer.dropoffAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = AnyRollerTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirmDecline) {
                    Button(
                        onClick = {
                            confirmDecline = false
                            onDecline()
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AnyRollerRed, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("confirm_decline_stacked_button")
                    ) {
                        Text("CONFIRM", fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmDecline = true },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("decline_stacked_button")
                    ) {
                        Text("Skip", color = AnyRollerTextMuted)
                    }
                }
                Button(
                    onClick = {
                        confirmDecline = false
                        onAccept()
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = AnyRollerPurple, contentColor = Color.White),
                    modifier = Modifier.weight(1.6f).height(48.dp).testTag("accept_stacked_button")
                ) {
                    Text("Queue Next Ride", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ActiveTripSheetCard(
    ride: RideRequest,
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var passcodeInput by remember(ride.id) { mutableStateOf("") }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var earlyDropoffReason by remember(ride.id, state.showCompleteConfirmDialog) { mutableStateOf("") }

    val requiresPasscode = state.profile.requirePasscode || ride.driverRequirePasscode
    val hasRemainingStop = state.rideState == TerminalRideState.IN_PROGRESS &&
        ride.currentStopIndex < ride.stops.size
    val liveFare = ride.finalFare?.takeIf { it > 0.0 } ?: ride.fareEstimate

    // Auto-expand card when urgent interactive controls need immediate visibility (PIN, alerts, active wait-at-stop)
    val forceExpanded = (state.rideState == TerminalRideState.WAITING && requiresPasscode) ||
        state.showStartTripReminder ||
        state.showLeaveStopReminder ||
        !state.lastRideUpdateNotice.isNullOrBlank() ||
        state.isWaitingAtStop
    val isCollapsed = state.isActiveCardCollapsed && !forceExpanded

    Surface(
        color = AnyRollerBgSecondary,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AnyRollerBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(max = 470.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Sleek Drag Handle & Expand/Collapse Pill (mirrors DriverTerminal.tsx isCardCollapsed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !forceExpanded) { viewModel.toggleActiveCardCollapsed() }
                    .padding(vertical = 4.dp)
                    .testTag("active_card_collapse_toggle"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AnyRollerBorder)
                )
                if (!forceExpanded) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isCollapsed) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isCollapsed) "Expand trip details" else "Collapse trip details",
                        tint = AnyRollerTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isCollapsed) "Trip Details" else "Minimize",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnyRollerTextMuted
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Header row: status + queued chip + passenger name + live fare/tip pill + Chat/Call
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when (state.rideState) {
                                TerminalRideState.EN_ROUTE_PICKUP -> "EN ROUTE TO PICKUP"
                                TerminalRideState.WAITING -> "WAITING FOR PASSENGER"
                                else -> "TRIP IN PROGRESS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = AnyRollerGreen,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.queuedStackedRide != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AnyRollerPurple.copy(alpha = 0.22f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("queued_next_ride_chip")
                            ) {
                                Text(
                                    text = "NEXT QUEUED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnyRollerPurple,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = ride.passengerName,
                            style = MaterialTheme.typography.titleMedium,
                            color = AnyRollerTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AnyRollerBgPrimary)
                                .border(1.dp, AnyRollerGreen.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("live_fare_pill")
                        ) {
                            Text(
                                text = if (ride.tipAmount > 0.0) {
                                    "£%.2f +£%.2f tip".format(liveFare, ride.tipAmount)
                                } else {
                                    "£%.2f".format(liveFare)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFont,
                                color = AnyRollerGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (!ride.riderNotes.isNullOrBlank()) {
                        Text(
                            text = "Note: ${ride.riderNotes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AnyRollerOrange,
                            maxLines = 2
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Quick Chat & Call Buttons (with unread badge on Chat button)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgedBox(
                        badge = {
                            if (state.unreadChatCount > 0) {
                                Badge(
                                    containerColor = AnyRollerRed,
                                    contentColor = Color.White,
                                    modifier = Modifier.testTag("active_ride_chat_unread_badge")
                                ) {
                                    Text(if (state.unreadChatCount > 9) "9+" else "${state.unreadChatCount}")
                                }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = { viewModel.setChatSheetVisible(true) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AnyRollerBgTertiary)
                                .testTag("active_ride_chat_button")
                        ) {
                            Icon(Icons.Rounded.ChatBubble, contentDescription = "Open Rider Chat", tint = AnyRollerGreen)
                        }
                    }
                    if (!ride.passengerPhone.isNullOrBlank()) {
                        IconButton(
                            onClick = { ExternalIntentHelpers.dialPhoneNumber(context, ride.passengerPhone) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AnyRollerBgTertiary)
                                .testTag("active_ride_call_button")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Call Passenger", tint = AnyRollerBlue)
                        }
                    }
                }
            }

            // Compact single-line summary when collapsed (destination + waiting timer pill)
            if (isCollapsed) {
                Spacer(Modifier.height(6.dp))
                val compactDest = when (state.rideState) {
                    TerminalRideState.IN_PROGRESS -> {
                        val nextStop = ride.stops.getOrNull(ride.currentStopIndex)
                        if (nextStop != null) "Stop ${ride.currentStopIndex + 1}: ${nextStop.address}" else ride.dropoffAddress
                    }
                    else -> ride.pickupAddress
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        state.rideState != TerminalRideState.IN_PROGRESS -> AnyRollerGreen
                                        hasRemainingStop -> AnyRollerPurple
                                        else -> AnyRollerRed
                                    }
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = compactDest,
                            style = MaterialTheme.typography.bodySmall,
                            color = AnyRollerTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.rideState == TerminalRideState.WAITING) {
                        val freeLeft = (state.platformConfig.freeWaitSeconds - state.waitSecondsElapsed).coerceAtLeast(0)
                        val paidSecs = (state.waitSecondsElapsed - state.platformConfig.freeWaitSeconds).coerceAtLeast(0)
                        val displaySecs = if (freeLeft > 0) freeLeft else paidSecs
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = (if (freeLeft > 0) "FREE " else "PAID ") + "%02d:%02d".format(displaySecs / 60, displaySecs % 60),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFont,
                            color = if (freeLeft > 0) AnyRollerTextPrimary else AnyRollerOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Expandable Detailed Trip Section (Route, Wait Timer, PIN, Wait-at-Stop, Notices)
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Mid-trip passenger modification notice (inline inside card — zero map clutter)
                    if (!state.lastRideUpdateNotice.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AnyRollerOrange.copy(alpha = 0.16f))
                                .border(1.dp, AnyRollerOrange.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                                .testTag("ride_update_notice_banner"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = AnyRollerOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = state.lastRideUpdateNotice,
                                style = MaterialTheme.typography.labelSmall,
                                color = AnyRollerTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissRideUpdateNotice() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss trip update notice",
                                    tint = AnyRollerOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    RouteAddressBlock(
                        pickup = ride.pickupAddress,
                        dropoff = ride.dropoffAddress,
                        stops = ride.stops,
                        currentStopIndex = if (state.rideState == TerminalRideState.IN_PROGRESS) ride.currentStopIndex else -1
                    )

            // WAITING state: live timer, Forgot-to-Start-Trip reminder, and optional 4-digit PIN
            if (state.rideState == TerminalRideState.WAITING) {
                Spacer(Modifier.height(10.dp))
                val freeLeft = (state.platformConfig.freeWaitSeconds - state.waitSecondsElapsed).coerceAtLeast(0)
                val paidSecs = (state.waitSecondsElapsed - state.platformConfig.freeWaitSeconds).coerceAtLeast(0)
                val waitFee = (paidSecs / 60.0) * state.platformConfig.waitRatePerMinute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AnyRollerBgPrimary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (freeLeft > 0) "Free Wait Remaining" else "Paid Wait (+£%.2f @ £%.2f/min)".format(
                            waitFee,
                            state.platformConfig.waitRatePerMinute
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (freeLeft > 0) AnyRollerTextMuted else AnyRollerOrange
                    )
                    val displaySecs = if (freeLeft > 0) freeLeft else paidSecs
                    Text(
                        text = "%02d:%02d".format(displaySecs / 60, displaySecs % 60),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFont,
                        color = if (freeLeft > 0) AnyRollerTextPrimary else AnyRollerOrange
                    )
                }

                if (state.showStartTripReminder) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AnyRollerOrange.copy(alpha = 0.18f))
                            .border(1.dp, AnyRollerOrange, RoundedCornerShape(12.dp))
                            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                            .testTag("start_trip_reminder_strip"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = AnyRollerOrange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Moving away from pickup? Tap START TRIP below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnyRollerTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissStartTripReminder() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss reminder", tint = AnyRollerOrange, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (requiresPasscode) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passcodeInput,
                        onValueChange = { if (it.length <= 4) passcodeInput = it.filter(Char::isDigit) },
                        label = { Text("4-Digit Passenger Passcode (PIN)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnyRollerGreen,
                            unfocusedBorderColor = AnyRollerBorder,
                            focusedContainerColor = AnyRollerBgInput,
                            unfocusedContainerColor = AnyRollerBgInput,
                            focusedTextColor = AnyRollerTextPrimary,
                            unfocusedTextColor = AnyRollerTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ride_passcode_input")
                    )
                }
            }

            // IN_PROGRESS state: Wait at Stop stopwatch + Left-Stop reminder
            if (state.rideState == TerminalRideState.IN_PROGRESS) {
                Spacer(Modifier.height(10.dp))
                val stopWaitCost = (state.currentStopWaitSeconds / 60.0) * state.platformConfig.waitRatePerMinute
                val totalPaidCost = (state.totalPaidWaitSeconds / 60.0) * state.platformConfig.waitRatePerMinute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.isWaitingAtStop) AnyRollerOrange.copy(alpha = 0.16f) else AnyRollerBgPrimary)
                        .border(
                            width = 1.dp,
                            color = if (state.isWaitingAtStop) AnyRollerOrange else AnyRollerBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.toggleWaitAtStop() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("wait_at_stop_toggle"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (state.isWaitingAtStop) Icons.Rounded.PauseCircle else Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = if (state.isWaitingAtStop) AnyRollerOrange else AnyRollerTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (state.isWaitingAtStop) {
                                    "Waiting at Stop (+£%.2f)".format(stopWaitCost)
                                } else {
                                    "Wait at Stop (£%.2f/min)".format(state.platformConfig.waitRatePerMinute)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.isWaitingAtStop) AnyRollerOrange else AnyRollerTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.totalPaidWaitSeconds > 0) {
                                Text(
                                    text = "Total paid wait: %02d:%02d (+£%.2f)".format(
                                        state.totalPaidWaitSeconds / 60,
                                        state.totalPaidWaitSeconds % 60,
                                        totalPaidCost
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnyRollerTextMuted
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.isWaitingAtStop) {
                            Text(
                                text = "%02d:%02d".format(state.currentStopWaitSeconds / 60, state.currentStopWaitSeconds % 60),
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = JetBrainsMonoFont,
                                color = AnyRollerOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PAUSE",
                                style = MaterialTheme.typography.labelSmall,
                                color = AnyRollerOrange,
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = "Start Wait at Stop",
                                tint = AnyRollerGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "START",
                                style = MaterialTheme.typography.labelSmall,
                                color = AnyRollerGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Mid-trip rider abandonment (only after 3+ minutes waiting at stop)
                if (state.isWaitingAtStop && state.currentStopWaitSeconds >= 180 && state.platformConfig.allowRiderAbandonment) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            if (state.abandonmentWarningSent) {
                                viewModel.processRiderAbandonment()
                            } else {
                                viewModel.sendAbandonmentWarning()
                            }
                        },
                        enabled = !state.isBusy,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("midtrip_abandonment_button")
                    ) {
                        Text(
                            text = if (state.abandonmentWarningSent) {
                                "Confirm Rider Abandoned Trip"
                            } else {
                                "Send Abandonment Warning to Rider"
                            },
                            color = AnyRollerOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Left-Stop Reminder (>300m away from stop without advancing)
                if (state.showLeaveStopReminder && hasRemainingStop) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AnyRollerPurple.copy(alpha = 0.2f))
                            .border(1.dp, AnyRollerPurple, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("leave_stop_reminder_strip"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = AnyRollerPurple, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Left Stop ${ride.currentStopIndex + 1}? Slide GO NEXT below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnyRollerTextPrimary
                        )
                    }
                }
            }
            } // End of expandable details Column
            } // End of AnimatedVisibility

            Spacer(Modifier.height(12.dp))

            // Primary Ride Action Slide-to-Confirm CTA (mirrors web DriverTerminal.tsx SwipeButton)
            when (state.rideState) {
                TerminalRideState.EN_ROUTE_PICKUP -> {
                    SwipeConfirmButton(
                        label = "MARK AS ARRIVED",
                        trackColor = AnyRollerOrange,
                        contentColor = Color.White,
                        enabled = !state.isBusy,
                        resetKey = "${ride.id}:arrived:${state.showEarlyArrivalDialog}",
                        testTag = "arrived_pickup_button",
                        onConfirm = { viewModel.requestArrivedAtPickup() }
                    )
                }
                TerminalRideState.WAITING -> {
                    val pinReady = !requiresPasscode || passcodeInput.length == 4
                    SwipeConfirmButton(
                        label = if (pinReady) "START TRIP" else "ENTER 4-DIGIT PIN",
                        trackColor = AnyRollerGreen,
                        contentColor = AnyRollerBgPrimary,
                        enabled = !state.isBusy && pinReady,
                        resetKey = "${ride.id}:start:$pinReady",
                        testTag = "start_trip_button",
                        onConfirm = { viewModel.startTrip(passcodeInput) }
                    )
                }
                TerminalRideState.IN_PROGRESS -> {
                    if (hasRemainingStop) {
                        SwipeConfirmButton(
                            label = "GO NEXT • STOP ${ride.currentStopIndex + 1}",
                            trackColor = AnyRollerOrange,
                            contentColor = Color.White,
                            enabled = !state.isBusy,
                            resetKey = "${ride.id}:stop:${ride.currentStopIndex}",
                            testTag = "next_stop_button",
                            onConfirm = { viewModel.advanceMultiStop() }
                        )
                    } else {
                        SwipeConfirmButton(
                            label = "COMPLETE",
                            trackColor = AnyRollerRed,
                            contentColor = Color.White,
                            enabled = !state.isBusy,
                            resetKey = "${ride.id}:complete:${state.showCompleteConfirmDialog}",
                            testTag = "complete_trip_button",
                            onConfirm = { viewModel.requestCompleteTrip() }
                        )
                    }
                }
                else -> Unit
            }

            // Secondary actions (Cancel / No-Show) shown when expanded
            AnimatedVisibility(visible = !isCollapsed && state.rideState != TerminalRideState.IN_PROGRESS) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = { showCancelConfirm = true },
                        modifier = Modifier.testTag("cancel_trip_button")
                    ) {
                        Text("Cancel Trip", color = AnyRollerRed)
                    }
                    if (state.rideState == TerminalRideState.WAITING && state.platformConfig.allowRiderAbandonment) {
                        TextButton(
                            onClick = {
                                if (state.abandonmentWarningSent) {
                                    viewModel.processRiderAbandonment()
                                } else {
                                    viewModel.sendAbandonmentWarning()
                                }
                            },
                            enabled = !state.isBusy,
                            modifier = Modifier.testTag("rider_abandonment_button")
                        ) {
                            Text(
                                text = if (state.abandonmentWarningSent) "Confirm No-Show" else "Warn Rider (No-Show)",
                                color = AnyRollerOrange,
                                fontWeight = if (state.abandonmentWarningSent) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Cancel Trip Confirmation Dialog
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            containerColor = AnyRollerBgSecondary,
            title = { Text("Cancel Active Trip?", color = AnyRollerTextPrimary) },
            text = { Text("Cancelling an accepted ride may affect your cancellation rate.", color = AnyRollerTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    viewModel.cancelActiveRide()
                }) { Text("Confirm Cancel", color = AnyRollerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep Trip", color = AnyRollerTextPrimary) }
            }
        )
    }

    // Early Arrival (>200m from pickup pin) Confirmation Dialog
    if (state.showEarlyArrivalDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEarlyArrivalDialog() },
            containerColor = AnyRollerBgSecondary,
            title = { Text("Not at Pickup Pin Yet", color = AnyRollerTextPrimary) },
            text = {
                Text(
                    "GPS shows you are more than 200m away from the passenger's pickup pin. Mark arrived anyway?",
                    color = AnyRollerTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.markArrivedAtPickup(isAuto = false) },
                    modifier = Modifier.testTag("confirm_early_arrival_button")
                ) {
                    Text("Mark Arrived", color = AnyRollerGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEarlyArrivalDialog() }) {
                    Text("Not Yet", color = AnyRollerTextPrimary)
                }
            }
        )
    }

    // Complete Trip / Early Drop-Off (>300m) Confirmation Dialog
    if (state.showCompleteConfirmDialog) {
        val quickReasons = remember {
            listOf(
                "Passenger requested early drop-off",
                "Road closure / traffic",
                "Wrong destination pin"
            )
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissCompleteConfirmDialog() },
            containerColor = AnyRollerBgSecondary,
            title = {
                Text(
                    text = if (state.isEarlyDropoff) "Early Drop-Off Detected (>300m)" else "Complete Trip & Settle Fare?",
                    color = AnyRollerTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isEarlyDropoff) {
                        Text(
                            text = "You are more than 300m away from the booked drop-off. Please select or enter a brief reason:",
                            style = MaterialTheme.typography.bodySmall,
                            color = AnyRollerTextSecondary
                        )
                        quickReasons.forEach { preset ->
                            val selected = earlyDropoffReason == preset
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AnyRollerGreen.copy(alpha = 0.2f) else AnyRollerBgPrimary)
                                    .border(
                                        1.dp,
                                        if (selected) AnyRollerGreen else AnyRollerBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { earlyDropoffReason = preset }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) AnyRollerGreen else AnyRollerTextPrimary
                                )
                            }
                        }
                        OutlinedTextField(
                            value = earlyDropoffReason,
                            onValueChange = { earlyDropoffReason = it },
                            label = { Text("Early Drop-Off Reason") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnyRollerGreen,
                                unfocusedBorderColor = AnyRollerBorder,
                                focusedContainerColor = AnyRollerBgInput,
                                unfocusedContainerColor = AnyRollerBgInput,
                                focusedTextColor = AnyRollerTextPrimary,
                                unfocusedTextColor = AnyRollerTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("early_dropoff_reason_input")
                        )
                    } else {
                        val methodDesc = when {
                            ride.paymentMethod.equals("cash", ignoreCase = true) -> "Proceed to cash collection confirmation."
                            ride.hasCardOnFile -> "The passenger's saved card will be charged automatically."
                            else -> "A scannable Stripe payment QR will be shown to the passenger."
                        }
                        Text(text = methodDesc, color = AnyRollerTextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmCompleteTrip(if (state.isEarlyDropoff) earlyDropoffReason else null) },
                    enabled = !state.isEarlyDropoff || earlyDropoffReason.isNotBlank(),
                    modifier = Modifier.testTag("confirm_complete_trip_button")
                ) {
                    Text("Complete Trip", color = AnyRollerGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCompleteConfirmDialog() }) {
                    Text("Cancel", color = AnyRollerTextPrimary)
                }
            }
        )
    }
}

/**
 * Floating 10-Second Incoming Chat Quick-Reply Popup Banner (mirrors DriverTerminal.tsx:4794-4848):
 * Displays just above the active ride sheet when a passenger sends a message while chat is closed,
 * allowing hands-free 1-tap quick replies or tap-to-open chat without obscuring the road map.
 */
@Composable
fun IncomingChatQuickReplyPopup(
    passengerName: String,
    message: ChatMessage,
    onOpenChat: () -> Unit,
    onQuickReply: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickReplies = remember {
        listOf(
            "OK, got it!",
            "I'll be right there",
            "Traffic is heavy",
            "I'll be outside shortly",
            "I'm at location but can not find you."
        )
    }

    var progress by remember(message.id) { mutableFloatStateOf(1f) }
    LaunchedEffect(message.id) {
        val totalSteps = 100
        for (i in totalSteps downTo 0) {
            progress = i / totalSteps.toFloat()
            delay(100L)
        }
    }

    Surface(
        color = AnyRollerBgSecondary.copy(alpha = 0.96f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, AnyRollerGreen.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
            .testTag("incoming_chat_popup_banner")
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenChat),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AnyRollerGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = passengerName.firstOrNull()?.uppercase() ?: "P",
                        style = MaterialTheme.typography.titleSmall,
                        color = AnyRollerGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$passengerName • NEW MESSAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnyRollerGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = message.text?.takeIf { it.isNotBlank() } ?: "Sent a photo — tap to view",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnyRollerTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dismiss_chat_popup_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss incoming message popup",
                        tint = AnyRollerTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickReplies.forEachIndexed { idx, reply ->
                    Surface(
                        onClick = { onQuickReply(reply) },
                        shape = RoundedCornerShape(14.dp),
                        color = AnyRollerBgPrimary,
                        modifier = Modifier
                            .border(1.dp, AnyRollerGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .testTag("popup_quick_reply_$idx")
                    ) {
                        Text(
                            text = reply,
                            style = MaterialTheme.typography.labelMedium,
                            color = AnyRollerTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = AnyRollerGreen,
                trackColor = AnyRollerBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun PaymentHandshakeCard(
    ride: RideRequest,
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summary = remember(ride, state.activeCommissionRate) {
        resolveRideFinancialSummary(
            ride,
            FallbackFareConfig(state.activeCommissionRate, state.platformConfig.fixedTripFee)
        )
    }
    var cashInput by remember(ride.id) { mutableStateOf("%.2f".format(summary.totalFarePaid)) }
    var forceReason by remember(ride.id) { mutableStateOf("") }

    Surface(
        color = AnyRollerBgSecondary,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AnyRollerGreen.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (ride.paymentMethod.lowercase()) {
                        "cash" -> Icons.Rounded.Payments
                        "qr" -> Icons.Rounded.QrCode2
                        else -> Icons.Rounded.CreditCard
                    },
                    contentDescription = null,
                    tint = AnyRollerGreen
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Trip Payment Settlement", style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                    Text("Method: ${ride.paymentMethod.uppercase()}", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AnyRollerBgPrimary)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Fare", color = AnyRollerTextSecondary)
                    Text(
                        "£%.2f".format(summary.totalFarePaid),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = JetBrainsMonoFont,
                        color = AnyRollerGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (state.totalPaidWaitSeconds > 0) {
                    val waitFee = (state.totalPaidWaitSeconds / 60.0) * state.platformConfig.waitRatePerMinute
                    Text(
                        text = "Includes %02d:%02d paid wait (+£%.2f)".format(
                            state.totalPaidWaitSeconds / 60,
                            state.totalPaidWaitSeconds % 60,
                            waitFee
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = AnyRollerOrange
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (ride.paymentMethod.equals("cash", ignoreCase = true)) {
                if (state.isAwaitingCashConfirm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AnyRollerOrange.copy(alpha = 0.14f))
                            .border(1.dp, AnyRollerOrange, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                            .testTag("awaiting_cash_confirm_panel"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (state.cashConfirmSecondsLeft > 0) {
                                "Awaiting Passenger Confirmation (${state.cashConfirmSecondsLeft}s)"
                            } else {
                                "Passenger Did Not Confirm in Time"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = AnyRollerOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.cashConfirmSecondsLeft > 0) {
                                "The passenger has been prompted to confirm the unpaid cash balance on their phone. Trip will auto-finalize once confirmed."
                            } else {
                                "You may now record a brief reason and force-complete this trip."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AnyRollerTextSecondary
                        )

                        if (state.cashConfirmSecondsLeft == 0) {
                            OutlinedTextField(
                                value = forceReason,
                                onValueChange = { forceReason = it },
                                label = { Text("Force Complete Reason (optional)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AnyRollerOrange,
                                    unfocusedBorderColor = AnyRollerBorder,
                                    focusedContainerColor = AnyRollerBgInput,
                                    unfocusedContainerColor = AnyRollerBgInput,
                                    focusedTextColor = AnyRollerTextPrimary,
                                    unfocusedTextColor = AnyRollerTextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("force_complete_reason_input")
                            )
                            Button(
                                onClick = {
                                    viewModel.submitCashPayment(
                                        cashCollected = cashInput.toDoubleOrNull() ?: 0.0,
                                        forceComplete = true,
                                        forceReason = forceReason.ifBlank { "Passenger did not respond within 45s" }
                                    )
                                },
                                enabled = !state.isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = AnyRollerOrange, contentColor = AnyRollerBgPrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("force_complete_cash_button")
                            ) {
                                Text("FORCE COMPLETE TRIP", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = cashInput,
                        onValueChange = { cashInput = it },
                        label = { Text("Cash Collected (£)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnyRollerGreen,
                            unfocusedBorderColor = AnyRollerBorder,
                            focusedContainerColor = AnyRollerBgInput,
                            unfocusedContainerColor = AnyRollerBgInput,
                            focusedTextColor = AnyRollerTextPrimary,
                            unfocusedTextColor = AnyRollerTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("cash_collected_input")
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.submitCashPayment(
                                cashCollected = cashInput.toDoubleOrNull() ?: summary.totalFarePaid,
                                forceComplete = false
                            )
                        },
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_cash_button")
                    ) {
                        Text("CONFIRM CASH RECEIVED", fontWeight = FontWeight.ExtraBold)
                    }
                }
            } else {
                if (!state.paymentQrUrl.isNullOrBlank()) {
                    val qrBitmap = remember(state.paymentQrUrl) {
                        QrCodeGenerator.generateQrBitmap(state.paymentQrUrl, 420)
                    }
                    if (qrBitmap != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(14.dp)
                                .testTag("onscreen_payment_qr_card"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "Passenger Scan to Pay QR Code",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(180.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Passenger Scan to Pay",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF111114),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto-finishes as soon as payment confirms",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF52525B)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedButton(
                        onClick = { ExternalIntentHelpers.openCustomTab(context, state.paymentQrUrl) },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerBlue),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = AnyRollerBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Payment Link / QR", color = AnyRollerBlue)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = {
                        val action = if (ride.hasCardOnFile) "auto_card" else "qr_manual_close"
                        viewModel.completeTripWithAction(action = action)
                    },
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("finalize_card_payment_button")
                ) {
                    Text(
                        if (ride.hasCardOnFile) "CHARGE CARD & FINISH" else "MARK PAYMENT COMPLETE",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun TripReviewOverlayCard(
    ride: RideRequest,
    state: DriverTerminalUiState,
    onSubmitRating: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = remember(ride, state.activeCommissionRate) {
        resolveRideFinancialSummary(
            ride,
            FallbackFareConfig(state.activeCommissionRate, state.platformConfig.fixedTripFee)
        )
    }
    var targetEarnings by remember { mutableStateOf(0f) }
    LaunchedEffect(summary.driverEarnings) {
        targetEarnings = summary.driverEarnings.toFloat()
    }
    val animatedEarnings by animateFloatAsState(
        targetValue = targetEarnings,
        animationSpec = tween(durationMillis = 800),
        label = "earningsCountUp"
    )

    var selectedStars by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    Surface(
        color = AnyRollerBgSecondary,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AnyRollerGreen.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AnyRollerGreen, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(6.dp))
            Text("Trip Completed!", style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
            Text(
                text = "+£%.2f".format(animatedEarnings),
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = JetBrainsMonoFont,
                color = AnyRollerGreen,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AnyRollerBgPrimary)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BreakdownRow("Base Fare", "£%.2f".format(summary.baseFare))
                BreakdownRow("Platform Fee (%.1f%%)".format(summary.appliedCommissionPct), "-£%.2f".format(summary.platformFee))
                if (summary.tipAmount > 0.0) {
                    BreakdownRow("Passenger Tip (100% Yours)", "+£%.2f".format(summary.tipAmount), AnyRollerGreen)
                }
                HorizontalDivider(color = AnyRollerBorder, modifier = Modifier.padding(vertical = 4.dp))
                BreakdownRow("Net Driver Earnings", "£%.2f".format(summary.driverEarnings), AnyRollerGreen)
            }

            Spacer(Modifier.height(14.dp))
            Text("Rate ${ride.passengerName}", style = MaterialTheme.typography.labelLarge, color = AnyRollerTextPrimary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (star in 1..5) {
                    IconButton(
                        onClick = { selectedStars = star },
                        modifier = Modifier.testTag("review_star_$star")
                    ) {
                        Icon(
                            imageVector = if (star <= selectedStars) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "Rate $star stars",
                            tint = if (star <= selectedStars) AnyRollerOrange else AnyRollerTextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            if (selectedStars < 5) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Required: Tell us what went wrong") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AnyRollerOrange,
                        unfocusedBorderColor = AnyRollerBorder,
                        focusedContainerColor = AnyRollerBgInput,
                        unfocusedContainerColor = AnyRollerBgInput,
                        focusedTextColor = AnyRollerTextPrimary,
                        unfocusedTextColor = AnyRollerTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("review_comment_input")
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSubmitRating(selectedStars, comment) },
                enabled = !state.isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("done_back_to_map_button")
            ) {
                Text(
                    text = if (state.queuedStackedRide != null) {
                        "DONE — START NEXT QUEUED RIDE"
                    } else {
                        "DONE — BACK TO MAP"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, valueColor: Color = AnyRollerTextPrimary) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AnyRollerTextMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMonoFont, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RouteAddressBlock(
    pickup: String,
    dropoff: String,
    stops: List<RideStop> = emptyList(),
    currentStopIndex: Int = -1
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AnyRollerGreen))
            Spacer(Modifier.width(10.dp))
            Text(
                text = pickup,
                style = MaterialTheme.typography.bodyMedium,
                color = AnyRollerTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        stops.forEachIndexed { idx, stop ->
            val isCompleted = currentStopIndex >= 0 && idx < currentStopIndex
            val isCurrent = currentStopIndex >= 0 && idx == currentStopIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> AnyRollerGreen.copy(alpha = 0.35f)
                                isCurrent -> AnyRollerPurple
                                else -> AnyRollerPurple.copy(alpha = 0.65f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Stop ${idx + 1} completed",
                            tint = AnyRollerGreen,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    text = buildString {
                        append("Stop ${idx + 1}: ")
                        append(stop.address.ifBlank { "Intermediate Stop" })
                        if (isCurrent) append(" (Next)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isCompleted -> AnyRollerTextMuted
                        isCurrent -> AnyRollerPurple
                        else -> AnyRollerTextSecondary
                    },
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AnyRollerRed))
            Spacer(Modifier.width(10.dp))
            Text(
                text = dropoff,
                style = MaterialTheme.typography.bodyMedium,
                color = AnyRollerTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RideChatModalSheet(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onDismiss: () -> Unit
) {
    val ride = state.activeRide ?: return
    var messageText by remember { mutableStateOf("") }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var activeLightboxImageUrl by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendChatMessage(text = "", imageUri = uri)
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
            color = AnyRollerBgPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .clickable(enabled = false) {}
        ) {
            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AnyRollerBgSecondary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Chat with ${ride.passengerName}", style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                        Text("Live in-app chat • 45s smart offline fallback", style = MaterialTheme.typography.labelSmall, color = AnyRollerGreen)
                    }
                    Row {
                        IconButton(onClick = { showSafetyDialog = true }, modifier = Modifier.testTag("chat_report_button")) {
                            Icon(Icons.Rounded.Report, contentDescription = "Report or Block Passenger", tint = AnyRollerOrange)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_chat_button")) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Chat", tint = AnyRollerTextPrimary)
                        }
                    }
                }

                // 1-Tap Quick Replies (with 2-minute anti-spam cooldown mirroring DriverTerminal.tsx)
                val quickCooldown = state.quickMessageCooldownSeconds
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("I'm outside", "On my way", "Stuck in traffic, 2 mins", "Where are you?").forEach { quick ->
                            Surface(
                                onClick = { viewModel.sendQuickChatMessage(quick) },
                                enabled = quickCooldown == 0,
                                shape = RoundedCornerShape(16.dp),
                                color = if (quickCooldown == 0) AnyRollerBgSecondary else AnyRollerBgSecondary.copy(alpha = 0.45f),
                                modifier = Modifier.border(
                                    1.dp,
                                    if (quickCooldown == 0) AnyRollerBorder else AnyRollerBorder.copy(alpha = 0.4f),
                                    RoundedCornerShape(16.dp)
                                )
                            ) {
                                Text(
                                    text = quick,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (quickCooldown == 0) AnyRollerTextPrimary else AnyRollerTextMuted,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    if (quickCooldown > 0) {
                        Text(
                            text = "Quick replies ready again in ${quickCooldown}s (you can still type below)",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnyRollerTextMuted,
                            modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.chatMessages, key = { it.id }) { msg ->
                        val isMe = msg.senderId == state.uid
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isMe) AnyRollerGreenDeep else AnyRollerBgSecondary)
                                    .padding(10.dp)
                            ) {
                                if (!msg.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = msg.imageUrl,
                                        contentDescription = "Chat photo — tap to view fullscreen",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(180.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { activeLightboxImageUrl = msg.imageUrl }
                                            .testTag("chat_photo_thumb_${msg.id}")
                                    )
                                    if (!msg.text.isNullOrBlank()) Spacer(Modifier.height(6.dp))
                                }
                                if (!msg.text.isNullOrBlank()) {
                                    Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = AnyRollerTextPrimary)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AnyRollerBgSecondary)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.testTag("chat_photo_button")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = "Attach Photo", tint = AnyRollerTextMuted)
                    }
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Message passenger...", color = AnyRollerTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnyRollerGreen,
                            unfocusedBorderColor = AnyRollerBorder,
                            focusedContainerColor = AnyRollerBgInput,
                            unfocusedContainerColor = AnyRollerBgInput,
                            focusedTextColor = AnyRollerTextPrimary,
                            unfocusedTextColor = AnyRollerTextPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).testTag("chat_message_input")
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendChatMessage(messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AnyRollerGreen)
                            .testTag("chat_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send Message", tint = AnyRollerBgPrimary)
                    }
                }
            }
        }
    }

    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            containerColor = AnyRollerBgSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Flag, contentDescription = null, tint = AnyRollerRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Report / Block Passenger", color = AnyRollerTextPrimary)
                }
            },
            text = {
                Column {
                    Text("Submit a safety report to AnyRoller Support.", color = AnyRollerTextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text("Reason") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnyRollerRed,
                            unfocusedBorderColor = AnyRollerBorder,
                            focusedTextColor = AnyRollerTextPrimary,
                            unfocusedTextColor = AnyRollerTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val reason = reportReason.ifBlank { "Safety concern during trip" }
                    viewModel.reportAndBlockActivePassenger(reason, blockUser = true)
                    showSafetyDialog = false
                }) { Text("Report & Block", color = AnyRollerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyDialog = false }) { Text("Cancel", color = AnyRollerTextMuted) }
            }
        )
    }

    activeLightboxImageUrl?.let { imageUrl ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable { activeLightboxImageUrl = null }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Fullscreen chat photo preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { activeLightboxImageUrl = null },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .testTag("close_chat_lightbox_button")
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close fullscreen photo", tint = Color.White)
            }
        }
    }
}
