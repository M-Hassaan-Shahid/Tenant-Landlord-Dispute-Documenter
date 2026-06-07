package com.example.tenant_landlorddisputedocumenter.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Thin wrapper around FusedLocationProvider so callers don't have to deal with Tasks or permissions
 * directly. Returns null when permission is missing or the device can't fix.
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        // getCurrentLocation can stall indefinitely when the device cannot obtain a fix,
        // so bound it and cancel the underlying request once we stop waiting.
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                runCatching {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                        .await()
                }.getOrNull()
            }
        } finally {
            cancellation.cancel()
        }
    }

    private const val LOCATION_TIMEOUT_MS = 8_000L
}
