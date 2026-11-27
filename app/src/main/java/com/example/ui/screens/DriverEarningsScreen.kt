package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import android.widget.Toast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.model.FallbackFareConfig
import com.example.data.model.resolveRideFinancialSummary
import com.example.service.CsvExportHelper
import com.example.service.ReceiptOcrHelper
import kotlinx.coroutines.launch
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DriverEarningsScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel
) {
    val context = LocalContext.current
    var periodIndex by remember { mutableIntStateOf(0) } // 0=Today, 1=Week, 2=Month, 3=Year
    var showGoalsDialog by remember { mutableStateOf(false) }

    val isoDateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.UK) }
    var stmtStartDate by remember {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        mutableStateOf(isoDateFmt.format(cal.time))
    }
    var stmtEndDate by remember { mutableStateOf(isoDateFmt.format(Date())) }
    var showStatementCard by remember { mutableStateOf(false) }

    val displayedEarnings = when (periodIndex) {
        0 -> state.todayEarningsComputed
        1 -> state.weekEarningsComputed
        2 -> state.monthEarningsComputed
        else -> state.yearEarningsComputed
    }
    val goalTarget = when (periodIndex) {
        0 -> state.profile.earningsGoals.today
        1 -> state.profile.earningsGoals.week
        2 -> state.profile.earningsGoals.month
        else -> state.profile.earningsGoals.month * 12.0
    }.coerceAtLeast(1.0)
    val goalProgress = (displayedEarnings / goalTarget).toFloat().coerceIn(0f, 1f)

    // Compute last 7 days bar values from completedRides
    val sevenDayBars = remember(state.completedRides, state.activeCommissionRate) {
        val cal = Calendar.getInstance()
        val labels = mutableListOf<String>()
        val values = mutableListOf<Double>()
        val dayFmt = SimpleDateFormat("EEE", Locale.UK)
        for (offset in 6 downTo 0) {
            val dayStart = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayEnd = dayStart.timeInMillis + 24L * 3600L * 1000L
            val dayTotal = state.completedRides
                .filter {
                    val t = maxOf(it.completedAtMs, it.createdAtMs)
                    t >= dayStart.timeInMillis && t < dayEnd
                }
                .sumOf {
                    resolveRideFinancialSummary(
                        it,
                        FallbackFareConfig(state.activeCommissionRate, state.platformConfig.fixedTripFee)
                    ).driverEarnings
                }
            labels += dayFmt.format(dayStart.time)
            values += dayTotal
        }
        labels.zip(values)
    }

    val pendingFees = maxOf(state.profile.pendingPlatformFees, state.feeStatus.pendingPlatformFees)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Driver Earnings", style = MaterialTheme.typography.headlineSmall, color = AnyRollerTextPrimary)
                Text(
                    text = if (state.profile.isGoldDriver) "Gold Driver (%.0f%% commission)".format(state.activeCommissionRate * 100)
                    else "Standard Driver (%.0f%% commission)".format(state.activeCommissionRate * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.profile.isGoldDriver) AnyRollerOrange else AnyRollerTextMuted
                )
            }
            IconButton(
                onClick = { viewModel.refreshFinancials() },
                modifier = Modifier.testTag("earnings_refresh_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Earnings", tint = AnyRollerTextPrimary)
            }
        }

        // Live Current Shift Performance Card (when Online or completed trips in shift)
        if (state.isOnline || state.shiftTripsCompleted > 0 || state.shiftOnlineSeconds > 0L) {
            val sHrs = state.shiftOnlineSeconds / 3600L
            val sMins = (state.shiftOnlineSeconds % 3600L) / 60L
            val sDur = if (sHrs > 0L) "${sHrs}h ${sMins}m" else "${sMins}m"
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AnyRollerGreen.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .testTag("earnings_shift_summary_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, tint = AnyRollerGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("This Shift Summary", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                        }
                        Text(
                            text = if (state.isOnBreak) "ON BREAK" else if (state.isOnline) "LIVE SHIFT" else "LAST SHIFT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isOnBreak) AnyRollerOrange else AnyRollerGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AnyRollerBgPrimary)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Duration", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Text(sDur, style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMonoFont, color = AnyRollerTextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Trips", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Text("${state.shiftTripsCompleted}", style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMonoFont, color = AnyRollerBlue)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Live £/hr", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Text("£%.0f/hr".format(state.shiftHourlyRate), style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMonoFont, color = AnyRollerGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Paid / Empty", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Text(
                                "%.1f/%.1fmi".format(state.shiftPaidMiles, state.shiftDeadheadMiles),
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = JetBrainsMonoFont,
                                color = AnyRollerOrange
                            )
                        }
                    }
                }
            }
        }

        // Period Selector (Today | Week | Month | Year)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Today", "Week", "Month", "Year").forEachIndexed { idx, label ->
                val selected = periodIndex == idx
                Surface(
                    onClick = { periodIndex = idx },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) AnyRollerGreen else AnyRollerBgSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (selected) AnyRollerGreen else AnyRollerBorder, RoundedCornerShape(12.dp))
                        .testTag("earnings_period_$idx")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) AnyRollerBgPrimary else AnyRollerTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Hero Earnings & Goal Progress Card
        Surface(
            color = AnyRollerBgSecondary,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AnyRollerBorder, RoundedCornerShape(22.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Net Driver Earnings", style = MaterialTheme.typography.bodySmall, color = AnyRollerTextMuted)
                    TextButton(
                        onClick = { showGoalsDialog = true },
                        modifier = Modifier.testTag("edit_goals_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = AnyRollerGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit Target", color = AnyRollerGreen)
                    }
                }
                Text(
                    text = "£%.2f".format(displayedEarnings),
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = JetBrainsMonoFont,
                    color = AnyRollerGreen,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target: £%.0f".format(goalTarget), style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                    Text("%.0f%%".format(goalProgress * 100f), style = MaterialTheme.typography.labelSmall, color = AnyRollerGreen)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { goalProgress },
                    color = AnyRollerGreen,
                    trackColor = AnyRollerBgPrimary,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
            }
        }

        // 7-Day Bar Chart Card
        Surface(
            color = AnyRollerBgSecondary,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AnyRollerBorder, RoundedCornerShape(22.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Last 7 Days Performance", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                Spacer(Modifier.height(12.dp))
                val maxVal = (sevenDayBars.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(10.0)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val count = sevenDayBars.size
                    val slotW = size.width / count
                    val barW = slotW * 0.52f
                    sevenDayBars.forEachIndexed { idx, (_, value) ->
                        val ratio = (value / maxVal).toFloat().coerceIn(0.06f, 1f)
                        val barH = size.height * ratio
                        val x = idx * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            color = if (idx == count - 1) AnyRollerGreen else AnyRollerBlue.copy(alpha = 0.7f),
                            topLeft = Offset(x, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    sevenDayBars.forEach { (day, _) ->
                        Text(day, style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                    }
                }
            }
        }

        // Stripe Connect Balance & Payout Card
        Surface(
            color = AnyRollerBgSecondary,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AnyRollerBorder, RoundedCornerShape(22.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = AnyRollerBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Stripe Connect Payouts", style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
                }

                val isConnected = state.stripeAccountStatus.connected || !state.profile.stripeAccountId.isNullOrBlank()
                if (isConnected) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Available", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Text(
                                "£%.2f".format(state.stripeBalance.available),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = JetBrainsMonoFont,
                                color = AnyRollerGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Pending", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Text(
                                "£%.2f".format(state.stripeBalance.pending),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = JetBrainsMonoFont,
                                color = AnyRollerTextSecondary
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.requestStripePayout() },
                            enabled = !state.isBusy && state.stripeBalance.available > 0.0,
                            colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("withdraw_payout_button")
                        ) {
                            Text("Withdraw Now", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.launchStripeExpressDashboard() },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerBorder),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("stripe_dashboard_button")
                        ) {
                            Text("Stripe Portal", color = AnyRollerTextPrimary)
                        }
                    }
                } else {
                    Text(
                        "Connect your UK bank account via Stripe Connect Express to receive automatic ride payouts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextSecondary
                    )
                    Button(
                        onClick = { viewModel.launchStripeConnectOnboarding() },
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AnyRollerBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("stripe_onboard_button")
                    ) {
                        Text("Set Up Stripe Payouts", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Pending Platform Fees Settlement Card (for Cash trips)
        if (pendingFees > 0.0) {
            Surface(
                color = AnyRollerRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AnyRollerRed.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pending Platform Fees", style = MaterialTheme.typography.titleSmall, color = AnyRollerRed)
                        Text(
                            "£%.2f owed from cash trips".format(pendingFees),
                            style = MaterialTheme.typography.bodySmall,
                            color = AnyRollerTextPrimary
                        )
                    }
                    Button(
                        onClick = { viewModel.launchSettlePlatformFees() },
                        colors = ButtonDefaults.buttonColors(containerColor = AnyRollerRed, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("settle_fees_button")
                    ) {
                        Text("Settle Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Gold Driver Subscription Card
        Surface(
            color = AnyRollerBgSecondary,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (state.profile.isGoldDriver) AnyRollerOrange else AnyRollerPurple.copy(alpha = 0.6f),
                    RoundedCornerShape(22.dp)
                )
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = AnyRollerOrange)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.profile.isGoldDriver) "Gold Driver Active" else "Upgrade to Gold Driver",
                        style = MaterialTheme.typography.titleMedium,
                        color = AnyRollerTextPrimary
                    )
                }
                Text(
                    text = if (state.profile.isGoldDriver) {
                        "Enjoy reduced %.0f%% commission, priority dispatch, and HMRC tax-ready exports.".format(
                            state.platformConfig.goldCommissionRate * 100
                        )
                    } else {
                        "Lower commission to %.0f%% (down from %.0f%%), unlock priority demand radar & tax export.".format(
                            state.platformConfig.goldCommissionRate * 100,
                            state.platformConfig.commissionRate * 100
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AnyRollerTextSecondary
                )
                Button(
                    onClick = {
                        if (state.profile.isGoldDriver) viewModel.launchBillingPortal()
                        else viewModel.launchGoldDriverUpgrade()
                    },
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.profile.isGoldDriver) AnyRollerBgTertiary else AnyRollerOrange,
                        contentColor = if (state.profile.isGoldDriver) AnyRollerTextPrimary else AnyRollerBgPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("gold_driver_cta_button")
                ) {
                    Text(
                        if (state.profile.isGoldDriver) "Manage Gold Billing" else "Upgrade to Gold",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Quick Links: Expenses & Full Trip History
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { viewModel.openOverlay(DriverOverlayRoute.EXPENSES) },
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
                    .testTag("open_expenses_button")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = AnyRollerGreen)
                    Text("Driver Expenses", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                    Text("${state.expenses.size} logged", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                }
            }
            Surface(
                onClick = { viewModel.openOverlay(DriverOverlayRoute.TRIP_HISTORY) },
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
                    .testTag("open_trip_history_button")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = AnyRollerBlue)
                    Text("Trip History", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                    Text("${state.completedRides.size} trips", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                }
            }
        }

        // Custom Date-Range Statement Card (Mirrors DriverEarnings.tsx statement generator)
        val statementRides = remember(state.completedRides, stmtStartDate, stmtEndDate) {
            val startMs = runCatching { isoDateFmt.parse(stmtStartDate)?.time }.getOrNull() ?: 0L
            val endMs = (runCatching { isoDateFmt.parse(stmtEndDate)?.time }.getOrNull() ?: System.currentTimeMillis()) + 86_399_999L
            state.completedRides.filter { ride ->
                val t = maxOf(ride.completedAtMs, ride.createdAtMs)
                t in startMs..endMs
            }
        }
        val statementTotal = remember(statementRides, state.activeCommissionRate, state.platformConfig.fixedTripFee) {
            statementRides.sumOf {
                resolveRideFinancialSummary(
                    it,
                    FallbackFareConfig(state.activeCommissionRate, state.platformConfig.fixedTripFee)
                ).driverEarnings
            }
        }
        Surface(
            color = AnyRollerBgSecondary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStatementCard = !showStatementCard }
                        .testTag("toggle_statement_card_button"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, tint = AnyRollerBlue)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Custom Date-Range Statement", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                            Text("Filter earnings by period & export HMRC CSV", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                        }
                    }
                    Text(
                        text = if (showStatementCard) "Hide" else "Open",
                        style = MaterialTheme.typography.labelMedium,
                        color = AnyRollerGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showStatementCard) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = stmtStartDate,
                            onValueChange = { stmtStartDate = it },
                            label = { Text("From (YYYY-MM-DD)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnyRollerGreen,
                                unfocusedBorderColor = AnyRollerBorder,
                                focusedContainerColor = AnyRollerBgInput,
                                unfocusedContainerColor = AnyRollerBgInput,
                                focusedTextColor = AnyRollerTextPrimary,
                                unfocusedTextColor = AnyRollerTextPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("statement_start_date_input")
                        )
                        OutlinedTextField(
                            value = stmtEndDate,
                            onValueChange = { stmtEndDate = it },
                            label = { Text("To (YYYY-MM-DD)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnyRollerGreen,
                                unfocusedBorderColor = AnyRollerBorder,
                                focusedContainerColor = AnyRollerBgInput,
                                unfocusedContainerColor = AnyRollerBgInput,
                                focusedTextColor = AnyRollerTextPrimary,
                                unfocusedTextColor = AnyRollerTextPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("statement_end_date_input")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AnyRollerBgPrimary)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Trips", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Text("${statementRides.size}", style = MaterialTheme.typography.titleMedium, fontFamily = JetBrainsMonoFont, color = AnyRollerTextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Avg / Trip", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            val avg = if (statementRides.isNotEmpty()) statementTotal / statementRides.size else 0.0
                            Text("£%.2f".format(avg), style = MaterialTheme.typography.titleMedium, fontFamily = JetBrainsMonoFont, color = AnyRollerBlue)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Net Total", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                            Text("£%.2f".format(statementTotal), style = MaterialTheme.typography.titleMedium, fontFamily = JetBrainsMonoFont, color = AnyRollerGreen)
                        }
                    }

                    Button(
                        onClick = {
                            if (state.profile.hasProTaxAccess) {
                                CsvExportHelper.shareTripsCsv(
                                    context = context,
                                    rides = statementRides,
                                    commissionRate = state.activeCommissionRate,
                                    fixedTripFee = state.platformConfig.fixedTripFee
                                )
                            } else {
                                Toast.makeText(context, "HMRC CSV statements are included with Gold Driver.", Toast.LENGTH_SHORT).show()
                                viewModel.launchGoldDriverUpgrade()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange,
                            contentColor = AnyRollerBgPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_statement_csv_button")
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.profile.hasProTaxAccess) "Export Range CSV (${statementRides.size} trips)" else "Unlock Range CSV (Gold)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Gold Driver HMRC Tax-Ready CSV Export
        OutlinedButton(
            onClick = {
                if (state.profile.hasProTaxAccess) {
                    CsvExportHelper.shareTripsCsv(
                        context = context,
                        rides = state.completedRides,
                        commissionRate = state.activeCommissionRate,
                        fixedTripFee = state.platformConfig.fixedTripFee
                    )
                } else {
                    Toast.makeText(context, "HMRC CSV export is included with Gold Driver.", Toast.LENGTH_SHORT).show()
                    viewModel.launchGoldDriverUpgrade()
                }
            },
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("export_earnings_csv_button")
        ) {
            Icon(
                Icons.Rounded.FileDownload,
                contentDescription = null,
                tint = if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.profile.hasProTaxAccess) "Export All Trips CSV (HMRC)" else "Export Trips CSV (Gold Feature)",
                color = if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showGoalsDialog) {
        var todayIn by remember { mutableStateOf(state.profile.earningsGoals.today.toInt().toString()) }
        var weekIn by remember { mutableStateOf(state.profile.earningsGoals.week.toInt().toString()) }
        var monthIn by remember { mutableStateOf(state.profile.earningsGoals.month.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { showGoalsDialog = false },
            containerColor = AnyRollerBgSecondary,
            title = { Text("Edit Earnings Targets (£)", color = AnyRollerTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = todayIn,
                        onValueChange = { todayIn = it.filter(Char::isDigit) },
                        label = { Text("Daily Target (£)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weekIn,
                        onValueChange = { weekIn = it.filter(Char::isDigit) },
                        label = { Text("Weekly Target (£)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = monthIn,
                        onValueChange = { monthIn = it.filter(Char::isDigit) },
                        label = { Text("Monthly Target (£)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateEarningsGoals(
                        today = todayIn.toDoubleOrNull() ?: 200.0,
                        week = weekIn.toDoubleOrNull() ?: 1000.0,
                        month = monthIn.toDoubleOrNull() ?: 4000.0
                    )
                    showGoalsDialog = false
                }) { Text("Save", color = AnyRollerGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showGoalsDialog = false }) { Text("Cancel", color = AnyRollerTextMuted) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriverExpensesScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date()) }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var editingExpenseId by remember { mutableStateOf<String?>(null) }
    var editingExistingReceiptUrl by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Fuel") }
    var dateInput by remember { mutableStateOf(todayStr) }
    var notes by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }

    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var selectedMonth by remember { mutableIntStateOf(0) } // 0 = All Year, 1..12 = Jan..Dec

    val categories = listOf("Fuel", "Insurance", "Maintenance", "Charging", "Tolls", "Car Wash", "Other")
    val categoryColors = remember {
        mapOf(
            "Fuel" to AnyRollerOrange,
            "Insurance" to AnyRollerBlue,
            "Maintenance" to AnyRollerRed,
            "Charging" to AnyRollerGreen,
            "Tolls" to AnyRollerPurple,
            "Car Wash" to Color(0xFF06B6D4),
            "Other" to AnyRollerTextSecondary
        )
    }
    val monthLabels = listOf("All", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    val scope = rememberCoroutineScope()
    var isScanningReceipt by remember { mutableStateOf(false) }

    val receiptPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> receiptUri = uri }

    val receiptOcrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            receiptUri = uri
            isScanningReceipt = true
            scope.launch {
                val res = ReceiptOcrHelper.scanReceipt(context, uri)
                isScanningReceipt = false
                res.onSuccess { scan ->
                    val filled = mutableListOf<String>()
                    scan.amount?.let {
                        amount = "%.2f".format(it)
                        filled += "£%.2f".format(it)
                    }
                    scan.dateIso?.let {
                        dateInput = it
                        filled += it
                    }
                    scan.suggestedCategory?.let {
                        if (it in categories) {
                            category = it
                            filled += it
                        }
                    }
                    if (notes.isBlank() && !scan.merchantHint.isNullOrBlank()) {
                        notes = scan.merchantHint
                    }
                    val summary = if (filled.isNotEmpty()) {
                        "Receipt scanned: ${filled.joinToString(" • ")}"
                    } else {
                        "Receipt attached — enter amount manually if unreadable."
                    }
                    Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Receipt attached (OCR unavailable for this image).", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filteredExpenses = remember(state.expenses, selectedYear, selectedMonth) {
        state.expenses.filter { exp ->
            val parts = exp.date.split("-")
            val y = parts.getOrNull(0)?.toIntOrNull()
            val m = parts.getOrNull(1)?.toIntOrNull()
            val matchesYear = y == null || y == selectedYear
            val matchesMonth = selectedMonth == 0 || m == selectedMonth
            matchesYear && matchesMonth
        }
    }

    val totalLogged = filteredExpenses.sumOf { it.amount }
    val categoryTotals = remember(filteredExpenses) {
        filteredExpenses
            .groupBy { it.category.ifBlank { "Other" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
    }

    // HMRC Auto-Tracked Business Mileage (first 10,000 miles @ 45p/mi, remainder @ 25p/mi)
    val completedMilesSum = remember(state.completedRides) { state.completedRides.sumOf { it.distanceMiles } }
    val trackedMiles = maxOf(state.profile.totalBusinessMileage, completedMilesSum)
    val hmrcDeduction = if (trackedMiles <= 10_000.0) {
        trackedMiles * 0.45
    } else {
        (10_000.0 * 0.45) + ((trackedMiles - 10_000.0) * 0.25)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Driver Expenses & Tax", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // HMRC Auto-Tracked Mileage Card
            item {
                Surface(
                    color = AnyRollerBgSecondary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (state.profile.hasProTaxAccess) AnyRollerGreen.copy(alpha = 0.5f) else AnyRollerOrange.copy(alpha = 0.5f),
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = AnyRollerGreen)
                                Spacer(Modifier.width(8.dp))
                                Text("HMRC Business Mileage", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                            }
                            Text(
                                text = if (state.profile.hasProTaxAccess) "PRO TAX ACTIVE" else "GOLD PRO",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Auto-Tracked Miles", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                                Text(
                                    "%.1f mi".format(trackedMiles),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = JetBrainsMonoFont,
                                    color = AnyRollerTextPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("HMRC Claim (45p/mi)", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                                Text(
                                    "£%.2f".format(hmrcDeduction),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = JetBrainsMonoFont,
                                    color = AnyRollerGreen
                                )
                            }
                        }
                        if (!state.profile.hasProTaxAccess) {
                            Button(
                                onClick = { viewModel.launchGoldDriverUpgrade() },
                                colors = ButtonDefaults.buttonColors(containerColor = AnyRollerOrange, contentColor = AnyRollerBgPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("unlock_pro_tax_button")
                            ) {
                                Text("Unlock Pro Tax & HMRC Exports (Gold)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Add / Edit Expense Form (All 7 Categories + Date + Receipt)
            item {
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
                            Text(
                                text = if (editingExpenseId != null) "Edit Expense" else "Log Tax-Deductible Expense",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (editingExpenseId != null) AnyRollerBlue else AnyRollerTextPrimary
                            )
                            if (editingExpenseId != null) {
                                TextButton(
                                    onClick = {
                                        editingExpenseId = null
                                        editingExistingReceiptUrl = null
                                        amount = ""
                                        category = "Fuel"
                                        dateInput = todayStr
                                        notes = ""
                                        receiptUri = null
                                    },
                                    modifier = Modifier.testTag("cancel_edit_expense_button")
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = null, tint = AnyRollerTextMuted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cancel", color = AnyRollerTextMuted)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = { Text("Amount (£)") },
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
                                modifier = Modifier.weight(1f).testTag("expense_amount_input")
                            )
                            OutlinedTextField(
                                value = dateInput,
                                onValueChange = { dateInput = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AnyRollerGreen,
                                    unfocusedBorderColor = AnyRollerBorder,
                                    focusedContainerColor = AnyRollerBgInput,
                                    unfocusedContainerColor = AnyRollerBgInput,
                                    focusedTextColor = AnyRollerTextPrimary,
                                    unfocusedTextColor = AnyRollerTextPrimary
                                ),
                                modifier = Modifier.weight(1f).testTag("expense_date_input")
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val sel = category == cat
                                Surface(
                                    onClick = { category = cat },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) AnyRollerGreen else AnyRollerBgTertiary,
                                    modifier = Modifier.testTag("expense_cat_${cat.lowercase().replace(" ", "_")}")
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (sel) AnyRollerBgPrimary else AnyRollerTextPrimary,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes (e.g. Shell A40 diesel)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnyRollerGreen,
                                unfocusedBorderColor = AnyRollerBorder,
                                focusedContainerColor = AnyRollerBgInput,
                                unfocusedContainerColor = AnyRollerBgInput,
                                focusedTextColor = AnyRollerTextPrimary,
                                unfocusedTextColor = AnyRollerTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("expense_notes_input")
                        )

                        // 1-Tap On-Device ML Kit Receipt OCR Scan
                        OutlinedButton(
                            onClick = {
                                receiptOcrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !isScanningReceipt && !state.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerBlue.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("scan_receipt_ocr_button")
                        ) {
                            Icon(Icons.Rounded.DocumentScanner, contentDescription = null, tint = AnyRollerBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isScanningReceipt) "Scanning Receipt with On-Device OCR…" else "Scan Receipt Photo (Auto-Fill £ & Date)",
                                color = AnyRollerBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    receiptPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("attach_receipt_button")
                            ) {
                                Text(
                                    text = when {
                                        receiptUri != null -> "New Receipt ✓"
                                        !editingExistingReceiptUrl.isNullOrBlank() -> "Receipt Kept ✓"
                                        else -> "Attach Receipt"
                                    },
                                    color = AnyRollerTextPrimary
                                )
                            }
                            Button(
                                onClick = {
                                    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                                    val editId = editingExpenseId
                                    if (editId != null) {
                                        viewModel.updateExpense(
                                            expenseId = editId,
                                            amount = parsedAmount,
                                            category = category,
                                            date = dateInput,
                                            notes = notes,
                                            newReceiptUri = receiptUri,
                                            existingReceiptUrl = editingExistingReceiptUrl
                                        )
                                    } else {
                                        viewModel.addExpense(parsedAmount, category, notes, receiptUri, dateInput)
                                    }
                                    editingExpenseId = null
                                    editingExistingReceiptUrl = null
                                    amount = ""
                                    notes = ""
                                    dateInput = todayStr
                                    receiptUri = null
                                },
                                enabled = !state.isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("save_expense_button")
                            ) {
                                Text(if (editingExpenseId != null) "Update Expense" else "Save Expense", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Month & Year Filter Bar + Category Breakdown Donut Chart
            item {
                Surface(
                    color = AnyRollerBgSecondary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Filter & Breakdown", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(currentYear - 1, currentYear).forEach { yr ->
                                    val sel = selectedYear == yr
                                    Surface(
                                        onClick = { selectedYear = yr },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (sel) AnyRollerBlue else AnyRollerBgTertiary,
                                        modifier = Modifier.testTag("expense_year_$yr")
                                    ) {
                                        Text(
                                            "$yr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (sel) Color.White else AnyRollerTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            monthLabels.forEachIndexed { idx, label ->
                                val sel = selectedMonth == idx
                                Surface(
                                    onClick = { selectedMonth = idx },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (sel) AnyRollerGreen else AnyRollerBgTertiary,
                                    modifier = Modifier.testTag("expense_month_$idx")
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

                        if (categoryTotals.isNotEmpty() && totalLogged > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 14.dp.toPx()
                                        var startAngle = -90f
                                        categoryTotals.forEach { (cat, sum) ->
                                            val sweep = ((sum / totalLogged) * 360.0).toFloat().coerceAtLeast(2f)
                                            drawArc(
                                                color = categoryColors[cat] ?: AnyRollerOrange,
                                                startAngle = startAngle,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                            )
                                            startAngle += sweep
                                        }
                                    }
                                    Text(
                                        "£%.0f".format(totalLogged),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFont,
                                        fontWeight = FontWeight.Bold,
                                        color = AnyRollerTextPrimary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    categoryTotals.take(5).forEach { (cat, sum) ->
                                        val pct = (sum / totalLogged) * 100.0
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(categoryColors[cat] ?: AnyRollerOrange)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(cat, style = MaterialTheme.typography.labelSmall, color = AnyRollerTextPrimary)
                                            }
                                            Text(
                                                "£%.2f (%.0f%%)".format(sum, pct),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = JetBrainsMonoFont,
                                                color = AnyRollerTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Logged Expenses (${filteredExpenses.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = AnyRollerTextPrimary
                        )
                        Text(
                            "Total: £%.2f".format(totalLogged),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFont,
                            color = AnyRollerOrange
                        )
                    }
                    TextButton(
                        onClick = {
                            if (state.profile.hasProTaxAccess) {
                                CsvExportHelper.shareExpensesCsv(context, filteredExpenses)
                            } else {
                                Toast.makeText(context, "HMRC Expense CSV export is included with Gold Driver.", Toast.LENGTH_SHORT).show()
                                viewModel.launchGoldDriverUpgrade()
                            }
                        },
                        modifier = Modifier.testTag("export_expenses_csv_button")
                    ) {
                        Icon(
                            Icons.Rounded.FileDownload,
                            contentDescription = "Export Expenses CSV",
                            tint = if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (state.profile.hasProTaxAccess) "Export CSV" else "CSV (Gold)",
                            color = if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(filteredExpenses, key = { it.id }) { exp ->
                Surface(
                    color = AnyRollerBgSecondary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${exp.category} • ${exp.date}", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                            if (exp.notes.isNotBlank()) {
                                Text(exp.notes, style = MaterialTheme.typography.bodySmall, color = AnyRollerTextMuted)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "£%.2f".format(exp.amount),
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = JetBrainsMonoFont,
                                color = AnyRollerOrange
                            )
                            IconButton(
                                onClick = {
                                    editingExpenseId = exp.id
                                    editingExistingReceiptUrl = exp.receiptImage
                                    amount = "%.2f".format(exp.amount)
                                    category = exp.category.ifBlank { "Fuel" }
                                    dateInput = exp.date.ifBlank { todayStr }
                                    notes = exp.notes
                                    receiptUri = null
                                },
                                modifier = Modifier.testTag("edit_expense_${exp.id}")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit expense", tint = AnyRollerBlue)
                            }
                            IconButton(
                                onClick = { viewModel.deleteExpense(exp.id) },
                                modifier = Modifier.testTag("delete_expense_${exp.id}")
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete expense", tint = AnyRollerRed)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun DriverTripHistoryScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.UK) }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var selectedYear by remember { mutableIntStateOf(0) } // 0 = All Years
    var selectedMonth by remember { mutableIntStateOf(0) } // 0 = All Months
    var dayFilterText by remember { mutableStateOf("") } // Optional 1..31
    val monthLabels = listOf("All", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    val filteredRides = remember(state.completedRides, selectedYear, selectedMonth, dayFilterText) {
        val targetDay = dayFilterText.trim().toIntOrNull()
        val cal = Calendar.getInstance()
        state.completedRides.filter { ride ->
            cal.timeInMillis = maxOf(ride.completedAtMs, ride.createdAtMs)
            val yOk = selectedYear == 0 || cal.get(Calendar.YEAR) == selectedYear
            val mOk = selectedMonth == 0 || (cal.get(Calendar.MONTH) + 1) == selectedMonth
            val dOk = targetDay == null || cal.get(Calendar.DAY_OF_MONTH) == targetDay
            yOk && mOk && dOk
        }
    }
    val filteredNetTotal = remember(filteredRides, state.activeCommissionRate, state.platformConfig.fixedTripFee) {
        filteredRides.sumOf {
            resolveRideFinancialSummary(
                it,
                FallbackFareConfig(state.activeCommissionRate, state.platformConfig.fixedTripFee)
            ).driverEarnings
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SubScreenTopBar(title = "Completed Trip History", onBack = onBack)
            }
            TextButton(
                onClick = {
                    if (state.profile.hasProTaxAccess) {
                        CsvExportHelper.shareTripsCsv(
                            context = context,
                            rides = filteredRides,
                            commissionRate = state.activeCommissionRate,
                            fixedTripFee = state.platformConfig.fixedTripFee
                        )
                    } else {
                        Toast.makeText(context, "HMRC Trip CSV export is included with Gold Driver.", Toast.LENGTH_SHORT).show()
                        viewModel.launchGoldDriverUpgrade()
                    }
                },
                modifier = Modifier.testTag("export_history_csv_button")
            ) {
                Icon(
                    Icons.Rounded.FileDownload,
                    contentDescription = "Export Trips CSV",
                    tint = if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (state.profile.hasProTaxAccess) "CSV" else "Gold CSV",
                    color = if (state.profile.hasProTaxAccess) AnyRollerGreen else AnyRollerOrange,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Day / Month / Year Filter Bar
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                            .testTag("history_year_$yr")
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
                    value = dayFilterText,
                    onValueChange = { input -> dayFilterText = input.filter(Char::isDigit).take(2) },
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
                    modifier = Modifier.width(74.dp).testTag("history_day_filter_input")
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${filteredRides.size} trips", style = MaterialTheme.typography.labelSmall, color = AnyRollerTextMuted)
                    Text(
                        "£%.2f".format(filteredNetTotal),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = JetBrainsMonoFont,
                        color = AnyRollerGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

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
                        modifier = Modifier.testTag("history_month_$idx")
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
        }

        if (filteredRides.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No completed trips match this filter.", color = AnyRollerTextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRides, key = { it.id }) { ride ->
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
                                    color = AnyRollerGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            RouteAddressBlock(pickup = ride.pickupAddress, dropoff = ride.dropoffAddress)
                            HorizontalDivider(color = AnyRollerBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "Base £%.2f • Fee %.1f%% • Tip £%.2f".format(
                                        summary.baseFare,
                                        summary.appliedCommissionPct,
                                        summary.tipAmount
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnyRollerTextMuted
                                )
                                Text(
                                    ride.paymentMethod.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnyRollerTextSecondary
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("subscreen_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Go Back", tint = AnyRollerTextPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = AnyRollerTextPrimary)
    }
}
