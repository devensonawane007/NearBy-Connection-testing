package com.example.samekanprivatetrekroom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.samekanprivatetrekroom.presentation.ui.MainAppScreen
import com.example.samekanprivatetrekroom.presentation.viewmodel.TrekRoomViewModel
import com.example.samekanprivatetrekroom.theme.SamekanPrivateTrekRoomTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: TrekRoomViewModel by viewModels()

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        Log.d(TAG, "Permissions callback: All granted = $allGranted")
        hasPermissionsState.value = checkAllPermissionsGranted()
    }

    private val hasPermissionsState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasPermissionsState.value = checkAllPermissionsGranted()

        setContent {
            SamekanPrivateTrekRoomTheme {
                val hasPermissions by remember { hasPermissionsState }
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
        hasPermissionsState.value = checkAllPermissionsGranted()
    }

    private fun checkAllPermissionsGranted(): Boolean {
        val required = getRequiredPermissions()
        val allGranted = required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Checking permissions: All granted = $allGranted")
        return allGranted
    }

    private fun requestRequiredPermissions() {
        val required = getRequiredPermissions()
        Log.d(TAG, "Requesting permissions: ${required.joinToString()}")
        permissionRequestLauncher.launch(required)
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        // Bluetooth Permissions Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        // Nearby Wi-Fi Devices Permission Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions.toTypedArray()
    }
}
