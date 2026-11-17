package com.ivy.wallet.sms

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.ivy.data.model.Location
import com.ivy.legacy.sms.SmsLocationAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "SmsLocationProvider"

// The SMS broadcast must finish within about 10 seconds, so the fix gets less than that.
private const val FIX_TIMEOUT_MS = 6_000L
private const val MAX_CACHED_FIX_AGE_MS = 2 * 60_000L
private const val MAX_LAST_KNOWN_AGE_MS = 15 * 60_000L

class SmsLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Where the phone is now, or null when location is off, not allowed, or not found in time. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!SmsLocationAccess.hasForeground(context)) {
            Log.i(TAG, "No location permission, skipping location")
            return null
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(MAX_CACHED_FIX_AGE_MS)
                .setDurationMillis(FIX_TIMEOUT_MS)
                .build()
            val cancellation = CancellationTokenSource()
            val fix = withTimeoutOrNull(FIX_TIMEOUT_MS) {
                client.getCurrentLocation(request, cancellation.token).await(cancellation)
            } ?: client.lastLocation.await()?.takeIf {
                System.currentTimeMillis() - it.time <= MAX_LAST_KNOWN_AGE_MS
            }
            if (fix == null) Log.i(TAG, "No location fix available")
            fix?.let { Location(lat = it.latitude, lng = it.longitude) }
        } catch (e: Exception) {
            // SecurityException when the app is in the background without "Allow all the time".
            Log.w(TAG, "Couldn't get location: ${e.javaClass.simpleName}")
            null
        }
    }
}
