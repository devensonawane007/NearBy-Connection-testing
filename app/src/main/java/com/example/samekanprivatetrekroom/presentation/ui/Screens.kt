package com.example.samekanprivatetrekroom.presentation.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samekanprivatetrekroom.data.local.*
import com.example.samekanprivatetrekroom.domain.model.*
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

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color(0x3300FF66),
    containerColor: Color = Color(0xCC0E1612),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBg, DarkSurface)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = DarkGreenPrimary.copy(alpha = 0.4f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CompassCalibration,
                    contentDescription = null,
                    tint = DarkGreenPrimary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "OFF-GRID PERMISSIONS",
                    color = TrekWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Samekan Private Trek Room requires local hardware permissions to establish a serverless peer-to-peer ad-hoc network for search, rescue, and navigation.",
                    color = MutedText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                PermissionRationaleRow(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth & Wi-Fi",
                    desc = "Discovers and advertises ad-hoc peer connections offline."
                )
                Spacer(modifier = Modifier.height(12.dp))
                PermissionRationaleRow(
                    icon = Icons.Default.LocationOn,
                    title = "GPS Fine Location",
                    desc = "Shares your trail coordinates, bearing, and altitude."
                )
                Spacer(modifier = Modifier.height(12.dp))
                PermissionRationaleRow(
                    icon = Icons.Default.Mic,
                    title = "Microphone",
                    desc = "Enables Push-To-Talk (PTT) walkie-talkie voice streaming."
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("GRANT SYSTEM ACCESS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PermissionRationaleRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Star,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(DarkGreenSecondary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DarkGreenPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = MutedText, fontSize = 11.sp)
        }
    }
}

