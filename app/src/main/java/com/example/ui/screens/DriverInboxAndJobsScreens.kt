package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DriverNotification
import com.example.data.model.FallbackFareConfig
import com.example.data.model.resolveRideFinancialSummary
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Exact 1:1 mirror of `DriverInbox.tsx` (Messages Tab):
 * Filter chips (`All | Rides | Alerts | AnyTrader`), unread badge, tap-to-mark-read,
 * and exact 2-tap red delete confirmation with "Press Again" pill.
 */
@Composable
fun DriverInboxScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel
) {
    val filters = listOf("All", "Rides", "Alerts", "AnyTrader")
    var selectedFilter by remember { mutableStateOf("All") }
    var armedDeleteId by remember { mutableStateOf<String?>(null) }
    var selectedNotification by remember { mutableStateOf<DriverNotification?>(null) }
    val timeFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.UK) }

    val filtered = remember(state.notifications, selectedFilter) {
        state.notifications.filter { n ->
            val t = n.type.lowercase()
            when (selectedFilter) {
                "Rides" -> t.contains("ride") || t.contains("trip") || !n.rideId.isNullOrBlank()
                "Alerts" -> t.contains("alert") || t.contains("document") || t.contains("compliance") || t.contains("system")
                "AnyTrader" -> t.contains("anytrader") || t.contains("trade") || t.contains("market")
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Messages & Alerts", style = MaterialTheme.typography.headlineSmall, color = AnyRollerTextPrimary)
                Text("${state.unreadNotificationsCount} unread", style = MaterialTheme.typography.labelSmall, color = AnyRollerGreen)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            filters.forEach { label ->
                val sel = selectedFilter == label
                Surface(
                    onClick = { selectedFilter = label },
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) AnyRollerGreen else AnyRollerBgSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (sel) AnyRollerGreen else AnyRollerBorder, RoundedCornerShape(12.dp))
                        .testTag("inbox_filter_${label.lowercase()}")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sel) AnyRollerBgPrimary else AnyRollerTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.NotificationsNone, contentDescription = null, tint = AnyRollerTextMuted, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No messages in $selectedFilter", color = AnyRollerTextMuted)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { notif ->
                    val isArmed = armedDeleteId == notif.id
                    Surface(
                        color = if (notif.read) AnyRollerBgSecondary else AnyRollerBgTertiary,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (!notif.read) AnyRollerGreen.copy(alpha = 0.5f) else AnyRollerBorder,
                                RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                if (!notif.read) viewModel.markNotificationRead(notif.id)
                                selectedNotification = notif
                            }
                            .testTag("inbox_item_${notif.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!notif.read) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AnyRollerGreen)
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(notif.title, style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(notif.message, style = MaterialTheme.typography.bodySmall, color = AnyRollerTextSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    timeFmt.format(Date(notif.createdAtMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnyRollerTextMuted
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // Exact 2-tap red delete confirmation with "Press Again" pill
                            Surface(
                                onClick = {
                                    if (isArmed) {
                                        viewModel.deleteNotification(notif.id)
                                        armedDeleteId = null
                                    } else {
                                        armedDeleteId = notif.id
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isArmed) AnyRollerRed else AnyRollerRed.copy(alpha = 0.14f),
                                modifier = Modifier.testTag("inbox_delete_${notif.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete message",
                                        tint = if (isArmed) Color.White else AnyRollerRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (isArmed) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Press Again",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedNotification?.let { notif ->
        val lowerType = notif.type.lowercase()
        val lowerText = "${notif.title} ${notif.message}".lowercase()
        val isDocAlert = lowerType.contains("document") || lowerType.contains("compliance") ||
            lowerText.contains("document") || lowerText.contains("mot") || lowerText.contains("insurance") || lowerText.contains("licence")
        val isEarningsAlert = lowerType.contains("ride") || lowerType.contains("payout") ||
            lowerText.contains("payout") || lowerText.contains("earnings") || lowerText.contains("fee")

        AlertDialog(
            onDismissRequest = { selectedNotification = null },
            containerColor = AnyRollerBgSecondary,
            title = {
                Column {
                    Text(notif.title.ifBlank { "AnyRoller Alert" }, color = AnyRollerTextPrimary)
                    Text(
                        timeFmt.format(Date(notif.createdAtMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = AnyRollerTextMuted
                    )
                }
            },
            text = {
                Text(notif.message, style = MaterialTheme.typography.bodyMedium, color = AnyRollerTextSecondary)
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isDocAlert) {
                        TextButton(
                            onClick = {
                                selectedNotification = null
                                viewModel.openOverlay(DriverOverlayRoute.DOCUMENTS)
                            },
                            modifier = Modifier.testTag("inbox_detail_open_docs_button")
                        ) {
                            Text("Open Documents", color = AnyRollerGreen, fontWeight = FontWeight.Bold)
                        }
                    } else if (isEarningsAlert) {
                        TextButton(
                            onClick = {
                                selectedNotification = null
                                viewModel.selectTab(DriverTab.EARNINGS)
                            },
                            modifier = Modifier.testTag("inbox_detail_open_earnings_button")
                        ) {
                            Text("Open Earnings", color = AnyRollerGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = { selectedNotification = null },
                        modifier = Modifier.testTag("inbox_detail_close_button")
                    ) {
                        Text("Close", color = AnyRollerTextPrimary)
                    }
                }
            }
        )
    }
}

/**
 * Exact 1:1 mirror of `DriverJobs.tsx` (My Jobs Tab):
 * Active / Scheduled rides & Completed history ledger.
 */
@Composable
fun DriverJobsScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel
) {
    var segment by remember { mutableIntStateOf(0) } // 0 = Active & Scheduled, 1 = Completed
    val dateFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.UK) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text("My Jobs", style = MaterialTheme.typography.headlineSmall, color = AnyRollerTextPrimary)
        Text("Live dispatch queue & completed trips", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Active & Scheduled", "Completed (${state.completedRides.size})").forEachIndexed { idx, label ->
                val sel = segment == idx
                Surface(
                    onClick = { segment = idx },
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) AnyRollerGreen else AnyRollerBgSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (sel) AnyRollerGreen else AnyRollerBorder, RoundedCornerShape(12.dp))
                        .testTag("jobs_segment_$idx")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) AnyRollerBgPrimary else AnyRollerTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (segment == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                state.activeRide?.let { active ->
                    item {
                        Surface(
                            color = AnyRollerBgSecondary,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, AnyRollerGreen, RoundedCornerShape(20.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = AnyRollerGreen)
                                    Spacer(Modifier.width(6.dp))
                                    Text("ACTIVE TRIP • ${active.status.uppercase()}", style = MaterialTheme.typography.labelMedium, color = AnyRollerGreen, fontWeight = FontWeight.Bold)
                                }
                                Text(active.passengerName, style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                                RouteAddressBlock(pickup = active.pickupAddress, dropoff = active.dropoffAddress)
                                Button(
                                    onClick = { viewModel.selectTab(DriverTab.TERMINAL) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("resume_active_trip_button")
                                ) {
                                    Text("Open Live Cockpit", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (state.scheduledRides.isEmpty() && state.activeRide == null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = AnyRollerTextMuted, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No active or scheduled jobs right now.", color = AnyRollerTextMuted)
                            }
                        }
                    }
                } else {
                    items(state.scheduledRides, key = { it.id }) { ride ->
                        Surface(
                            color = AnyRollerBgSecondary,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Scheduled: ${ride.scheduledTime ?: "Upcoming"}", style = MaterialTheme.typography.labelSmall, color = AnyRollerOrange)
                                    Text("£%.2f".format(ride.fareEstimate), style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMonoFont, color = AnyRollerGreen)
                                }
                                RouteAddressBlock(pickup = ride.pickupAddress, dropoff = ride.dropoffAddress)
                            }
                        }
                    }
                }
            }
        } else {
            val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
            var selectedYear by remember { mutableIntStateOf(0) } // 0 = All Years
            var selectedMonth by remember { mutableIntStateOf(0) } // 0 = All Months
            var dayInput by remember { mutableStateOf("") }
            var expandedRideId by remember { mutableStateOf<String?>(null) }
            val monthLabels = listOf("All", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

            val filteredCompleted = remember(state.completedRides, selectedYear, selectedMonth, dayInput) {
                val dayNum = dayInput.trim().toIntOrNull()
                val cal = Calendar.getInstance()
                state.completedRides.filter { ride ->
                    cal.timeInMillis = maxOf(ride.completedAtMs, ride.createdAtMs)
                    val yOk = selectedYear == 0 || cal.get(Calendar.YEAR) == selectedYear
                    val mOk = selectedMonth == 0 || (cal.get(Calendar.MONTH) + 1) == selectedMonth
                    val dOk = dayNum == null || cal.get(Calendar.DAY_OF_MONTH) == dayNum
                    yOk && mOk && dOk
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                // Year + Day Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0 to "All Yrs", currentYear to "$currentYear", (currentYear - 1) to "${currentYear - 1}").forEach { (yr, label) ->
                        val sel = selectedYear == yr
                        Surface(
                            onClick = { selectedYear = yr },
                            shape = RoundedCornerShape(10.dp),
                            color = if (sel) AnyRollerBlue else AnyRollerBgSecondary,
                            modifier = Modifier
                                .border(1.dp, if (sel) AnyRollerBlue else AnyRollerBorder, RoundedCornerShape(10.dp))
                                .testTag("jobs_year_$yr")
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) Color.White else AnyRollerTextPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = dayInput,
                        onValueChange = { input -> dayInput = input.filter(Char::isDigit).take(2) },
                        placeholder = { Text("Day", style = MaterialTheme.typography.labelSmall) },
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
                        modifier = Modifier.width(74.dp).testTag("jobs_day_filter_input")
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${filteredCompleted.size} trips", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                }

                // Month Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    monthLabels.forEachIndexed { idx, label ->
                        val sel = selectedMonth == idx
                        Surface(
                            onClick = { selectedMonth = idx },
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) AnyRollerGreen else AnyRollerBgSecondary,
                            modifier = Modifier.testTag("jobs_month_$idx")
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) AnyRollerBgPrimary else AnyRollerTextPrimary,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                if (filteredCompleted.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No completed trips match this filter.", color = AnyRollerTextMuted)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(filteredCompleted, key = { it.id }) { ride ->
                            val summary = resolveRideFinancialSummary(
                                ride,
                                FallbackFareConfig(state.activeCommissionRate, state.platformConfig.fixedTripFee)
                            )
                            val isExpanded = expandedRideId == ride.id
                            Surface(
                                color = AnyRollerBgSecondary,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isExpanded) AnyRollerGreen.copy(alpha = 0.6f) else AnyRollerBorder,
                                        RoundedCornerShape(18.dp)
                                    )
                                    .clickable {
                                        expandedRideId = if (isExpanded) null else ride.id
                                    }
                                    .testTag("completed_job_card_${ride.id}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            dateFmt.format(Date(maxOf(ride.completedAtMs, ride.createdAtMs))),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AnyRollerTextMuted
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "+£%.2f".format(summary.driverEarnings),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = JetBrainsMonoFont,
                                                color = AnyRollerGreen
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse fare breakdown" else "Expand fare breakdown",
                                                tint = AnyRollerTextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    RouteAddressBlock(pickup = ride.pickupAddress, dropoff = ride.dropoffAddress)

                                    if (isExpanded) {
                                        HorizontalDivider(color = AnyRollerBorder, modifier = Modifier.padding(vertical = 4.dp))
                                        FareBreakdownRow("Base Fare", "£%.2f".format(summary.baseFare))
                                        FareBreakdownRow(
                                            "Platform Fee (%.1f%%)".format(summary.appliedCommissionPct),
                                            "-£%.2f".format(summary.platformFee),
                                            valueColor = AnyRollerOrange
                                        )
                                        if (summary.tipAmount > 0.0) {
                                            FareBreakdownRow("Passenger Tip (100% Yours)", "+£%.2f".format(summary.tipAmount), valueColor = AnyRollerGreen)
                                        }
                                        if (ride.priorityFee > 0.0) {
                                            FareBreakdownRow("Priority Booking Fee", "+£%.2f".format(ride.priorityFee), valueColor = AnyRollerBlue)
                                        }
                                        if (ride.abandonmentFee > 0.0) {
                                            FareBreakdownRow("Abandonment Fee", "+£%.2f".format(ride.abandonmentFee), valueColor = AnyRollerOrange)
                                        }
                                        FareBreakdownRow("Payment Method", ride.paymentMethod.uppercase())
                                        HorizontalDivider(color = AnyRollerBorder, modifier = Modifier.padding(vertical = 2.dp))
                                        FareBreakdownRow(
                                            "Net Driver Earnings",
                                            "£%.2f".format(summary.driverEarnings),
                                            valueColor = AnyRollerGreen,
                                            bold = true
                                        )
                                    }
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
private fun FareBreakdownRow(
    label: String,
    value: String,
    valueColor: Color = AnyRollerTextPrimary,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) AnyRollerTextPrimary else AnyRollerTextMuted
        )
        Text(
            value,
            style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFont,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
