package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.driverPrefsDataStore by preferencesDataStore(name = "anyroller_driver_prefs")

data class CockpitLocalSettings(
    val autoLaunchExternalNav: Boolean = true, // ON by default (toggleable OFF in settings)
    val mapThemeMode: String = "auto", // "auto" | "dark" | "light"
    val keepScreenAwake: Boolean = true,
    val autoBrightnessBoost: Boolean = false,
    val pipEnabled: Boolean = true,
    val speedAlertMph: Int = 70, // 0 = Off, else 30/40/50/60/70
    val fatigueReminderHours: Int = 4, // 0 = Off, else 2/3/4/5
    val autoAcceptEnabled: Boolean = false,
    val autoAcceptMinFare: Double = 8.0,
    val autoAcceptMaxPickupMiles: Double = 3.0,
    val emergencyContactPhone: String = "",
    val scheduledRemindersEnabled: Boolean = true
)

class DriverPreferencesStore(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val CACHED_FCM_TOKEN = stringPreferencesKey("cached_fcm_token")
        val DECLINED_OFFER_IDS = stringSetPreferencesKey("declined_offer_ids")
        val LOCAL_PET_FRIENDLY = booleanPreferencesKey("local_pet_friendly")
        val LOCAL_VEHICLE_CATEGORIES = stringSetPreferencesKey("local_vehicle_categories")
        val ACTIVE_ONLINE_FLAG = booleanPreferencesKey("active_online_flag")

        // Part 2 Cockpit Local Settings
        val AUTO_LAUNCH_EXTERNAL_NAV = booleanPreferencesKey("cockpit_auto_launch_ext_nav")
        val MAP_THEME_MODE = stringPreferencesKey("cockpit_map_theme_mode")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("cockpit_keep_screen_awake")
        val AUTO_BRIGHTNESS_BOOST = booleanPreferencesKey("cockpit_auto_brightness_boost")
        val PIP_ENABLED = booleanPreferencesKey("cockpit_pip_enabled")
        val SPEED_ALERT_MPH = intPreferencesKey("cockpit_speed_alert_mph")
        val FATIGUE_REMINDER_HOURS = intPreferencesKey("cockpit_fatigue_reminder_hours")
        val AUTO_ACCEPT_ENABLED = booleanPreferencesKey("cockpit_auto_accept_enabled")
        val AUTO_ACCEPT_MIN_FARE = doublePreferencesKey("cockpit_auto_accept_min_fare")
        val AUTO_ACCEPT_MAX_PICKUP_MILES = doublePreferencesKey("cockpit_auto_accept_max_pickup_miles")
        val EMERGENCY_CONTACT_PHONE = stringPreferencesKey("cockpit_emergency_contact_phone")
        val SCHEDULED_REMINDERS_ENABLED = booleanPreferencesKey("cockpit_scheduled_reminders_enabled")
    }

    val cockpitSettingsFlow: Flow<CockpitLocalSettings> = context.driverPrefsDataStore.data.map { prefs ->
        CockpitLocalSettings(
            autoLaunchExternalNav = prefs[Keys.AUTO_LAUNCH_EXTERNAL_NAV] ?: true,
            mapThemeMode = prefs[Keys.MAP_THEME_MODE] ?: "auto",
            keepScreenAwake = prefs[Keys.KEEP_SCREEN_AWAKE] ?: true,
            autoBrightnessBoost = prefs[Keys.AUTO_BRIGHTNESS_BOOST] ?: false,
            pipEnabled = prefs[Keys.PIP_ENABLED] ?: true,
            speedAlertMph = prefs[Keys.SPEED_ALERT_MPH] ?: 70,
            fatigueReminderHours = prefs[Keys.FATIGUE_REMINDER_HOURS] ?: 4,
            autoAcceptEnabled = prefs[Keys.AUTO_ACCEPT_ENABLED] ?: false,
            autoAcceptMinFare = prefs[Keys.AUTO_ACCEPT_MIN_FARE] ?: 8.0,
            autoAcceptMaxPickupMiles = prefs[Keys.AUTO_ACCEPT_MAX_PICKUP_MILES] ?: 3.0,
            emergencyContactPhone = prefs[Keys.EMERGENCY_CONTACT_PHONE] ?: "",
            scheduledRemindersEnabled = prefs[Keys.SCHEDULED_REMINDERS_ENABLED] ?: true
        )
    }

    suspend fun updateCockpitSettings(transform: (CockpitLocalSettings) -> CockpitLocalSettings): CockpitLocalSettings {
        var updated = CockpitLocalSettings()
        context.driverPrefsDataStore.edit { prefs ->
            val current = CockpitLocalSettings(
                autoLaunchExternalNav = prefs[Keys.AUTO_LAUNCH_EXTERNAL_NAV] ?: true,
                mapThemeMode = prefs[Keys.MAP_THEME_MODE] ?: "auto",
                keepScreenAwake = prefs[Keys.KEEP_SCREEN_AWAKE] ?: true,
                autoBrightnessBoost = prefs[Keys.AUTO_BRIGHTNESS_BOOST] ?: false,
                pipEnabled = prefs[Keys.PIP_ENABLED] ?: true,
                speedAlertMph = prefs[Keys.SPEED_ALERT_MPH] ?: 70,
                fatigueReminderHours = prefs[Keys.FATIGUE_REMINDER_HOURS] ?: 4,
                autoAcceptEnabled = prefs[Keys.AUTO_ACCEPT_ENABLED] ?: false,
                autoAcceptMinFare = prefs[Keys.AUTO_ACCEPT_MIN_FARE] ?: 8.0,
                autoAcceptMaxPickupMiles = prefs[Keys.AUTO_ACCEPT_MAX_PICKUP_MILES] ?: 3.0,
                emergencyContactPhone = prefs[Keys.EMERGENCY_CONTACT_PHONE] ?: "",
                scheduledRemindersEnabled = prefs[Keys.SCHEDULED_REMINDERS_ENABLED] ?: true
            )
            val next = transform(current)
            updated = next
            prefs[Keys.AUTO_LAUNCH_EXTERNAL_NAV] = next.autoLaunchExternalNav
            prefs[Keys.MAP_THEME_MODE] = next.mapThemeMode
            prefs[Keys.KEEP_SCREEN_AWAKE] = next.keepScreenAwake
            prefs[Keys.AUTO_BRIGHTNESS_BOOST] = next.autoBrightnessBoost
            prefs[Keys.PIP_ENABLED] = next.pipEnabled
            prefs[Keys.SPEED_ALERT_MPH] = next.speedAlertMph
            prefs[Keys.FATIGUE_REMINDER_HOURS] = next.fatigueReminderHours
            prefs[Keys.AUTO_ACCEPT_ENABLED] = next.autoAcceptEnabled
            prefs[Keys.AUTO_ACCEPT_MIN_FARE] = next.autoAcceptMinFare
            prefs[Keys.AUTO_ACCEPT_MAX_PICKUP_MILES] = next.autoAcceptMaxPickupMiles
            prefs[Keys.EMERGENCY_CONTACT_PHONE] = next.emergencyContactPhone
            prefs[Keys.SCHEDULED_REMINDERS_ENABLED] = next.scheduledRemindersEnabled
        }
        return updated
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.driverPrefsDataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val created = "android-${UUID.randomUUID()}"
        context.driverPrefsDataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = created
        }
        return created
    }

    suspend fun saveFcmToken(token: String) {
        context.driverPrefsDataStore.edit { prefs ->
            prefs[Keys.CACHED_FCM_TOKEN] = token
        }
    }

    suspend fun getCachedFcmToken(): String? {
        return context.driverPrefsDataStore.data.first()[Keys.CACHED_FCM_TOKEN]
    }

    val declinedOfferIdsFlow: Flow<Set<String>> = context.driverPrefsDataStore.data.map { prefs ->
        prefs[Keys.DECLINED_OFFER_IDS] ?: emptySet()
    }

    suspend fun addDeclinedOfferId(rideId: String) {
        context.driverPrefsDataStore.edit { prefs ->
            val current = (prefs[Keys.DECLINED_OFFER_IDS] ?: emptySet()).toMutableSet()
            if (current.size > 50) {
                current.remove(current.first())
            }
            current.add(rideId)
            prefs[Keys.DECLINED_OFFER_IDS] = current
        }
    }

    val localPetFriendlyFlow: Flow<Boolean> = context.driverPrefsDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_PET_FRIENDLY] ?: false
    }

    suspend fun setLocalPetFriendly(enabled: Boolean) {
        context.driverPrefsDataStore.edit { prefs ->
            prefs[Keys.LOCAL_PET_FRIENDLY] = enabled
        }
    }

    val localVehicleCategoriesFlow: Flow<Set<String>> = context.driverPrefsDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_VEHICLE_CATEGORIES] ?: setOf("standard")
    }

    suspend fun setLocalVehicleCategories(categories: Set<String>) {
        val safe = categories.ifEmpty { setOf("standard") }
        context.driverPrefsDataStore.edit { prefs ->
            prefs[Keys.LOCAL_VEHICLE_CATEGORIES] = safe
        }
    }

    suspend fun setOnlineFlag(isOnline: Boolean) {
        context.driverPrefsDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_ONLINE_FLAG] = isOnline
        }
    }

    suspend fun isOnlineFlag(): Boolean {
        return context.driverPrefsDataStore.data.first()[Keys.ACTIVE_ONLINE_FLAG] ?: false
    }

    suspend fun clearSessionTransientPrefs() {
        context.driverPrefsDataStore.edit { prefs ->
            prefs.remove(Keys.DECLINED_OFFER_IDS)
            prefs[Keys.ACTIVE_ONLINE_FLAG] = false
        }
    }
}
