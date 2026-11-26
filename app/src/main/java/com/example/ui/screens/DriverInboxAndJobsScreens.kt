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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.data.model.FallbackFareConfig
import com.example.data.model.resolveRideFinancialSummary
import com.example.ui.theme.AnyRollerBgPrimary
import com.example.ui.theme.AnyRollerBgSecondary
import com.example.ui.theme.AnyRollerBgTertiary
import com.example.ui.theme.AnyRollerBorder
import com.example.ui.theme.AnyRollerGreen
import com.example.ui.theme.AnyRollerOrange
import com.example.ui.theme.AnyRollerRed
import com.example.ui.theme.AnyRollerTextMuted
import com.example.ui.theme.AnyRollerTextPrimary
import com.example.ui.theme.AnyRollerTextSecondary
import com.example.ui.theme.JetBrainsMonoFont
import com.example.viewmodel.DriverTab
import com.example.viewmodel.DriverTerminalUiState
import com.example.viewmodel.DriverTerminalViewModel
import java.text.SimpleDateFormat
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
                            }
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
            if (state.completedRides.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No completed trips yet.", color = AnyRollerTextMuted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(state.completedRides, key = { it.id }) { ride ->
                        val summary = resolveRideFinancialSummary(
                            ride,
                            FallbackFareConfig(state.activeCommissionRate, state.platformConfig.fixedTripFee)
                        )
                        Surface(
                            color = AnyRollerBgSecondary,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        dateFmt.format(Date(maxOf(ride.completedAtMs, ride.createdAtMs))),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AnyRollerTextMuted
                                    )
                                    Text(
                                        "+£%.2f".format(summary.driverEarnings),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = JetBrainsMonoFont,
                                        color = AnyRollerGreen
                                    )
                                }
                                RouteAddressBlock(pickup = ride.pickupAddress, dropoff = ride.dropoffAddress)
                            }
                        }
                    }
                }
            }
        }
    }
}
