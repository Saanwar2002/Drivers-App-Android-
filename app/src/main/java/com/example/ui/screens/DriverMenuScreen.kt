package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.service.ExternalIntentHelpers
import com.example.service.FloatingRideOverlayController
import com.example.service.UrgentPushEventBus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AnyRollerBgInput
import com.example.ui.theme.AnyRollerBgPrimary
import com.example.ui.theme.AnyRollerBgSecondary
import com.example.ui.theme.AnyRollerBgTertiary
import com.example.ui.theme.AnyRollerBlue
import com.example.ui.theme.AnyRollerBorder
import com.example.ui.theme.AnyRollerGreen
import com.example.ui.theme.AnyRollerOrange
import com.example.ui.theme.AnyRollerRed
import com.example.ui.theme.AnyRollerTextMuted
import com.example.ui.theme.AnyRollerTextPrimary
import com.example.ui.theme.AnyRollerTextSecondary
import com.example.ui.theme.JetBrainsMonoFont
import com.example.viewmodel.DriverOverlayRoute
import com.example.viewmodel.DriverTab
import com.example.viewmodel.DriverTerminalUiState
import com.example.viewmodel.DriverTerminalViewModel

/**
 * Logically grouped & ordered AnyRoller Driver Menu & Settings:
 * 0. Profile + Live "This Shift" Summary + Daily Driving Hours Safety Bar + Action Banners
 * 1. Shift & Dispatch Controls (Coffee Break, Stack Next Ride, Last Job, Destination Mode, Auto-Accept)
 * 2. Navigation & Cockpit Display (Auto-Launch External Nav [ON by default], Nav App, Map Theme, PiP, Screen Awake, Brightness Boost)
 * 3. Safety, Audio & Alerts (PIN Passcode, Over-Speed Alert, Fatigue Reminder, Emergency Contact, Audio Mutes, Scheduled Alarms, WhatsApp Fallback)
 * 4. Vehicle & Service Tiers (Pet-Friendly, Admin Vehicle Tiers, Live Commission Rate)
 * 5. Operations & Business (Documents, Weekly Availability, Zones & Radar, Analytics, Expenses & Tax)
 * 6. AnyTrader Ecosystem & Account (Marketplace, Mechanic Quotes, Help, Refer a Driver, Sign Out)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriverMenuScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onToggleOnline: () -> Unit,
    onClose: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val profile = state.profile
    val cockpit = state.cockpitSettings

    var showHomeModal by remember { mutableStateOf(false) }
    var pendingToggleConfirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

    // Logical collapsible sections
    var expandNavCockpit by remember { mutableStateOf(true) } // Expanded by default so Auto-Launch toggle is immediately accessible
    var expandSafetyAudio by remember { mutableStateOf(false) }
    var expandVehicleTiers by remember { mutableStateOf(false) }
    var showWhatsAppCard by remember { mutableStateOf(!state.notificationPrefs.whatsappWindowActive) }
    var emergencyPhoneDraft by remember(cockpit.emergencyContactPhone) { mutableStateOf(cockpit.emergencyContactPhone) }

    val batteryOptimized = remember { !ExternalIntentHelpers.isIgnoringBatteryOptimizations(context) }

    val drivenHours = state.metrics.onlineSecondsToday / 3600.0
    val maxHours = state.platformConfig.maxDailyDriverHours.coerceAtLeast(1.0)
    val drivingProgress = (drivenHours / maxHours).toFloat().coerceIn(0f, 1f)
    val drivingColor = when {
        drivingProgress >= 0.9f -> AnyRollerRed
        drivingProgress >= 0.75f -> AnyRollerOrange
        else -> AnyRollerGreen
    }

    val shiftHrs = state.shiftOnlineSeconds / 3600L
    val shiftMins = (state.shiftOnlineSeconds % 3600L) / 60L
    val shiftDurationText = if (shiftHrs > 0L) "${shiftHrs}h ${shiftMins}m" else "${shiftMins}m"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header with Quick Online / Break / Offline Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Driver Menu & Settings", style = MaterialTheme.typography.titleLarge, color = AnyRollerTextPrimary, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val pillColor = when {
                    state.isOnBreak -> AnyRollerOrange
                    state.isOnline -> AnyRollerGreen
                    else -> AnyRollerTextMuted
                }
                Surface(
                    onClick = {
                        if (state.isOnBreak) viewModel.endBreak() else onToggleOnline()
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (state.isOnline) pillColor.copy(alpha = 0.2f) else AnyRollerBgSecondary,
                    modifier = Modifier
                        .border(1.dp, if (state.isOnline) pillColor else AnyRollerBorder, RoundedCornerShape(18.dp))
                        .testTag("menu_online_toggle_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(pillColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                state.isOnBreak -> "BREAK"
                                state.isOnline -> "ONLINE"
                                else -> "OFFLINE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.isOnline) pillColor else AnyRollerTextPrimary
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_driver_menu_button")) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close Menu", tint = AnyRollerTextPrimary)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =====================================================================
            // 0. PROFILE, LIVE SHIFT SUMMARY & DAILY DRIVING HOURS BAR
            // =====================================================================
            Surface(
                onClick = { viewModel.openOverlay(DriverOverlayRoute.PROFILE) },
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
                    .testTag("menu_profile_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!profile.photoURL.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.photoURL,
                                contentDescription = "Driver Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(54.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(54.dp).clip(CircleShape).background(AnyRollerGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(profile.initials, style = MaterialTheme.typography.titleLarge, color = AnyRollerGreen)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.fullName, style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Star, contentDescription = null, tint = AnyRollerOrange, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "%.2f • %s".format(
                                        profile.rating,
                                        if (profile.isGoldDriver) "Gold Driver" else "Standard Driver"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AnyRollerTextSecondary
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AnyRollerTextMuted)
                    }

                    // Live "This Shift" Summary Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AnyRollerBgPrimary)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("menu_shift_summary_strip"),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ShiftStatCell(label = "This Shift", value = shiftDurationText, color = AnyRollerTextPrimary)
                        ShiftStatCell(label = "Trips", value = "${state.shiftTripsCompleted}", color = AnyRollerBlue)
                        ShiftStatCell(
                            label = "Live Rate",
                            value = "£%.0f/hr".format(state.shiftHourlyRate),
                            color = AnyRollerGreen
                        )
                        ShiftStatCell(
                            label = "Paid / Empty",
                            value = "%.1f/%.1fmi".format(state.shiftPaidMiles, state.shiftDeadheadMiles),
                            color = AnyRollerOrange
                        )
                    }

                    // Daily Driving Hours Safety Bar (synced to Admin maxDailyDriverHours)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Timer, contentDescription = null, tint = drivingColor, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Daily Driving Hours", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            }
                            Text(
                                text = "%.1f / %.0f hrs".format(drivenHours, maxHours),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = drivingColor
                            )
                        }
                        LinearProgressIndicator(
                            progress = { drivingProgress },
                            color = drivingColor,
                            trackColor = AnyRollerBgPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // Conditional Priority Action Banners (housed inside Menu, never cluttering active map)
            if (state.fatigueReminderActive && state.isOnline && !state.isOnBreak) {
                Surface(
                    color = AnyRollerOrange.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AnyRollerOrange.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.LocalCafe, contentDescription = null, tint = AnyRollerOrange)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Fatigue Break Reminder", style = MaterialTheme.typography.labelLarge, color = AnyRollerOrange)
                                Text(
                                    "You've been driving ${cockpit.fatigueReminderHours}h+ continuously. Take a quick 15m coffee break?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AnyRollerTextSecondary
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.startBreak(15) }) {
                            Text("15m Break", color = AnyRollerOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (state.globalPlatformStatus.taxiComingSoon) {
                Surface(
                    color = AnyRollerBlue.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBlue.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = AnyRollerBlue)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.globalPlatformStatus.taxiComingSoonTitle?.takeIf { it.isNotBlank() } ?: "Dispatch Soft-Launch Notice",
                                style = MaterialTheme.typography.labelLarge,
                                color = AnyRollerBlue
                            )
                            Text(
                                text = state.globalPlatformStatus.taxiComingSoonMessage?.takeIf { it.isNotBlank() }
                                    ?: "Passenger bookings are currently in scheduled rollout. Keep your documents verified for launch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AnyRollerTextSecondary
                            )
                        }
                    }
                }
            }

            if (state.needsStripeSetup) {
                Surface(
                    onClick = { viewModel.launchStripeConnectOnboarding() },
                    color = AnyRollerBlue.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AnyRollerBlue.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .testTag("menu_stripe_setup_banner")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = AnyRollerBlue)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Payments Setup Required", style = MaterialTheme.typography.labelLarge, color = AnyRollerBlue)
                            Text("Tap to connect your UK bank account via Stripe Express for payouts.", style = MaterialTheme.typography.bodySmall, color = AnyRollerTextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AnyRollerBlue)
                    }
                }
            }

            if (state.pendingFeesAmount > 0.0) {
                Surface(
                    onClick = { viewModel.launchSettlePlatformFees() },
                    color = AnyRollerRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AnyRollerRed.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .testTag("menu_pending_fees_banner")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Payments, contentDescription = null, tint = AnyRollerRed)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pending Platform Fees (£%.2f)".format(state.pendingFeesAmount),
                                style = MaterialTheme.typography.labelLarge,
                                color = AnyRollerRed
                            )
                            Text("Tap to settle cash-trip commission balance and keep dispatch uninterrupted.", style = MaterialTheme.typography.bodySmall, color = AnyRollerTextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AnyRollerRed)
                    }
                }
            }

            state.unreadBroadcast?.let { broadcast ->
                Surface(
                    onClick = {
                        viewModel.markNotificationRead(broadcast.id)
                        viewModel.closeOverlay()
                        viewModel.selectTab(DriverTab.MESSAGES)
                    },
                    color = AnyRollerGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AnyRollerGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .testTag("menu_broadcast_banner")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = AnyRollerGreen)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = broadcast.title.ifBlank { "AnyRoller Dispatch Broadcast" },
                                style = MaterialTheme.typography.labelLarge,
                                color = AnyRollerGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = broadcast.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = AnyRollerTextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AnyRollerGreen)
                    }
                }
            }

            if (batteryOptimized) {
                Surface(
                    onClick = { ExternalIntentHelpers.openBatteryOptimizationSettings(context) },
                    color = AnyRollerOrange.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerOrange.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BatteryAlert, contentDescription = null, tint = AnyRollerOrange)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Disable Battery Restrictions", style = MaterialTheme.typography.labelLarge, color = AnyRollerOrange)
                            Text("Tap to prevent Android from delaying live ride offers in background.", style = MaterialTheme.typography.bodySmall, color = AnyRollerTextSecondary)
                        }
                    }
                }
            }

            // =====================================================================
            // SECTION 1 — SHIFT & DISPATCH CONTROLS (Always expanded)
            // =====================================================================
            SectionGroupLabel("1. SHIFT & DISPATCH CONTROLS")
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Coffee Break / Pause New Requests
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalCafe, contentDescription = null, tint = AnyRollerOrange, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Coffee Break (Pause New Requests)", style = MaterialTheme.typography.bodyMedium, color = AnyRollerTextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (state.isOnBreak) {
                                        if (state.breakIsOpenEnded) "Requests paused (Open break)"
                                        else "Paused • %02d:%02d remaining".format(state.breakSecondsLeft / 60, state.breakSecondsLeft % 60)
                                    } else "Take a breather without losing your shift streak",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isOnBreak) AnyRollerOrange else AnyRollerTextMuted
                                )
                            }
                        }
                        if (state.isOnBreak) {
                            Button(
                                onClick = { viewModel.endBreak() },
                                colors = ButtonDefaults.buttonColors(containerColor = AnyRollerOrange, contentColor = AnyRollerBgPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("menu_resume_break_button")
                            ) {
                                Text("Resume Receiving Ride Requests", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(15 to "15m", 30 to "30m", null to "Open").forEach { (mins, label) ->
                                    OutlinedButton(
                                        onClick = { viewModel.startBreak(mins) },
                                        enabled = state.isOnline && state.activeRide == null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("menu_break_${mins ?: "open"}")
                                    ) {
                                        Text(label, color = if (state.isOnline) AnyRollerOrange else AnyRollerTextMuted, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = AnyRollerBorder)

                    MenuSwitchRow(
                        title = "Stack Next Ride",
                        subtitle = "Receive queued offers while finishing current trip",
                        checked = profile.stackingEnabled,
                        testTag = "switch_stacking",
                        onCheckedChange = { enabled ->
                            pendingToggleConfirm = Triple(
                                if (enabled) "Enable Stack Next Ride?" else "Disable Stack Next Ride?",
                                if (enabled) "You will receive queued next-ride offers while approaching your current drop-off."
                                else "You will not receive queued offers until your current trip is finished.",
                                { viewModel.updateUserPref("isStackingEnabled", enabled) }
                            )
                        }
                    )
                    MenuSwitchRow(
                        title = "Last Job Mode",
                        subtitle = "Go Offline automatically after completing current ride",
                        checked = profile.lastJob,
                        testTag = "switch_last_job",
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                pendingToggleConfirm = Triple(
                                    "Enable Last Job Mode?",
                                    "You will be switched Offline automatically once your current trip finishes.",
                                    { viewModel.updateUserPref("isLastJob", true) }
                                )
                            } else {
                                viewModel.updateUserPref("isLastJob", false)
                            }
                        }
                    )
                    MenuSwitchRow(
                        title = "Destination Mode",
                        subtitle = profile.homeAddress?.takeIf { it.isNotBlank() } ?: "Head toward home address",
                        checked = profile.destinationModeActive,
                        testTag = "switch_destination_mode",
                        onCheckedChange = { enabled ->
                            pendingToggleConfirm = Triple(
                                if (enabled) "Enable Destination Mode?" else "Disable Destination Mode?",
                                if (enabled) "Dispatch will prioritise trips heading toward your saved Home destination."
                                else "You will resume receiving ride offers in all directions.",
                                { viewModel.updateUserPref("destinationModeActive", enabled) }
                            )
                        }
                    )
                    OutlinedButton(
                        onClick = { showHomeModal = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("edit_home_address_button")
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(16.dp), tint = AnyRollerGreen)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            profile.homeAddress?.takeIf { it.isNotBlank() } ?: "Set Home UK Postcode / Address",
                            color = AnyRollerTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(color = AnyRollerBorder)

                    // Auto-Accept Mode (OFF by default, with safety confirmation & min fare / max pickup rules)
                    MenuSwitchRow(
                        title = "Auto-Accept Matching Rides",
                        subtitle = if (cockpit.autoAcceptEnabled) {
                            "Active: Min £%.0f • Max %.0f mi pickup".format(cockpit.autoAcceptMinFare, cockpit.autoAcceptMaxPickupMiles)
                        } else "Off by default — automatically accept offers matching your rules",
                        checked = cockpit.autoAcceptEnabled,
                        testTag = "switch_auto_accept",
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                pendingToggleConfirm = Triple(
                                    "Enable Auto-Accept Mode?",
                                    "Incoming offers that meet your minimum fare and pickup distance will be accepted automatically while you are Online.",
                                    { viewModel.updateCockpitSettings { it.copy(autoAcceptEnabled = true) } }
                                )
                            } else {
                                viewModel.updateCockpitSettings { it.copy(autoAcceptEnabled = false) }
                            }
                        }
                    )
                    AnimatedVisibility(visible = cockpit.autoAcceptEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Minimum Fare (£)", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(6.0, 8.0, 12.0, 15.0, 20.0).forEach { minFare ->
                                    val sel = cockpit.autoAcceptMinFare == minFare
                                    ChipOption(
                                        label = "£${minFare.toInt()}",
                                        selected = sel,
                                        testTag = "auto_accept_fare_${minFare.toInt()}",
                                        onClick = { viewModel.updateCockpitSettings { it.copy(autoAcceptMinFare = minFare) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Text("Max Pickup Distance", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(1.5, 3.0, 5.0, 8.0).forEach { maxMi ->
                                    val sel = cockpit.autoAcceptMaxPickupMiles == maxMi
                                    ChipOption(
                                        label = "${maxMi}mi",
                                        selected = sel,
                                        testTag = "auto_accept_dist_${maxMi}",
                                        onClick = { viewModel.updateCockpitSettings { it.copy(autoAcceptMaxPickupMiles = maxMi) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // SECTION 2 — NAVIGATION & COCKPIT DISPLAY
            // =====================================================================
            SectionGroupLabel("2. NAVIGATION & COCKPIT DISPLAY")
            CollapsibleSettingsCard(
                icon = Icons.Rounded.Navigation,
                iconTint = AnyRollerBlue,
                title = "Navigation & Cockpit Display",
                subtitle = "${profile.defaultNavApp} • Auto-Launch ${if (cockpit.autoLaunchExternalNav) "ON" else "OFF"} • Map ${cockpit.mapThemeMode.replaceFirstChar { it.uppercase() }}",
                expanded = expandNavCockpit,
                testTag = "section_nav_cockpit_header",
                onToggle = { expandNavCockpit = !expandNavCockpit }
            ) {
                // Auto-Launch External Navigation (ON by default, toggleable OFF)
                MenuSwitchRow(
                    title = "Auto-Launch External Navigation",
                    subtitle = "Auto-opens ${profile.defaultNavApp} on Accept, Start Trip, Multi-Stops, route updates & queued rides",
                    checked = cockpit.autoLaunchExternalNav,
                    testTag = "switch_auto_launch_external_nav",
                    onCheckedChange = { enabled ->
                        viewModel.updateCockpitSettings { it.copy(autoLaunchExternalNav = enabled) }
                    }
                )

                // Default Navigation App Segmented Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Default Navigation App", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Google Maps", "Waze").forEach { app ->
                            val sel = profile.defaultNavApp.equals(app, ignoreCase = true)
                            ChipOption(
                                label = app,
                                selected = sel,
                                testTag = "nav_app_${app.lowercase().replace(" ", "_")}",
                                onClick = { viewModel.updateUserPref("defaultNavApp", app) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Auto-Night / High-Contrast Map Theme
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Map Theme (High-Contrast Day / Night)", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("auto" to "Auto", "dark" to "Dark", "light" to "Daylight").forEach { (key, label) ->
                            ChipOption(
                                label = label,
                                selected = cockpit.mapThemeMode.equals(key, ignoreCase = true),
                                testTag = "map_theme_$key",
                                onClick = { viewModel.updateCockpitSettings { it.copy(mapThemeMode = key) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(color = AnyRollerBorder)

                // Opt-In Floating Ride Info Overlay Card (Primary companion over Google Maps / Waze)
                val fullScreenTick by UrgentPushEventBus.isFullScreenVisible.collectAsStateWithLifecycle()
                val hasOverlayPermission = remember(fullScreenTick, cockpit.floatingOverlayEnabled) {
                    FloatingRideOverlayController.canDrawOverlays(context)
                }
                MenuSwitchRow(
                    title = "Floating Ride Info Overlay",
                    subtitle = when {
                        cockpit.floatingOverlayEnabled && hasOverlayPermission ->
                            "Active — draggable Ride Info Card floats over Google Maps & Waze"
                        cockpit.floatingOverlayEnabled ->
                            "Permission needed — allow 'Display over other apps' in Android Settings"
                        else ->
                            "Float a readable, draggable Ride Info Card over Google Maps or Waze"
                    },
                    checked = cockpit.floatingOverlayEnabled,
                    testTag = "switch_floating_overlay",
                    onCheckedChange = { enabled ->
                        viewModel.updateCockpitSettings { it.copy(floatingOverlayEnabled = enabled) }
                        if (enabled && !FloatingRideOverlayController.canDrawOverlays(context)) {
                            FloatingRideOverlayController.openOverlayPermissionSettings(context)
                        }
                    }
                )
                if (cockpit.floatingOverlayEnabled && !hasOverlayPermission) {
                    OutlinedButton(
                        onClick = { FloatingRideOverlayController.openOverlayPermissionSettings(context) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("grant_overlay_permission_button")
                    ) {
                        Text(
                            text = "Grant 'Display Over Other Apps' Permission",
                            color = AnyRollerOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                MenuSwitchRow(
                    title = "Wide Picture-in-Picture Strip",
                    subtitle = "Automatic zero-permission fallback over Google Maps / Waze when Floating Overlay is off",
                    checked = cockpit.pipEnabled,
                    testTag = "switch_pip_enabled",
                    onCheckedChange = { enabled ->
                        viewModel.updateCockpitSettings { it.copy(pipEnabled = enabled) }
                    }
                )
                MenuSwitchRow(
                    title = "Keep Screen Awake While Online",
                    subtitle = "Prevent screen sleep during shifts and active rides",
                    checked = cockpit.keepScreenAwake,
                    testTag = "switch_keep_screen_awake",
                    onCheckedChange = { enabled ->
                        viewModel.updateCockpitSettings { it.copy(keepScreenAwake = enabled) }
                    }
                )
                MenuSwitchRow(
                    title = "Auto-Brightness Boost",
                    subtitle = "Boost cockpit screen brightness (85%) in bright UK daylight",
                    checked = cockpit.autoBrightnessBoost,
                    testTag = "switch_auto_brightness_boost",
                    onCheckedChange = { enabled ->
                        viewModel.updateCockpitSettings { it.copy(autoBrightnessBoost = enabled) }
                    }
                )
            }

            // =====================================================================
            // SECTION 3 — SAFETY, AUDIO & ALERTS
            // =====================================================================
            SectionGroupLabel("3. SAFETY, AUDIO & ALERTS")
            CollapsibleSettingsCard(
                icon = Icons.Rounded.Shield,
                iconTint = AnyRollerOrange,
                title = "Safety, Audio & Alerts",
                subtitle = "Speed alert ${if (cockpit.speedAlertMph == 0) "Off" else "${cockpit.speedAlertMph} mph"} • Fatigue ${if (cockpit.fatigueReminderHours == 0) "Off" else "${cockpit.fatigueReminderHours}h"}",
                expanded = expandSafetyAudio,
                testTag = "section_safety_audio_header",
                onToggle = { expandSafetyAudio = !expandSafetyAudio }
            ) {
                MenuSwitchRow(
                    title = "Require 4-Digit Passcode (PIN)",
                    subtitle = "Verify passenger PIN before starting every trip",
                    checked = profile.requirePasscode,
                    testTag = "switch_passcode",
                    onCheckedChange = { enabled ->
                        pendingToggleConfirm = Triple(
                            if (enabled) "Require 4-Digit Passcode?" else "Disable Passcode Requirement?",
                            if (enabled) "Passengers must read you their 4-digit PIN before you can start any trip."
                            else "Trips will start without asking for a 4-digit passenger PIN.",
                            { viewModel.updateUserPref("requirePasscode", enabled) }
                        )
                    }
                )

                // Over-Speed Alert Threshold
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Over-Speed Gentle Voice & Haptic Alert", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(0, 30, 40, 50, 60, 70).forEach { mph ->
                            ChipOption(
                                label = if (mph == 0) "Off" else "$mph mph",
                                selected = cockpit.speedAlertMph == mph,
                                testTag = "speed_alert_$mph",
                                onClick = { viewModel.updateCockpitSettings { it.copy(speedAlertMph = mph) } }
                            )
                        }
                    }
                }

                // Smart Fatigue Break Reminder
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Smart Fatigue Break Reminder", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0, 2, 3, 4, 5).forEach { hrs ->
                            ChipOption(
                                label = if (hrs == 0) "Off" else "${hrs}h",
                                selected = cockpit.fatigueReminderHours == hrs,
                                testTag = "fatigue_hours_$hrs",
                                onClick = { viewModel.updateCockpitSettings { it.copy(fatigueReminderHours = hrs) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Emergency Contact for 1-Tap Safety Toolkit Live Share
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Trusted Emergency Contact (Safety Share)", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = emergencyPhoneDraft,
                            onValueChange = { emergencyPhoneDraft = it },
                            placeholder = { Text("+44 7700 900000", color = AnyRollerTextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnyRollerGreen,
                                unfocusedBorderColor = AnyRollerBorder,
                                focusedContainerColor = AnyRollerBgInput,
                                unfocusedContainerColor = AnyRollerBgInput,
                                focusedTextColor = AnyRollerTextPrimary,
                                unfocusedTextColor = AnyRollerTextPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("emergency_contact_input")
                        )
                        Button(
                            onClick = {
                                viewModel.updateCockpitSettings { it.copy(emergencyContactPhone = emergencyPhoneDraft.trim()) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AnyRollerBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp).testTag("save_emergency_contact_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = AnyRollerBorder)

                MenuSwitchRow(
                    title = "Mute Ride Offer Alerts",
                    subtitle = "Silence chime for incoming ride offers",
                    checked = profile.muteRideOfferAlerts,
                    testTag = "switch_mute_offers",
                    onCheckedChange = { enabled ->
                        pendingToggleConfirm = Triple(
                            if (enabled) "Mute Ride Offer Alerts?" else "Unmute Ride Offer Alerts?",
                            if (enabled) "Incoming ride offer chimes will be silenced. You may miss offers if not watching the screen."
                            else "Incoming ride offer chimes will play normally.",
                            { viewModel.updateUserPref("muteRideOfferAlerts", enabled) }
                        )
                    }
                )
                MenuSwitchRow(
                    title = "Mute Heads-Up Voice & Chat Read-Aloud",
                    subtitle = "Silence spoken navigation callouts and hands-free passenger messages",
                    checked = profile.muteHeadsUpVolume,
                    testTag = "switch_mute_headsup",
                    onCheckedChange = { enabled ->
                        pendingToggleConfirm = Triple(
                            if (enabled) "Mute Heads-Up Voice?" else "Unmute Heads-Up Voice?",
                            if (enabled) "Spoken turn-by-turn directions and hands-free chat read-aloud will be muted."
                            else "Spoken navigation directions and incoming passenger messages will be read aloud.",
                            { viewModel.updateUserPref("muteHeadsUpVolume", enabled) }
                        )
                    }
                )
                MenuSwitchRow(
                    title = "Scheduled Job Reminder Alarms",
                    subtitle = "Local alerts 45 min & 15 min before accepted scheduled pickups",
                    checked = cockpit.scheduledRemindersEnabled,
                    testTag = "switch_scheduled_reminders",
                    onCheckedChange = { enabled ->
                        viewModel.updateCockpitSettings { it.copy(scheduledRemindersEnabled = enabled) }
                    }
                )
            }

            // 4-Tier Cost-Capped Offline & WhatsApp Alerts Card (Exact mirror of OfflineAlertsCard.tsx)
            OfflineWhatsAppAlertsCard(
                state = state,
                viewModel = viewModel,
                expanded = showWhatsAppCard,
                onToggleExpanded = { showWhatsAppCard = !showWhatsAppCard }
            )

            // =====================================================================
            // SECTION 4 — VEHICLE & SERVICE TIERS
            // =====================================================================
            SectionGroupLabel("4. VEHICLE & SERVICE TIERS")
            CollapsibleSettingsCard(
                icon = Icons.Rounded.DirectionsCar,
                iconTint = AnyRollerGreen,
                title = "Vehicle & Service Tiers",
                subtitle = "${state.localVehicleCategories.size} tiers active • %.0f%% commission".format(state.activeCommissionRate * 100.0),
                expanded = expandVehicleTiers,
                testTag = "section_vehicle_tiers_header",
                onToggle = { expandVehicleTiers = !expandVehicleTiers }
            ) {
                MenuSwitchRow(
                    title = "Pet-Friendly Vehicle",
                    subtitle = "Accept riders travelling with pets (live dispatch)",
                    checked = state.localPetFriendly,
                    testTag = "switch_pet_friendly",
                    onCheckedChange = { viewModel.togglePetFriendly(it) }
                )

                Text("Active Vehicle Tiers (Live Admin Config)", style = MaterialTheme.typography.labelMedium, color = AnyRollerTextMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    state.platformConfig.vehicleTypes.forEach { tier ->
                        val active = tier.id in state.localVehicleCategories
                        Surface(
                            onClick = { viewModel.toggleVehicleCategory(tier.id) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) AnyRollerGreen else AnyRollerBgTertiary,
                            modifier = Modifier.testTag("tier_chip_${tier.id}")
                        ) {
                            Text(
                                text = "${tier.name} (${"%.1fx".format(tier.multiplier)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) AnyRollerBgPrimary else AnyRollerTextPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = AnyRollerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Percent, contentDescription = null, tint = AnyRollerGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Platform Commission", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                        Text(
                            text = if (state.platformConfig.fixedTripFee > 0.0) {
                                "%.0f%% + £%.2f fixed fee (Live Admin rate)".format(
                                    state.activeCommissionRate * 100.0,
                                    state.platformConfig.fixedTripFee
                                )
                            } else {
                                "Fixed at %.0f%% (%s tier)".format(
                                    state.activeCommissionRate * 100.0,
                                    if (profile.isGoldDriver) "Gold Driver" else "Standard"
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AnyRollerTextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AnyRollerGreen.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "%.0f%%".format(state.activeCommissionRate * 100.0),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = AnyRollerGreen
                        )
                    }
                }
            }

            // =====================================================================
            // SECTION 5 — OPERATIONS & BUSINESS
            // =====================================================================
            SectionGroupLabel("5. OPERATIONS & BUSINESS")
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
            ) {
                Column {
                    MenuActionRow(
                        icon = Icons.Rounded.Description,
                        title = "Documents & Compliance",
                        subtitle = "${profile.verificationDocs.count { it.status in setOf("approved", "verified") }}/5 verified • PHV & Insurance",
                        badgeText = if (state.hasDocumentActionRequired) "ACTION" else null,
                        badgeColor = AnyRollerRed,
                        testTag = "menu_documents_row",
                        onClick = { viewModel.openOverlay(DriverOverlayRoute.DOCUMENTS) }
                    )
                    HorizontalDivider(color = AnyRollerBorder)
                    MenuActionRow(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "Weekly Availability",
                        subtitle = "Set Mon–Sun preferred shift windows",
                        testTag = "menu_availability_row",
                        onClick = { viewModel.openOverlay(DriverOverlayRoute.AVAILABILITY) }
                    )
                    HorizontalDivider(color = AnyRollerBorder)
                    MenuActionRow(
                        icon = Icons.Rounded.Map,
                        title = "Zones & Demand Radar",
                        subtitle = if (profile.zoneEnabled) "Radius locked to ${profile.zoneMaxDistance.toInt()} mi" else "All active UK zones",
                        testTag = "menu_zones_row",
                        onClick = { viewModel.openOverlay(DriverOverlayRoute.ZONES) }
                    )
                    HorizontalDivider(color = AnyRollerBorder)
                    MenuActionRow(
                        icon = Icons.Rounded.Analytics,
                        title = "Driver Analytics & Pulse",
                        subtitle = "Acceptance %.0f%% • Cancellation %.0f%%".format(profile.acceptanceRate, profile.cancellationRate),
                        testTag = "menu_analytics_row",
                        onClick = { viewModel.openOverlay(DriverOverlayRoute.ANALYTICS) }
                    )
                    HorizontalDivider(color = AnyRollerBorder)
                    MenuActionRow(
                        icon = Icons.Rounded.ReceiptLong,
                        title = "Driver Expenses & Receipt OCR",
                        subtitle = "${state.expenses.size} receipts logged for HMRC tax",
                        testTag = "menu_expenses_row",
                        onClick = { viewModel.openOverlay(DriverOverlayRoute.EXPENSES) }
                    )
                }
            }

            // =====================================================================
            // SECTION 6 — ANYTRADER ECOSYSTEM & ACCOUNT
            // =====================================================================
            SectionGroupLabel("6. ANYTRADER ECOSYSTEM & ACCOUNT")
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
            ) {
                Column {
                    MenuActionRow(
                        icon = Icons.Rounded.Storefront,
                        title = "Switch to AnyTrader Marketplace",
                        subtitle = "Vehicles, parts & trade deals on anytrader.co.uk",
                        testTag = "menu_anytrader_row",
                        onClick = { ExternalIntentHelpers.openCustomTab(context, "https://www.anytrader.co.uk") }
                    )
                    HorizontalDivider(color = AnyRollerBorder)
                    MenuActionRow(
                        icon = Icons.Rounded.Build,
                        title = "Mechanic & MOT Quotes",
                        subtitle = "Book trusted UK servicing & MOT repairs",
                        testTag = "menu_mechanic_row",
                        onClick = { ExternalIntentHelpers.openCustomTab(context, "https://www.anytrader.co.uk/mechanics") }
                    )
                    HorizontalDivider(color = AnyRollerBorder)
                    MenuActionRow(
                        icon = Icons.Rounded.HelpOutline,
                        title = "Driver Help Center",
                        subtitle = "FAQs, dispatch support & safety guidelines",
                        testTag = "menu_help_row",
                        onClick = { ExternalIntentHelpers.openCustomTab(context, "https://www.anytrader.co.uk/help") }
                    )
                    HorizontalDivider(color = AnyRollerBorder)
                    MenuActionRow(
                        icon = Icons.Rounded.Share,
                        title = "Refer a Driver",
                        subtitle = "Share your code: ${profile.referralCode}",
                        testTag = "menu_referral_row",
                        onClick = {
                            ExternalIntentHelpers.shareText(
                                context = context,
                                subject = "Drive with AnyRoller UK",
                                text = "Join me driving on AnyRoller UK! Use my driver referral code: ${profile.referralCode} at https://www.anytrader.co.uk/driver-signup"
                            )
                        }
                    )
                    HorizontalDivider(color = AnyRollerBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSignOut)
                            .padding(16.dp)
                            .testTag("menu_signout_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = AnyRollerRed)
                        Spacer(Modifier.width(12.dp))
                        Text("Sign Out (Go Offline)", style = MaterialTheme.typography.titleSmall, color = AnyRollerRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showHomeModal) {
        var homeInput by remember { mutableStateOf(profile.homeAddress ?: "") }
        AlertDialog(
            onDismissRequest = { showHomeModal = false },
            containerColor = AnyRollerBgSecondary,
            title = { Text("Home Destination", color = AnyRollerTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a UK postcode or street address. We automatically geocode exact coordinates for Destination Mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextMuted
                    )
                    OutlinedTextField(
                        value = homeInput,
                        onValueChange = { homeInput = it },
                        label = { Text("UK Postcode or Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveHomeDestination(
                        address = homeInput,
                        fallbackLat = state.driverLocation.lat,
                        fallbackLng = state.driverLocation.lng
                    )
                    showHomeModal = false
                }) { Text("Save", color = AnyRollerGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showHomeModal = false }) { Text("Cancel", color = AnyRollerTextMuted) }
            }
        )
    }

    pendingToggleConfirm?.let { (title, message, onConfirm) ->
        AlertDialog(
            onDismissRequest = { pendingToggleConfirm = null },
            containerColor = AnyRollerBgSecondary,
            title = { Text(title, color = AnyRollerTextPrimary) },
            text = { Text(message, color = AnyRollerTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    pendingToggleConfirm = null
                }) { Text("Confirm", color = AnyRollerGreen) }
            },
            dismissButton = {
                TextButton(onClick = { pendingToggleConfirm = null }) { Text("Cancel", color = AnyRollerTextMuted) }
            }
        )
    }
}

@Composable
private fun SectionGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = AnyRollerTextMuted,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun ShiftStatCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFont,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun CollapsibleSettingsCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    expanded: Boolean,
    testTag: String,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        color = AnyRollerBgSecondary,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .testTag(testTag),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle $title",
                    tint = AnyRollerTextMuted
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = AnyRollerBorder)
                    content()
                }
            }
        }
    }
}

@Composable
private fun ChipOption(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AnyRollerGreen else AnyRollerBgTertiary,
        modifier = modifier
            .border(1.dp, if (selected) AnyRollerGreen else AnyRollerBorder, RoundedCornerShape(10.dp))
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) AnyRollerBgPrimary else AnyRollerTextPrimary,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OfflineWhatsAppAlertsCard(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val prefs = state.notificationPrefs
    var phoneInput by remember(prefs.whatsappPhoneMasked) { mutableStateOf("") }

    Surface(
        color = AnyRollerBgSecondary,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AnyRollerGreen.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = AnyRollerGreen)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Offline & WhatsApp Alerts", style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                        Text(
                            text = if (prefs.whatsappWindowActive) "24h Free Window ACTIVE" else "4-Tier Cost-Capped Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (prefs.whatsappWindowActive) AnyRollerGreen else AnyRollerOrange
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle WhatsApp Alerts Card",
                    tint = AnyRollerTextMuted
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Zero-waste dispatch fallback: 1) Active in-app chat (£0) → 2) Free FCM Push (${prefs.activePushDevicesCount} active) → 3) 24h Free WhatsApp Window → 4) Cooldown-protected fallback (45s chat delay, 5s rider-arrived fast path, 5-min cooldown, 20/day cap). Reply STOP anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextSecondary
                    )

                    MenuSwitchRow(
                        title = "Enable WhatsApp Offline Fallback",
                        subtitle = prefs.whatsappPhoneMasked?.let { "Linked number: $it" } ?: "Add your WhatsApp number below",
                        checked = prefs.whatsappOptIn,
                        testTag = "switch_whatsapp_optin",
                        onCheckedChange = { enabled ->
                            viewModel.updateWhatsAppSettings(phone = null, optIn = enabled)
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            placeholder = { Text(prefs.whatsappPhoneMasked ?: "+44 7700 900000", color = AnyRollerTextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnyRollerGreen,
                                unfocusedBorderColor = AnyRollerBorder,
                                focusedContainerColor = AnyRollerBgInput,
                                unfocusedContainerColor = AnyRollerBgInput,
                                focusedTextColor = AnyRollerTextPrimary,
                                unfocusedTextColor = AnyRollerTextPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("whatsapp_phone_input")
                        )
                        Button(
                            onClick = {
                                if (phoneInput.isNotBlank()) {
                                    viewModel.updateWhatsAppSettings(phone = phoneInput, optIn = true)
                                    phoneInput = ""
                                }
                            },
                            enabled = !state.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp).testTag("save_whatsapp_phone_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { viewModel.activateWhatsAppFreeWindow() },
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = AnyRollerBgPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("activate_whatsapp_window_button")
                    ) {
                        Text(
                            text = state.whatsAppActivation?.activationCode?.let { "Open WhatsApp ($it) — Free 24h Window" }
                                ?: "1-Tap Activate Free 24h WhatsApp Window",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    badgeText: String? = null,
    badgeColor: Color = AnyRollerRed,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AnyRollerGreen, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AnyRollerTextMuted)
        }
        if (!badgeText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AnyRollerTextMuted)
    }
}

@Composable
private fun MenuSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = AnyRollerTextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AnyRollerBgPrimary,
                checkedTrackColor = AnyRollerGreen,
                uncheckedThumbColor = AnyRollerTextMuted,
                uncheckedTrackColor = AnyRollerBgInput
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
