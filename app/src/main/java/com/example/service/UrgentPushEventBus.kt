package com.example.service

import com.example.data.model.IncomingOfferPush
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class UrgentPushEvent {
    data class RideOffer(val offer: IncomingOfferPush) : UrgentPushEvent()
    data class RideCancelled(val rideId: String, val reason: String? = null) : UrgentPushEvent()
    data class RideAcceptedElsewhere(val rideId: String) : UrgentPushEvent()
    data class ChatMessageReceived(val rideId: String, val title: String, val body: String) : UrgentPushEvent()
    data class DailyLimitReached(val maxHours: Double) : UrgentPushEvent()
    data object AuthRevoked : UrgentPushEvent()
    data class DeepLinkReturned(val path: String) : UrgentPushEvent()
}

data class DriverLocationSnapshot(
    val lat: Double = 51.5074, // Default Central London until GPS fix arrives
    val lng: Double = -0.1278,
    val heading: Float = 0f,
    val speedMps: Float = 0f,
    val hasRealFix: Boolean = false
)

object UrgentPushEventBus {
    private val _events = MutableSharedFlow<UrgentPushEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<UrgentPushEvent> = _events.asSharedFlow()

    private val _lastLocation = MutableStateFlow(DriverLocationSnapshot())
    val lastLocation: StateFlow<DriverLocationSnapshot> = _lastLocation.asStateFlow()

    private val _isLocationServiceRunning = MutableStateFlow(false)
    val isLocationServiceRunning: StateFlow<Boolean> = _isLocationServiceRunning.asStateFlow()

    fun emit(event: UrgentPushEvent) {
        _events.tryEmit(event)
    }

    fun updateLocation(snapshot: DriverLocationSnapshot) {
        _lastLocation.value = snapshot
    }

    fun setLocationServiceRunning(running: Boolean) {
        _isLocationServiceRunning.value = running
    }
}
