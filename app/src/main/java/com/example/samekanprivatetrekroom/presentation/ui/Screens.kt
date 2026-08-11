package com.example.samekanprivatetrekroom.presentation.ui

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import android.content.Context
import java.util.UUID
import androidx.compose.foundation.BorderStroke
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samekanprivatetrekroom.data.local.LocationEntity
import com.example.samekanprivatetrekroom.data.local.MessageEntity
import com.example.samekanprivatetrekroom.data.local.RoomEntity
import com.example.samekanprivatetrekroom.domain.model.Peer
import com.example.samekanprivatetrekroom.location.QrCodeAnalyzer
import com.example.samekanprivatetrekroom.location.QrCodeGenerator
import com.example.samekanprivatetrekroom.presentation.viewmodel.TrekRoomViewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Curated Dark Trek Theme Palette
val TrekBg = Color(0xFF0F1512)
val TrekSurface = Color(0xFF16201B)
val TrekCardBg = Color(0xFF1E2B25)
val TrekPrimary = Color(0xFF00FF66)
val TrekSecondary = Color(0xFF4A6B56)
val TrekText = Color(0xFFE2EBE6)
val TrekMuted = Color(0xFF90A398)

val ColorStale = Color(0xFFFFCC00)
val ColorDisconnected = Color(0xFFFF4F4F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: TrekRoomViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TrekBg
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = "Bluetooth Required",
            tint = TrekPrimary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permissions Required",
            color = TrekText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Samekan Private Trek Room requires local connectivity permissions to function offline:\n\n✓ Bluetooth (Scan, Advertise, Connect)\n✓ Location Access (GPS Tracking)\n✓ Camera Access (QR Room Sync)",
            color = TrekMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = TrekPrimary, contentColor = TrekBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("GRANT PERMISSIONS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = "SAMEKAN",
            color = TrekPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Text(
            text = "Private Trek Room",
            color = TrekText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(30.dp))

        // Device Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TrekSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, TrekSecondary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("LOCAL PEER PROFILE", color = TrekMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(localDisplayName, color = TrekText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Device ID: $localDeviceId", color = TrekMuted, fontSize = 13.sp)
                }
                IconButton(
                    onClick = { showEditProfile = true },
                    modifier = Modifier.background(TrekCardBg, CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile", tint = TrekPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Main Actions
        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TrekPrimary, contentColor = TrekBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("CREATE NEW ROOM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showJoinDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                border = BorderStroke(1.5.dp, TrekPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TrekPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("MANUAL JOIN", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { showScanner = true },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TrekSecondary, contentColor = TrekText),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("SCAN CODE", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Operates 100% offline via local ad-hoc P2P network",
            color = TrekMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    // Dialogs
    if (showEditProfile) {
        var tempName by remember { mutableStateOf(localDisplayName) }
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            containerColor = TrekSurface,
            titleContentColor = TrekText,
            title = { Text("Edit Display Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Display Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TrekText,
                        unfocusedTextColor = TrekText,
                        focusedBorderColor = TrekPrimary,
                        unfocusedBorderColor = TrekSecondary,
                        focusedLabelColor = TrekPrimary
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
                    colors = ButtonDefaults.buttonColors(containerColor = TrekPrimary, contentColor = TrekBg)
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }, colors = ButtonDefaults.textButtonColors(contentColor = TrekMuted)) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showCreateDialog) {
        var roomName by remember { mutableStateOf("") }
        val randomRoomId = remember { "T-ROOM-" + UUID.randomUUID().toString().substring(0, 4).uppercase() }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = TrekSurface,
            titleContentColor = TrekText,
            title = { Text("Create Trek Room", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Room ID: $randomRoomId", color = TrekPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it },
                        label = { Text("Room Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekText,
                            unfocusedTextColor = TrekText,
                            focusedBorderColor = TrekPrimary,
                            unfocusedBorderColor = TrekSecondary,
                            focusedLabelColor = TrekPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roomName.trim().isNotEmpty()) {
                            viewModel.createRoom(roomName.trim(), randomRoomId)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrekPrimary, contentColor = TrekBg)
                ) {
                    Text("CREATE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = TrekMuted)) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showJoinDialog) {
        var inputRoomId by remember { mutableStateOf("") }
        var inputRoomName by remember { mutableStateOf("") }

        // Auto fill if scanned via QR
        LaunchedEffect(scannerResult) {
            if (scannerResult.isNotEmpty()) {
                try {
                    val data = Gson().fromJson(scannerResult, RoomQrData::class.java)
                    inputRoomId = data.roomId
                    inputRoomName = data.roomName
                } catch (e: Exception) {
                    // Try parsing as simple string
                    inputRoomId = scannerResult
                }
                scannerResult = ""
            }
        }

        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = TrekSurface,
            titleContentColor = TrekText,
            title = { Text("Join Trek Room", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputRoomId,
                        onValueChange = { inputRoomId = it },
                        label = { Text("Room ID (e.g. T-ROOM-A8F1)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekText,
                            unfocusedTextColor = TrekText,
                            focusedBorderColor = TrekPrimary,
                            unfocusedBorderColor = TrekSecondary,
                            focusedLabelColor = TrekPrimary
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputRoomName,
                        onValueChange = { inputRoomName = it },
                        label = { Text("Room Name (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TrekText,
                            unfocusedTextColor = TrekText,
                            focusedBorderColor = TrekPrimary,
                            unfocusedBorderColor = TrekSecondary,
                            focusedLabelColor = TrekPrimary
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
                            viewModel.joinRoom(name, inputRoomId.trim().uppercase())
                            showJoinDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrekPrimary, contentColor = TrekBg)
                ) {
                    Text("JOIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = TrekMuted)) {
                    Text("CANCEL")
                }
            }
        )
    }
}

data class RoomQrData(val roomId: String, val roomName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekRoomDashboard(viewModel: TrekRoomViewModel, room: RoomEntity) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()

    // Handle system back press
    BackHandler {
        viewModel.leaveRoom()
    }

    // Connection Authorization popup
    if (pendingRequests.isNotEmpty()) {
        val request = pendingRequests.values.first()
        Dialog(onDismissRequest = { viewModel.rejectConnection(request.endpointId) }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TrekSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TrekPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = TrekPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connection Request",
                        color = TrekText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Device '${request.displayName}' is attempting to link.",
                        color = TrekMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("AUTHENTICATION CODE", color = TrekMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = request.authenticationDigits,
                        color = TrekPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Verify that this code matches on both screens before accepting.",
                        color = TrekMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.rejectConnection(request.endpointId) },
                            colors = ButtonDefaults.buttonColors(containerColor = TrekCardBg, contentColor = ColorDisconnected),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("REJECT", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { viewModel.acceptConnection(request.endpointId) },
                            colors = ButtonDefaults.buttonColors(containerColor = TrekPrimary, contentColor = TrekBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ACCEPT", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(room.roomName, color = TrekText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Room ID: ${room.roomId}", color = TrekMuted, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.leaveRoom() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Leave Room", tint = TrekText)
                    }
                },
                actions = {
                    val context = LocalContext.current
                    var showQr by remember { mutableStateOf(false) }

                    // Check Internet Connectivity
                    val connectivityManager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
                    var isOnline by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        while (true) {
                            val activeNet = connectivityManager.activeNetwork
                            val caps = connectivityManager.getNetworkCapabilities(activeNet)
                            isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                            delay(3000)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            color = if (isOnline) TrekPrimary else ColorDisconnected,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .border(1.dp, if (isOnline) TrekPrimary else ColorDisconnected, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(onClick = { showQr = true }) {
                            Icon(Icons.Default.QrCode, contentDescription = "Share Room QR", tint = TrekPrimary)
                        }
                    }

                    if (showQr) {
                        val qrContent = Gson().toJson(RoomQrData(room.roomId, room.roomName))
                        val qrBitmap = remember { QrCodeGenerator.generateQrCode(qrContent) }
                        Dialog(onDismissRequest = { showQr = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TrekSurface),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(room.roomName, color = TrekText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Room QR Code",
                                            modifier = Modifier.size(240.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Scan to Join Trek Room", color = TrekMuted, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextButton(onClick = { showQr = false }, colors = ButtonDefaults.textButtonColors(contentColor = TrekPrimary)) {
                                        Text("CLOSE")
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TrekSurface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = TrekSurface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Radar, contentDescription = null) },
                    label = { Text("Radar") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TrekPrimary,
                        unselectedIconColor = TrekMuted,
                        selectedTextColor = TrekPrimary,
                        unselectedTextColor = TrekMuted,
                        indicatorColor = TrekCardBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    label = { Text("Chat") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TrekPrimary,
                        unselectedIconColor = TrekMuted,
                        selectedTextColor = TrekPrimary,
                        unselectedTextColor = TrekMuted,
                        indicatorColor = TrekCardBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("Members") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TrekPrimary,
                        unselectedIconColor = TrekMuted,
                        selectedTextColor = TrekPrimary,
                        unselectedTextColor = TrekMuted,
                        indicatorColor = TrekCardBg
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Debug") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TrekPrimary,
                        unselectedIconColor = TrekMuted,
                        selectedTextColor = TrekPrimary,
                        unselectedTextColor = TrekMuted,
                        indicatorColor = TrekCardBg
                    )
                )
            }
        },
        containerColor = TrekBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> RadarScreen(viewModel)
                1 -> ChatScreen(viewModel)
                2 -> MembersScreen(viewModel)
                3 -> DebugScreen(viewModel)
            }
        }
    }
}

// 1. Radar Screen (Trek Compass / Concentric Plotting)
@Composable
fun RadarScreen(viewModel: TrekRoomViewModel) {
    val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()
    val locations by viewModel.memberLocations.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val myDeviceId = viewModel.localDeviceId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TREK RADAR", color = TrekMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("North represents top of screen", color = TrekSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (myLoc == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Awaiting GPS Fix...\nEnsure device location (GPS) is turned ON.",
                    color = TrekMuted,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .background(TrekSurface, CircleShape)
                    .border(2.dp, TrekSecondary.copy(alpha = 0.5f), CircleShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.width / 2 - 16.dp.toPx()

                    // Draw concentric rings
                    val rings = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
                    val ringDistances = listOf("20m", "50m", "100m", "150m", "250m")

                    rings.forEachIndexed { index, ratio ->
                        val radius = maxRadius * ratio
                        drawCircle(
                            color = TrekSecondary.copy(alpha = 0.2f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        // Label distances along East axis
                        drawContext.canvas.nativeCanvas.drawText(
                            ringDistances[index],
                            center.x + radius - 15.dp.toPx(),
                            center.y - 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 10.sp.toPx()
                                textAlign = android.graphics.Paint.Align.RIGHT
                            }
                        )
                    }

                    // Plot Self at center (Lime Dot)
                    drawCircle(
                        color = TrekPrimary,
                        radius = 8.dp.toPx(),
                        center = center
                    )

                    // Plot members relative to self
                    val myLatitude = myLoc!!.latitude
                    val myLongitude = myLoc!!.longitude

                    locations.forEach { loc ->
                        if (loc.deviceId != myDeviceId) {
                            // Check peer connection state
                            val peer = peers.find { it.deviceId == loc.deviceId }
                            val isConnected = peer?.connected == true
                            val isStale = (System.currentTimeMillis() - loc.timestamp) > 30000

                            val dotColor = when {
                                !isConnected -> ColorDisconnected
                                isStale -> ColorStale
                                else -> TrekPrimary
                            }

                            // Calculate local offset using flat earth approximation
                            val dy = (loc.latitude - myLatitude) * 111139.0 // meters
                            val dx = (loc.longitude - myLongitude) * 111139.0 * Math.cos(Math.toRadians(myLatitude)) // meters

                            // Scale factors (Max range mapped to 250m)
                            val maxRange = 250.0 // meters
                            val distance = Math.sqrt(dx * dx + dy * dy)

                            val scale = Math.min(1.0, distance / maxRange)
                            val angle = Math.atan2(dx, dy) // 0 is North (up), East is right

                            val plotX = center.x + (Math.sin(angle) * scale * maxRadius).toFloat()
                            val plotY = center.y - (Math.cos(angle) * scale * maxRadius).toFloat()

                            // Draw member dot
                            drawCircle(
                                color = dotColor,
                                radius = 6.dp.toPx(),
                                center = Offset(plotX, plotY)
                            )

                            // Label member name
                            drawContext.canvas.nativeCanvas.drawText(
                                loc.displayName,
                                plotX,
                                plotY - 8.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 11.sp.toPx()
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

        // Compass Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(TrekPrimary, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Connected", color = TrekText, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(ColorStale, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stale (>30s)", color = TrekText, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(ColorDisconnected, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Disconnected", color = TrekText, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

// 2. Chat Screen
@Composable
fun ChatScreen(viewModel: TrekRoomViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val localDeviceId = viewModel.localDeviceId

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                val isSelf = msg.senderDeviceId == localDeviceId
                val alignment = if (isSelf) Alignment.End else Alignment.Start
                val cardColor = if (isSelf) TrekSecondary else TrekCardBg

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignment
                ) {
                    Text(
                        text = if (isSelf) "You" else msg.senderDisplayName,
                        color = TrekMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isSelf) 12.dp else 0.dp,
                                    bottomEnd = if (isSelf) 0.dp else 12.dp
                                )
                            )
                            .background(cardColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(msg.text, color = TrekText, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                val timeString = remember(msg.timestamp) {
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                }
                                Text(timeString, color = TrekMuted.copy(alpha = 0.8f), fontSize = 9.sp)
                                if (isSelf) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = when (msg.deliveryStatus) {
                                            "SENDING" -> Icons.Default.Pending
                                            "SENT" -> Icons.Default.Done
                                            else -> Icons.Default.Error
                                        },
                                        contentDescription = msg.deliveryStatus,
                                        tint = if (msg.deliveryStatus == "FAILED") ColorDisconnected else TrekPrimary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Text input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrekSurface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Send offline message...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TrekText,
                    unfocusedTextColor = TrekText,
                    focusedBorderColor = TrekPrimary,
                    unfocusedBorderColor = TrekSecondary,
                    focusedPlaceholderColor = TrekMuted,
                    unfocusedPlaceholderColor = TrekMuted
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            IconButton(
                onClick = {
                    if (inputText.trim().isNotEmpty()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .background(TrekPrimary, CircleShape)
                    .size(46.dp)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = TrekBg)
            }
        }
    }
}

// 3. Members List Screen
@Composable
fun MembersScreen(viewModel: TrekRoomViewModel) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val locations by viewModel.memberLocations.collectAsStateWithLifecycle()
    val myLoc by viewModel.localLocation.collectAsStateWithLifecycle()
    val myDeviceId = viewModel.localDeviceId

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "${peers.size} MEMBERS IN ROOM",
                color = TrekMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(peers) { peer ->
            val loc = locations.find { it.deviceId == peer.deviceId }
            val isSelf = peer.deviceId == myDeviceId

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TrekSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (peer.connected) TrekPrimary.copy(alpha = 0.2f) else TrekSecondary.copy(alpha = 0.1f))
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
                            Text(
                                text = peer.displayName,
                                color = TrekText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSelf) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "YOU",
                                    color = TrekBg,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(TrekPrimary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text("Device: ${peer.deviceId}", color = TrekMuted, fontSize = 12.sp)

                        // Distance calculation using local FusedLocation coordinates
                        if (isSelf) {
                            Text("Centered at your GPS coordinates", color = TrekSecondary, fontSize = 12.sp)
                        } else if (loc != null && myLoc != null) {
                            val distance = calculateDistance(myLoc!!.latitude, myLoc!!.longitude, loc.latitude, loc.longitude)
                            val distanceCategory = getDistanceCategory(distance)
                            Text(
                                text = "${distance.toInt()} m away ($distanceCategory)",
                                color = TrekPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text("Distance: Awaiting coordinates", color = TrekMuted, fontSize = 12.sp)
                        }

                        // Last update tracker
                        if (loc != null) {
                            val ageSeconds = (System.currentTimeMillis() - loc.timestamp) / 1000
                            val ageText = if (ageSeconds < 60) "$ageSeconds sec ago" else "${ageSeconds / 60} min ago"
                            Text("Last GPS update: $ageText", color = TrekMuted, fontSize = 11.sp)
                        }
                    }

                    // Member Status Dot
                    val (statusColor, statusLabel) = when {
                        isSelf -> Pair(TrekPrimary, "🟢 CONNECTED")
                        !peer.connected -> Pair(ColorDisconnected, "🔴 DISCONNECTED")
                        loc != null && (System.currentTimeMillis() - loc.timestamp) > 30000 -> Pair(ColorStale, "🟡 STALE")
                        else -> Pair(TrekPrimary, "🟢 CONNECTED")
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(statusLabel, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

fun getDistanceCategory(distanceMeters: Double): String {
    return when {
        distanceMeters < 15 -> "Nearby"
        distanceMeters in 15.0..50.0 -> "Close"
        distanceMeters in 50.0..100.0 -> "Moderate"
        else -> "Far"
    }
}

// 4. Debug Screen
@Composable
fun DebugScreen(viewModel: TrekRoomViewModel) {
    val isAdvertising by viewModel.isAdvertising.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()
    val gpsInterval by viewModel.prefs.getGpsIntervalSeconds().let { remember { mutableStateOf(it) } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("CONNECTION DEBUG", color = TrekMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TrekSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local Details", color = TrekPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Divider(color = TrekSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                    Text("Device ID: ${viewModel.localDeviceId}", color = TrekText, fontSize = 13.sp)
                    Text("Room Name: ${currentRoom?.roomName ?: "None"}", color = TrekText, fontSize = 13.sp)
                    Text("Room ID: ${currentRoom?.roomId ?: "None"}", color = TrekText, fontSize = 13.sp)
                    Text("Strategy: P2P_CLUSTER", color = TrekText, fontSize = 13.sp)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TrekSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nearby Status Flags", color = TrekPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Divider(color = TrekSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Advertising Active", color = TrekText, fontSize = 13.sp)
                        Text(if (isAdvertising) "ON" else "OFF", color = if (isAdvertising) TrekPrimary else ColorDisconnected, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discovery Active", color = TrekText, fontSize = 13.sp)
                        Text(if (isDiscovering) "ON" else "OFF", color = if (isDiscovering) TrekPrimary else ColorDisconnected, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TrekSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("GPS Shared Settings", color = TrekPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Divider(color = TrekSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

                    var interval by remember { mutableStateOf(gpsInterval) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Interval: ${interval}s", color = TrekText, fontSize = 13.sp)
                        Row {
                            IconButton(onClick = { if (interval > 5) { interval -= 5; viewModel.updateGpsInterval(interval) } }) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = TrekPrimary)
                            }
                            IconButton(onClick = { if (interval < 60) { interval += 5; viewModel.updateGpsInterval(interval) } }) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = TrekPrimary)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("RAW CONNECTED ENDPOINTS (${peers.filter { it.connected }.size})", color = TrekMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        items(peers.filter { it.connected }) { peer ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TrekCardBg)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Name: ${peer.displayName}", color = TrekText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Device ID: ${peer.deviceId}", color = TrekMuted, fontSize = 12.sp)
                    Text("Endpoint ID: ${peer.endpointId}", color = TrekMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

// 5. QR Code Camera Scanner
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

        // Scanner viewfinder overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Scan Samekan Room QR",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .border(3.dp, TrekPrimary, RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = TrekSurface, contentColor = TrekText),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CANCEL")
                }
            }
        }
    }
}
