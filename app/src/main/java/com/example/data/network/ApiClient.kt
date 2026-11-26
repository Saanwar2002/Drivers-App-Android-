package com.example.data.network

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(
        val httpCode: Int,
        val errorCode: String? = null,
        val message: String,
        val isAuthRevoked: Boolean = false,
        val isConflict: Boolean = false,
        val isRateLimited: Boolean = false,
        val isServiceUnavailable: Boolean = false
    ) : ApiResult<Nothing>()
}

object ApiClient {
    private const val TAG = "AnyRollerApi"
    private const val DEFAULT_BASE_URL = "https://www.anytrader.co.uk/"

    val resolvedBaseUrl: String
        get() {
            val raw = BuildConfig.API_BASE_URL.trim()
            val cleaned = if (raw.startsWith("https://") || raw.startsWith("http://")) {
                raw
            } else {
                DEFAULT_BASE_URL
            }
            return if (cleaned.endsWith("/")) cleaned else "$cleaned/"
        }

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val errorAdapter = moshi.adapter(ApiErrorBody::class.java)

    /**
     * Attaches fresh Firebase Auth Bearer token on every request.
     * NEVER sends synthetic browser `Origin` headers (Slice 8 CORS compliance).
     * Automatically performs one forced token refresh on HTTP 401 before returning unauthorized.
     */
    private val firebaseAuthInterceptor = Interceptor { chain ->
        val original = chain.request()
        val user = Firebase.auth.currentUser
        val token = if (user != null) {
            try {
                runBlocking { user.getIdToken(false).await()?.token }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to fetch cached Firebase ID token")
                null
            }
        } else null

        val authedBuilder = original.newBuilder()
            .header("Accept", "application/json")
            .removeHeader("Origin")

        if (!token.isNullOrBlank()) {
            authedBuilder.header("Authorization", "Bearer $token")
        }

        val firstResponse = chain.proceed(authedBuilder.build())
        if (firstResponse.code == 401 && user != null) {
            val refreshedToken = try {
                runBlocking { user.getIdToken(true).await()?.token }
            } catch (e: Exception) {
                null
            }
            if (!refreshedToken.isNullOrBlank() && refreshedToken != token) {
                firstResponse.close()
                val retryRequest = original.newBuilder()
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $refreshedToken")
                    .removeHeader("Origin")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            }
        }
        firstResponse
    }

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(firebaseAuthInterceptor)

        if (BuildConfig.DEBUG) {
            val logger = HttpLoggingInterceptor { msg ->
                // Never log Bearer tokens in debug logs
                if (!msg.startsWith("Authorization:", ignoreCase = true)) {
                    Log.d(TAG, msg)
                }
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logger)
        }
        builder.build()
    }

    val service: AnyRollerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(resolvedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnyRollerApiService::class.java)
    }

    suspend fun <T> safeCall(block: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(
                        httpCode = response.code(),
                        message = "Empty response from server."
                    )
                }
            } else {
                val rawError = try {
                    response.errorBody()?.string()
                } catch (_: Exception) {
                    null
                }
                val parsed = rawError?.let {
                    try {
                        errorAdapter.fromJson(it)
                    } catch (_: Exception) {
                        null
                    }
                }
                val code = response.code()
                val errCode = parsed?.code
                val serverMsg = parsed?.error ?: parsed?.message
                val friendlyMsg = when (code) {
                    400 -> serverMsg ?: "Please check your details and try again."
                    401 -> "Session expired or revoked. Please sign in again."
                    403 -> serverMsg ?: "Your driver account does not have permission for this action."
                    409 -> serverMsg ?: "This ride was already updated or claimed by another driver."
                    429 -> "Too many requests — please pause a moment before retrying."
                    503 -> serverMsg ?: "AnyRoller servers are temporarily unavailable. Please retry shortly."
                    else -> if (BuildConfig.DEBUG && !serverMsg.isNullOrBlank()) {
                        serverMsg
                    } else {
                        "Something went wrong communicating with AnyRoller. Please try again."
                    }
                }
                ApiResult.Error(
                    httpCode = code,
                    errorCode = errCode,
                    message = friendlyMsg,
                    isAuthRevoked = code == 401,
                    isConflict = code == 409,
                    isRateLimited = code == 429,
                    isServiceUnavailable = code == 503
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Network error: ${if (BuildConfig.DEBUG) e.message else "IO failure"}")
            ApiResult.Error(
                httpCode = 0,
                message = "No network connection. Check mobile signal and retry.",
                isServiceUnavailable = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected API failure", if (BuildConfig.DEBUG) e else null)
            ApiResult.Error(
                httpCode = -1,
                message = "Unable to complete request right now. Please retry."
            )
        }
    }
}
