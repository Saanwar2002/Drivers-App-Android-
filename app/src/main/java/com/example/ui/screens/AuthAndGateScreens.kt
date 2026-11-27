package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CURRENT_TERMS_VERSION
import com.example.data.model.DriverProfile
import com.example.data.network.ApiClient
import com.example.service.ExternalIntentHelpers
import com.example.ui.theme.AnyRollerBgInput
import com.example.ui.theme.AnyRollerBgPrimary
import com.example.ui.theme.AnyRollerBgSecondary
import com.example.ui.theme.AnyRollerBorder
import com.example.ui.theme.AnyRollerGreen
import com.example.ui.theme.AnyRollerOrange
import com.example.ui.theme.AnyRollerRed
import com.example.ui.theme.AnyRollerTextMuted
import com.example.ui.theme.AnyRollerTextPrimary
import com.example.ui.theme.AnyRollerTextSecondary

@Composable
fun LoadingGateScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AnyRollerBgSecondary)
                    .border(1.dp, AnyRollerGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "AnyRoller Driver",
                    tint = AnyRollerGreen,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = AnyRollerGreen, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Connecting to AnyRoller Dispatch...",
                style = MaterialTheme.typography.bodyMedium,
                color = AnyRollerTextMuted
            )
        }
    }
}

