package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.service.ExternalIntentHelpers
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
import com.example.viewmodel.DriverOverlayRoute
import com.example.viewmodel.DriverTerminalUiState
import com.example.viewmodel.DriverTerminalViewModel

/**
 * Exact 1:1 mirror of `DriverMenu.tsx` + `OfflineAlertsCard.tsx`:
 * Dedicated Driver Menu separate from AnyTrader consumer profile, sharing live unified
 * auth & Firestore data, including the 4-Tier Low-Cost WhatsApp Offline Alerts card.
 */
@Composable
fun DriverMenuScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onClose: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val profile = state.profile
    var showHomeModal by remember { mutableStateOf(false) }
    var showNavAppModal by remember { mutableStateOf(false) }
    var pendingToggleConfirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

    val batteryOptimized = remember { !ExternalIntentHelpers.isIgnoringBatteryOptimizations(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Driver Menu", style = MaterialTheme.typography.headlineSmall, color = AnyRollerTextPrimary)
            IconButton(onClick = onClose, modifier = Modifier.testTag("close_driver_menu_button")) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Menu", tint = AnyRollerTextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Driver Profile Header Card
            Surface(
                onClick = { viewModel.openOverlay(DriverOverlayRoute.PROFILE) },
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
                    .testTag("menu_profile_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!profile.photoURL.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.photoURL,
                            contentDescription = "Driver Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(AnyRollerGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(profile.initials, style = MaterialTheme.typography.titleLarge, color = AnyRollerGreen)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
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
            }

            // Battery Optimisation Warning Banner (for reliable background offers)
            if (batteryOptimized) {
                Surface(
                    onClick = { ExternalIntentHelpers.openBatteryOptimizationSettings(context) },
                    color = AnyRollerOrange.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AnyRollerOrange.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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

            // Driver Operations Sub-Screens
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
                        title = "Driver Expenses",
                        subtitle = "${state.expenses.size} receipts logged for tax",
                        testTag = "menu_expenses_row",
                        onClick = { viewModel.openOverlay(DriverOverlayRoute.EXPENSES) }
                    )
                }
            }

            // Dispatch & Driving Preferences (All rule-allowlisted on users/{uid}, except vehicleCategories/isPetFriendly -> live_tracking/{uid})
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Dispatch & Safety Controls", style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)

                    MenuSwitchRow(
                        title = "Stack Next Ride",
                        subtitle = "Receive queued offers while finishing current trip",
                        checked = profile.stackingEnabled,
                        testTag = "switch_stacking",
                        onCheckedChange = { viewModel.updateUserPref("isStackingEnabled", it) }
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
                        onCheckedChange = { viewModel.updateUserPref("destinationModeActive", it) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showHomeModal = true },
                            modifier = Modifier.weight(1f).testTag("edit_home_address_button")
                        ) {
                            Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(16.dp), tint = AnyRollerGreen)
                            Spacer(Modifier.width(6.dp))
                            Text("Home Address", color = AnyRollerTextPrimary)
                        }
                        OutlinedButton(
                            onClick = { showNavAppModal = true },
                            modifier = Modifier.weight(1f).testTag("edit_nav_app_button")
                        ) {
                            Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(16.dp), tint = AnyRollerBlue)
                            Spacer(Modifier.width(6.dp))
                            Text(profile.defaultNavApp, color = AnyRollerTextPrimary)
                        }
                    }

                    HorizontalDivider(color = AnyRollerBorder)

                    MenuSwitchRow(
                        title = "Require 4-Digit Passcode (PIN)",
                        subtitle = "Verify passenger PIN before starting every trip",
                        checked = profile.requirePasscode,
                        testTag = "switch_passcode",
                        onCheckedChange = { viewModel.updateUserPref("requirePasscode", it) }
                    )
                    MenuSwitchRow(
                        title = "Mute Ride Offer Alerts",
                        subtitle = "Silence chime for incoming ride offers",
                        checked = profile.muteRideOfferAlerts,
                        testTag = "switch_mute_offers",
                        onCheckedChange = { viewModel.updateUserPref("muteRideOfferAlerts", it) }
                    )
                    MenuSwitchRow(
                        title = "Mute Heads-Up Voice",
                        subtitle = "Silence spoken navigation callouts",
                        checked = profile.muteHeadsUpVolume,
                        testTag = "switch_mute_headsup",
                        onCheckedChange = { viewModel.updateUserPref("muteHeadsUpVolume", it) }
                    )
                    MenuSwitchRow(
                        title = "Pet-Friendly Vehicle",
                        subtitle = "Accept riders travelling with pets (live dispatch)",
                        checked = state.localPetFriendly,
                        testTag = "switch_pet_friendly",
                        onCheckedChange = { viewModel.togglePetFriendly(it) }
                    )

                    Text("Active Vehicle Tiers", style = MaterialTheme.typography.labelMedium, color = AnyRollerTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("standard", "exec", "xl", "ev").forEach { tier ->
                            val active = tier in state.localVehicleCategories
                            Surface(
                                onClick = { viewModel.toggleVehicleCategory(tier) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) AnyRollerGreen else AnyRollerBgTertiary,
                                modifier = Modifier.weight(1f).testTag("tier_chip_$tier")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text(
                                        tier.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) AnyRollerBgPrimary else AnyRollerTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4-Tier Cost-Capped Offline & WhatsApp Alerts Card (Exact mirror of OfflineAlertsCard.tsx)
            OfflineWhatsAppAlertsCard(
                state = state,
                viewModel = viewModel
            )

            // Refer a Driver & Sign Out
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
            ) {
                Column {
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
                OutlinedTextField(
                    value = homeInput,
                    onValueChange = { homeInput = it },
                    label = { Text("UK Postcode or Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveHomeDestination(
                        address = homeInput,
                        lat = state.driverLocation.lat,
                        lng = state.driverLocation.lng
                    )
                    showHomeModal = false
                }) { Text("Save", color = AnyRollerGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showHomeModal = false }) { Text("Cancel", color = AnyRollerTextMuted) }
            }
        )
    }

    if (showNavAppModal) {
        AlertDialog(
            onDismissRequest = { showNavAppModal = false },
            containerColor = AnyRollerBgSecondary,
            title = { Text("Default Navigation App", color = AnyRollerTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Google Maps", "Waze").forEach { app ->
                        val selected = profile.defaultNavApp.equals(app, ignoreCase = true)
                        Surface(
                            onClick = {
                                viewModel.updateUserPref("defaultNavApp", app)
                                showNavAppModal = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) AnyRollerGreen.copy(alpha = 0.2f) else AnyRollerBgTertiary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (selected) "$app (Selected)" else app,
                                color = if (selected) AnyRollerGreen else AnyRollerTextPrimary,
                                modifier = Modifier.padding(14.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNavAppModal = false }) { Text("Close", color = AnyRollerTextMuted) }
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
private fun OfflineWhatsAppAlertsCard(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
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
