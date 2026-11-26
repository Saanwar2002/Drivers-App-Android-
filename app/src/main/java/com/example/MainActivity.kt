package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AnyRollerMessagingService
import com.example.service.UrgentPushEvent
import com.example.service.UrgentPushEventBus
import com.example.ui.screens.DriverSignInScreen
import com.example.ui.screens.DriverTerminalRoot
import com.example.ui.screens.LegalTermsGateScreen
import com.example.ui.screens.LoadingGateScreen
import com.example.ui.screens.MaintenanceGateScreen
import com.example.ui.screens.NonDriverGateScreen
import com.example.ui.screens.PendingApprovalGateScreen
import com.example.ui.screens.SuspendedGateScreen
import com.example.ui.theme.AnyRollerDriverTheme
import com.example.viewmodel.DriverTerminalViewModel
import com.example.viewmodel.SessionGateState
import com.example.viewmodel.SessionViewModel

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels()
    private val terminalViewModel: DriverTerminalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AnyRollerMessagingService.createNotificationChannels(applicationContext)
        handleIncomingIntent(intent)

        setContent {
            AnyRollerDriverTheme {
                val context = LocalContext.current
                val gateState by sessionViewModel.gateState.collectAsStateWithLifecycle()
                val authBusy by sessionViewModel.authBusy.collectAsStateWithLifecycle()
                val sessionBanner by sessionViewModel.statusBanner.collectAsStateWithLifecycle()
                val terminalState by terminalViewModel.uiState.collectAsStateWithLifecycle()

                // Request POST_NOTIFICATIONS on Android 13+ when driver enters Ready state
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    sessionViewModel.registerFcmTokenNow()
                }

                LaunchedEffect(sessionBanner) {
                    if (!sessionBanner.isNullOrBlank()) {
                        Toast.makeText(context, sessionBanner, Toast.LENGTH_LONG).show()
                        sessionViewModel.clearBanner()
                    }
                }

                // Keep cockpit screen awake while Online or on an Active Trip
                LaunchedEffect(terminalState.isOnline, terminalState.activeRide != null) {
                    if (terminalState.isOnline || terminalState.activeRide != null) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                when (val gate = gateState) {
                    is SessionGateState.Loading -> LoadingGateScreen()

                    is SessionGateState.Maintenance -> MaintenanceGateScreen(
                        message = gate.message,
                        onRetry = { sessionViewModel.checkMaintenanceAndBootstrap() }
                    )

                    is SessionGateState.SignedOut -> DriverSignInScreen(
                        errorMessage = gate.errorMessage,
                        isBusy = authBusy,
                        onSignInEmail = { email, password ->
                            sessionViewModel.signInWithEmail(email, password)
                        },
                        onSignInGoogle = {
                            sessionViewModel.signInWithGoogle(this@MainActivity)
                        },
                        onForgotPassword = { email ->
                            sessionViewModel.sendPasswordReset(email)
                        }
                    )

                    is SessionGateState.NonDriverAccount -> NonDriverGateScreen(
                        email = gate.email,
                        onSignOut = { sessionViewModel.signOut() }
                    )

                    is SessionGateState.SuspendedAccount -> SuspendedGateScreen(
                        statusLabel = gate.statusLabel,
                        onSignOut = { sessionViewModel.signOut() }
                    )

                    is SessionGateState.TermsRequired -> LegalTermsGateScreen(
                        profile = gate.profile,
                        isSubmitting = gate.isSubmitting,
                        errorMessage = gate.error,
                        onAccept = { marketing, partner ->
                            sessionViewModel.acceptCurrentTerms(marketing, partner)
                        },
                        onSignOut = { sessionViewModel.signOut() }
                    )

                    is SessionGateState.PendingApproval -> PendingApprovalGateScreen(
                        profile = gate.profile,
                        onSignOut = { sessionViewModel.signOut() }
                    )

                    is SessionGateState.Ready -> {
                        key(gate.uid) {
                            LaunchedEffect(gate.uid) {
                                terminalViewModel.bindToDriver(gate.uid, gate.profile)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }

                            DriverTerminalRoot(
                                state = terminalState,
                                viewModel = terminalViewModel,
                                onSignOut = { sessionViewModel.signOut() },
                                onExportGdpr = {
                                    sessionViewModel.exportGdprData { }
                                },
                                onDeleteAccount = { cb ->
                                    sessionViewModel.deleteAccountPermanently(cb)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        // Clear active chat presence heartbeat when app goes to background
        // so server's 45s WhatsApp fallback activates cleanly if driver is away
        if (terminalViewModel.uiState.value.showChatSheet) {
            terminalViewModel.stopChatPresence()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null) {
            val path = data.path ?: ""
            if (path.startsWith("/driver-return") ||
                path.startsWith("/platform-fee-success") ||
                path.startsWith("/driver-terminal") ||
                path.startsWith("/driver-dashboard")
            ) {
                UrgentPushEventBus.emit(UrgentPushEvent.DeepLinkReturned(path))
            }
        }
    }
}