@Composable
fun DriverSignInScreen(
    errorMessage: String?,
    isBusy: Boolean,
    onSignInEmail: (String, String) -> Unit,
    onSignInGoogle: () -> Unit,
    onForgotPassword: (String) -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, AnyRollerBorder, RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_driver_auth_hero),
                    contentDescription = "AnyRoller Driver Terminal Hero",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, AnyRollerBgPrimary.copy(alpha = 0.92f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AnyRollerGreen)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ANYROLLER DISPATCH",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnyRollerGreen
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Driver Terminal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AnyRollerTextPrimary
                    )
                    Text(
                        text = "Sign in with your unified AnyTrader driver account",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AnyRollerBorder, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (!errorMessage.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AnyRollerRed.copy(alpha = 0.14f))
                                .border(1.dp, AnyRollerRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AnyRollerRed
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Driver Email") },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = AnyRollerTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnyRollerGreen,
                            unfocusedBorderColor = AnyRollerBorder,
                            focusedContainerColor = AnyRollerBgInput,
                            unfocusedContainerColor = AnyRollerBgInput,
                            focusedTextColor = AnyRollerTextPrimary,
                            unfocusedTextColor = AnyRollerTextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_email_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = AnyRollerTextMuted) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = AnyRollerTextMuted
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnyRollerGreen,
                            unfocusedBorderColor = AnyRollerBorder,
                            focusedContainerColor = AnyRollerBgInput,
                            unfocusedContainerColor = AnyRollerBgInput,
                            focusedTextColor = AnyRollerTextPrimary,
                            unfocusedTextColor = AnyRollerTextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_password_input")
                    )

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = { onForgotPassword(email) },
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Text("Forgot password?", color = AnyRollerTextMuted)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { onSignInEmail(email, password) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnyRollerGreen,
                            contentColor = AnyRollerBgPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("signin_email_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = AnyRollerBgPrimary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.action_sign_in_email),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = AnyRollerBorder)
                        Text(
                            text = "  OR  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnyRollerTextMuted
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = AnyRollerBorder)
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onSignInGoogle,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AnyRollerBgInput,
                            contentColor = AnyRollerTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("signin_google_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_sign_in_google),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // New Driver Onboarding CTA
            Surface(
                color = AnyRollerBgSecondary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AnyRollerBorder, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "New to AnyRoller?",
                        style = MaterialTheme.typography.titleSmall,
                        color = AnyRollerTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Create your unified AnyTrader account and select AnyRoller Driver during setup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            ExternalIntentHelpers.openCustomTab(
                                context,
                                ApiClient.resolvedBaseUrl.trimEnd('/')
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerGreen.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("become_driver_button")
                    ) {
                        Text(
                            text = "Become a Driver on AnyTrader",
                            style = MaterialTheme.typography.labelLarge,
                            color = AnyRollerGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Connected live to AnyTrader / AnyRoller UK Fleet",
                style = MaterialTheme.typography.labelSmall,
                color = AnyRollerTextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MaintenanceGateScreen(message: String, onRetry: () -> Unit) {
    GateInfoLayout(
        icon = Icons.Rounded.Build,
        accent = AnyRollerOrange,
        title = "Scheduled Maintenance",
        body = message,
        primaryLabel = "Check Again",
        onPrimary = onRetry,
        primaryTag = "maintenance_retry_button"
    )
}

@Composable
fun NonDriverGateScreen(email: String, onSignOut: () -> Unit) {
    val context = LocalContext.current
    GateInfoLayout(
        icon = Icons.Rounded.PersonOff,
        accent = AnyRollerOrange,
        title = "Driver Account Required",
        body = "The account ($email) is not registered as an AnyRoller Driver. Apply as a driver on AnyTrader or sign in with your driver account.",
        primaryLabel = "Sign Out",
        onPrimary = onSignOut,
        primaryTag = "nondriver_signout_button",
        secondaryLabel = "Open AnyTrader Driver Portal",
        onSecondary = {
            ExternalIntentHelpers.openCustomTab(context, ApiClient.resolvedBaseUrl.trimEnd('/'))
        }
    )
}

@Composable
fun SuspendedGateScreen(statusLabel: String, onSignOut: () -> Unit) {
    GateInfoLayout(
        icon = Icons.Rounded.Block,
        accent = AnyRollerRed,
        title = "Account ${statusLabel.replaceFirstChar { it.uppercase() }}",
        body = "Your AnyRoller Driver access is currently restricted ($statusLabel). Please contact support@anytrader.co.uk for assistance.",
        primaryLabel = "Sign Out",
        onPrimary = onSignOut,
        primaryTag = "suspended_signout_button"
    )
}

@Composable
fun PendingApprovalGateScreen(profile: DriverProfile, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val uploadedCount = profile.verificationDocs.count { !it.fileUrl.isNullOrBlank() }
    GateInfoLayout(
        icon = Icons.Rounded.HourglassTop,
        accent = AnyRollerOrange,
        title = "Verification Pending",
        body = "Welcome ${profile.fullName}. Your driver profile is under compliance review ($uploadedCount/5 documents on file). Once approved by AnyRoller Ops, your Driver Terminal unlocks automatically.",
        primaryLabel = "Check Documents on Web",
        onPrimary = {
            ExternalIntentHelpers.openCustomTab(context, "${ApiClient.resolvedBaseUrl.trimEnd('/')}/driver-terminal")
        },
        primaryTag = "pending_docs_button",
        secondaryLabel = "Sign Out",
        onSecondary = onSignOut
    )
}

@Composable
fun LegalTermsGateScreen(
    profile: DriverProfile,
    isSubmitting: Boolean,
    errorMessage: String?,
    onAccept: (Boolean, Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var agreedCore by remember { mutableStateOf(false) }
    var marketingOptIn by remember { mutableStateOf(false) }
    var partnerOptIn by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = AnyRollerBgSecondary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .border(1.dp, AnyRollerBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = AnyRollerGreen, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Driver Terms & Privacy", style = MaterialTheme.typography.titleLarge, color = AnyRollerTextPrimary)
                        Text("Version $CURRENT_TERMS_VERSION • Required", style = MaterialTheme.typography.labelSmall, color = AnyRollerGreen)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Hi ${profile.fullName}, please review and accept the latest AnyTrader / AnyRoller Driver Terms, UK PHV Compliance Policy, and Privacy Notice before going online.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnyRollerTextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        ExternalIntentHelpers.openCustomTab(context, "${ApiClient.resolvedBaseUrl.trimEnd('/')}/terms")
                    }) {
                        Text("Read Terms", color = AnyRollerGreen)
                    }
                    TextButton(onClick = {
                        ExternalIntentHelpers.openCustomTab(context, "${ApiClient.resolvedBaseUrl.trimEnd('/')}/privacy")
                    }) {
                        Text("Privacy Notice", color = AnyRollerGreen)
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = AnyRollerRed)
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = agreedCore,
                        onCheckedChange = { agreedCore = it },
                        colors = CheckboxDefaults.colors(checkedColor = AnyRollerGreen, checkmarkColor = AnyRollerBgPrimary),
                        modifier = Modifier.testTag("terms_agree_checkbox")
                    )
                    Text(
                        "I accept the Driver Terms & Privacy Policy (v$CURRENT_TERMS_VERSION)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = marketingOptIn,
                        onCheckedChange = { marketingOptIn = it },
                        colors = CheckboxDefaults.colors(checkedColor = AnyRollerGreen, checkmarkColor = AnyRollerBgPrimary)
                    )
                    Text(
                        "Optional: Receive AnyRoller driver incentives & promo alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextMuted
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = partnerOptIn,
                        onCheckedChange = { partnerOptIn = it },
                        colors = CheckboxDefaults.colors(checkedColor = AnyRollerGreen, checkmarkColor = AnyRollerBgPrimary)
                    )
                    Text(
                        "Optional: Share eligibility with UK PHV insurance partners",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnyRollerTextMuted
                    )
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onAccept(marketingOptIn, partnerOptIn) },
                    enabled = agreedCore && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("accept_terms_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = AnyRollerBgPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Accept & Enter Terminal", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out", color = AnyRollerTextMuted)
                }
            }
        }
    }
}

@Composable
private fun GateInfoLayout(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryTag: String,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnyRollerBgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = AnyRollerBgSecondary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 460.dp)
                .border(1.dp, AnyRollerBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = AnyRollerTextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = AnyRollerTextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(containerColor = AnyRollerGreen, contentColor = AnyRollerBgPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag(primaryTag)
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.Bold)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSecondary,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AnyRollerBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(secondaryLabel, color = AnyRollerTextPrimary)
                    }
                }
            }
        }
    }
}
