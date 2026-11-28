package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.network.ApiClient
import com.example.data.network.ApiResult
import com.example.data.repository.DriverFirestoreRepository
import com.example.data.repository.DriverPreferencesStore
import com.example.data.repository.FirebaseProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DriverLocationService : Service() {

    companion object {
        private const val TAG = "DriverLocationService"
        const val CHANNEL_ID = "driver_online_location"
        private const val NOTIFICATION_ID = 4101
        const val ACTION_START = "com.anyroller.driver.action.START_LOCATION"
        const val ACTION_STOP = "com.anyroller.driver.action.STOP_LOCATION"
        const val ACTION_UPDATE_STATE = "com.anyroller.driver.action.UPDATE_STATE"

        const val EXTRA_DRIVER_NAME = "extra_driver_name"
        const val EXTRA_IS_ON_RIDE = "extra_is_on_ride"

        fun start(context: Context, driverName: String, isOnRide: Boolean = false) {
            val intent = Intent(context, DriverLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DRIVER_NAME, driverName)
                putExtra(EXTRA_IS_ON_RIDE, isOnRide)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateOnRide(context: Context, driverName: String, isOnRide: Boolean) {
            if (!UrgentPushEventBus.isLocationServiceRunning.value) return
            val intent = Intent(context, DriverLocationService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_DRIVER_NAME, driverName)
                putExtra(EXTRA_IS_ON_RIDE, isOnRide)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DriverLocationService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var repository: DriverFirestoreRepository
    private lateinit var prefsStore: DriverPreferencesStore

    private var heartbeatJob: Job? = null
    private var locationCallback: LocationCallback? = null
    private var driverName: String = "AnyRoller Driver"
    private var isOnRide: Boolean = false
    private var lastWriteMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        repository = DriverFirestoreRepository(applicationContext)
        prefsStore = DriverPreferencesStore(applicationContext)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTrackingAndShutdown()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE -> {
                val previousOnRide = isOnRide
                driverName = intent.getStringExtra(EXTRA_DRIVER_NAME) ?: driverName
                isOnRide = intent.getBooleanExtra(EXTRA_IS_ON_RIDE, isOnRide)
                updateNotification()
                if (previousOnRide != isOnRide && locationCallback != null) {
                    startLocationUpdates()
                }
                return START_STICKY
            }
            else -> {
                driverName = intent?.getStringExtra(EXTRA_DRIVER_NAME) ?: driverName
                isOnRide = intent?.getBooleanExtra(EXTRA_IS_ON_RIDE, false) ?: false
                startForegroundWithLocation()
                startLocationUpdates()
                startHeartbeatLoop()
                UrgentPushEventBus.setLocationServiceRunning(true)
                return START_STICKY
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver Online Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active GPS tracking and dispatch status while you are Online."
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isOnRide) {
            "On active trip — sharing live GPS with rider"
        } else {
            "Online & waiting for ride offers"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AnyRoller Driver — ACTIVE")
            .setContentText(statusText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    private fun startForegroundWithLocation() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Location permission missing; stopping DriverLocationService")
            stopTrackingAndShutdown()
            return
        }

        locationCallback?.let { fusedClient.removeLocationUpdates(it) }

        val intervalMs = if (isOnRide) 3_500L else 5_000L
        val minIntervalMs = if (isOnRide) 2_500L else 4_000L
        val minDistanceMeters = if (isOnRide) 8f else 15f
        val writeThrottleMs = if (isOnRide) 3_500L else 4_500L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val snapshot = DriverLocationSnapshot(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    heading = loc.bearing,
                    speedMps = loc.speed,
                    accuracyMeters = loc.accuracy,
                    hasRealFix = true
                )
                UrgentPushEventBus.updateLocation(snapshot)

                val now = System.currentTimeMillis()
                if (now - lastWriteMs >= writeThrottleMs) {
                    lastWriteMs = now
                    val uid = FirebaseProvider.auth().currentUser?.uid ?: return
                    serviceScope.launch {
                        val categories = prefsStore.localVehicleCategoriesFlow.first().toList()
                        val petFriendly = prefsStore.localPetFriendlyFlow.first()
                        repository.mergeLiveTracking(
                            uid = uid,
                            lat = loc.latitude,
                            lng = loc.longitude,
                            heading = loc.bearing,
                            speedMps = loc.speed,
                            isOnline = true,
                            isOnRide = isOnRide,
                            driverName = driverName,
                            vehicleCategory = categories.firstOrNull() ?: "standard",
                            vehicleCategories = categories,
                            isPetFriendly = petFriendly
                        )
                    }
                }
            }
        }
        locationCallback = callback
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", se)
            stopTrackingAndShutdown()
        }
    }

    /**
     * 60-second online-hours heartbeat loop to `POST /api/driver/metrics/heartbeat`.
     * Automatically takes driver offline if daily hours cap is hit or auth is revoked.
     */
    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                val res = ApiClient.safeCall { ApiClient.service.sendDriverHeartbeat() }
                when (res) {
                    is ApiResult.Success -> {
                        if (res.data.limitReached) {
                            val maxH = res.data.maxDailyDriverHours ?: 10.0
                            UrgentPushEventBus.emit(UrgentPushEvent.DailyLimitReached(maxH))
                            stopTrackingAndShutdown()
                            return@launch
                        }
                    }
                    is ApiResult.Error -> {
                        if (res.isAuthRevoked) {
                            UrgentPushEventBus.emit(UrgentPushEvent.AuthRevoked)
                            stopTrackingAndShutdown()
                            return@launch
                        }
                    }
                }
                delay(60_000L)
            }
        }
    }

    private fun stopTrackingAndShutdown() {
        heartbeatJob?.cancel()
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        UrgentPushEventBus.setLocationServiceRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        UrgentPushEventBus.setLocationServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
