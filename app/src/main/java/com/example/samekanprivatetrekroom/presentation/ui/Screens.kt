

package com.example.samekanprivatetrekroom.presentation.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samekanprivatetrekroom.data.local.LocationEntity
import com.example.samekanprivatetrekroom.data.local.LocationHistoryEntity
import com.example.samekanprivatetrekroom.data.local.MessageEntity
import com.example.samekanprivatetrekroom.data.local.RoomEntity
import com.example.samekanprivatetrekroom.domain.model.Peer
import com.example.samekanprivatetrekroom.domain.model.RoomQrData
import com.example.samekanprivatetrekroom.location.QrCodeAnalyzer
import com.example.samekanprivatetrekroom.location.QrCodeGenerator
import com.example.samekanprivatetrekroom.location.GpsStatus
import com.example.samekanprivatetrekroom.presentation.viewmodel.TrekRoomViewModel
import com.example.samekanprivatetrekroom.theme.*
import com.google.gson.Gson
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputChange

@Composable
fun MainAppScreen(
    viewModel: TrekRoomViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        if (!hasPermissions) {
            PermissionGatewayScreen(onRequestPermissions)
        } else {
            if (currentRoom == null) {
                HomeScreen(viewModel)
            } else {
                TrekRoomDashboard(viewModel, currentRoom!!)
            }
        }
    }
}

