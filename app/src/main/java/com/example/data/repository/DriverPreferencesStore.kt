package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.driverPrefsDataStore by preferencesDataStore(name = "anyroller_driver_prefs")

class DriverPreferencesStore(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val CACHED_FCM_TOKEN = stringPreferencesKey("cached_fcm_token")
        val DECLINED_OFFER_IDS = stringSetPreferencesKey("declined_offer_ids")
        val LOCAL_PET_FRIENDLY = booleanPreferencesKey("local_pet_friendly")
        val LOCAL_VEHICLE_CATEGORIES = stringSetPreferencesKey("local_vehicle_categories")
        val ACTIVE_ONLINE_FLAG = booleanPreferencesKey("active_online_flag")
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
