package com.example.samekanprivatetrekroom.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.samekanprivatetrekroom.MainActivity
import com.example.samekanprivatetrekroom.data.local.Logger
import com.example.samekanprivatetrekroom.data.local.PermissionManager

class TrekForegroundService : Service() {
    companion object {
        private const val TAG = "TrekForegroundService"
        private const val CHANNEL_ID = "trek_service_channel"
        private const val NOTIFICATION_ID = 8801
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TrekForegroundService = this@TrekForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Logger.info(TAG, "TrekForegroundService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.info(TAG, "TrekForegroundService onStartCommand triggered.")

        val permissionManager = PermissionManager(this)
        if (!permissionManager.checkAllRequiredPermissionsGranted()) {
            Logger.warn(TAG, "Service started but permissions are missing. Aborting foreground execution.")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification("Trek background synchronization active")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Logger.info(TAG, "Service transitioned to Foreground successfully.")
        } catch (e: SecurityException) {
            Logger.error(TAG, "FGS transition failed due to SecurityException", e)
            stopSelf()
            return START_NOT_STICKY
        } catch (e: Exception) {
            Logger.error(TAG, "FGS transition failed due to general exception", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Logger.info(TAG, "Service bound.")
        return binder
    }

    fun updateNotificationText(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(text)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to update notification text", e)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Samekan Trek Room")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Trek Room Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.info(TAG, "TrekForegroundService destroyed.")
    }
}
