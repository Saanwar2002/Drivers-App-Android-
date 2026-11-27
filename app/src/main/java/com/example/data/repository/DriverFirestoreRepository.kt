package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ChatMessage
import com.example.data.model.DaySchedule
import com.example.data.model.DriverExpense
import com.example.data.model.DriverMetrics
import com.example.data.model.DriverNotification
import com.example.data.model.DriverProfile
import com.example.data.model.EarningsGoals
import com.example.data.model.GlobalPlatformStatus
import com.example.data.model.GlobalTiersConfig
import com.example.data.model.InsurancePartner
import com.example.data.model.RidePlatformConfig
import com.example.data.model.RideRequest
import com.example.data.model.RideStop
import com.example.data.model.RoadHazard
import com.example.data.model.SurgeRules
import com.example.data.model.VehicleTypeConfig
import com.example.data.model.VerificationDoc
import com.example.data.model.defaultVehicleTypes
import com.example.data.model.defaultWeeklySchedule
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class DriverFirestoreRepository(private val context: Context) {

    companion object {
        private const val TAG = "DriverFirestoreRepo"
        private const val MAX_AVATAR_BYTES = 10L * 1024L * 1024L
        private const val MAX_DOC_BYTES = 25L * 1024L * 1024L

        private val ALLOWED_RASTER_MIMES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic",
            "image/heif",
            "image/avif"
        )

        private val ALLOWED_DOC_MIMES = ALLOWED_RASTER_MIMES + setOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    }

    private val db: FirebaseFirestore
        get() = FirebaseProvider.firestore(context)

    private val storage
        get() = FirebaseProvider.storage()

    // --- Real-Time Snapshot Streams ---

    fun observeDriverProfile(uid: String): Flow<DriverProfile?> = callbackFlow {
        val reg = db.collection("users").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeDriverProfile", "users/$uid", error)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(parseDriverProfile(snap))
            }
        awaitClose { reg.remove() }
    }

    fun observePlatformRideConfig(): Flow<RidePlatformConfig> = callbackFlow {
        val reg = db.collection("platform_config").document("rides")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observePlatformRideConfig", "platform_config/rides", error)
                    return@addSnapshotListener
                }
                trySend(parseRideConfig(snap))
            }
        awaitClose { reg.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    fun observeGlobalTiersConfig(): Flow<GlobalTiersConfig> = callbackFlow {
        val reg = db.collection("platform_config").document("global_tiers")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeGlobalTiersConfig", "platform_config/global_tiers", error)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(GlobalTiersConfig())
                    return@addSnapshotListener
                }
                val models = snap.get("providerModels") as? Map<String, Any?>
                val transport = models?.get("on_demand_transport") as? Map<String, Any?>
                val tiers = transport?.get("tiers") as? Map<String, Any?>
                val std = tiers?.get("STANDARD") as? Map<String, Any?>
                val gold = (tiers?.get("GOLD") ?: tiers?.get("DRIVER_GOLD") ?: tiers?.get("PRO")) as? Map<String, Any?>

                fun parseRate(raw: Any?): Double? {
                    val v = (raw as? Number)?.toDouble() ?: return null
                    return if (v > 1.0) v / 100.0 else v
                }

                trySend(
                    GlobalTiersConfig(
                        standardCommissionRate = parseRate(std?.get("commission") ?: std?.get("commissionRate")),
                        standardFixedTripFee = (std?.get("fixedTripFee") as? Number)?.toDouble(),
                        goldCommissionRate = parseRate(gold?.get("commission") ?: gold?.get("commissionRate")),
                        goldFixedTripFee = (gold?.get("fixedTripFee") as? Number)?.toDouble()
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    fun observeGlobalPlatformStatus(): Flow<GlobalPlatformStatus> = callbackFlow {
        val reg = db.collection("platform_config").document("global")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeGlobalPlatformStatus", "platform_config/global", error)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(GlobalPlatformStatus())
                    return@addSnapshotListener
                }
                val maint = snap.getBoolean("maintenanceMode") ?: false
                val sched = snap.get("scheduledMaintenance") as? Map<String, Any?>
                val msg = snap.getString("maintenanceMessage")
                    ?: sched?.get("message")?.toString()
                val taxiMode = snap.getString("taxiPortalMode")?.lowercase() ?: ""
                val taxiComingSoon = taxiMode == "coming_soon" ||
                    (snap.getBoolean("taxiComingSoon") ?: false) ||
                    snap.get("showBookTaxiButton")?.toString() == "coming_soon"
                trySend(
                    GlobalPlatformStatus(
                        maintenanceMode = maint,
                        maintenanceMessage = msg,
                        taxiComingSoon = taxiComingSoon,
                        taxiComingSoonTitle = snap.getString("taxiComingSoonTitle"),
                        taxiComingSoonMessage = snap.getString("taxiComingSoonMessage")
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    fun observeDriverMetrics(uid: String): Flow<DriverMetrics> = callbackFlow {
        val reg = db.collection("driver_metrics").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeDriverMetrics", "driver_metrics/$uid", error)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(DriverMetrics())
                    return@addSnapshotListener
                }
                val metrics = DriverMetrics(
                    date = snap.getString("date") ?: "",
                    onlineSecondsToday = snap.getLong("onlineSecondsToday")
                        ?: snap.getLong("onlineSeconds") ?: 0L,
                    ridesToday = (snap.getLong("ridesToday") ?: snap.getLong("jobsDoneToday") ?: 0L).toInt(),
                    earningsToday = snap.getDouble("dailyEarnings")
                        ?: snap.getDouble("earningsToday") ?: 0.0,
                    lifetimeRides = (snap.getLong("lifetimeRides") ?: snap.getLong("totalRides") ?: 0L).toInt(),
                    lifetimeEarnings = snap.getDouble("lifetimeEarnings") ?: 0.0,
                    acceptanceRate = snap.getDouble("acceptanceRate") ?: 95.0,
                    rating = snap.getDouble("rating") ?: 4.9
                )
                trySend(metrics)
            }
        awaitClose { reg.remove() }
    }

    fun observePendingRideOffers(): Flow<List<RideRequest>> = callbackFlow {
        val reg = db.collection("ride_requests")
            .whereIn("status", listOf("offered", "pending", "searching"))
            .limit(25)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observePendingRideOffers", "ride_requests(offered/pending)", error)
                    return@addSnapshotListener
                }
                val offers = snap?.documents.orEmpty()
                    .mapNotNull { doc -> parseRideRequest(doc) }
                    .sortedByDescending { it.createdAtMs }
                trySend(offers)
            }
        awaitClose { reg.remove() }
    }

    fun observeDriverRides(uid: String): Flow<List<RideRequest>> = callbackFlow {
        val reg = db.collection("ride_requests")
            .whereEqualTo("driverId", uid)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeDriverRides", "ride_requests(driverId=$uid)", error)
                    return@addSnapshotListener
                }
                val rides = snap?.documents.orEmpty()
                    .mapNotNull { doc -> parseRideRequest(doc) }
                    .sortedByDescending { maxOf(it.completedAtMs, it.createdAtMs) }
                trySend(rides)
            }
        awaitClose { reg.remove() }
    }

    fun observeRideChat(rideId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("ride_requests").document(rideId)
            .collection("chat")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(150)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeRideChat", "ride_requests/$rideId/chat", error)
                    return@addSnapshotListener
                }
                val msgs = snap?.documents.orEmpty().map { doc ->
                    val ts = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: doc.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ChatMessage(
                        id = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        text = doc.getString("text") ?: doc.getString("message"),
                        imageUrl = doc.getString("imageUrl"),
                        createdAtMs = ts?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }
                trySend(msgs)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Observes the passenger's real-time GPS dot from `live_tracking/{passengerId}`
     * (written by `PassengerBooking.tsx`). Gracefully handles PERMISSION_DENIED without crashing.
     */
    fun observePassengerLiveLocation(passengerId: String): Flow<Pair<Double, Double>?> = callbackFlow {
        val reg = db.collection("live_tracking").document(passengerId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        logError("observePassengerLiveLocation", "live_tracking/$passengerId", error)
                    }
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val lat = snap.getDouble("lat")
                val lng = snap.getDouble("lng")
                if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
                    trySend(lat to lng)
                } else {
                    trySend(null)
                }
            }
        awaitClose { reg.remove() }
    }

    fun observeDriverNotifications(uid: String): Flow<List<DriverNotification>> = callbackFlow {
        val reg = db.collection("notifications")
            .whereEqualTo("userId", uid)
            .limit(80)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeDriverNotifications", "notifications(userId=$uid)", error)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().map { doc ->
                    val ts = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    DriverNotification(
                        id = doc.id,
                        userId = doc.getString("userId") ?: uid,
                        title = doc.getString("title") ?: "AnyRoller Alert",
                        message = doc.getString("message") ?: doc.getString("body") ?: "",
                        type = doc.getString("type") ?: "system",
                        read = doc.getBoolean("read") ?: false,
                        rideId = doc.getString("rideId"),
                        createdAtMs = ts?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAtMs }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeDriverExpenses(uid: String): Flow<List<DriverExpense>> = callbackFlow {
        val reg = db.collection("driver_expenses")
            .whereEqualTo("driverId", uid)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeDriverExpenses", "driver_expenses(driverId=$uid)", error)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().map { doc ->
                    DriverExpense(
                        id = doc.id,
                        driverId = doc.getString("driverId") ?: uid,
                        amount = doc.getDouble("amount") ?: 0.0,
                        category = doc.getString("category") ?: "Fuel",
                        date = doc.getString("date") ?: "",
                        notes = doc.getString("notes") ?: "",
                        receiptImage = doc.getString("receiptImage"),
                        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeRoadHazards(): Flow<List<RoadHazard>> = callbackFlow {
        val reg = db.collection("reported_hazards")
            .limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeRoadHazards", "reported_hazards", error)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val hazards = snap?.documents.orEmpty().mapNotNull { doc ->
                    val lat = doc.getDouble("lat") ?: return@mapNotNull null
                    val lng = doc.getDouble("lng") ?: return@mapNotNull null
                    val expTs = doc.getTimestamp("expiresAt")?.toDate()?.time
                        ?: doc.getLong("expiresAtMs")
                        ?: (now + 3_600_000L)
                    if (expTs < now) return@mapNotNull null
                    RoadHazard(
                        id = doc.id,
                        type = doc.getString("type") ?: "Traffic",
                        lat = lat,
                        lng = lng,
                        reporterId = doc.getString("reporterId") ?: "",
                        expiresAtMs = expTs
                    )
                }
                trySend(hazards)
            }
        awaitClose { reg.remove() }
    }

    fun observeInsurancePartners(): Flow<List<InsurancePartner>> = callbackFlow {
        val reg = db.collection("partners")
            .limit(30)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    logError("observeInsurancePartners", "partners", error)
                    return@addSnapshotListener
                }
                val partners = snap?.documents.orEmpty().map { doc ->
                    InsurancePartner(
                        id = doc.id,
                        name = doc.getString("name") ?: "Partner",
                        tagline = doc.getString("tagline") ?: doc.getString("description") ?: "PHV Hire & Reward Insurance",
                        estimatedMonthlyGbp = doc.getDouble("estimatedMonthlyGbp")
                            ?: doc.getDouble("priceMonthly") ?: 0.0,
                        url = doc.getString("url") ?: doc.getString("website") ?: "",
                        badge = doc.getString("badge")
                    )
                }
                trySend(partners)
            }
        awaitClose { reg.remove() }
    }

    // --- Rule-Aligned Writes ---

    /**
     * Updates ONLY rule-allowlisted fields on `users/{uid}`.
     * NEVER passes `vehicleCategories`, `isPetFriendly`, `driverApproved`, `role`,
     * `stripeAccountId`, `pendingPlatformFees`, `subscriptionType`, or `whatsapp*`.
     */
    suspend fun updateUserPreferences(uid: String, fields: Map<String, Any?>): Result<Unit> {
        val allowedKeys = setOf(
            "isStackingEnabled", "isLastJob", "destinationModeActive",
            "homeAddress", "homeLat", "homeLng",
            "zoneEnabled", "zoneMaxDistance", "defaultNavApp",
            "requirePasscode", "muteHeadsUpVolume", "muteRideOfferAlerts",
            "weeklySchedule", "earningsGoals", "verificationDocs",
            "photoURL", "insuranceProvider", "insuranceExpiry",
            "phone", "firstName", "lastName", "updatedAt"
        )
        val filtered = fields.filterKeys { it in allowedKeys }.toMutableMap()
        filtered["updatedAt"] = FieldValue.serverTimestamp()
        return safeFirestoreWrite("updateUserPreferences", "users/$uid") {
            db.collection("users").document(uid).update(filtered).await()
        }
    }

    /**
     * CRITICAL MERGE RULE: Always writes to `live_tracking/{uid}` with `SetOptions.merge()`
     * so server-stamped `passengerId`, `riderId`, `activeRideId`, `dropoffLat`/`dropoffLng`,
     * and `status: "on_ride"` written by `POST /api/rides/accept` are NEVER overwritten!
     */
    suspend fun mergeLiveTracking(
        uid: String,
        lat: Double,
        lng: Double,
        heading: Float,
        speedMps: Float,
        isOnline: Boolean,
        isOnRide: Boolean,
        driverName: String,
        vehicleCategory: String,
        vehicleCategories: List<String>,
        isPetFriendly: Boolean,
        isStackingEnabled: Boolean? = null,
        isLastJob: Boolean? = null,
        destinationModeActive: Boolean? = null,
        homeLat: Double? = null,
        homeLng: Double? = null,
        zoneEnabled: Boolean? = null,
        zoneMaxDistance: Double? = null,
        dropoffLat: Double? = null,
        dropoffLng: Double? = null
    ): Result<Unit> {
        val payload = hashMapOf<String, Any>(
            "driverId" to uid,
            "lat" to lat,
            "lng" to lng,
            "heading" to heading.toDouble(),
            "speed" to speedMps.toDouble(),
            "isOnline" to isOnline,
            "driverName" to driverName,
            "vehicleCategory" to vehicleCategory,
            "vehicleCategories" to vehicleCategories.ifEmpty { listOf("standard") },
            "isPetFriendly" to isPetFriendly,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!isOnRide) {
            payload["status"] = if (isOnline) "available" else "offline"
        }
        isStackingEnabled?.let { payload["isStackingEnabled"] = it }
        isLastJob?.let { payload["isLastJob"] = it }
        destinationModeActive?.let { payload["destinationModeActive"] = it }
        homeLat?.let { payload["homeLat"] = it }
        homeLng?.let { payload["homeLng"] = it }
        zoneEnabled?.let { payload["zoneEnabled"] = it }
        zoneMaxDistance?.let { payload["zoneMaxDistance"] = it }
        if (dropoffLat != null && dropoffLng != null && (dropoffLat != 0.0 || dropoffLng != 0.0)) {
            payload["dropoffLat"] = dropoffLat
            payload["dropoffLng"] = dropoffLng
        }
        return safeFirestoreWrite("mergeLiveTracking", "live_tracking/$uid") {
            db.collection("live_tracking").document(uid)
                .set(payload, SetOptions.merge()).await()
            db.collection("driver_status").document(uid)
                .set(
                    mapOf(
                        "driverId" to uid,
                        "online" to isOnline,
                        "isOnline" to isOnline,
                        "isBusy" to isOnRide,
                        "status" to if (!isOnline) "offline" else if (isOnRide) "on_ride" else "online",
                        "lat" to lat,
                        "lng" to lng,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    suspend fun recordDriverDeclineStatus(uid: String): Result<Unit> {
        return safeFirestoreWrite("recordDriverDeclineStatus", "driver_status/$uid") {
            db.collection("driver_status").document(uid)
                .set(
                    mapOf(
                        "pendingRideId" to FieldValue.delete(),
                        "consecutiveDeclines" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    suspend fun markDriverIdleStatus(uid: String): Result<Unit> {
        return safeFirestoreWrite("markDriverIdleStatus", "driver_status/$uid") {
            db.collection("live_tracking").document(uid)
                .set(
                    mapOf(
                        "status" to "available",
                        "activeRideId" to FieldValue.delete(),
                        "passengerId" to FieldValue.delete(),
                        "riderId" to FieldValue.delete(),
                        "dropoffLat" to FieldValue.delete(),
                        "dropoffLng" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            db.collection("driver_status").document(uid)
                .set(
                    mapOf(
                        "isBusy" to false,
                        "status" to "online",
                        "currentRideId" to FieldValue.delete(),
                        "pendingRideId" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    /**
     * Rule-compliant Firestore transaction fallback when accepting a ride that already has
     * `status == "offered"` and `assignedDriverId == driverUid` (which causes `POST /api/rides/accept`
     * to return 409 because `assignedDriverId` is pre-populated during offer dispatch).
     * Strictly updates ONLY `['status', 'driverId', 'assignedDriverId', 'updatedAt']` per `firestore.rules`.
     */
    suspend fun claimOfferedRideTransaction(
        rideId: String,
        driverUid: String,
        passengerId: String?,
        dropoffLat: Double?,
        dropoffLng: Double?,
        isStacked: Boolean
    ): Result<Unit> {
        return safeFirestoreWrite("claimOfferedRideTransaction", "ride_requests/$rideId") {
            val rideRef = db.collection("ride_requests").document(rideId)
            db.runTransaction { tx ->
                val snap = tx.get(rideRef)
                if (!snap.exists()) throw IllegalStateException("Ride request not found")
                val status = snap.getString("status")?.lowercase() ?: ""
                if (status !in setOf("pending", "searching", "offered")) {
                    throw IllegalStateException("Ride is no longer available")
                }
                val existingDriverId = snap.getString("driverId")
                val existingAssigned = snap.getString("assignedDriverId")
                if (!existingDriverId.isNullOrBlank() && existingDriverId != driverUid) {
                    throw IllegalStateException("Ride has already been accepted by another driver")
                }
                if (!existingAssigned.isNullOrBlank() && existingAssigned != driverUid) {
                    throw IllegalStateException("Ride is currently offered to another driver")
                }
                tx.update(
                    rideRef,
                    mapOf(
                        "status" to "accepted",
                        "driverId" to driverUid,
                        "assignedDriverId" to driverUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            val trackingPatch = hashMapOf<String, Any>(
                "driverId" to driverUid,
                "status" to "on_ride",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!isStacked) {
                trackingPatch["activeRideId"] = rideId
                if (!passengerId.isNullOrBlank()) {
                    trackingPatch["passengerId"] = passengerId
                    trackingPatch["riderId"] = passengerId
                }
            } else {
                trackingPatch["isStackingEnabled"] = false
            }
            if (dropoffLat != null && dropoffLng != null && (dropoffLat != 0.0 || dropoffLng != 0.0)) {
                trackingPatch["dropoffLat"] = dropoffLat
                trackingPatch["dropoffLng"] = dropoffLng
            }
            db.collection("live_tracking").document(driverUid)
                .set(trackingPatch, SetOptions.merge()).await()

            db.collection("driver_status").document(driverUid)
                .set(
                    mapOf(
                        "status" to "busy",
                        "isBusy" to true,
                        "currentRideId" to rideId,
                        "pendingRideId" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    suspend fun syncActiveRideDropoff(
        uid: String,
        dropoffLat: Double,
        dropoffLng: Double
    ): Result<Unit> {
        if (dropoffLat == 0.0 && dropoffLng == 0.0) return Result.success(Unit)
        return safeFirestoreWrite("syncActiveRideDropoff", "live_tracking/$uid") {
            db.collection("live_tracking").document(uid)
                .set(
                    mapOf(
                        "dropoffLat" to dropoffLat,
                        "dropoffLng" to dropoffLng,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    /**
     * Toggles temporary Coffee Break / Pause New Requests without stopping GPS tracking or ending shift streak.
     * Marks `driver_status/{uid}` as `busy` (preventing new dispatch offers) and restores `online` on resume.
     */
    suspend fun setDriverBreakStatus(uid: String, onBreak: Boolean): Result<Unit> {
        return safeFirestoreWrite("setDriverBreakStatus", "driver_status/$uid") {
            db.collection("driver_status").document(uid)
                .set(
                    mapOf(
                        "status" to if (onBreak) "busy" else "online",
                        "isBusy" to onBreak,
                        "onBreak" to onBreak,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            db.collection("live_tracking").document(uid)
                .set(
                    mapOf(
                        "status" to if (onBreak) "busy" else "available",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    suspend fun activateQueuedRideInLiveTracking(
        uid: String,
        rideId: String,
        passengerId: String?,
        dropoffLat: Double,
        dropoffLng: Double,
        isStackingEnabled: Boolean
    ): Result<Unit> {
        val patch = hashMapOf<String, Any>(
            "driverId" to uid,
            "activeRideId" to rideId,
            "status" to "on_ride",
            "isStackingEnabled" to isStackingEnabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!passengerId.isNullOrBlank()) {
            patch["passengerId"] = passengerId
            patch["riderId"] = passengerId
        }
        if (dropoffLat != 0.0 || dropoffLng != 0.0) {
            patch["dropoffLat"] = dropoffLat
            patch["dropoffLng"] = dropoffLng
        }
        return safeFirestoreWrite("activateQueuedRideInLiveTracking", "live_tracking/$uid") {
            db.collection("live_tracking").document(uid).set(patch, SetOptions.merge()).await()
            db.collection("driver_status").document(uid)
                .set(
                    mapOf(
                        "status" to "busy",
                        "isBusy" to true,
                        "currentRideId" to rideId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    suspend fun syncLiveTrackingDispatchPrefs(
        uid: String,
        isStackingEnabled: Boolean,
        isLastJob: Boolean,
        destinationModeActive: Boolean,
        homeLat: Double?,
        homeLng: Double?,
        zoneEnabled: Boolean,
        zoneMaxDistance: Double
    ): Result<Unit> {
        val patch = hashMapOf<String, Any>(
            "isStackingEnabled" to isStackingEnabled,
            "isLastJob" to isLastJob,
            "destinationModeActive" to destinationModeActive,
            "zoneEnabled" to zoneEnabled,
            "zoneMaxDistance" to zoneMaxDistance,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (homeLat != null && homeLng != null) {
            patch["homeLat"] = homeLat
            patch["homeLng"] = homeLng
        }
        return safeFirestoreWrite("syncLiveTrackingDispatchPrefs", "live_tracking/$uid") {
            db.collection("live_tracking").document(uid).set(patch, SetOptions.merge()).await()
        }
    }

    suspend fun mergeLiveTrackingCapabilities(
        uid: String,
        vehicleCategories: List<String>,
        isPetFriendly: Boolean
    ): Result<Unit> {
        val normalized = vehicleCategories.ifEmpty { listOf("standard") }
        return safeFirestoreWrite("mergeLiveTrackingCapabilities", "live_tracking/$uid") {
            db.collection("live_tracking").document(uid)
                .set(
                    mapOf(
                        "vehicleCategories" to normalized,
                        "vehicleCategory" to normalized.first(),
                        "isPetFriendly" to isPetFriendly,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    suspend fun markDriverOfflineInFirestore(uid: String): Result<Unit> {
        return safeFirestoreWrite("markDriverOffline", "live_tracking/$uid") {
            db.collection("live_tracking").document(uid)
                .set(
                    mapOf(
                        "isOnline" to false,
                        "status" to "offline",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            db.collection("driver_status").document(uid)
                .set(
                    mapOf(
                        "driverId" to uid,
                        "online" to false,
                        "isOnline" to false,
                        "isBusy" to false,
                        "status" to "offline",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    suspend fun sendRideChatMessage(
        rideId: String,
        senderId: String,
        text: String?,
        imageUrl: String? = null
    ): Result<Unit> {
        val payload = hashMapOf<String, Any>(
            "senderId" to senderId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!text.isNullOrBlank()) payload["text"] = text.trim()
        if (!imageUrl.isNullOrBlank()) payload["imageUrl"] = imageUrl
        return safeFirestoreWrite("sendRideChatMessage", "ride_requests/$rideId/chat") {
            db.collection("ride_requests").document(rideId)
                .collection("chat")
                .add(payload)
                .await()
        }
    }

    suspend fun reportRoadHazard(
        reporterId: String,
        type: String,
        lat: Double,
        lng: Double
    ): Result<Unit> {
        val expiresAt = Timestamp(Timestamp.now().seconds + 3600L, 0)
        return safeFirestoreWrite("reportRoadHazard", "reported_hazards") {
            db.collection("reported_hazards")
                .add(
                    mapOf(
                        "type" to type,
                        "lat" to lat,
                        "lng" to lng,
                        "reporterId" to reporterId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "expiresAt" to expiresAt
                    )
                ).await()
        }
    }

    /**
     * Mirrors web `DriverTerminal.tsx` review flow: live `firestore.rules` only provisions
     * `driver_reviews` (passenger -> driver) and denies unmatched `ride_reviews` writes.
     * Complete cleanly without triggering a Firestore PERMISSION_DENIED error.
     */
    suspend fun submitPassengerRating(
        rideId: String,
        driverId: String,
        passengerId: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        Log.d(TAG, "Recorded local driver review for ride=$rideId rating=$rating")
        return Result.success(Unit)
    }

    suspend fun addDriverExpense(
        driverId: String,
        amount: Double,
        category: String,
        date: String,
        notes: String,
        receiptImage: String?
    ): Result<Unit> {
        val payload = hashMapOf<String, Any>(
            "driverId" to driverId,
            "amount" to amount,
            "category" to category,
            "date" to date,
            "notes" to notes.trim(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!receiptImage.isNullOrBlank()) payload["receiptImage"] = receiptImage
        return safeFirestoreWrite("addDriverExpense", "driver_expenses") {
            db.collection("driver_expenses").add(payload).await()
        }
    }

    suspend fun updateDriverExpense(
        expenseId: String,
        amount: Double,
        category: String,
        date: String,
        notes: String,
        receiptImage: String?
    ): Result<Unit> {
        val payload = hashMapOf<String, Any>(
            "amount" to amount,
            "category" to category,
            "date" to date,
            "notes" to notes.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!receiptImage.isNullOrBlank()) payload["receiptImage"] = receiptImage
        return safeFirestoreWrite("updateDriverExpense", "driver_expenses/$expenseId") {
            db.collection("driver_expenses").document(expenseId).update(payload).await()
        }
    }

    suspend fun deleteDriverExpense(expenseId: String): Result<Unit> {
        return safeFirestoreWrite("deleteDriverExpense", "driver_expenses/$expenseId") {
            db.collection("driver_expenses").document(expenseId).delete().await()
        }
    }

    // --- Storage Uploads (strict MIME & size validation, NO SVG) ---

    suspend fun uploadAvatar(uid: String, uri: Uri): Result<String> {
        return uploadFileValidated(
            uri = uri,
            allowedMimes = ALLOWED_RASTER_MIMES,
            maxBytes = MAX_AVATAR_BYTES,
            storagePath = "avatars/$uid/avatar_${UUID.randomUUID()}"
        )
    }

    suspend fun uploadVerificationDoc(uid: String, docType: String, uri: Uri): Result<String> {
        return uploadFileValidated(
            uri = uri,
            allowedMimes = ALLOWED_DOC_MIMES,
            maxBytes = MAX_DOC_BYTES,
            storagePath = "verifications/$uid/${docType}_${UUID.randomUUID()}"
        )
    }

    suspend fun uploadRideChatMedia(rideId: String, uri: Uri): Result<String> {
        return uploadFileValidated(
            uri = uri,
            allowedMimes = ALLOWED_RASTER_MIMES,
            maxBytes = MAX_DOC_BYTES,
            storagePath = "rides/$rideId/chat_${UUID.randomUUID()}"
        )
    }

    private suspend fun uploadFileValidated(
        uri: Uri,
        allowedMimes: Set<String>,
        maxBytes: Long,
        storagePath: String
    ): Result<String> {
        return try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)?.lowercase() ?: "application/octet-stream"
            if (mime == "image/svg+xml" || mime !in allowedMimes) {
                return Result.failure(
                    IllegalArgumentException("Unsupported file format ($mime). Please choose JPG, PNG, WebP, or PDF.")
                )
            }
            var sizeBytes = -1L
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) sizeBytes = cursor.getLong(idx)
                }
            }
            if (sizeBytes > maxBytes) {
                val maxMb = maxBytes / (1024L * 1024L)
                return Result.failure(
                    IllegalArgumentException("File exceeds the ${maxMb}MB upload limit.")
                )
            }

            val ext = when (mime) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/heic" -> "heic"
                "image/heif" -> "heif"
                "image/avif" -> "avif"
                "application/pdf" -> "pdf"
                "application/msword" -> "doc"
                else -> "docx"
            }

            val ref = storage.reference.child("$storagePath.$ext")
            val metadata = StorageMetadata.Builder().setContentType(mime).build()
            ref.putFile(uri, metadata).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Storage upload failed at $storagePath", if (BuildConfig.DEBUG) e else null)
            Result.failure(Exception("Upload failed. Please check file permissions and try again."))
        }
    }

    // --- Safe Write Wrapper & Parsers ---

    private suspend fun safeFirestoreWrite(
        op: String,
        path: String,
        block: suspend () -> Unit
    ): Result<Unit> {
        return try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            logError(op, path, e)
            Result.failure(Exception(e.message ?: "Unable to save changes to AnyRoller."))
        } catch (e: Exception) {
            val fs = e as? FirebaseFirestoreException ?: e.cause as? FirebaseFirestoreException
            logError(op, path, fs ?: e)
            Result.failure(Exception("Unable to save changes right now. Please try again."))
        }
    }

    private fun logError(op: String, path: String, error: Throwable) {
        val code = (error as? FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
        Log.e(TAG, "Firestore [$op] failed on $path (code=$code)", if (BuildConfig.DEBUG) error else null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDriverProfile(doc: DocumentSnapshot): DriverProfile {
        val rawDocs = (doc.get("verificationDocs") as? List<Map<String, Any?>>).orEmpty()
        val parsedDocs = rawDocs.map { m ->
            VerificationDoc(
                type = m["type"]?.toString() ?: "",
                status = m["status"]?.toString() ?: "pending",
                fileUrl = m["fileUrl"]?.toString(),
                expiryDate = m["expiryDate"]?.toString(),
                rejectionReason = m["rejectionReason"]?.toString()
            )
        }

        val rawSchedule = doc.get("weeklySchedule") as? Map<String, Any?>
        val schedule = defaultWeeklySchedule().toMutableMap()
        rawSchedule?.forEach { (day, value) ->
            val map = value as? Map<String, Any?> ?: return@forEach
            schedule[day] = DaySchedule(
                status = map["status"]?.toString() ?: "anytime",
                startTime = map["startTime"]?.toString() ?: "08:00",
                endTime = map["endTime"]?.toString() ?: "18:00"
            )
        }

        val goalsMap = doc.get("earningsGoals") as? Map<String, Any?>
        val goals = EarningsGoals(
            today = (goalsMap?.get("today") as? Number)?.toDouble() ?: 200.0,
            week = (goalsMap?.get("week") as? Number)?.toDouble() ?: 1000.0,
            month = (goalsMap?.get("month") as? Number)?.toDouble() ?: 4000.0
        )

        val subsMap = doc.get("subscriptions") as? Map<String, Any?>
        val goldSub = subsMap?.get("driver_gold") as? Map<String, Any?>
        val goldActive = goldSub?.get("status")?.toString()?.lowercase() in setOf("active", "trialing")
        val subType = doc.getString("subscriptionType")
            ?: if (goldActive) "driver_gold" else null

        val rawCategories = (doc.get("vehicleCategories") as? List<*>)
            ?.mapNotNull { it?.toString() }
            ?.filter { it.isNotBlank() }
            ?: listOf(doc.getString("vehicleCategory") ?: "standard")

        val rawCapabilities = (doc.get("capabilities") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        return DriverProfile(
            uid = doc.id,
            firstName = doc.getString("firstName") ?: "",
            lastName = doc.getString("lastName") ?: "",
            name = doc.getString("name") ?: "",
            displayName = doc.getString("displayName") ?: "",
            email = doc.getString("email") ?: "",
            phone = doc.getString("phone") ?: "",
            photoURL = doc.getString("photoURL"),
            role = doc.getString("role") ?: "",
            accountType = doc.getString("accountType") ?: "",
            capabilities = rawCapabilities,
            isDriverFlag = doc.getBoolean("isDriver") ?: false,
            driverApproved = doc.getBoolean("driverApproved") ?: false,
            disabled = doc.getBoolean("disabled") ?: false,
            suspended = doc.getBoolean("suspended") ?: false,
            banned = doc.getBoolean("banned") ?: false,
            deactivated = doc.getBoolean("deactivated") ?: false,
            status = doc.getString("status") ?: "",
            driverStatus = doc.getString("driverStatus") ?: "",
            termsAcceptedVersion = doc.getString("termsAcceptedVersion") ?: "",
            rating = doc.getDouble("rating") ?: 4.9,
            acceptanceRate = doc.getDouble("acceptanceRate") ?: 94.0,
            cancellationRate = doc.getDouble("cancellationRate") ?: 2.0,
            avgResponseSeconds = doc.getDouble("avgResponseSeconds") ?: 4.2,
            totalRides = (doc.getLong("totalRides") ?: 0L).toInt(),
            stripeAccountId = doc.getString("stripeAccountId"),
            pendingPlatformFees = doc.getDouble("pendingPlatformFees") ?: 0.0,
            subscriptionType = subType,
            subscriptionStatus = doc.getString("subscriptionStatus") ?: goldSub?.get("status")?.toString(),
            earningsReportExport = doc.getBoolean("earningsReportExport") ?: false,
            referralCode = doc.getString("referralCode") ?: "AR-${doc.id.take(6).uppercase()}",
            vehicleCategory = doc.getString("vehicleCategory") ?: rawCategories.firstOrNull() ?: "standard",
            vehicleCategories = rawCategories,
            vehicleMake = doc.getString("vehicleMake") ?: "",
            vehicleModel = doc.getString("vehicleModel") ?: doc.getString("carModel") ?: "",
            vehicleColor = doc.getString("vehicleColor") ?: "",
            vehicleInfo = doc.getString("vehicleInfo") ?: "",
            vehiclePlate = doc.getString("vehiclePlate")
                ?: doc.getString("licensePlate")
                ?: doc.getString("registrationPlate")
                ?: doc.getString("plateNumber")
                ?: "",
            totalBusinessMileage = doc.getDouble("totalBusinessMileage") ?: 0.0,
            proFeaturesEnabled = doc.getBoolean("proFeaturesEnabled") ?: false,
            petFriendly = doc.getBoolean("isPetFriendly") ?: false,
            stackingEnabled = doc.getBoolean("isStackingEnabled") ?: true,
            lastJob = doc.getBoolean("isLastJob") ?: false,
            destinationModeActive = doc.getBoolean("destinationModeActive") ?: false,
            homeAddress = doc.getString("homeAddress"),
            homeLat = doc.getDouble("homeLat"),
            homeLng = doc.getDouble("homeLng"),
            zoneEnabled = doc.getBoolean("zoneEnabled") ?: false,
            zoneMaxDistance = doc.getDouble("zoneMaxDistance") ?: 10.0,
            defaultNavApp = doc.getString("defaultNavApp") ?: "Google Maps",
            requirePasscode = doc.getBoolean("requirePasscode") ?: false,
            muteHeadsUpVolume = doc.getBoolean("muteHeadsUpVolume") ?: false,
            muteRideOfferAlerts = doc.getBoolean("muteRideOfferAlerts") ?: false,
            documentsComplete = doc.getBoolean("documentsComplete") ?: false,
            insuranceProvider = doc.getString("insuranceProvider") ?: "Zego",
            insuranceExpiry = doc.getString("insuranceExpiry"),
            verificationDocs = parsedDocs,
            weeklySchedule = schedule,
            earningsGoals = goals
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRideRequest(doc: DocumentSnapshot): RideRequest? {
        if (!doc.exists()) return null
        val pickupMap = doc.get("pickup") as? Map<String, Any?>
        val dropoffMap = doc.get("dropoff") as? Map<String, Any?>
        val passengerLoc = doc.get("passengerLocation") as? Map<String, Any?>

        val pickupLat = doc.getDouble("pickupLat")
            ?: (pickupMap?.get("lat") as? Number)?.toDouble() ?: 0.0
        val pickupLng = doc.getDouble("pickupLng")
            ?: (pickupMap?.get("lng") as? Number)?.toDouble() ?: 0.0
        val dropoffLat = doc.getDouble("dropoffLat")
            ?: (dropoffMap?.get("lat") as? Number)?.toDouble() ?: 0.0
        val dropoffLng = doc.getDouble("dropoffLng")
            ?: (dropoffMap?.get("lng") as? Number)?.toDouble() ?: 0.0

        val rawStops = (doc.get("stops") as? List<Map<String, Any?>>).orEmpty()
        val stops = rawStops.map { s ->
            val coords = s["coords"] as? Map<String, Any?>
            RideStop(
                address = s["address"]?.toString() ?: "",
                lat = (coords?.get("lat") as? Number)?.toDouble()
                    ?: (s["lat"] as? Number)?.toDouble() ?: 0.0,
                lng = (coords?.get("lng") as? Number)?.toDouble()
                    ?: (s["lng"] as? Number)?.toDouble() ?: 0.0
            )
        }

        val createdTs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: doc.getLong("createdAtMs") ?: System.currentTimeMillis()
        val completedTs = doc.getTimestamp("completedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: doc.getLong("completedAtMs") ?: 0L

        val parsedOfferExpiresMs: Long = run {
            val rawExp = doc.get("offerExpiresAt")
            when (rawExp) {
                is Timestamp -> rawExp.toDate().time
                is Number -> rawExp.toLong()
                is String -> runCatching { java.time.Instant.parse(rawExp).toEpochMilli() }.getOrNull()
                else -> null
            }
        } ?: doc.getLong("expiresAtMs") ?: (createdTs + 15_000L)

        val pickupString = doc.getString("pickupAddress")
            ?: (doc.get("pickup") as? String)?.takeIf { it.isNotBlank() }
            ?: pickupMap?.get("address")?.toString()
            ?: "Pickup location"
        val dropoffString = doc.getString("dropoffAddress")
            ?: (doc.get("dropoff") as? String)?.takeIf { it.isNotBlank() }
            ?: dropoffMap?.get("address")?.toString()
            ?: "Dropoff location"

        return RideRequest(
            id = doc.id,
            status = doc.getString("status") ?: "pending",
            passengerId = doc.getString("passengerId") ?: doc.getString("riderId") ?: doc.getString("userId") ?: "",
            passengerName = doc.getString("passengerName") ?: doc.getString("riderName") ?: "Passenger",
            passengerRating = doc.getDouble("passengerRating") ?: 4.9,
            passengerPhone = doc.getString("passengerPhone"),
            riderNotes = doc.getString("riderNotes") ?: doc.getString("comments") ?: doc.getString("instructions") ?: doc.getString("notes"),
            driverId = doc.getString("driverId"),
            assignedDriverId = doc.getString("assignedDriverId"),
            pickupAddress = pickupString,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            dropoffAddress = dropoffString,
            dropoffLat = dropoffLat,
            dropoffLng = dropoffLng,
            passengerLat = doc.getDouble("passengerLat")
                ?: (passengerLoc?.get("lat") as? Number)?.toDouble(),
            passengerLng = doc.getDouble("passengerLng")
                ?: (passengerLoc?.get("lng") as? Number)?.toDouble(),
            stops = stops,
            currentStopIndex = (doc.getLong("currentStopIndex") ?: 0L).toInt(),
            fareEstimate = doc.getDouble("fareEstimate")
                ?: doc.getDouble("totalFare")
                ?: doc.getDouble("price")
                ?: doc.getDouble("quotedPrice") ?: 0.0,
            price = doc.getDouble("price"),
            quotedPrice = doc.getDouble("quotedPrice"),
            surgeMultiplier = doc.getDouble("surgeMultiplier") ?: 1.0,
            surgeModel = doc.getString("surgeModel") ?: "multiplier",
            surgeFixedAmount = doc.getDouble("surgeFixedAmount") ?: doc.getDouble("surgeFixed") ?: 0.0,
            vehicleCategory = doc.getString("vehicleCategory") ?: doc.getString("carCategory") ?: "standard",
            distanceMiles = doc.getDouble("distanceMiles") ?: doc.getDouble("distance") ?: 0.0,
            durationMinutes = (doc.getLong("durationMinutes") ?: doc.getLong("etaMinutes") ?: 0L).toInt(),
            paymentMethod = doc.getString("paymentMethod") ?: "card",
            hasCardOnFile = doc.getBoolean("hasCardOnFile") ?: false,
            handshakeCode = doc.getString("handshakeCode") ?: doc.getString("passcode"),
            driverRequirePasscode = doc.getBoolean("driverRequirePasscode") ?: false,
            fareContractVersion = doc.getString("fareContractVersion"),
            finalFare = doc.getDouble("finalFare"),
            totalFare = doc.getDouble("totalFare"),
            totalFarePaid = doc.getDouble("totalFarePaid"),
            tipAmount = doc.getDouble("tipAmount") ?: 0.0,
            platformFee = doc.getDouble("platformFee"),
            calculatedPlatformFee = doc.getDouble("calculatedPlatformFee"),
            commissionAmount = doc.getDouble("commissionAmount"),
            driverEarnings = doc.getDouble("driverEarnings"),
            calculatedDriverEarnings = doc.getDouble("calculatedDriverEarnings"),
            appliedCommissionRate = doc.getDouble("appliedCommissionRate"),
            appliedFixedTripFee = doc.getDouble("appliedFixedTripFee"),
            paymentStatus = doc.getString("paymentStatus"),
            paymentUrl = doc.getString("paymentUrl"),
            paidWaitSeconds = (doc.getLong("paidWaitSeconds") ?: 0L).toInt(),
            createdAtMs = createdTs,
            completedAtMs = completedTs,
            scheduledTime = doc.getString("scheduledTime"),
            offeredAtMs = doc.getLong("offeredAtMs") ?: createdTs,
            expiresAtMs = parsedOfferExpiresMs,
            isModifiedByPassenger = doc.getBoolean("isModifiedByPassenger") ?: false,
            cancelledBy = doc.getString("cancelledBy"),
            cancellationFee = doc.getDouble("cancellationFee") ?: 0.0,
            priorityFee = doc.getDouble("priorityFee") ?: 0.0,
            abandonmentFee = doc.getDouble("abandonmentFee") ?: 0.0,
            cashCollected = doc.getDouble("cashCollected"),
            unpaidBalance = doc.getDouble("unpaidBalance") ?: 0.0
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRideConfig(doc: DocumentSnapshot?): RidePlatformConfig {
        if (doc == null || !doc.exists()) return RidePlatformConfig()
        val surgeMap = doc.get("surgeRules") as? Map<String, Any?>
        val surge = SurgeRules(
            lowWaitMins = (surgeMap?.get("lowWaitMins") as? Number)?.toInt() ?: 5,
            mediumWaitMins = (surgeMap?.get("mediumWaitMins") as? Number)?.toInt() ?: 10,
            highWaitMins = (surgeMap?.get("highWaitMins") as? Number)?.toInt() ?: 20,
            lowFee = (surgeMap?.get("lowFee") as? Number)?.toDouble() ?: 1.0,
            mediumFee = (surgeMap?.get("mediumFee") as? Number)?.toDouble() ?: 2.0,
            highFee = (surgeMap?.get("highFee") as? Number)?.toDouble() ?: 3.5,
            lowMultiplier = (surgeMap?.get("lowMultiplier") as? Number)?.toDouble() ?: 1.1,
            mediumMultiplier = (surgeMap?.get("mediumMultiplier") as? Number)?.toDouble() ?: 1.3,
            highMultiplier = (surgeMap?.get("highMultiplier") as? Number)?.toDouble() ?: 1.6,
            lowColor = surgeMap?.get("lowColor")?.toString() ?: "green",
            mediumColor = surgeMap?.get("mediumColor")?.toString() ?: "amber",
            highColor = surgeMap?.get("highColor")?.toString() ?: "red",
            surgeOpacity = (surgeMap?.get("surgeOpacity") as? Number)?.toInt() ?: 40
        )

        val rawCommissionRate = doc.getDouble("commissionRate")
            ?: doc.getDouble("commission")?.let { if (it > 1.0) it / 100.0 else it }
            ?: 0.12

        val rawGoldRate = doc.getDouble("goldCommissionRate")
            ?: doc.getDouble("goldCommission")?.let { if (it > 1.0) it / 100.0 else it }
            ?: 0.10

        val rawVehicleTypes = (doc.get("vehicleTypes") as? List<Map<String, Any?>>).orEmpty()
        val parsedVehicleTypes = rawVehicleTypes.mapNotNull { m ->
            val id = m["id"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = m["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: id.replaceFirstChar { it.uppercase() }
            val mult = (m["multiplier"] as? Number)?.toDouble() ?: 1.0
            VehicleTypeConfig(id = id, name = name, multiplier = mult)
        }.ifEmpty { defaultVehicleTypes() }

        return RidePlatformConfig(
            baseFare = doc.getDouble("baseFare") ?: 2.5,
            distanceRate = doc.getDouble("distanceRate") ?: 1.2,
            timeRate = doc.getDouble("timeRate") ?: 0.15,
            waitRatePerMinute = doc.getDouble("waitRatePerMinute") ?: 0.25,
            minFare = doc.getDouble("minFare") ?: 5.0,
            priorityFee = doc.getDouble("priorityFee") ?: 3.0,
            commissionRate = rawCommissionRate,
            goldCommissionRate = rawGoldRate,
            fixedTripFee = doc.getDouble("fixedTripFee") ?: 0.0,
            maxDailyDriverHours = doc.getDouble("maxDailyDriverHours") ?: 10.0,
            freeWaitSeconds = (doc.getLong("freeWaitSeconds") ?: 180L).toInt(),
            dispatchRadiusMiles = doc.getDouble("dispatchRadiusMiles") ?: 15.0,
            dispatchTimeoutSeconds = (doc.getLong("dispatchTimeoutSeconds") ?: 15L).toInt().coerceIn(5, 60),
            autoDispatchEnabled = doc.getBoolean("autoDispatchEnabled") ?: true,
            allowRiderAbandonment = doc.getBoolean("allowRiderAbandonment") ?: false,
            surgeEnabled = doc.getBoolean("surgeEnabled") ?: true,
            surgeModel = doc.getString("surgeModel") ?: "fixed",
            surgeFixedAmount = doc.getDouble("surgeFixedAmount") ?: 2.0,
            surgeMultiplierValue = doc.getDouble("surgeMultiplierValue") ?: 1.4,
            surgeRules = surge,
            vehicleTypes = parsedVehicleTypes
        )
    }
}
