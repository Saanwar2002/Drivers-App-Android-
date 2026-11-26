package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    var periodIndex by remember { mutableIntStateOf(0) } // 0=Today, 1=Week, 2=Month
    var showGoalsDialog by remember { mutableStateOf(false) }

    val displayedEarnings = when (periodIndex) {
        0 -> state.todayEarningsComputed
        1 -> state.weekEarningsComputed
        else -> state.monthEarningsComputed
    }
    val goalTarget = when (periodIndex) {
        0 -> state.profile.earningsGoals.today
        1 -> state.profile.earningsGoals.week
        else -> state.profile.earningsGoals.month
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

        // Period Selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Today", "This Week", "This Month").forEachIndexed { idx, label ->
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

@Composable
fun DriverExpensesScreen(
    state: DriverTerminalUiState,
    viewModel: DriverTerminalViewModel,
    onBack: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Fuel") }
    var notes by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }

    val categories = listOf("Fuel", "Insurance", "Maintenance", "Charging", "Tolls", "Car Wash", "Other")
    val receiptPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> receiptUri = uri }

    val totalLogged = state.expenses.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Driver Expenses", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = AnyRollerBgSecondary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, AnyRollerBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Log Tax-Deductible Expense", style = MaterialTheme.typography.titleMedium, color = AnyRollerTextPrimary)
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
                            modifier = Modifier.fillMaxWidth().testTag("expense_amount_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.take(4).forEach { cat ->
                                val sel = category == cat
                                Surface(
                                    onClick = { category = cat },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) AnyRollerGreen else AnyRollerBgTertiary,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            cat,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (sel) AnyRollerBgPrimary else AnyRollerTextPrimary
                                        )
                                    }
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
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    receiptPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text(if (receiptUri != null) "Receipt Added ✓" else "Attach Receipt", color = AnyRollerTextPrimary)
                            }
                            Button(
                                onClick = {
                                    viewModel.addExpense(amount.toDoubleOrNull() ?: 0.0, category, notes, receiptUri)
                                    amount = ""
                                    notes = ""
                                    receiptUri = null
                                },
                                enabled = !state.isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("save_expense_button")
                            ) {
                                Text("Save Expense", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Logged Expenses", style = MaterialTheme.typography.titleSmall, color = AnyRollerTextPrimary)
                    Text("Total: £%.2f".format(totalLogged), style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMonoFont, color = AnyRollerOrange)
                }
            }

            items(state.expenses, key = { it.id }) { exp ->
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
                            IconButton(onClick = { viewModel.deleteExpense(exp.id) }) {
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
    onBack: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.UK) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Completed Trip History", onBack = onBack)

        if (state.completedRides.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No completed trips recorded yet.", color = AnyRollerTextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
