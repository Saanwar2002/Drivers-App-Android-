package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.IncomingOfferPush
import com.example.data.network.ApiClient
import com.example.data.network.RegisterTokenRequest
import com.example.data.repository.DriverPreferencesStore
import com.example.data.repository.FirebaseProvider
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnyRollerMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AnyRollerFCM"
        const val CHANNEL_RIDE_OFFERS = "ride_offers_urgent"
        const val CHANNEL_CHAT_UPDATES = "ride_chat_updates"
        const val EXTRA_OFFER_RIDE_ID = "extra_offer_ride_id"
        const val EXTRA_CHAT_RIDE_ID = "extra_chat_ride_id"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val offerChannel = NotificationChannel(
                CHANNEL_RIDE_OFFERS,
                "Urgent Ride Offers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Time-critical incoming passenger ride dispatch offers and queued ride alerts."
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 400L, 200L, 400L, 200L, 600L)
                setSound(ringtoneUri, audioAttrs)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val chatChannel = NotificationChannel(
                CHANNEL_CHAT_UPDATES,
                "Rider Chat & Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Messages from riders and trip updates."
                enableVibration(true)
            }

            mgr.createNotificationChannel(offerChannel)
            mgr.createNotificationChannel(chatChannel)
        }

        /**
         * Posts a high-priority 15s heads-up notification banner over Google Maps / Waze
         * (including when AnyRoller is shrunk into PiP). Deduplicated by `rideId.hashCode()`.
         */
        fun showUrgentOfferNotification(
            context: Context,
            rideId: String,
            fareEstimate: Double,
            pickupAddress: String,
            dropoffAddress: String,
            isStacked: Boolean = false,
            timeoutMs: Long = 15_000L
        ) {
            if (rideId.isBlank()) return
            createNotificationChannels(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OFFER_RIDE_ID, rideId)
            }
            val pi = PendingIntent.getActivity(
                context,
                rideId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val fareLabel = "£%.2f".format(fareEstimate)
            val title = if (isStacked) {
                "Stacked Next Ride • $fareLabel"
            } else {
                "New Ride Offer • $fareLabel"
            }
            val routeSummary = "${pickupAddress.ifBlank { "Nearby Pickup" }} → ${dropoffAddress.ifBlank { "Destination" }}"
            val expandedText = if (isStacked) {
                "$routeSummary\nQueued for after your current drop-off — tap to Accept or Decline."
            } else {
                "$routeSummary\nTap to Accept or Decline."
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_RIDE_OFFERS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(routeSummary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setTimeoutAfter(timeoutMs.coerceIn(5_000L, 60_000L))
                .setContentIntent(pi)
                .build()

            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { mgr.notify(rideId.hashCode(), notification) }
        }

        /**
         * Immediately dismisses an offer heads-up notification once accepted, declined, cancelled, or expired.
         */
        fun cancelOfferNotification(context: Context, rideId: String) {
            if (rideId.isBlank()) return
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            runCatching { mgr.cancel(rideId.hashCode()) }
        }

        /**
         * Posts a prominent heads-up alert over Google Maps / Waze when a passenger cancels
         * an already-accepted stacked (queued) ride so the driver is informed without losing
         * their active trip navigation.
         */
        fun showStackedCancelledNotification(
            context: Context,
            rideId: String,
            message: String
        ) {
            createNotificationChannels(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val notifId = ("stacked_cancel_$rideId").hashCode()
            val pi = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_RIDE_OFFERS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Queued Next Ride Cancelled")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(20_000L)
                .setContentIntent(pi)
                .build()

            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { mgr.notify(notifId, notification) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        val prefs = DriverPreferencesStore(applicationContext)
        scope.launch {
            prefs.saveFcmToken(token)
            if (FirebaseProvider.auth().currentUser != null) {
                val deviceId = prefs.getOrCreateDeviceId()
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
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        createNotificationChannels(applicationContext)
        val data = message.data
        val type = data["type"]?.lowercase() ?: ""
        val rideId = data["rideId"] ?: ""

        when (type) {
            "ride_offer", "new_ride_offer", "stacked_ride_offer" -> {
                val now = System.currentTimeMillis()
                val offeredAt = data["offeredAtMs"]?.toLongOrNull() ?: now
                val expiresAt = data["expiresAtMs"]?.toLongOrNull() ?: (now + 15_000L)
                val offer = IncomingOfferPush(
                    rideId = rideId,
                    pickupAddress = data["pickupAddress"] ?: "Nearby Pickup",
                    dropoffAddress = data["dropoffAddress"] ?: "Destination",
                    pickupLat = data["pickupLat"]?.toDoubleOrNull() ?: 0.0,
                    pickupLng = data["pickupLng"]?.toDoubleOrNull() ?: 0.0,
                    dropoffLat = data["dropoffLat"]?.toDoubleOrNull() ?: 0.0,
                    dropoffLng = data["dropoffLng"]?.toDoubleOrNull() ?: 0.0,
                    fareEstimate = data["fareEstimate"]?.toDoubleOrNull() ?: 0.0,
                    surgeMultiplier = data["surgeMultiplier"]?.toDoubleOrNull() ?: 1.0,
                    vehicleCategory = data["vehicleCategory"] ?: "standard",
                    distanceMiles = data["distanceMiles"]?.toDoubleOrNull() ?: 0.0,
                    offeredAtMs = offeredAt,
                    expiresAtMs = expiresAt
                )
                UrgentPushEventBus.emit(UrgentPushEvent.RideOffer(offer))
                // Show high-priority heads-up banner whenever app is backgrounded OR shrunk in PiP over Google Maps/Waze
                if (!UrgentPushEventBus.isFullScreenVisible.value) {
                    showUrgentOfferNotification(
                        context = applicationContext,
                        rideId = offer.rideId,
                        fareEstimate = offer.fareEstimate,
                        pickupAddress = offer.pickupAddress,
                        dropoffAddress = offer.dropoffAddress,
                        isStacked = (type == "stacked_ride_offer"),
                        timeoutMs = (offer.expiresAtMs - now).coerceIn(5_000L, 60_000L)
                    )
                }
            }

            "ride_cancelled", "ride_expired" -> {
                if (rideId.isNotBlank()) {
                    cancelOfferNotification(applicationContext, rideId)
                    UrgentPushEventBus.emit(
                        UrgentPushEvent.RideCancelled(rideId, data["reason"])
                    )
                }
                showStandardNotification(
                    title = message.notification?.title ?: "Ride Cancelled",
                    body = message.notification?.body ?: "The ride request was cancelled.",
                    rideId = rideId
                )
            }

            "ride_accepted" -> {
                if (rideId.isNotBlank()) {
                    cancelOfferNotification(applicationContext, rideId)
                    UrgentPushEventBus.emit(UrgentPushEvent.RideAcceptedElsewhere(rideId))
                }
            }

            else -> {
                val title = message.notification?.title ?: data["title"] ?: "AnyRoller Driver"
                val body = message.notification?.body ?: data["body"] ?: data["message"] ?: "New update"
                if (rideId.isNotBlank()) {
                    UrgentPushEventBus.emit(UrgentPushEvent.ChatMessageReceived(rideId, title, body))
                }
                showStandardNotification(title, body, rideId)
            }
        }
    }

    private fun showStandardNotification(title: String, body: String, rideId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (rideId.isNotBlank()) putExtra(EXTRA_CHAT_RIDE_ID, rideId)
        }
        val pi = PendingIntent.getActivity(
            this,
            (title + rideId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_CHAT_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
