package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.NAMED_FIRESTORE_DB_ID
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage

object FirebaseProvider {
    @Volatile
    private var cachedFirestore: FirebaseFirestore? = null

    fun auth(): FirebaseAuth = Firebase.auth

    fun storage(): FirebaseStorage = Firebase.storage

    /**
     * Always connects to the live shared AnyTrader named Firestore database ID
     * (`ai-studio-anytrader-44dab8b3-bbc9-4352-b725-2cbe7a1dfd2a`), NEVER `(default)`.
     */
    fun firestore(context: Context): FirebaseFirestore {
        cachedFirestore?.let { return it }
        synchronized(this) {
            cachedFirestore?.let { return it }
            val configuredDbId = try {
                context.getString(R.string.firestore_database_id).takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            } ?: NAMED_FIRESTORE_DB_ID
            val instance = FirebaseFirestore.getInstance(Firebase.app, configuredDbId)
            cachedFirestore = instance
            return instance
        }
    }
}
