package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.DaySchedule
import com.example.service.ExternalIntentHelpers
import com.example.ui.theme.AnyRollerBgInput
import com.example.ui.theme.AnyRollerBgPrimary
import com.example.ui.theme.AnyRollerBgSecondary
import com.example.ui.theme.AnyRollerBgTertiary
import com.example.ui.theme.AnyRollerBlue
import com.example.ui.theme.AnyRollerBorder
import com.example.ui.theme.AnyRollerGreen
import com.example.ui.theme.AnyRollerOrange
import com.example.ui.theme.AnyRollerPurple
import com.example.ui.theme.AnyRollerRed
import com.example.ui.theme.AnyRollerTextMuted
import com.example.ui.theme.AnyRollerTextPrimary
import com.example.ui.theme.AnyRollerTextSecondary
import com.example.ui.theme.JetBrainsMonoFont
import com.example.viewmodel.DriverOverlayRoute
import com.example.viewmodel.DriverTerminalUiState
import com.example.viewmodel.DriverTerminalViewModel

@Composable
fun DriverDocumentsScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onBack: () -> Unit
) {
    val requiredDocs = listOf(
        "dvla_license" to "UK Driving Licence (DVLA)",
        "phv_license" to "PHV / Council Driver Badge",
        "insurance" to "Hire & Reward Insurance",
        "mot" to "MOT Certificate",
        "logbook" to "Vehicle V5C Logbook"
    )
    var uploadingType by remember { mutableStateOf<String?>(null) }
    var expiryDateInput by remember { mutableStateOf("2027-01-01") }

    // Zero-permission SAF document picker (PDF / Images — no storage permissions required)
    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val docType = uploadingType
        if (uri != null && docType != null) {
            viewModel.uploadVerificationDocument(docType, expiryDateInput, uri)
        }
        uploadingType = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Documents & Compliance", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = { viewModel.openOverlay(DriverOverlayRoute.INSURANCE) },
                color = AnyRollerBlue.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AnyRollerBlue.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .testTag("open_insurance_marketplace_button")
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = AnyRollerBlue)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PHV Insurance Marketplace", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                        Text("Compare UK Hire & Reward quotes via AnyRoller Partners", style = MaterialTheme.typography.bodySmall, color = AnyRollerTextSecondary)
                    }
                }
            }

            OutlinedTextField(
                value = expiryDateInput,
                onValueChange = { expiryDateInput = it },
                label = { Text("Expiry Date for Next Upload (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            requiredDocs.forEach { (typeKey, label) ->
                val existing = state.profile.verificationDocs.firstOrNull { it.type == typeKey }
                val status = existing?.status ?: "missing"
                val statusColor = when (status.lowercase()) {
                    "approved", "verified" -> AnyRollerGreen
                    "pending" -> AnyRollerOrange
                    "rejected", "expired" -> AnyRollerRed
                    else -> AnyRollerTextMuted
                }
                Surface(
                    color = AnyRollerBgSecondary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                            Text(
                                text = "Status: ${status.uppercase()}" + (existing?.expiryDate?.let { " • Exp $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                            if (!existing?.rejectionReason.isNullOrBlank()) {
                                Text("Note: ${existing?.rejectionReason}", style = MaterialTheme.typography.bodySmall, color = AnyRollerRed)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                uploadingType = typeKey
                                docPicker.launch(arrayOf("image/*", "application/pdf"))
                            },
                            enabled = !state.isBusy,
                            modifier = Modifier.testTag("upload_doc_$typeKey")
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = AnyRollerGreen)
                            Spacer(Modifier.width(4.dp))
                            Text("Upload", color = AnyRollerTextPrimary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun InsuranceMarketplaceScreen(
    state: DriverTerminalUiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Insurance Marketplace", onBack = onBack)
        if (state.insurancePartners.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No UK insurance partner listings are currently active. Check back soon.",
                    color = AnyRollerTextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.insurancePartners, key = { it.id }) { partner ->
                    Surface(
                        color = AnyRollerBgSecondary,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(partner.name, style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                                if (partner.estimatedMonthlyGbp > 0.0) {
                                    Text("From £%.0f/mo".format(partner.estimatedMonthlyGbp), style = MaterialTheme.typography.labelLarge, color = AnyRollerGreen)
                                }
                            }
                            Text(partner.tagline, style = MaterialTheme.typography.bodySmall, color = AnyRollerTextSecondary)
                            if (partner.url.isNotBlank()) {
                                Button(
                                    onClick = { ExternalIntentHelpers.openCustomTab(context, partner.url) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AnyRollerBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Get Quote", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverAvailabilityScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onBack: () -> Unit
) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var schedule by remember(state.profile.weeklySchedule) { mutableStateOf(state.profile.weeklySchedule) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Weekly Availability", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            days.forEach { day ->
                val current = schedule[day] ?: DaySchedule("anytime")
                Surface(
                    color = AnyRollerBgSecondary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(day, style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("anytime" to "Anytime", "specific" to "08:00–18:00", "unavailable" to "Off").forEach { (key, label) ->
                                val sel = current.status == key
                                Surface(
                                    onClick = {
                                        schedule = schedule.toMutableMap().apply {
                                            this[day] = current.copy(status = key)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) AnyRollerGreen else AnyRollerBgTertiary,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (sel) AnyRollerBgPrimary else AnyRollerTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = { viewModel.saveWeeklySchedule(schedule) },
            colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp)
                .testTag("save_availability_button")
        ) {
            Text("Save Schedule", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun DriverZonesScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onBack: () -> Unit
) {
    var sliderVal by remember(state.profile.zoneMaxDistance) {
        mutableFloatStateOf(state.profile.zoneMaxDistance.toFloat().coerceIn(2f, 50f))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Zones & Max Radius", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Zone Radius Filter", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                            Text("Only receive pickup offers within your chosen radius", style = MaterialTheme.typography.bodySmall, color = AnyRollerTextMuted)
                        }
                        Switch(
                            checked = state.profile.zoneEnabled,
                            onCheckedChange = { viewModel.updateUserPref("zoneEnabled", it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = AnyRollerGreen, checkedThumbColor = AnyRollerBgPrimary),
                            modifier = Modifier.testTag("switch_zone_enabled")
                        )
                    }
                    Text("Maximum Distance: ${sliderVal.toInt()} miles", style = MaterialTheme.typography.labelLarge, color = AnyRollerGreen)
                    Slider(
                        value = sliderVal,
                        onValueChange = { sliderVal = it },
                        onValueChangeFinished = {
                            viewModel.updateUserPref("zoneMaxDistance", sliderVal.toDouble())
                        },
                        valueRange = 2f..50f,
                        colors = SliderDefaults.colors(thumbColor = AnyRollerGreen, activeTrackColor = AnyRollerGreen)
                    )
                }
            }

            Text("Live High-Demand Zones", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)

            if (state.demandZones.isEmpty()) {
                Text("No live surge hotspots reported near your location right now.", color = AnyRollerTextMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                state.demandZones.forEach { zone ->
                    Surface(
                        color = AnyRollerBgSecondary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(zone.name, style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                                Text("Demand: ${zone.level.uppercase()}", style = MaterialTheme.typography.labelSmall, color = AnyRollerOrange)
                            }
                            Text("%.1fx".format(zone.multiplier), style = MaterialTheme.typography.titleMedium, fontFamily = JetBrainsMonoFont, color = AnyRollerGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverAnalyticsScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onBack: () -> Unit
) {
    val p = state.profile
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Driver Analytics", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiMetricCard("Rating", "%.2f ★".format(p.rating), AnyRollerOrange, Modifier.weight(1f))
                KpiMetricCard("Acceptance", "%.0f%%".format(p.acceptanceRate), AnyRollerGreen, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiMetricCard("Cancellation", "%.1f%%".format(p.cancellationRate), AnyRollerRed, Modifier.weight(1f))
                KpiMetricCard("Avg Response", "%.1fs".format(p.avgResponseSeconds), AnyRollerBlue, Modifier.weight(1f))
            }

            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerPurple.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = AnyRollerPurple)
                        Spacer(Modifier.width(8.dp))
                        Text("AnyRoller Performance Pulse", style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                    }
                    Text(
                        text = state.analyticsInsight ?: "Tap Refresh Pulse to load live dispatch insights based on your shift metrics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnyRollerTextSecondary
                    )
                    state.analyticsTips.forEach { tip ->
                        Text("• $tip", style = MaterialTheme.typography.bodySmall, color = AnyRollerGreen)
                    }
                    OutlinedButton(
                        onClick = { viewModel.fetchAnalyticsPulse() },
                        modifier = Modifier.fillMaxWidth().testTag("refresh_analytics_pulse_button")
                    ) {
                        Text("Refresh Pulse", color = AnyRollerTextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        color = AnyRollerBgSecondary,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = JetBrainsMonoFont, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DriverProfileScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onBack: () -> Unit,
    onExportGdpr: () -> Unit,
    onDeleteAccount: ((Boolean, String) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    val profile = state.profile
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.uploadDriverAvatar(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Driver Profile & GDPR", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!profile.photoURL.isNullOrBlank()) {
                AsyncImage(
                    model = profile.photoURL,
                    contentDescription = "Driver Profile Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(92.dp).clip(CircleShape).border(2.dp, AnyRollerGreen, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier.size(92.dp).clip(CircleShape).background(AnyRollerGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(profile.initials, style = MaterialTheme.typography.headlineMedium, color = AnyRollerGreen)
                }
            }

            OutlinedButton(
                onClick = {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.testTag("change_avatar_button")
            ) {
                Text("Update Profile Photo", color = AnyRollerTextPrimary)
            }

            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile.fullName, style = MaterialTheme.typography.titleLarge, color = AnyRollerTextPrimary)
                    Text(profile.email, style = MaterialTheme.typography.bodyMedium, color = AnyRollerTextSecondary)
                    if (profile.phone.isNotBlank()) {
                        Text(profile.phone, style = MaterialTheme.typography.bodySmall, color = AnyRollerTextMuted)
                    }
                    Text(
                        "Account Role: ${profile.role.ifBlank { "driver" }.uppercase()} • Referral: ${profile.referralCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnyRollerGreen
                    )
                }
            }

            // GDPR Controls (Play Policy & UK GDPR compliant)
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Privacy & Data Rights (UK GDPR)", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                    OutlinedButton(
                        onClick = onExportGdpr,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gdpr_export_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = AnyRollerBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("Export My Driver Data", color = AnyRollerTextPrimary)
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerRed),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gdpr_delete_button")
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = AnyRollerRed)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Account Permanently", color = AnyRollerRed)
                    }
                }
            }

            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = AnyRollerBgTertiary, contentColor = AnyRollerTextPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Sign Out", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = AnyRollerBgSecondary,
            title = { Text("Delete AnyTrader / AnyRoller Account?", color = AnyRollerRed) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This permanently deletes your unified AnyTrader & AnyRoller Driver account. Type DELETE to confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextSecondary
                    )
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        placeholder = { Text("Type DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteConfirmText.trim().equals("DELETE", ignoreCase = true),
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccount { _, _ -> }
                    }
                ) { Text("Delete Forever", color = AnyRollerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = AnyRollerTextPrimary) }
            }
        )
    }
}
