package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.data.model.CURRENT_TERMS_VERSION
import com.example.data.model.DriverProfile
import com.example.data.network.AcceptTermsRequest
import com.example.data.network.ApiClient
import com.example.data.network.ApiResult
import com.example.data.network.RegisterTokenRequest
import com.example.data.network.UnregisterTokenRequest
import com.example.data.repository.DriverFirestoreRepository
import com.example.data.repository.DriverPreferencesStore
import com.example.data.repository.FirebaseProvider
import com.example.service.DriverLocationService
import com.example.service.UrgentPushEvent
import com.example.service.UrgentPushEventBus
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class SessionGateState {
    data object Loading : SessionGateState()
    data class Maintenance(val message: String) : SessionGateState()
    data class SignedOut(val errorMessage: String? = null) : SessionGateState()
    data class NonDriverAccount(val email: String) : SessionGateState()
    data class SuspendedAccount(val statusLabel: String) : SessionGateState()
    data class TermsRequired(val profile: DriverProfile, val isSubmitting: Boolean = false, val error: String? = null) : SessionGateState()
    data class PendingApproval(val profile: DriverProfile) : SessionGateState()
    data class Ready(val uid: String, val profile: DriverProfile) : SessionGateState()
}

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SessionViewModel"
    }

    private val context: Context = application.applicationContext
    private val auth: FirebaseAuth = FirebaseProvider.auth()
    private val repository = DriverFirestoreRepository(context)
    private val prefsStore = DriverPreferencesStore(context)

    private val _gateState = MutableStateFlow<SessionGateState>(SessionGateState.Loading)
    val gateState: StateFlow<SessionGateState> = _gateState.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var profileJob: Job? = null
    private var hasRunSessionSyncForUid: String? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        handleAuthUserChanged(firebaseAuth.currentUser)
    }

    init {
        auth.addAuthStateListener(authListener)
        checkMaintenanceAndBootstrap()
        observeUrgentSessionEvents()
    }

    private fun observeUrgentSessionEvents() {
        viewModelScope.launch {
            UrgentPushEventBus.events.collect { event ->
                if (event is UrgentPushEvent.AuthRevoked) {
                    _statusBanner.value = "Session expired or revoked. Please sign in again."
                    signOut()
                }
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun checkMaintenanceAndBootstrap() {
        viewModelScope.launch {
            _gateState.value = SessionGateState.Loading
            when (val res = ApiClient.safeCall { ApiClient.service.getMaintenanceStatus() }) {
                is ApiResult.Success -> {
                    if (res.data.maintenanceMode) {
                        _gateState.value = SessionGateState.Maintenance(
                            res.data.message?.takeIf { it.isNotBlank() }
                                ?: "AnyRoller is currently undergoing scheduled maintenance. Please check back shortly."
                        )
                        return@launch
                    }
                }
                is ApiResult.Error -> {
                    // Fail-open on transient health check timeout so offline/poor-signal login still evaluates auth
                    Log.w(TAG, "Maintenance status check failed (${res.httpCode})")
                }
            }
            handleAuthUserChanged(auth.currentUser)
        }
    }

    private fun handleAuthUserChanged(user: FirebaseUser?) {
        profileJob?.cancel()
        if (user == null) {
            hasRunSessionSyncForUid = null
            DriverLocationService.stop(context)
            _gateState.value = SessionGateState.SignedOut()
            return
        }

        _gateState.value = SessionGateState.Loading
        profileJob = viewModelScope.launch {
            repository.observeDriverProfile(user.uid).collect { profile ->
                if (profile == null) {
                    _gateState.value = SessionGateState.NonDriverAccount(user.email ?: "Unknown account")
                    return@collect
                }
                evaluateComplianceGates(user.uid, profile)
            }
        }
    }

    private fun evaluateComplianceGates(uid: String, profile: DriverProfile) {
        if (!profile.hasDriverRole()) {
            _gateState.value = SessionGateState.NonDriverAccount(profile.email.ifBlank { auth.currentUser?.email ?: "" })
            return
        }
        if (profile.isAccountSuspendedOrDisabled()) {
            val label = profile.driverStatus.ifBlank { profile.status.ifBlank { "Suspended" } }
            DriverLocationService.stop(context)
            _gateState.value = SessionGateState.SuspendedAccount(label)
            return
        }
        if (profile.termsAcceptedVersion != CURRENT_TERMS_VERSION) {
            _gateState.value = SessionGateState.TermsRequired(profile)
            return
        }

        if (hasRunSessionSyncForUid != uid) {
            hasRunSessionSyncForUid = uid
            runOncePerSessionBootstrap()
        }

        if (!profile.driverApproved) {
            DriverLocationService.stop(context)
            _gateState.value = SessionGateState.PendingApproval(profile)
            return
        }

        _gateState.value = SessionGateState.Ready(uid, profile)
    }

    private fun runOncePerSessionBootstrap() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Sync expired driver documents with server
            ApiClient.safeCall { ApiClient.service.syncExpiredDocs() }
            // 2. Register FCM push token
            registerFcmTokenNow()
        }
    }

    fun registerFcmTokenNow() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                if (token.isNotBlank()) {
                    prefsStore.saveFcmToken(token)
                    val deviceId = prefsStore.getOrCreateDeviceId()
                    ApiClient.safeCall {
                        ApiClient.service.registerPushToken(
                            RegisterTokenRequest(
                                fcmToken = token,
                                deviceId = deviceId,
                                platform = "android"
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "FCM token registration deferred: ${if (BuildConfig.DEBUG) e.message else "unavailable"}")
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            _gateState.value = SessionGateState.SignedOut("Please enter both email and password.")
            return
        }
        viewModelScope.launch {
            _authBusy.value = true
            try {
                auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Email sign-in failed", if (BuildConfig.DEBUG) e else null)
                _gateState.value = SessionGateState.SignedOut(
                    "Invalid email or password, or account unavailable. Please check your credentials."
                )
            } finally {
                _authBusy.value = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            _statusBanner.value = "Enter your email address first to reset your password."
            return
        }
        viewModelScope.launch {
            _authBusy.value = true
            try {
                auth.sendPasswordResetEmail(trimmed).await()
                _statusBanner.value = "Password reset link sent to $trimmed."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _statusBanner.value = "Unable to send reset email right now. Please check the address."
            } finally {
                _authBusy.value = false
            }
        }
    }

    /**
     * Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption`).
     * Uses a single option per request and catches `GetCredentialCancellationException`
     * separately so dismissing the picker never displays an error.
     */
    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _authBusy.value = true
            try {
                val webClientId = context.getString(R.string.default_web_client_id)
                val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()

                val credentialManager = CredentialManager.create(activityContext)
                val result = credentialManager.getCredential(
                    context = activityContext,
                    request = request
                )
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                    auth.signInWithCredential(firebaseCred).await()
                } else {
                    _gateState.value = SessionGateState.SignedOut("Unsupported Google credential returned.")
                }
            } catch (_: GetCredentialCancellationException) {
                // User dismissed account chooser — stay cleanly on Sign-In screen
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Google CredentialManager failed", if (BuildConfig.DEBUG) e else null)
                _gateState.value = SessionGateState.SignedOut(
                    "Google Sign-In could not complete on this device. You can also sign in with email & password."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Firebase sign-in failed", if (BuildConfig.DEBUG) e else null)
                _gateState.value = SessionGateState.SignedOut("Unable to sign in with Google. Please try again.")
            } finally {
                _authBusy.value = false
            }
        }
    }

    fun acceptCurrentTerms(marketingOptIn: Boolean, partnerSharingOptIn: Boolean) {
        val current = _gateState.value as? SessionGateState.TermsRequired ?: return
        viewModelScope.launch {
            _gateState.value = current.copy(isSubmitting = true, error = null)
            val res = ApiClient.safeCall {
                ApiClient.service.acceptTerms(
                    AcceptTermsRequest(
                        termsVersion = CURRENT_TERMS_VERSION,
                        privacyVersion = CURRENT_TERMS_VERSION,
                        marketingOptIn = marketingOptIn,
                        partnerDataSharingOptIn = partnerSharingOptIn
                    )
                )
            }
            when (res) {
                is ApiResult.Success -> {
                    _statusBanner.value = "Terms v$CURRENT_TERMS_VERSION accepted."
                    // Snapshot listener on users/{uid} will automatically advance the gate
                }
                is ApiResult.Error -> {
                    _gateState.value = current.copy(isSubmitting = false, error = res.message)
                }
            }
        }
    }

    fun exportGdprData(onResult: (String) -> Unit) {
        viewModelScope.launch {
            when (val res = ApiClient.safeCall { ApiClient.service.exportGdprData() }) {
                is ApiResult.Success -> {
                    val msg = res.data.downloadUrl?.let { "GDPR data export ready: $it" }
                        ?: "GDPR data export generated (${res.data.exportedAt ?: "just now"})."
                    _statusBanner.value = msg
                    onResult(msg)
                }
                is ApiResult.Error -> {
                    _statusBanner.value = res.message
                    onResult(res.message)
                }
            }
        }
    }

    fun deleteAccountPermanently(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val res = ApiClient.safeCall { ApiClient.service.deleteAccount() }) {
                is ApiResult.Success -> {
                    onResult(true, "Your account deletion request has been completed.")
                    signOut()
                }
                is ApiResult.Error -> {
                    onResult(false, res.message)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                val uid = auth.currentUser?.uid
                DriverLocationService.stop(context)
                if (uid != null) {
                    repository.markDriverOfflineInFirestore(uid)
                    val deviceId = prefsStore.getOrCreateDeviceId()
                    val token = prefsStore.getCachedFcmToken()
                    ApiClient.safeCall {
                        ApiClient.service.unregisterPushToken(
                            UnregisterTokenRequest(deviceId = deviceId, token = token)
                        )
                    }
                }
                prefsStore.clearSessionTransientPrefs()
                runCatching {
                    CredentialManager.create(context)
                        .clearCredentialState(ClearCredentialStateRequest())
                }
                auth.signOut()
            }
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        profileJob?.cancel()
        super.onCleared()
    }
}
