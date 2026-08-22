package com.example.samekanprivatetrekroom.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.example.samekanprivatetrekroom.data.local.Logger
import com.example.samekanprivatetrekroom.data.local.PermissionManager
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class GpsStatus {
    object Idle : GpsStatus()
    object Active : GpsStatus()
    object PermissionMissing : GpsStatus()
    object Disabled : GpsStatus()
    object BatterySaverActive : GpsStatus()
    object AirplaneModeActive : GpsStatus()
}

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

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    private val _statusFlow = MutableStateFlow<GpsStatus>(GpsStatus.Idle)
    val statusFlow: StateFlow<GpsStatus> = _statusFlow.asStateFlow()

    fun updateInterval(seconds: Int) {
        if (updateIntervalSeconds != seconds) {
            updateIntervalSeconds = seconds
            Logger.info(TAG, "GPS update interval changed to ${seconds}s")
            if (isTracking) {
                stopLocationUpdates()
                startLocationUpdates()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isTracking) return

        val permissionManager = PermissionManager(context)
        if (!permissionManager.isPermissionGranted(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            Logger.warn(TAG, "Cannot start GPS updates. ACCESS_FINE_LOCATION missing.")
            _statusFlow.value = GpsStatus.PermissionMissing
            return
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Logger.warn(TAG, "GPS Provider is disabled.")
            _statusFlow.value = GpsStatus.Disabled
            return
        }

        var adaptedIntervalSeconds = updateIntervalSeconds
        val isAirplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        if (isAirplaneMode) {
            Logger.warn(TAG, "Airplane mode is active. GPS may be degraded.")
            _statusFlow.value = GpsStatus.AirplaneModeActive
        }

        if (powerManager.isPowerSaveMode) {
            // Adaptive GPS: Slow down GPS interval if battery is low / power saver is active
            adaptedIntervalSeconds = updateIntervalSeconds.coerceAtLeast(30)
            Logger.warn(TAG, "Battery Saver is active. Adapting GPS interval to ${adaptedIntervalSeconds}s.")
            _statusFlow.value = GpsStatus.BatterySaverActive
        }

        val intervalMillis = adaptedIntervalSeconds * 1000L
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis
        )
        .setMinUpdateIntervalMillis(intervalMillis / 2)
        .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!permissionManager.isPermissionGranted(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Logger.warn(TAG, "Location permission revoked dynamically during tracking.")
                    _statusFlow.value = GpsStatus.PermissionMissing
                    stopLocationUpdates()
                    return
                }

                for (location in locationResult.locations) {
                    Logger.debug(TAG, "GPS Update: Lat=${location.latitude}, Lng=${location.longitude}, Alt=${location.altitude}")
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
            if (_statusFlow.value !is GpsStatus.BatterySaverActive && _statusFlow.value !is GpsStatus.AirplaneModeActive) {
                _statusFlow.value = GpsStatus.Active
            }
            Logger.info(TAG, "Location updates successfully started.")
        } catch (e: SecurityException) {
            Logger.error(TAG, "SecurityException requesting location updates", e)
            _statusFlow.value = GpsStatus.PermissionMissing
        } catch (e: Exception) {
            Logger.error(TAG, "Exception requesting location updates", e)
            _statusFlow.value = GpsStatus.Disabled
        }
    }

    fun stopLocationUpdates() {
        if (!isTracking) return
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        isTracking = false
        _statusFlow.value = GpsStatus.Idle
        Logger.info(TAG, "Location updates stopped.")
    }
}