@Composable
fun PermissionGatewayScreen(onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val permissionManager = remember { com.example.samekanprivatetrekroom.data.local.PermissionManager(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Brush.radialGradient(listOf(DarkGreenPrimary.copy(alpha = 0.2f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CompassCalibration,
                contentDescription = null,
                tint = DarkGreenPrimary,
                modifier = Modifier.size(48.dp)
            )
        }

        Text(
            text = "Permissions Required",
            color = TrekWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Samekan is a serverless application that operates 100% offline. Please grant permissions to enable offline synchronization:",
            color = MutedText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Bluetooth justification card
        PermissionRationaleRow(
            icon = Icons.Default.Bluetooth,
            title = "Bluetooth & Local WiFi",
            description = "Used to establish the serverless ad-hoc mesh network with nearby trekkers.",
            isGranted = permissionManager.isPermissionGranted(android.Manifest.permission.BLUETOOTH_CONNECT)
        )

        // GPS Location justification card
        PermissionRationaleRow(
            icon = Icons.Default.LocationOn,
            title = "GPS & Location",
            description = "Provides precise locations for compass radar tracking and trail mapping.",
            isGranted = permissionManager.isPermissionGranted(android.Manifest.permission.ACCESS_FINE_LOCATION)
        )

        // Camera justification card
        PermissionRationaleRow(
            icon = Icons.Default.CameraAlt,
            title = "Camera Access",
            description = "Required to scan the QR codes of active trek rooms to join instantly.",
            isGranted = permissionManager.isPermissionGranted(android.Manifest.permission.CAMERA)
        )

        // Microphone justification card
        PermissionRationaleRow(
            icon = Icons.Default.Mic,
            title = "Microphone Access",
            description = "Required only while using the Push-To-Talk walkie-talkie feature.",
            isGranted = permissionManager.isPermissionGranted(android.Manifest.permission.RECORD_AUDIO)
        )

        // Notifications justification card
        PermissionRationaleRow(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            description = "Keeps the offline room connection active in the background.",
            isGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionManager.isPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("GRANT PERMISSIONS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        TextButton(
            onClick = {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // ignore
                }
            }
        ) {
            Text("Open Application Settings", color = DarkGreenPrimary)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun PermissionRationaleRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Info, // custom default fallback
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCardBg)
            .border(1.dp, if (isGranted) DarkGreenPrimary.copy(alpha = 0.3f) else GlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) DarkGreenPrimary else MutedText,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(description, color = MutedText, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isGranted) DarkGreenPrimary else TrekError, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: TrekRoomViewModel) {
    val localDeviceId = viewModel.localDeviceId
    val localDisplayName by viewModel.localDisplayName.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scannerResult by remember { mutableStateOf("") }

    if (showScanner) {
        QrScannerScreen(
            onCodeScanned = { result ->
                scannerResult = result
                showScanner = false
                showJoinDialog = true
            },
            onClose = { showScanner = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = DarkGreenPrimary.copy(alpha = 0.05f),
                radius = size.width,
                center = Offset(0f, 0f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "SAMEKAN",
                color = DarkGreenPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
            Text(
                text = "Trek Room",
                color = TrekWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "OFFLINE COMMUNICATIONS",
                color = MutedText,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCardBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(DarkGreenSecondary, CircleShape)
                                .border(1.dp, DarkGreenPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = localDisplayName.take(1).uppercase(),
                                color = DarkGreenPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("TREKKER PROFILE", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(localDisplayName, color = TrekWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Device ID: $localDeviceId", color = MutedText, fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = { showEditProfile = true },
                        modifier = Modifier.background(DarkGreenSecondary, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile", tint = DarkGreenPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CREATE OFFLINE ROOM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    border = BorderStroke(1.5.dp, DarkGreenPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkGreenPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MANUAL JOIN", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { showScanner = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreenSecondary, contentColor = TrekWhite),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SCAN QR", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(DarkGreenPrimary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Operates 100% serverless over Local P2P",
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Dialogs
    if (showEditProfile) {
        var tempName by remember { mutableStateOf(localDisplayName) }
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            containerColor = DarkSurface,
            title = { Text("Edit Display Name", color = TrekWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Display Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TrekWhite,
                        unfocusedTextColor = TrekWhite,
                        focusedBorderColor = DarkGreenPrimary,
                        unfocusedBorderColor = DarkGreenSecondary,
                        focusedLabelColor = DarkGreenPrimary
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.trim().isNotEmpty()) {
                            viewModel.updateDisplayName(tempName.trim())
                            showEditProfile = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg)
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }, colors = ButtonDefaults.textButtonColors(contentColor = MutedText)) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showCreateDialog) {
        var roomName by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        val randomRoomId = remember { "T-ROOM-" + UUID.randomUUID().toString().substring(0, 4).uppercase() }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = DarkSurface,
            title = { Text("Create Trek Room", color = TrekWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Room ID: $randomRoomId", color = DarkGreenPrimary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it },
                        label = { Text("Room Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary,
                            focusedLabelColor = DarkGreenPrimary
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary,
                            focusedLabelColor = DarkGreenPrimary
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary,
                            focusedLabelColor = DarkGreenPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roomName.trim().isNotEmpty()) {
                            viewModel.createRoom(roomName.trim(), randomRoomId, description.trim(), password.trim())
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg)
                ) {
                    Text("CREATE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MutedText)) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showJoinDialog) {
        var inputRoomId by remember { mutableStateOf("") }
        var inputRoomName by remember { mutableStateOf("") }
        var inputPassword by remember { mutableStateOf("") }

        LaunchedEffect(scannerResult) {
            if (scannerResult.isNotEmpty()) {
                try {
                    val data = Gson().fromJson(scannerResult, RoomQrData::class.java)
                    inputRoomId = data.roomId
                    inputRoomName = data.roomName
                } catch (e: Exception) {
                    inputRoomId = scannerResult
                }
                scannerResult = ""
            }
        }

        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = DarkSurface,
            title = { Text("Join Trek Room", color = TrekWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputRoomId,
                        onValueChange = { inputRoomId = it },
                        label = { Text("Room ID (e.g. T-ROOM-A8F1)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary,
                            focusedLabelColor = DarkGreenPrimary
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = inputRoomName,
                        onValueChange = { inputRoomName = it },
                        label = { Text("Room Name (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary,
                            focusedLabelColor = DarkGreenPrimary
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        label = { Text("Password (If required)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary,
                            focusedLabelColor = DarkGreenPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputRoomId.trim().isNotEmpty()) {
                            val name = if (inputRoomName.trim().isEmpty()) "Trek Room ${inputRoomId.takeLast(4)}" else inputRoomName.trim()
                            viewModel.joinRoom(name, inputRoomId.trim().uppercase(), "", inputPassword.trim())
                            showJoinDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg)
                ) {
                    Text("JOIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MutedText)) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekRoomDashboard(viewModel: TrekRoomViewModel, room: RoomEntity) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()
    val activeSos by viewModel.activeSosAlert.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.leaveRoom()
    }

    if (pendingRequests.isNotEmpty()) {
        val request = pendingRequests.values.first()
        Dialog(onDismissRequest = { viewModel.rejectConnection(request.endpointId) }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkGreenPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = DarkGreenPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Link Authentication", color = TrekWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Trekker '${request.displayName}' is linking.", color = MutedText, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = request.authenticationDigits,
                        color = DarkGreenPrimary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.rejectConnection(request.endpointId) },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreenSecondary, contentColor = TrekError),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("REJECT")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { viewModel.acceptConnection(request.endpointId) },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ACCEPT")
                        }
                    }
                }
            }
        }
    }

    if (activeSos != null && !activeSos!!.acknowledged && activeSos!!.senderId != viewModel.localDeviceId) {
        val sos = activeSos!!
        val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()

        var flashState by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                flashState = !flashState
                delay(300)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (flashState) TrekError.copy(alpha = 0.9f) else DarkBg)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = TrekWhite,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "EMERGENCY SOS ALERT",
                    color = TrekWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${sos.senderName} needs help! Type: ${sos.emergencyType.uppercase()}",
                    color = TrekWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(30.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Coordinates: ${sos.latitude}, ${sos.longitude}", color = TrekWhite, fontSize = 14.sp)
                        Text("Altitude: ${sos.altitude.toInt()}m | Accuracy: ${sos.accuracy.toInt()}m", color = TrekWhite, fontSize = 14.sp)
                        Text("Battery level: ${sos.batteryLevel}%", color = TrekWhite, fontSize = 14.sp)

                        if (myLoc != null) {
                            val dist = calculateDistance(myLoc!!.latitude, myLoc!!.longitude, sos.latitude, sos.longitude)
                            val bearingVal = calculateBearing(myLoc!!.latitude, myLoc!!.longitude, sos.latitude, sos.longitude)
                            Text(
                                text = "Distance: ${dist.toInt()}m | Bearing: ${bearingVal.toInt()}°",
                                color = DarkGreenPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { viewModel.acknowledgeSos(sos.messageId, sos.senderId) },
                    colors = ButtonDefaults.buttonColors(containerColor = TrekWhite, contentColor = TrekError),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text("SEND ACKNOWLEDGEMENT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(room.roomName, color = TrekWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Room ID: ${room.roomId}", color = MutedText, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.leaveRoom() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, tint = TrekWhite)
                    }
                },
                actions = {
                    val context = LocalContext.current
                    var showQr by remember { mutableStateOf(false) }

                    val connectivityManager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
                    var isOnline by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        while (true) {
                            val activeNet = connectivityManager.activeNetwork
                            val caps = connectivityManager.getNetworkCapabilities(activeNet)
                            isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                            delay(4000)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            color = if (isOnline) TrekWarning else DarkGreenPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .border(1.dp, if (isOnline) TrekWarning else DarkGreenPrimary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showQr = true }) {
                            Icon(Icons.Default.QrCode, contentDescription = "Share Room", tint = DarkGreenPrimary)
                        }
                    }

                    if (showQr) {
                        val qrContent = Gson().toJson(RoomQrData(room.roomId, room.roomName))
                        val qrBitmap = remember { QrCodeGenerator.generateQrCode(qrContent) }
                        Dialog(onDismissRequest = { showQr = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, GlassBorder),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(room.roomName, color = TrekWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(240.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Scan to join Room ad-hoc", color = MutedText, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { showQr = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg)
                                    ) {
                                        Text("CLOSE")
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = DarkSurface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Radar, contentDescription = null) },
                    label = { Text("Radar") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkGreenPrimary,
                        unselectedIconColor = MutedText,
                        selectedTextColor = DarkGreenPrimary,
                        unselectedTextColor = MutedText,
                        indicatorColor = DarkGreenSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("Map") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkGreenPrimary,
                        unselectedIconColor = MutedText,
                        selectedTextColor = DarkGreenPrimary,
                        unselectedTextColor = MutedText,
                        indicatorColor = DarkGreenSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.Chat, contentDescription = null) },
                    label = { Text("Chat") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkGreenPrimary,
                        unselectedIconColor = MutedText,
                        selectedTextColor = DarkGreenPrimary,
                        unselectedTextColor = MutedText,
                        indicatorColor = DarkGreenSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("Group") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkGreenPrimary,
                        unselectedIconColor = MutedText,
                        selectedTextColor = DarkGreenPrimary,
                        unselectedTextColor = MutedText,
                        indicatorColor = DarkGreenSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("Diag") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkGreenPrimary,
                        unselectedIconColor = MutedText,
                        selectedTextColor = DarkGreenPrimary,
                        unselectedTextColor = MutedText,
                        indicatorColor = DarkGreenSecondary
                    )
                )
            }
        },
        containerColor = DarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Live Status Indicators Dashboard Bar
            LiveStatusDashboardRow(viewModel)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> RadarScreen(viewModel)
                    1 -> TrekTrailMapScreen(viewModel)
                    2 -> ChatScreen(viewModel)
                    3 -> MembersScreen(viewModel)
                    4 -> DebugScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun LiveStatusDashboardRow(viewModel: TrekRoomViewModel) {
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val gpsStatus by viewModel.gpsStatus.collectAsStateWithLifecycle()
    val isAdvertising by viewModel.isAdvertising.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val hasPermissions by viewModel.hasPermissions.collectAsStateWithLifecycle()

    val connectedCount = peers.filter { it.connected && it.deviceId != viewModel.localDeviceId }.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bluetooth indicator
        StatusIndicatorPill(label = "BT", isActive = isBtEnabled)

        // GPS status indicator
        val isGpsActive = gpsStatus is GpsStatus.Active || gpsStatus is GpsStatus.BatterySaverActive || gpsStatus is GpsStatus.AirplaneModeActive
        StatusIndicatorPill(
            label = "GPS",
            isActive = isGpsActive,
            colorOverride = if (gpsStatus is GpsStatus.BatterySaverActive) TrekWarning else null
        )

        // Advertising indicator
        StatusIndicatorPill(label = "ADV", isActive = isAdvertising)

        // Discovery indicator
        StatusIndicatorPill(label = "DISC", isActive = isDiscovering)

        // Connected Peers count indicator
        StatusIndicatorPill(label = "PEERS: $connectedCount", isActive = connectedCount > 0)

        // Foreground Service indicator
        StatusIndicatorPill(label = "FGS", isActive = isServiceRunning)

        // Permissions indicator
        StatusIndicatorPill(label = "PERM", isActive = hasPermissions)
    }
}

@Composable
fun StatusIndicatorPill(
    label: String,
    isActive: Boolean,
    colorOverride: Color? = null
) {
    val glowColor = colorOverride ?: if (isActive) DarkGreenPrimary else TrekError
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkCardBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(glowColor, CircleShape)
            )
            Text(label, color = TrekWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// 1. Compass Radar Screen
@Composable
fun RadarScreen(viewModel: TrekRoomViewModel) {
    val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()
    val locations by viewModel.memberLocations.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val myDeviceId = viewModel.localDeviceId

    var radarRangeScale by remember { mutableStateOf(250.0) }
    var showRangeDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COMPASS RADAR", color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(
                    onClick = { showRangeDropdown = true },
                    border = BorderStroke(1.dp, DarkGreenPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkGreenPrimary),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Range: ${radarRangeScale.toInt()}m", fontSize = 12.sp)
                }
                DropdownMenu(
                    expanded = showRangeDropdown,
                    onDismissRequest = { showRangeDropdown = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    listOf(100.0, 250.0, 500.0, 1000.0).forEach { range ->
                        DropdownMenuItem(
                            text = { Text("${range.toInt()} meters", color = TrekWhite) },
                            onClick = {
                                radarRangeScale = range
                                showRangeDropdown = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (myLoc == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DarkGreenPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Awaiting GPS coordinates...",
                        color = MutedText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
            val sweepAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "SweepAngle"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .background(DarkSurface, CircleShape)
                    .border(2.dp, DarkGreenSecondary, CircleShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.width / 2 - 16.dp.toPx()

                    val rings = listOf(0.25f, 0.5f, 0.75f, 1.0f)
                    rings.forEach { ratio ->
                        val radius = maxRadius * ratio
                        drawCircle(
                            color = DarkGreenSecondary.copy(alpha = 0.3f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    val sweepRad = Math.toRadians(sweepAngle.toDouble())
                    val endX = center.x + (Math.cos(sweepRad) * maxRadius).toFloat()
                    val endY = center.y + (Math.sin(sweepRad) * maxRadius).toFloat()
                    drawLine(
                        color = DarkGreenPrimary.copy(alpha = 0.4f),
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 2.dp.toPx()
                    )

                    drawCircle(
                        color = DarkGreenPrimary,
                        radius = 8.dp.toPx(),
                        center = center
                    )

                    val myLatitude = myLoc!!.latitude
                    val myLongitude = myLoc!!.longitude

                    locations.forEach { loc ->
                        if (loc.deviceId != myDeviceId) {
                            val peer = peers.find { it.deviceId == loc.deviceId }
                            val isConnected = peer?.connected == true
                            val isStale = (System.currentTimeMillis() - loc.timestamp) > 30000

                            val dotColor = when {
                                !isConnected -> TrekError
                                isStale -> TrekWarning
                                else -> DarkGreenPrimary
                            }

                            val dy = (loc.latitude - myLatitude) * 111139.0
                            val dx = (loc.longitude - myLongitude) * 111139.0 * Math.cos(Math.toRadians(myLatitude))

                            val distance = Math.sqrt(dx * dx + dy * dy)
                            val scale = Math.min(1.0, distance / radarRangeScale)
                            val angle = Math.atan2(dx, dy)

                            val plotX = center.x + (Math.sin(angle) * scale * maxRadius).toFloat()
                            val plotY = center.y - (Math.cos(angle) * scale * maxRadius).toFloat()

                            drawCircle(
                                color = dotColor,
                                radius = 6.dp.toPx(),
                                center = Offset(plotX, plotY)
                            )

                            drawContext.canvas.nativeCanvas.drawText(
                                loc.displayName,
                                plotX,
                                plotY - 10.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 10.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(DarkGreenPrimary, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Connected", color = TrekWhite, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(TrekWarning, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stale (>30s)", color = TrekWhite, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(TrekError, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Disconnected", color = TrekWhite, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

// 2. Private Trail Canvas Map Screen
@Composable
fun TrekTrailMapScreen(viewModel: TrekRoomViewModel) {
    val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()
    val locations by viewModel.memberLocations.collectAsStateWithLifecycle()
    val trailPoints by viewModel.trailPoints.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()

    var mapZoom by remember { mutableStateOf(1.0f) }
    var mapPanX by remember { mutableStateOf(0f) }
    var mapPanY by remember { mutableStateOf(0f) }
    var mapRotateWithHeading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PRIVATE TRAIL MAP", color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { mapZoom = (mapZoom + 0.2f).coerceIn(0.5f, 5.0f) }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = DarkGreenPrimary)
                }
                IconButton(onClick = { mapZoom = (mapZoom - 0.2f).coerceIn(0.5f, 5.0f) }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = null, tint = DarkGreenPrimary)
                }
                IconButton(onClick = { mapRotateWithHeading = !mapRotateWithHeading }) {
                    Icon(
                        imageVector = Icons.Default.CompassCalibration,
                        contentDescription = null,
                        tint = if (mapRotateWithHeading) DarkGreenPrimary else MutedText
                    )
                }
            }
        }

        if (myLoc == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Awaiting GPS Fix to draw trail...", color = MutedText)
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomAmount, _ ->
                            mapZoom = (mapZoom * zoomAmount).coerceIn(0.5f, 5.0f)
                            mapPanX += pan.x
                            mapPanY += pan.y
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2 + mapPanX, size.height / 2 + mapPanY)
                    val baseScale = 200000.0f * mapZoom

                    val rotationAngle = if (mapRotateWithHeading) -myLoc!!.bearing else 0f

                    withTransform({
                        rotate(rotationAngle, Offset(size.width / 2, size.height / 2))
                    }) {
                        val gridSize = 100f
                        for (x in 0 until (size.width.toInt() / gridSize.toInt()) * 2) {
                            val pos = x * gridSize - size.width
                            drawLine(
                                color = DarkGreenSecondary.copy(alpha = 0.1f),
                                start = Offset(pos, -size.height),
                                end = Offset(pos, size.height * 2)
                            )
                        }

                        val groupedTrails = trailPoints.groupBy { it.deviceId }
                        groupedTrails.forEach { (deviceId, points) ->
                            val peer = peers.find { it.deviceId == deviceId }
                            val isSelf = deviceId == viewModel.localDeviceId
                            val lineColor = if (isSelf) DarkGreenPrimary.copy(alpha = 0.6f) else TrekWhite.copy(alpha = 0.4f)

                            if (points.size > 1) {
                                for (i in 0 until points.size - 1) {
                                    val p1 = points[i]
                                    val p2 = points[i + 1]

                                    val startX = center.x + ((p1.longitude - myLoc!!.longitude) * baseScale).toFloat()
                                    val startY = center.y - ((p1.latitude - myLoc!!.latitude) * baseScale).toFloat()
                                    val endX = center.x + ((p2.longitude - myLoc!!.longitude) * baseScale).toFloat()
                                    val endY = center.y - ((p2.latitude - myLoc!!.latitude) * baseScale).toFloat()

                                    drawLine(
                                        color = lineColor,
                                        start = Offset(startX, startY),
                                        end = Offset(endX, endY),
                                        strokeWidth = 3f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                }
                            }
                        }

                        locations.forEach { loc ->
                            val isSelf = loc.deviceId == viewModel.localDeviceId
                            val dotColor = if (isSelf) DarkGreenPrimary else TrekWhite
                            val px = center.x + ((loc.longitude - myLoc!!.longitude) * baseScale).toFloat()
                            val py = center.y - ((loc.latitude - myLoc!!.latitude) * baseScale).toFloat()

                            val length = 20.dp.toPx()
                            val angleRad = Math.toRadians((90 - loc.bearing).toDouble())
                            val endArrowX = px + (Math.cos(angleRad) * length).toFloat()
                            val endArrowY = py - (Math.sin(angleRad) * length).toFloat()

                            drawLine(
                                color = dotColor,
                                start = Offset(px, py),
                                end = Offset(endArrowX, endArrowY),
                                strokeWidth = 4f
                            )

                            drawCircle(
                                color = dotColor,
                                radius = 6.dp.toPx(),
                                center = Offset(px, py)
                            )

                            drawContext.canvas.nativeCanvas.drawText(
                                loc.displayName,
                                px,
                                py - 12.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 9.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 3. Reliable Chat Screen
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(viewModel: TrekRoomViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val fileTransfers by viewModel.fileTransfers.collectAsStateWithLifecycle()
    val isRecording by viewModel.pttManager.isRecordingFlow.collectAsStateWithLifecycle()
    val isPlaying by viewModel.pttManager.isPlayingFlow.collectAsStateWithLifecycle()
    val audioLevel by viewModel.pttManager.audioLevel.collectAsStateWithLifecycle()
    val currentSpeaker by viewModel.pttManager.currentSpeaker.collectAsStateWithLifecycle()

    val localDeviceId = viewModel.localDeviceId
    var inputText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var replyMessage by remember { mutableStateOf<MessageEntity?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val resolver = context.contentResolver
                val inputStream = resolver.openInputStream(uri)
                val cursor = resolver.query(uri, null, null, null, null)
                var name = "shared_file_${System.currentTimeMillis()}"
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                    cursor.close()
                }
                val localFile = File(context.filesDir, name)
                localFile.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }
                viewModel.shareFile(localFile, name, "FILE")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val filteredMessages = if (searchQuery.isBlank()) {
        messages
    } else {
        messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search messages...", color = MutedText) },
            prefix = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedText, modifier = Modifier.padding(end = 8.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TrekWhite,
                unfocusedTextColor = TrekWhite,
                focusedBorderColor = DarkGreenPrimary,
                unfocusedBorderColor = DarkGreenSecondary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        if (isPlaying && currentSpeaker != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkGreenSecondary)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = DarkGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Voice playing from $currentSpeaker...", color = TrekWhite, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(16.dp)
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(audioLevel.coerceIn(0.01f, 1f))
                                .background(DarkGreenPrimary, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredMessages) { msg ->
                val isSelf = msg.senderDeviceId == localDeviceId
                val alignment = if (isSelf) Alignment.End else Alignment.Start
                val cardColor = if (isSelf) DarkGreenSecondary else DarkCardBg
                val outlineColor = if (isSelf) DarkGreenPrimary.copy(alpha = 0.6f) else GlassBorder

                var parentMessageText: String? = null
                if (msg.replyToId != null) {
                    parentMessageText = messages.find { it.messageId == msg.replyToId }?.text
                }

                var showActionsMenu by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onLongClick = { showActionsMenu = true },
                            onClick = {}
                        ),
                    horizontalAlignment = alignment
                ) {
                    Text(
                        text = if (isSelf) "You" else msg.senderDisplayName,
                        color = MutedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    if (parentMessageText != null) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("↳ $parentMessageText", color = MutedText, fontSize = 11.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isSelf) 16.dp else 0.dp,
                                    bottomEnd = if (isSelf) 0.dp else 16.dp
                                )
                            )
                            .background(cardColor)
                            .border(
                                1.dp,
                                outlineColor,
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isSelf) 16.dp else 0.dp,
                                    bottomEnd = if (isSelf) 0.dp else 16.dp
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(msg.text, color = TrekWhite, fontSize = 15.sp)

                            if (msg.reactions.isNotBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    msg.reactions.split(",").filter { it.isNotBlank() }.forEach { reaction ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(reaction, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                Text(time, color = MutedText, fontSize = 9.sp)
                                if (isSelf) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = when (msg.deliveryStatus) {
                                            "SENDING" -> Icons.Default.Pending
                                            "SENT" -> Icons.Default.Done
                                            else -> Icons.Default.DoneAll
                                        },
                                        contentDescription = null,
                                        tint = DarkGreenPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showActionsMenu) {
                        AlertDialog(
                            onDismissRequest = { showActionsMenu = false },
                            containerColor = DarkSurface,
                            confirmButton = {},
                            title = { Text("Message Actions", color = TrekWhite) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        listOf("👍", "❤️", "⚠️", "🚨").forEach { emoji ->
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(DarkGreenSecondary, CircleShape)
                                                    .clickable {
                                                        viewModel.addMessageReaction(msg.messageId, emoji)
                                                        showActionsMenu = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(emoji, fontSize = 20.sp)
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = DarkGreenSecondary)
                                    TextButton(onClick = {
                                        replyMessage = msg
                                        showActionsMenu = false
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Reply", color = DarkGreenPrimary, textAlign = TextAlign.Start)
                                    }
                                    TextButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(msg.text))
                                        showActionsMenu = false
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Copy Message", color = TrekWhite)
                                    }
                                    TextButton(onClick = {
                                        viewModel.deleteMessageLocal(msg.messageId)
                                        showActionsMenu = false
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Delete Locally", color = TrekError)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (fileTransfers.isNotEmpty()) {
                item {
                    Text("FILE TRANSFERS", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(fileTransfers) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FilePresent, contentDescription = null, tint = DarkGreenPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.fileName, color = TrekWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Size: ${file.fileSize / 1024} KB", color = MutedText, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { file.progress },
                                    color = DarkGreenPrimary,
                                    trackColor = DarkGreenSecondary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (file.progress >= 1.0f) "Completed" else "${(file.progress * 100).toInt()}%",
                                color = if (file.progress >= 1.0f) DarkGreenPrimary else MutedText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (replyMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkGreenSecondary)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Replying to: \"${replyMessage!!.text}\"", color = TrekWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { replyMessage = null }) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TrekWhite)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = DarkGreenPrimary)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Offline msg...", color = MutedText) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TrekWhite,
                    unfocusedTextColor = TrekWhite,
                    focusedBorderColor = DarkGreenPrimary,
                    unfocusedBorderColor = DarkGreenSecondary
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer {
                        scaleX = if (isRecording) 1.2f else 1.0f
                        scaleY = if (isRecording) 1.2f else 1.0f
                    }
                    .background(if (isRecording) TrekError else DarkGreenPrimary, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                viewModel.pttManager.startRecording()
                            },
                            onDragEnd = {
                                viewModel.pttManager.stopRecording()
                            },
                            onDragCancel = {
                                viewModel.pttManager.stopRecording()
                            },
                            onDrag = { change: PointerInputChange, dragAmount: Offset -> }
                        )
                    }
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    tint = DarkBg
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.trim().isNotEmpty()) {
                        viewModel.sendMessage(inputText.trim(), replyMessage?.messageId)
                        inputText = ""
                        replyMessage = null
                    }
                },
                modifier = Modifier
                    .background(DarkGreenSecondary, CircleShape)
                    .size(46.dp)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = DarkGreenPrimary)
            }
        }
    }
}

// 4. Members Screen
@Composable
fun MembersScreen(viewModel: TrekRoomViewModel) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val locations by viewModel.memberLocations.collectAsStateWithLifecycle()
    val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()
    val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()

    val myDeviceId = viewModel.localDeviceId
    val isLocalHost = currentRoom?.hostDeviceId == myDeviceId

    var showSosConfirmation by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { showSosConfirmation = true },
                colors = ButtonDefaults.buttonColors(containerColor = TrekError, contentColor = TrekWhite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .border(2.dp, TrekWhite.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TrekWhite, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("TRIGGER EMERGENCY SOS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Text(
                text = "${peers.size} TREKKERS LINKED",
                color = MutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(peers) { peer ->
            val loc = locations.find { it.deviceId == peer.deviceId }
            val isSelf = peer.deviceId == myDeviceId
            val isHost = peer.role == "HOST"

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, if (peer.connected) GlassBorder else DarkGreenSecondary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(peer.displayName, color = TrekWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (isHost) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LEADER",
                                    color = DarkBg,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(DarkGreenPrimary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            if (isSelf) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "YOU",
                                    color = TrekWhite,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(DarkGreenSecondary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text("Device: ${peer.deviceId}", color = MutedText, fontSize = 12.sp)

                        if (isSelf) {
                            Text("Centered on your GPS coordinates", color = MutedText, fontSize = 11.sp)
                        } else if (loc != null && myLoc != null) {
                            val distance = calculateDistance(myLoc!!.latitude, myLoc!!.longitude, loc.latitude, loc.longitude)
                            Text(
                                text = "Dist: ${distance.toInt()} m | Alt: ${loc.altitude.toInt()}m | Bear: ${loc.bearing.toInt()}°",
                                color = DarkGreenPrimary,
                                fontSize = 12.sp
                            )
                            val age = (System.currentTimeMillis() - loc.timestamp) / 1000
                            Text("Last seen: ${age}s ago", color = MutedText, fontSize = 11.sp)
                        }
                    }

                    if (isLocalHost && !isSelf) {
                        var showAdminActions by remember { mutableStateOf(false) }
                        IconButton(onClick = { showAdminActions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = TrekWhite)
                        }

                        if (showAdminActions) {
                            AlertDialog(
                                onDismissRequest = { showAdminActions = false },
                                containerColor = DarkSurface,
                                confirmButton = {},
                                title = { Text("Manage ${peer.displayName}", color = TrekWhite) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            viewModel.transferHost(peer.deviceId)
                                            showAdminActions = false
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Transfer Leader Role", color = DarkGreenPrimary)
                                        }
                                        TextButton(onClick = {
                                            viewModel.kickMember(peer.deviceId)
                                            showAdminActions = false
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Kick from Trek", color = TrekError)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSosConfirmation) {
        var emergencyType by remember { mutableStateOf("Injury") }
        AlertDialog(
            onDismissRequest = { showSosConfirmation = false },
            containerColor = DarkSurface,
            title = { Text("Confirm Emergency SOS", color = TrekError, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select Emergency Type to broadcast to all group members via multi-hop mesh relays:", color = TrekWhite)
                    listOf("Injury", "Lost", "Wildlife Danger", "Severe Weather", "Other").forEach { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { emergencyType = type }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (emergencyType == type),
                                onClick = { emergencyType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = DarkGreenPrimary)
                            )
                            Text(type, color = TrekWhite, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.triggerSosAlert(emergencyType)
                        showSosConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrekError, contentColor = TrekWhite)
                ) {
                    Text("TRIGGER SOS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosConfirmation = false }, colors = ButtonDefaults.textButtonColors(contentColor = MutedText)) {
                    Text("CANCEL")
                }
            }
        )
    }
}

// 5. Diagnostics Debug Screen
@Composable
fun DebugScreen(viewModel: TrekRoomViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val totalSent by viewModel.totalPacketsSent.collectAsStateWithLifecycle()
    val totalReceived by viewModel.totalPacketsReceived.collectAsStateWithLifecycle()
    val dropped by viewModel.droppedPackets.collectAsStateWithLifecycle()
    val relays by viewModel.totalRelays.collectAsStateWithLifecycle()
    val latency by viewModel.averageLatencyMs.collectAsStateWithLifecycle()
    val isAdv by viewModel.isAdvertising.collectAsStateWithLifecycle()
    val isDisc by viewModel.isDiscovering.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("DIAGNOSTICS & LOGS", color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nearby Connections", color = DarkGreenPrimary, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = DarkGreenSecondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Advertising Active", color = MutedText)
                        Text(if (isAdv) "ACTIVE" else "INACTIVE", color = if (isAdv) DarkGreenPrimary else TrekError)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discovery Active", color = MutedText)
                        Text(if (isDisc) "ACTIVE" else "INACTIVE", color = if (isDisc) DarkGreenPrimary else TrekError)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Avg Ping RTT", color = MutedText)
                        Text("${latency} ms", color = DarkGreenPrimary)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Packet Statistics", color = DarkGreenPrimary, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = DarkGreenSecondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Packets Sent", color = MutedText)
                        Text(totalSent.toString(), color = TrekWhite)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Packets Received", color = MutedText)
                        Text(totalReceived.toString(), color = TrekWhite)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SOS Relays (Mesh)", color = MutedText)
                        Text(relays.toString(), color = DarkGreenPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dropped/Corrupted Packets", color = MutedText)
                        Text(dropped.toString(), color = TrekError)
                    }
                }
            }
        }

        item {
            Text("LIVE LOGS MONITOR", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color.Black)
                    .border(1.dp, DarkGreenSecondary)
                    .padding(8.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { logLine ->
                        Text(logLine, color = DarkGreenPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// 6. QR Code Camera Scanner
@Composable
fun QrScannerScreen(
    onCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var isScanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(executor, QrCodeAnalyzer { result ->
                        if (!isScanned) {
                            isScanned = true
                            onCodeScanned(result)
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Scan Trek Room QR",
                    color = TrekWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .border(3.dp, DarkGreenPrimary, RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(30.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = TrekWhite),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CANCEL")
                }
            }
        }
    }
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(2)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0]
}

fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(2)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return if (results.size > 1) results[1] else 0f
}