@Composable
fun HomeScreen(viewModel: TrekRoomViewModel) {
    var displayNameInput by remember { mutableStateOf(viewModel.localDisplayName.value) }
    var roomNameInput by remember { mutableStateOf("") }
    var roomIdInput by remember { mutableStateOf("") }
    var roomPasswordInput by remember { mutableStateOf("") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBg, DarkSurface)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = DarkGreenPrimary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SAMEKAN PRIVATE TREK",
                color = TrekWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Text(
                text = "COMMUNICATION & MESH NAVIGATION SYSTEM",
                color = DarkGreenPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("YOUR TREKKER CALLSIGN", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = {
                        displayNameInput = it
                        viewModel.updateDisplayName(it)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TrekWhite,
                        unfocusedTextColor = TrekWhite,
                        focusedBorderColor = DarkGreenPrimary,
                        unfocusedBorderColor = DarkGreenSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CREATE ROOM", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showJoinDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreenSecondary, contentColor = DarkGreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                    border = BorderStroke(1.dp, DarkGreenPrimary)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("JOIN ROOM", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showScanner = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TrekWhite),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCAN QR CODE TO JOIN")
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = DarkSurface,
            title = { Text("Create Offline Trek Room", color = TrekWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = roomNameInput,
                        onValueChange = { roomNameInput = it },
                        label = { Text("Trek Room Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = roomPasswordInput,
                        onValueChange = { roomPasswordInput = it },
                        label = { Text("Room Encryption Key (Password)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roomNameInput.trim().isNotEmpty()) {
                            val id = "R-${UUID.randomUUID().toString().substring(0, 4).uppercase()}"
                            viewModel.createRoom(roomNameInput.trim(), id, "", roomPasswordInput.trim())
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
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = DarkSurface,
            title = { Text("Join Offline Room", color = TrekWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = roomIdInput,
                        onValueChange = { roomIdInput = it },
                        label = { Text("Room ID (e.g. R-ABCD)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = roomPasswordInput,
                        onValueChange = { roomPasswordInput = it },
                        label = { Text("Room Password") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekWhite,
                            unfocusedTextColor = TrekWhite,
                            focusedBorderColor = DarkGreenPrimary,
                            unfocusedBorderColor = DarkGreenSecondary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roomIdInput.trim().isNotEmpty()) {
                            viewModel.joinRoom("Trek Room ${roomIdInput.uppercase()}", roomIdInput.trim().uppercase(), "", roomPasswordInput.trim())
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

    if (showScanner) {
        QrScannerScreen(
            onCodeScanned = { result ->
                try {
                    val qrData = Gson().fromJson(result, RoomQrData::class.java)
                    viewModel.joinRoom(qrData.roomName, qrData.roomId, "", "")
                } catch (e: Exception) {
                    // fallback plain scan
                    viewModel.joinRoom("Scanned Room", result.uppercase(), "", "")
                }
                showScanner = false
            },
            onClose = { showScanner = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekRoomDashboard(viewModel: TrekRoomViewModel, room: RoomEntity) {
    val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()
    val activeSos by viewModel.activeSosAlert.collectAsStateWithLifecycle()

    var showSettingsOverlay by remember { mutableStateOf(false) }
    var showPttOverlay by remember { mutableStateOf(false) }

    // Bottom draggable sheet state
    var sheetOffset by remember { mutableStateOf(160.dp) }
    val maxSheetHeight = 650.dp
    val minSheetHeight = 160.dp

    // FAB menu expansion
    var isFabExpanded by remember { mutableStateOf(false) }

    // Selected tab in sheet
    var activeTab by remember { mutableIntStateOf(0) }

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
                modifier = Modifier.fillMaxWidth().padding(16.dp)
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

    // Active Full-Screen SOS Alarm Alert Overlay
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
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "EMERGENCY SOS ALERT",
                    color = TrekWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${sos.senderName} is in distress! [${sos.emergencyType.uppercase()}]",
                    color = TrekWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Compass Direction Arrow pointing to SOS sender
                if (myLoc != null) {
                    val bearingVal = calculateBearing(myLoc!!.latitude, myLoc!!.longitude, sos.latitude, sos.longitude)
                    val rotationAngle = bearingVal - myLoc!!.bearing
                    val distance = calculateDistance(myLoc!!.latitude, myLoc!!.longitude, sos.latitude, sos.longitude)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(140.dp)) {
                            val rCenter = Offset(size.width / 2, size.height / 2)
                            val rRadius = size.width / 2 - 8.dp.toPx()
                            drawCircle(
                                color = TrekWhite.copy(alpha = 0.2f),
                                radius = rRadius,
                                center = rCenter,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            withTransform({
                                rotate(rotationAngle, rCenter)
                            }) {
                                val arrowPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(rCenter.x, rCenter.y - rRadius)
                                    lineTo(rCenter.x - 12.dp.toPx(), rCenter.y - rRadius + 24.dp.toPx())
                                    lineTo(rCenter.x - 4.dp.toPx(), rCenter.y - rRadius + 18.dp.toPx())
                                    lineTo(rCenter.x - 4.dp.toPx(), rCenter.y + rRadius - 12.dp.toPx())
                                    lineTo(rCenter.x + 4.dp.toPx(), rCenter.y + rRadius - 12.dp.toPx())
                                    lineTo(rCenter.x + 4.dp.toPx(), rCenter.y - rRadius + 18.dp.toPx())
                                    lineTo(rCenter.x + 12.dp.toPx(), rCenter.y - rRadius + 24.dp.toPx())
                                    close()
                                }
                                drawPath(arrowPath, color = DarkGreenPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Distance: ${distance.toInt()} meters", color = DarkGreenPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val etaMins = (distance / 80.0f).toInt() // walking speed ~80m/min
                        Text("Estimated Hiker ETA: $etaMins mins", color = TrekWhite, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Coordinates: ${sos.latitude}, ${sos.longitude}", color = TrekWhite, fontSize = 13.sp)
                        Text("Altitude: ${sos.altitude.toInt()}m | Accuracy: ${sos.accuracy.toInt()}m", color = TrekWhite, fontSize = 13.sp)
                        Text("Distress Node Battery: ${sos.batteryLevel}%", color = TrekWhite, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { viewModel.acknowledgeSos(sos.messageId, sos.senderId) },
                    colors = ButtonDefaults.buttonColors(containerColor = TrekWhite, contentColor = TrekError),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("SEND RESCUE ACKNOWLEDGEMENT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // MAIN Map-First Dashboard
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight

        // 1. Map Canvas occupies 100% of background
        Box(modifier = Modifier.fillMaxSize()) {
            TrekTrailMapScreen(viewModel)
        }

        // 2. Top Floating Info Card Overlay (inspirations from AllTrails / Gaia GPS)
        TopFloatingInfoCard(viewModel, room)

        // 3. Right Floating Expandable FAB Actions Panel
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = sheetOffset + 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Settings Option
                        FloatingActionButton(
                            onClick = {
                                showSettingsOverlay = true
                                isFabExpanded = false
                            },
                            containerColor = DarkSurface,
                            contentColor = DarkGreenPrimary,
                            shape = CircleShape,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                        }

                        // PTT walkie-talkie toggle
                        FloatingActionButton(
                            onClick = {
                                showPttOverlay = true
                                isFabExpanded = false
                            },
                            containerColor = DarkSurface,
                            contentColor = DarkGreenPrimary,
                            shape = CircleShape,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "PTT", modifier = Modifier.size(20.dp))
                        }

                        // SOS trigger
                        FloatingActionButton(
                            onClick = {
                                activeTab = 0 // Select trekkers tab to find SOS
                                sheetOffset = maxSheetHeight // Expand bottom sheet
                                isFabExpanded = false
                            },
                            containerColor = TrekError,
                            contentColor = TrekWhite,
                            shape = CircleShape,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "SOS", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Main Toggle Button
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = DarkGreenPrimary,
                    contentColor = DarkBg,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Menu,
                        contentDescription = "Expand",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // 4. Bottom Draggable Tab Sheet
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetOffset)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xEE070B09))
                .border(1.dp, GlassBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag handle bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val currentOffset = sheetOffset - dragAmount.y.dp
                                sheetOffset = currentOffset.coerceIn(minSheetHeight, maxSheetHeight)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .clip(CircleShape)
                            .background(MutedText.copy(alpha = 0.5f))
                    )
                }

                // Dynamic UI depending on draggable sheet size state
                if (sheetOffset <= 180.dp) {
                    // Collapsed Telemetry Summary (Latitude, Walked Distance, Altitude, Compass)
                    CollapsedTelemetryRow(viewModel)
                } else {
                    // Expanded Mode with Bottom Tabs
                    TabsRow(
                        selectedIndex = activeTab,
                        onTabSelected = { activeTab = it }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (activeTab) {
                            0 -> MembersScreen(viewModel)
                            1 -> ChatScreen(viewModel)
                            2 -> RadarScreen(viewModel)
                            3 -> FilesTabScreen(viewModel)
                            4 -> DebugScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    // PTT Voice walkie talkie floating overlay panel
    if (showPttOverlay) {
        PttVoiceOverlay(
            viewModel = viewModel,
            onClose = { showPttOverlay = false }
        )
    }

    // Settings overlay dialog
    if (showSettingsOverlay) {
        SettingsOverlayDialog(
            viewModel = viewModel,
            onClose = { showSettingsOverlay = false }
        )
    }
}

@Composable
fun TopFloatingInfoCard(viewModel: TrekRoomViewModel, room: RoomEntity) {
    val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val distanceWalked by viewModel.distanceWalked.collectAsStateWithLifecycle()

    val connectedCount = peers.count { it.connected && it.deviceId != viewModel.localDeviceId }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            borderColor = GlassBorder,
            containerColor = Color(0xDD0E1612)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Header line: Room name & Connected count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(room.roomName, color = TrekWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Room ID: ${room.roomId} | Active mesh", color = MutedText, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkGreenSecondary)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("${connectedCount + 1} ONLINE", color = DarkGreenPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }

                HorizontalDivider(color = DarkGreenSecondary)

                // Sub Info: Leader, GPS Acc, Walked Distance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TREK DISTANCE", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(String.format("%.1f m", distanceWalked), color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GPS ACCURACY", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val accuracyStr = if (myLoc != null) "${myLoc!!.accuracy.toInt()}m" else "No Fix"
                        Text(accuracyStr, color = if (myLoc != null && myLoc!!.accuracy < 15f) DarkGreenPrimary else TrekWarning, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("ALTITUDE", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val altStr = if (myLoc != null) "${myLoc!!.altitude.toInt()}m" else "---"
                        Text(altStr, color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsedTelemetryRow(viewModel: TrekRoomViewModel) {
    val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()
    val distanceWalked by viewModel.distanceWalked.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text("CURRENT COORDINATES", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            val coordsText = if (myLoc != null) "${String.format("%.5f", myLoc!!.latitude)}, ${String.format("%.5f", myLoc!!.longitude)}" else "Awaiting GPS..."
            Text(coordsText, color = TrekWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ALTITUDE", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            val altVal = if (myLoc != null) "${myLoc!!.altitude.toInt()} m" else "---"
            Text(altVal, color = TrekWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
            Text("HEADING", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            val headingVal = if (myLoc != null) "${myLoc!!.bearing.toInt()}°" else "---"
            Text(headingVal, color = DarkGreenPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TabsRow(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabLabels = listOf("Trekkers", "Chat", "Radar", "Files", "Diag")
    val tabIcons = listOf(Icons.Default.People, Icons.Default.Chat, Icons.Default.Radar, Icons.Default.FilePresent, Icons.Default.Build)

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        contentColor = DarkGreenPrimary,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                color = DarkGreenPrimary
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        tabLabels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(tabIcons[index], contentDescription = null, modifier = Modifier.size(20.dp)) },
                text = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                selectedContentColor = DarkGreenPrimary,
                unselectedContentColor = MutedText
            )
        }
    }
}

// Files download/upload manager tab screen
@Composable
fun FilesTabScreen(viewModel: TrekRoomViewModel) {
    val fileTransfers by viewModel.fileTransfers.collectAsStateWithLifecycle()
    val localDeviceId = viewModel.localDeviceId

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val context = viewModel.getApplication<Application>()
            val file = copyUriToCacheFile(context, it)
            if (file != null) {
                viewModel.shareFile(file, file.name, "GPX")
            }
        }
    }

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
            Text("FILE SHARING PROTOCOL", color = TrekWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreenPrimary, contentColor = DarkBg),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SHARE FILE", fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (fileTransfers.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No files shared yet in this session.", color = MutedText, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fileTransfers) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FilePresent, contentDescription = null, tint = DarkGreenPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.fileName, color = TrekWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text("Size: ${file.fileSize / 1024} KB | Status: ${file.status}", color = MutedText, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { file.progress },
                                    color = if (file.status == "FAILED") TrekError else DarkGreenPrimary,
                                    trackColor = DarkGreenSecondary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!file.isIncoming) {
                                    if (file.status == "SENDING") {
                                        IconButton(onClick = { viewModel.pauseFileTransfer(file.fileId) }) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = DarkGreenPrimary)
                                        }
                                    } else if (file.status == "PAUSED" || file.status == "INTERRUPTED") {
                                        IconButton(onClick = { viewModel.resumeFileTransfer(file.fileId) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = DarkGreenPrimary)
                                        }
                                    }
                                }
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
        }
    }
}

// Reusable file picker helper
private fun copyUriToCacheFile(context: Context, uri: Uri): File? {
    return try {
        val resolver = context.contentResolver
        val cursor = resolver.query(uri, null, null, null, null)
        val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        cursor?.moveToFirst()
        val name = cursor?.getString(nameIndex ?: 0) ?: "shared_file"
        cursor?.close()

        val tempFile = File(context.cacheDir, name)
        if (tempFile.exists()) tempFile.delete()
        val output = tempFile.outputStream()
        resolver.openInputStream(uri)?.use { input ->
            input.copyTo(output)
        }
        output.close()
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 4. Voice walkie-talkie UI pop-up overlay panel
@Composable
fun PttVoiceOverlay(
    viewModel: TrekRoomViewModel,
    onClose: () -> Unit
) {
    val isRecording by viewModel.pttManager.isRecordingFlow.collectAsStateWithLifecycle()
    val isPlaying by viewModel.pttManager.isPlayingFlow.collectAsStateWithLifecycle()
    val activeSpeaker by viewModel.pttManager.currentSpeaker.collectAsStateWithLifecycle()
    val audioLevel by viewModel.pttManager.audioLevel.collectAsStateWithLifecycle()

    var timerVal by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            timerVal = 0
            while (isRecording) {
                delay(1000)
                timerVal++
            }
        }
    }

    Dialog(onDismissRequest = onClose) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            borderColor = DarkGreenPrimary.copy(alpha = 0.5f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("WALKIE TALKIE MODE", color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TrekWhite)
                    }
                }

                // Breathing Ripple Animation around PTT Mic
                val rippleTransition = rememberInfiniteTransition(label = "PttMicRipple")
                val rippleScale by rippleTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = if (isRecording) 1.5f else 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "RippleScale"
                )

                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Outer Ripple Ring
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(110.dp * rippleScale)
                                .clip(CircleShape)
                                .background(TrekError.copy(alpha = 0.15f))
                        )
                    }

                    // Recording button (Press & hold)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isRecording -> TrekError
                                    isPlaying -> DarkGreenPrimary
                                    else -> DarkGreenSecondary
                                }
                            )
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { viewModel.pttManager.startRecording() },
                                    onDragEnd = { viewModel.pttManager.stopRecording() },
                                    onDragCancel = { viewModel.pttManager.stopRecording() },
                                    onDrag = { _, _ -> }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Hold to talk",
                            tint = DarkBg,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Speaking banners
                val bannerText = when {
                    isRecording -> "TRANSMITTING VOICE (Hold button)"
                    isPlaying -> "RECEIVING: $activeSpeaker"
                    else -> "HOLD MIC AND SPEAK"
                }
                Text(bannerText, color = if (isRecording) TrekError else if (isPlaying) DarkGreenPrimary else MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // Timer
                if (isRecording) {
                    Text(
                        text = String.format("00:%02d", timerVal),
                        color = TrekWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Live Audio Level Waveform
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    val bars = 15
                    val barWidth = 4.dp.toPx()
                    val spacing = 6.dp.toPx()
                    val startX = (size.width - (bars * (barWidth + spacing) - spacing)) / 2
                    
                    val centerH = size.height / 2
                    for (i in 0 until bars) {
                        // random multiplier to simulate live waveform modulated by audioLevel
                        val randFactor = if (isRecording || isPlaying) (Math.random().toFloat() * 0.8f + 0.2f) else 0.05f
                        val height = size.height * audioLevel * randFactor
                        val x = startX + i * (barWidth + spacing)
                        
                        drawLine(
                            color = if (isRecording) TrekError else DarkGreenPrimary,
                            start = Offset(x, centerH - height / 2 - 2.dp.toPx()),
                            end = Offset(x, centerH + height / 2 + 2.dp.toPx()),
                            strokeWidth = barWidth
                        )
                    }
                }
            }
        }
    }
}

// 5. Settings overlay dialog
@Composable
fun SettingsOverlayDialog(
    viewModel: TrekRoomViewModel,
    onClose: () -> Unit
) {
    val localDisplayName by viewModel.localDisplayName.collectAsStateWithLifecycle()
    var intervalSlider by remember { mutableFloatStateOf(viewModel.prefs.getGpsIntervalSeconds().toFloat()) }
    var inputName by remember { mutableStateOf(localDisplayName) }

    Dialog(onDismissRequest = onClose) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            borderColor = GlassBorder
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SYSTEM SETTINGS", color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TrekWhite)
                    }
                }

                Text("Trekker Identity Name", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = inputName,
                    onValueChange = {
                        inputName = it
                        viewModel.updateDisplayName(it)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TrekWhite,
                        unfocusedTextColor = TrekWhite,
                        focusedBorderColor = DarkGreenPrimary,
                        unfocusedBorderColor = DarkGreenSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("GPS Polling Interval: ${intervalSlider.toInt()} seconds", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = intervalSlider,
                    onValueChange = {
                        intervalSlider = it
                        viewModel.updateGpsInterval(it.toInt())
                    },
                    valueRange = 5f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = DarkGreenPrimary,
                        activeTrackColor = DarkGreenPrimary,
                        inactiveTrackColor = DarkGreenSecondary
                    )
                )

                Button(
                    onClick = {
                        viewModel.leaveRoom()
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrekError, contentColor = TrekWhite),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LEAVE OFFLINE ROOM")
                }
            }
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
    val sosHistory by viewModel.sosHistory.collectAsStateWithLifecycle()

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
            Text("COMPASS RADAR", color = TrekWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(
                    onClick = { showRangeDropdown = true },
                    border = BorderStroke(1.dp, DarkGreenPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkGreenPrimary),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Range: ${radarRangeScale.toInt()}m", fontSize = 11.sp)
                }
                DropdownMenu(
                    expanded = showRangeDropdown,
                    onDismissRequest = { showRangeDropdown = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    listOf(100.0, 250.0, 500.0, 1000.0).forEach { range ->
                        DropdownMenuItem(
                            text = { Text("${range.toInt()} meters", color = TrekWhite, fontSize = 12.sp) },
                            onClick = {
                                radarRangeScale = range
                                showRangeDropdown = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

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

            val sosPulseScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "SosPulse"
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
                    val maxRadius = size.width / 2 - 20.dp.toPx()

                    // Draw range concentric rings
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

                    // Draw radar sweep gradient slice
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(DarkGreenPrimary.copy(alpha = 0.4f), Color.Transparent),
                            center = center
                        ),
                        startAngle = sweepAngle - 45f,
                        sweepAngle = 45f,
                        useCenter = true
                    )

                    // Draw local user dot (center)
                    drawCircle(
                        color = DarkGreenPrimary,
                        radius = 8.dp.toPx(),
                        center = center
                    )

                    // Draw Compass Outer Ticks
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - maxRadius - 6.dp.toPx(), textPaint)
                    drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + maxRadius + 12.dp.toPx(), textPaint)
                    drawContext.canvas.nativeCanvas.drawText("E", center.x + maxRadius + 12.dp.toPx(), center.y + 4.dp.toPx(), textPaint)
                    drawContext.canvas.nativeCanvas.drawText("W", center.x - maxRadius - 12.dp.toPx(), center.y + 4.dp.toPx(), textPaint)

                    val myLatitude = myLoc!!.latitude
                    val myLongitude = myLoc!!.longitude

                    locations.forEach { loc ->
                        if (loc.deviceId != myDeviceId) {
                            val peer = peers.find { it.deviceId == loc.deviceId }
                            val isConnected = peer?.connected == true
                            val isStale = (System.currentTimeMillis() - loc.timestamp) > 30000

                            val isSosActive = sosHistory.any { it.senderDeviceId == loc.deviceId && it.status == "ACTIVE" }

                            val dotColor = when {
                                isSosActive -> TrekError
                                !isConnected -> TrekWhite.copy(alpha = 0.3f)
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

                            if (isSosActive) {
                                // Pulsing SOS Alert Indicator
                                drawCircle(
                                    color = TrekError.copy(alpha = 0.2f),
                                    radius = 16.dp.toPx() * sosPulseScale,
                                    center = Offset(plotX, plotY)
                                )
                                drawCircle(
                                    color = TrekError,
                                    radius = 7.dp.toPx(),
                                    center = Offset(plotX, plotY)
                                )
                            } else {
                                drawCircle(
                                    color = dotColor,
                                    radius = 6.dp.toPx(),
                                    center = Offset(plotX, plotY)
                                )
                            }

                            // Info details drawn directly below node
                            val detailsString = "${loc.displayName} (${distance.toInt()}m | B:${peer?.batteryLevel ?: 100}%)"
                            drawContext.canvas.nativeCanvas.drawText(
                                detailsString,
                                plotX,
                                plotY - 10.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = if (isSosActive) android.graphics.Color.RED else android.graphics.Color.WHITE
                                    textSize = 8.sp.toPx()
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

// 2. Private Trail Canvas Map Screen
@Composable
fun TrekTrailMapScreen(viewModel: TrekRoomViewModel) {
    val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()
    val locations by viewModel.memberLocations.collectAsStateWithLifecycle()
    val trailPoints by viewModel.trailPoints.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val sosHistory by viewModel.sosHistory.collectAsStateWithLifecycle()

    var mapZoom by remember { mutableStateOf(1.0f) }
    var mapPanX by remember { mutableStateOf(0f) }
    var mapPanY by remember { mutableStateOf(0f) }
    var mapRotateWithHeading by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "MapSosSweep")
    val sosPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MapSosPulse"
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
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

                    // Draw Map Grid and trails with rotation
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

                        // Concentric distance circles from local user (center)
                        val metersPerDegree = 111139.0 * Math.cos(Math.toRadians(myLoc!!.latitude))
                        listOf(50.0, 100.0, 200.0).forEach { dist ->
                            val ringRadius = ((dist / metersPerDegree) * baseScale).toFloat()
                            drawCircle(
                                color = DarkGreenPrimary.copy(alpha = 0.15f),
                                radius = ringRadius,
                                center = center,
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                            )
                        }

                        // Render trails color-coded by elevation
                        val groupedTrails = trailPoints.groupBy { it.deviceId }
                        groupedTrails.forEach { (deviceId, points) ->
                            if (points.size > 1) {
                                for (i in 0 until points.size - 1) {
                                    val p1 = points[i]
                                    val p2 = points[i + 1]

                                    val startX = center.x + ((p1.longitude - myLoc!!.longitude) * baseScale).toFloat()
                                    val startY = center.y - ((p1.latitude - myLoc!!.latitude) * baseScale).toFloat()
                                    val endX = center.x + ((p2.longitude - myLoc!!.longitude) * baseScale).toFloat()
                                    val endY = center.y - ((p2.latitude - myLoc!!.latitude) * baseScale).toFloat()

                                    // Elevation coloring: Low elevation = green, High elevation = orange
                                    val lineColor = when {
                                        p1.altitude < 100.0 -> DarkGreenPrimary.copy(alpha = 0.7f)
                                        p1.altitude < 500.0 -> TrekWarning.copy(alpha = 0.7f)
                                        else -> TrekError.copy(alpha = 0.7f)
                                    }

                                    drawLine(
                                        color = lineColor,
                                        start = Offset(startX, startY),
                                        end = Offset(endX, endY),
                                        strokeWidth = 4f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                    )
                                }
                            }
                        }

                        // Render member locations & SOS flags
                        locations.forEach { loc ->
                            val isSelf = loc.deviceId == viewModel.localDeviceId
                            val isSosActive = sosHistory.any { it.senderDeviceId == loc.deviceId && it.status == "ACTIVE" }

                            val dotColor = if (isSosActive) TrekError else if (isSelf) DarkGreenPrimary else TrekWhite
                            val px = center.x + ((loc.longitude - myLoc!!.longitude) * baseScale).toFloat()
                            val py = center.y - ((loc.latitude - myLoc!!.latitude) * baseScale).toFloat()

                            // Heading directional vector arrow
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

                            if (isSosActive) {
                                // SOS pulsing flag warning on map
                                drawCircle(
                                    color = TrekError.copy(alpha = 0.25f),
                                    radius = 18.dp.toPx() * sosPulseScale,
                                    center = Offset(px, py)
                                )
                                drawCircle(
                                    color = TrekError,
                                    radius = 8.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            } else {
                                drawCircle(
                                    color = dotColor,
                                    radius = 6.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }

                            // Translucent GPS accuracy ring
                            val accuracyRadius = ((loc.accuracy / metersPerDegree) * baseScale).toFloat()
                            drawCircle(
                                color = dotColor.copy(alpha = 0.12f),
                                radius = accuracyRadius,
                                center = Offset(px, py)
                            )

                            drawContext.canvas.nativeCanvas.drawText(
                                "${loc.displayName} (${loc.altitude.toInt()}m)",
                                px,
                                py - 14.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = if (isSosActive) android.graphics.Color.RED else android.graphics.Color.WHITE
                                    textSize = 8.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                            )
                        }
                    }

                    // Static Rotating Compass Needle overlay in the Top Right Corner of Canvas
                    val compassCenter = Offset(size.width - 32.dp.toPx(), 90.dp.toPx())
                    drawCircle(
                        color = DarkCardBg.copy(alpha = 0.85f),
                        radius = 20.dp.toPx(),
                        center = compassCenter
                    )
                    drawCircle(
                        color = GlassBorder,
                        radius = 20.dp.toPx(),
                        center = compassCenter,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "N",
                        compassCenter.x,
                        compassCenter.y - 10.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.RED
                            textSize = 8.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                    withTransform({
                        rotate(rotationAngle, compassCenter)
                    }) {
                        drawLine(
                            color = Color.Red,
                            start = compassCenter,
                            end = Offset(compassCenter.x, compassCenter.y - 15.dp.toPx()),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = TrekWhite,
                            start = compassCenter,
                            end = Offset(compassCenter.x, compassCenter.y + 15.dp.toPx()),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
    }
}

// 3. Reliable Chat Screen
@Composable
fun ChatScreen(viewModel: TrekRoomViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val localDeviceId = viewModel.localDeviceId
    val typingPeers by viewModel.typingPeers.collectAsStateWithLifecycle()
    val fileTransfers by viewModel.fileTransfers.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var replyMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val context = viewModel.getApplication<Application>()
            val file = copyUriToCacheFile(context, it)
            if (file != null) {
                viewModel.shareFile(file, file.name, "IMAGE")
            }
        }
    }

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages else {
            messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search messages...", color = MutedText) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedText) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TrekWhite,
                unfocusedTextColor = TrekWhite,
                focusedBorderColor = DarkGreenPrimary,
                unfocusedBorderColor = DarkGreenSecondary
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
                            Text(msg.text, color = TrekWhite, fontSize = 14.sp)

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

        // Typing Status Overlay Indicator
        if (typingPeers.isNotEmpty()) {
            val typingText = if (typingPeers.size == 1) {
                "${typingPeers.values.first()} is typing..."
            } else {
                "${typingPeers.values.joinToString(", ")} are typing..."
            }
            Text(
                text = typingText,
                color = DarkGreenPrimary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { filePickerLauncher.launch("image/*") }) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = DarkGreenPrimary)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    viewModel.sendTypingStatus(it.isNotEmpty())
                },
                placeholder = { Text("Offline msg...", color = MutedText, fontSize = 12.sp) },
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
                onClick = {
                    if (inputText.trim().isNotEmpty()) {
                        viewModel.sendMessage(inputText.trim(), replyMessage?.messageId)
                        viewModel.sendTypingStatus(false)
                        inputText = ""
                        replyMessage = null
                    }
                },
                modifier = Modifier
                    .background(DarkGreenSecondary, CircleShape)
                    .size(40.dp)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = DarkGreenPrimary, modifier = Modifier.size(20.dp))
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TrekWhite, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("TRIGGER EMERGENCY SOS", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
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
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(peer.displayName, color = TrekWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                        Text("Device: ${peer.deviceId}", color = MutedText, fontSize = 11.sp)

                        if (isSelf) {
                            Text("Centered on your GPS coordinates", color = MutedText, fontSize = 11.sp)
                        } else if (loc != null && myLoc != null) {
                            val distance = calculateDistance(myLoc!!.latitude, myLoc!!.longitude, loc.latitude, loc.longitude)
                            Text(
                                text = "Dist: ${distance.toInt()} m | Alt: ${loc.altitude.toInt()}m | Bear: ${loc.bearing.toInt()}°",
                                color = DarkGreenPrimary,
                                fontSize = 11.sp
                            )
                            val age = (System.currentTimeMillis() - loc.timestamp) / 1000
                            Text("Last seen: ${age}s ago | Battery: ${peer.batteryLevel}%", color = MutedText, fontSize = 11.sp)
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
                    Text("Select Emergency Type to broadcast to all group members via multi-hop mesh relays:", color = TrekWhite, fontSize = 13.sp)
                    listOf("Injury", "Lost", "Wildlife Danger", "Severe Weather", "Medical", "Fall", "Fire").forEach { type ->
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
    val bandwidth by viewModel.estimatedBandwidthBps.collectAsStateWithLifecycle()
    val lossRate by viewModel.packetLossRate.collectAsStateWithLifecycle()
    val transportType by viewModel.estimatedTransport.collectAsStateWithLifecycle()
    
    val packetLogs by viewModel.packetLogs.collectAsStateWithLifecycle()
    val memberStats by viewModel.memberStats.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ad-Hoc Network Transport", color = DarkGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    HorizontalDivider(color = DarkGreenSecondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Advertising State", color = MutedText, fontSize = 11.sp)
                        Text(if (isAdv) "ACTIVE" else "INACTIVE", color = if (isAdv) DarkGreenPrimary else TrekError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discovery State", color = MutedText, fontSize = 11.sp)
                        Text(if (isDisc) "ACTIVE" else "INACTIVE", color = if (isDisc) DarkGreenPrimary else TrekError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimated Link Protocol", color = MutedText, fontSize = 11.sp)
                        Text(transportType, color = DarkGreenPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Average Ping RTT", color = MutedText, fontSize = 11.sp)
                        Text("${latency} ms", color = DarkGreenPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Throughput Bandwidth", color = MutedText, fontSize = 11.sp)
                        Text("${bandwidth / 8} B/s", color = DarkGreenPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Packet Loss Rate", color = MutedText, fontSize = 11.sp)
                        Text(String.format("%.2f %%", lossRate * 100), color = if (lossRate > 0.1f) TrekError else DarkGreenPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Session Packet Counters", color = DarkGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    HorizontalDivider(color = DarkGreenSecondary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Packets Dispatched", color = MutedText, fontSize = 11.sp)
                        Text(totalSent.toString(), color = TrekWhite, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Packets Captured", color = MutedText, fontSize = 11.sp)
                        Text(totalReceived.toString(), color = TrekWhite, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Mesh Relays Triggered", color = MutedText, fontSize = 11.sp)
                        Text(relays.toString(), color = DarkGreenPrimary, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Corrupt / Dropped Packets", color = MutedText, fontSize = 11.sp)
                        Text(dropped.toString(), color = if (dropped > 0) TrekError else TrekWhite, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Text("PEER PERFORMANCE METRICS", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (memberStats.isEmpty()) {
                Text("No peers tracked yet.", color = MutedText, fontSize = 11.sp)
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Callsign", color = DarkGreenPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                            Text("Sent", color = DarkGreenPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Text("Recv", color = DarkGreenPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Text("Batt", color = DarkGreenPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        }
                        HorizontalDivider(color = DarkGreenSecondary)
                        memberStats.forEach { stat ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stat.displayName, color = TrekWhite, fontSize = 11.sp, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stat.packetsSent.toString(), color = TrekWhite, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text(stat.packetsReceived.toString(), color = TrekWhite, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("${stat.batteryLevel}%", color = if (stat.batteryLevel < 25) TrekError else DarkGreenPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("MESH ROUTING AUDIT LOG", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (packetLogs.isEmpty()) {
                Text("No packet activity logged.", color = MutedText, fontSize = 11.sp)
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        packetLogs.take(6).forEach { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val directionIcon = if (log.direction == "SENT") "↗" else if (log.direction == "RECEIVED") "↘" else "↔"
                                val dirColor = if (log.direction == "SENT") DarkGreenPrimary else if (log.direction == "RECEIVED") TrekWhite else TrekWarning
                                Text(
                                    text = "$directionIcon ${log.type}",
                                    color = dirColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(2f)
                                )
                                Text(
                                    text = "Sender: ${log.senderId.takeLast(4)}",
                                    color = MutedText,
                                    fontSize = 10.sp,
                                    modifier = Modifier.weight(1.5f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Hops: ${log.hopCount} | Size: ${log.payloadSize}B",
                                    color = MutedText,
                                    fontSize = 10.sp,
                                    modifier = Modifier.weight(1.8f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("SYSTEM DEBUG LOGGER", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color.Black)
                    .border(1.dp, DarkGreenSecondary)
                    .padding(8.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { logLine ->
                        Text(logLine, color = DarkGreenPrimary, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
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

