package com.example.samekanprivatetrekroom.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

class GpsManager(
    private val context: Context,
    private var updateIntervalSeconds: Int,
    private val onLocationUpdated: (Location) -> Unit
) {
    companion object {
        private const val TAG = "GpsManager"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    fun updateInterval(seconds: Int) {
        if (updateIntervalSeconds != seconds) {
            updateIntervalSeconds = seconds
            if (isTracking) {
                // Restart with new interval
                stopLocationUpdates()
                startLocationUpdates()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isTracking) return

        val intervalMillis = updateIntervalSeconds * 1000L
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis
        )
        .setMinUpdateIntervalMillis(intervalMillis / 2)
        .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    Log.d(TAG, "Location updated: Lat=${location.latitude}, Lng=${location.longitude}, Acc=${location.accuracy}")
                    onLocationUpdated(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, "Location updates started with interval: ${updateIntervalSeconds}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates", e)
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        isTracking = false
        Log.d(TAG, "Location updates stopped")
    }
}
