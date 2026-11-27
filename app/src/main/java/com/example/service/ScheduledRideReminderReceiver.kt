package com.example.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.RideRequest
import java.text.SimpleDateFormat
import java.util.Locale

class ScheduledRideReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val rideId = intent.getStringExtra(EXTRA_RIDE_ID) ?: return
        val pickup = intent.getStringExtra(EXTRA_PICKUP) ?: "Scheduled pickup"
        val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 15)
        val fare = intent.getDoubleExtra(EXTRA_FARE, 0.0)

        AnyRollerMessagingService.createNotificationChannels(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AnyRollerMessagingService.EXTRA_OFFER_RIDE_ID, rideId)
        }
        val pi = PendingIntent.getActivity(
            context,
            (rideId.hashCode() * 31) + minutesBefore,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val farePart = if (fare > 0.0) " (£%.2f)".format(fare) else ""
        val title = "Scheduled Job in $minutesBefore min$farePart"
        val body = "Pickup: $pickup — Make sure you are Online and ready to head out."

        val notif = NotificationCompat.Builder(context, AnyRollerMessagingService.CHANNEL_RIDE_OFFERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            mgr.notify((rideId.hashCode() and 0x7FFFFFFF) + minutesBefore, notif)
        }.onFailure {
            Log.w(TAG, "Unable to show scheduled ride reminder", it)
        }
    }

    companion object {
        private const val TAG = "ScheduledRideReminder"
        const val EXTRA_RIDE_ID = "extra_ride_id"
        const val EXTRA_PICKUP = "extra_pickup"
        const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"
        const val EXTRA_FARE = "extra_fare"
    }
}

object ScheduledRideReminderScheduler {

    private val scheduledOffsetsMinutes = listOf(45, 15)

    fun syncReminders(
        context: Context,
        scheduledRides: List<RideRequest>,
        enabled: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nowMs = System.currentTimeMillis()
        for (ride in scheduledRides) {
            val pickupMs = parseScheduledTimeMs(ride.scheduledTime) ?: continue
            for (offsetMin in scheduledOffsetsMinutes) {
                val triggerAtMs = pickupMs - (offsetMin * 60_000L)
                val reqCode = ((ride.id.hashCode() and 0x0FFFFFFF) * 31) + offsetMin
                val intent = Intent(context, ScheduledRideReminderReceiver::class.java).apply {
                    putExtra(ScheduledRideReminderReceiver.EXTRA_RIDE_ID, ride.id)
                    putExtra(ScheduledRideReminderReceiver.EXTRA_PICKUP, ride.pickupAddress)
                    putExtra(ScheduledRideReminderReceiver.EXTRA_MINUTES_BEFORE, offsetMin)
                    putExtra(ScheduledRideReminderReceiver.EXTRA_FARE, ride.fareEstimate)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (!enabled || triggerAtMs <= nowMs + 5_000L) {
                    alarmManager.cancel(pending)
                } else {
                    // Play-Policy compliant inexact allow-while-idle alarm (no SCHEDULE_EXACT_ALARM needed)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
                    }
                }
            }
        }
    }

    private fun parseScheduledTimeMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        trimmed.toLongOrNull()?.let { num ->
            return if (num > 1_000_000_000_000L) num else num * 1000L
        }
        runCatching { java.time.Instant.parse(trimmed).toEpochMilli() }.getOrNull()?.let { return it }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.UK).parse(trimmed)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
