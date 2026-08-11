package com.example.samekanprivatetrekroom.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("samekan_trekroom_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_GPS_INTERVAL = "gps_interval_seconds"
    }

    init {
        // Automatically generate a device ID if not already set
        if (prefs.getString(KEY_DEVICE_ID, null) == null) {
            val randomHex = UUID.randomUUID().toString().substring(0, 4).uppercase()
            val deviceId = "SK-$randomHex"
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
    }

    fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, "SK-UNKNOWN") ?: "SK-UNKNOWN"
    }

    fun getDisplayName(): String {
        return prefs.getString(KEY_DISPLAY_NAME, "User-${getDeviceId().substringAfter("-")}") ?: "User"
    }

    fun setDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
    }

    fun getGpsIntervalSeconds(): Int {
        return prefs.getInt(KEY_GPS_INTERVAL, 10)
    }

    fun setGpsIntervalSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_GPS_INTERVAL, seconds).apply()
    }
}
