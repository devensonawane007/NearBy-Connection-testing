package com.example.samekanprivatetrekroom

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.example.samekanprivatetrekroom.data.local.Logger
import com.example.samekanprivatetrekroom.data.local.PermissionManager
import com.example.samekanprivatetrekroom.data.service.TrekForegroundService
import com.example.samekanprivatetrekroom.presentation.ui.MainAppScreen
import com.example.samekanprivatetrekroom.presentation.viewmodel.TrekRoomViewModel
import com.example.samekanprivatetrekroom.theme.SamekanPrivateTrekRoomTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: TrekRoomViewModel by viewModels()
    private val permissionManager by lazy { PermissionManager(this) }

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        Logger.info(TAG, "Permissions requested callback completed. All granted = $allGranted")
        viewModel.updatePermissionStatus()
        if (allGranted) {
            startTrekServiceIfPermitted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.updatePermissionStatus()

        // Start service if permissions are already granted upfront
        if (permissionManager.checkAllRequiredPermissionsGranted()) {
            startTrekServiceIfPermitted()
        } else {
            Logger.warn(TAG, "Permissions are missing on startup. Deferring foreground service launch.")
        }

        setContent {
            SamekanPrivateTrekRoomTheme {
                val hasPermissions by viewModel.hasPermissions.collectAsState()
                MainAppScreen(
                    viewModel = viewModel,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = { requestRequiredPermissions() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updatePermissionStatus()
        viewModel.checkBluetoothStatus()

        val allGranted = permissionManager.checkAllRequiredPermissionsGranted()
        val serviceRunning = viewModel.isServiceRunning.value

        if (!allGranted && serviceRunning) {
            Logger.warn(TAG, "Permissions revoked. Stopping TrekForegroundService gracefully.")
            stopTrekService()
        } else if (allGranted && !serviceRunning && viewModel.currentRoom.value != null) {
            Logger.info(TAG, "Permissions restored. Restarting TrekForegroundService.")
            startTrekServiceIfPermitted()
        }
    }

    private fun startTrekServiceIfPermitted() {
        if (permissionManager.checkAllRequiredPermissionsGranted()) {
            Logger.info(TAG, "Starting TrekForegroundService.")
            try {
                val intent = Intent(this, TrekForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                viewModel.setServiceRunning(true)
            } catch (e: SecurityException) {
                Logger.error(TAG, "SecurityException starting service", e)
                viewModel.setServiceRunning(false)
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to start service", e)
                viewModel.setServiceRunning(false)
            }
        } else {
            viewModel.setServiceRunning(false)
        }
    }

    private fun stopTrekService() {
        try {
            val intent = Intent(this, TrekForegroundService::class.java)
            stopService(intent)
            viewModel.setServiceRunning(false)
            Logger.info(TAG, "TrekForegroundService stopped.")
        } catch (e: Exception) {
            Logger.error(TAG, "Error stopping service", e)
        }
    }

    private fun requestRequiredPermissions() {
        val required = permissionManager.getRequiredPermissions()
        Logger.info(TAG, "Triggering runtime permissions request launcher for: ${required.joinToString()}")
        permissionRequestLauncher.launch(required)
    }
}
