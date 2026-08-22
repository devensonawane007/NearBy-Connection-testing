package com.example.samekanprivatetrekroom.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.samekanprivatetrekroom.data.audio.PTTManager
import com.example.samekanprivatetrekroom.data.local.*
import com.example.samekanprivatetrekroom.data.nearby.NearbyConnectionManager
import com.example.samekanprivatetrekroom.domain.model.*
import com.example.samekanprivatetrekroom.domain.serializer.PacketSerializer
import com.example.samekanprivatetrekroom.location.GpsManager
import com.example.samekanprivatetrekroom.location.GpsStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TrekRoomViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "TrekRoomViewModel"
        private const val CHUNK_SIZE = 32768 // 32KB
    }

    private val db = AppDatabase.getDatabase(application)
    private val roomDao = db.roomDao()
    private val memberDao = db.memberDao()
    private val messageDao = db.messageDao()
    private val locationDao = db.locationDao()
    private val locationHistoryDao = db.locationHistoryDao()
    private val fileTransferDao = db.fileTransferDao()
    private val sosHistoryDao = db.sosHistoryDao()
    private val voiceHistoryDao = db.voiceHistoryDao()
    private val packetLogDao = db.packetLogDao()
    private val diagnosticsDao = db.diagnosticsDao()
    private val memberStatsDao = db.memberStatsDao()
    private val batteryHistoryDao = db.batteryHistoryDao()

    val prefs = PreferenceHelper(application)
    val permissionManager = PermissionManager(application)

    // Push To Talk Manager
    val pttManager = PTTManager(application)

    // Bind logs directly to Logger flow
    val logs: StateFlow<List<String>> = Logger.logsFlow

    // Active SOS states
    private val _activeSosAlert = MutableStateFlow<SosAlertInfo?>(null)
    val activeSosAlert: StateFlow<SosAlertInfo?> = _activeSosAlert.asStateFlow()

    // Distance Walked (Accumulated)
    private val _distanceWalked = MutableStateFlow(0f)
    val distanceWalked: StateFlow<Float> = _distanceWalked.asStateFlow()
    private var lastWalkedLocation: Location? = null

    // Hardware status flows
    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _gpsStatus = MutableStateFlow<GpsStatus>(GpsStatus.Idle)
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    val hasPermissions: StateFlow<Boolean> = permissionManager.hasAllRequiredPermissions

    // Vibration and Alarm player for SOS
    private var ringtonePlayer: Ringtone? = null
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Nearby Connection Manager
    private val nearbyConnectionManager = NearbyConnectionManager(
        context = application,
        localDeviceId = prefs.getDeviceId(),
        localDisplayNameProvider = { prefs.getDisplayName() }
    )

    // GPS Manager
    private var gpsManager: GpsManager? = null

    private val _localLocation = MutableStateFlow<Location?>(null)
    val localLocation: StateFlow<Location?> = _localLocation.asStateFlow()

    val localDeviceId = prefs.getDeviceId()
    val localDisplayName = MutableStateFlow(prefs.getDisplayName())

    val currentRoom: StateFlow<RoomEntity?> = roomDao.getRoomFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isAdvertising = nearbyConnectionManager.isAdvertising
    val isDiscovering = nearbyConnectionManager.isDiscovering
    val pendingRequests = nearbyConnectionManager.pendingRequests

    // Diagnostics stats from Nearby Connection Manager
    val totalPacketsSent = nearbyConnectionManager.totalPacketsSent
    val totalPacketsReceived = nearbyConnectionManager.totalPacketsReceived
    val droppedPackets = nearbyConnectionManager.droppedPackets
    val totalRelays = nearbyConnectionManager.totalRelays
    val averageLatencyMs = nearbyConnectionManager.averageLatencyMs
    val packetLossRate = nearbyConnectionManager.packetLossRate
    val estimatedBandwidthBps = nearbyConnectionManager.estimatedBandwidthBps
    val estimatedTransport = nearbyConnectionManager.estimatedTransport

    // Room DB Live lists
    val packetLogs: StateFlow<List<PacketLogEntity>> = packetLogDao.getPacketLogsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sosHistory: StateFlow<List<SosHistoryEntity>> = sosHistoryDao.getSosHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val voiceHistory: StateFlow<List<VoiceHistoryEntity>> = voiceHistoryDao.getVoiceHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val diagnostics: StateFlow<List<DiagnosticsEntity>> = diagnosticsDao.getDiagnosticsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val memberStats: StateFlow<List<MemberStatsEntity>> = memberStatsDao.getMemberStatsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Typing state of active peers: map of deviceId -> displayName
    private val _typingPeers = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingPeers: StateFlow<Map<String, String>> = _typingPeers.asStateFlow()
    private val typingTimestamps = ConcurrentHashMap<String, Long>()

    // File transfer active jobs
    private val fileSendingJobs = ConcurrentHashMap<String, Job>()

    // Combine Room DB members with active Nearby connection state
    val peers: StateFlow<List<Peer>> = combine(
        memberDao.getMembersFlow(),
        nearbyConnectionManager.connectedPeers
    ) { dbMembers, nearbyPeers ->
        dbMembers.map { dbMember ->
            val nearbyPeer = nearbyPeers.values.find { it.deviceId == dbMember.deviceId }
            Peer(
                endpointId = nearbyPeer?.endpointId ?: "",
                deviceId = dbMember.deviceId,
                displayName = dbMember.displayName,
                roomId = nearbyPeer?.roomId ?: "",
                connected = nearbyPeer?.isConnected == true || dbMember.deviceId == localDeviceId,
                lastSeen = dbMember.lastSeen,
                role = dbMember.role,
                rssi = nearbyPeer?.rssi,
                batteryLevel = dbMember.batteryLevel,
                latencyMs = nearbyPeer?.latencyMs ?: 0L
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val chatMessages: StateFlow<List<MessageEntity>> = currentRoom.flatMapLatest { room ->
        if (room != null) {
            messageDao.getMessagesFlow(room.roomId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val memberLocations: StateFlow<List<LocationEntity>> = locationDao.getLocationsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trailPoints: StateFlow<List<LocationHistoryEntity>> = locationHistoryDao.getAllTrailsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val fileTransfers: StateFlow<List<FileTransferEntity>> = fileTransferDao.getTransfersFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class SosAlertInfo(
        val messageId: String,
        val senderId: String,
        val senderName: String,
        val emergencyType: String,
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val accuracy: Float,
        val batteryLevel: Int,
        val timestamp: Long,
        val acknowledged: Boolean = false
    )

    init {
        Logger.info(TAG, "TrekRoomViewModel initialized with DeviceID: $localDeviceId")
        checkBluetoothStatus()

        // Set up nearby packet receiver
        nearbyConnectionManager.onPacketReceivedListener = { packet ->
            logPacketTraffic(packet, "RECEIVED")
            handleReceivedPacket(packet)
        }

        // Set up peer disconnect listener
        nearbyConnectionManager.onPeerDisconnectedListener = { deviceId ->
            Logger.info(TAG, "Peer disconnected in database update: $deviceId")
            viewModelScope.launch(Dispatchers.IO) {
                memberDao.updateConnectionStatus(deviceId, false)
            }
        }

        // Set up reliable packet ACK listener
        nearbyConnectionManager.onPacketAckedListener = { ackedId ->
            viewModelScope.launch(Dispatchers.IO) {
                if (ackedId.startsWith("MSG-")) {
                    messageDao.updateMessageStatus(ackedId, "SENT")
                } else if (ackedId.startsWith("FC-")) {
                    // Ack of file chunk. Handled if selective repeat is active.
                }
            }
        }

        nearbyConnectionManager.onPacketFailedListener = { failedPacket ->
            viewModelScope.launch(Dispatchers.IO) {
                if (failedPacket.messageId.startsWith("MSG-")) {
                    messageDao.updateMessageStatus(failedPacket.messageId, "FAILED")
                }
            }
        }

        // Setup PTT recorded chunk callback with packet sequencing
        pttManager.setOnChunkRecordedListener { compressedAudio, seq ->
            val room = currentRoom.value
            if (room != null) {
                val packet = SamekanPacket(
                    messageId = "PTT-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                    roomId = room.roomId,
                    senderDeviceId = localDeviceId,
                    senderDisplayName = localDisplayName.value,
                    type = PacketType.PTT_CHUNK,
                    timestamp = System.currentTimeMillis(),
                    ttl = 1,
                    payload = android.util.Base64.encodeToString(compressedAudio, android.util.Base64.NO_WRAP),
                    sequenceNumber = seq,
                    priority = 1
                )
                logPacketTraffic(packet, "SENT")
                nearbyConnectionManager.broadcastPacket(packet)
            }
        }

        // Start ping loop to compute diagnostics latency
        viewModelScope.launch {
            while (true) {
                val room = currentRoom.value
                if (room != null && peers.value.any { it.connected && it.deviceId != localDeviceId }) {
                    nearbyConnectionManager.sendPing(room.roomId)
                }
                delay(10000)
            }
        }

        // Diagnostics logging and battery history scheduler
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(15000)
                val room = currentRoom.value
                if (room != null) {
                    val batt = getBatteryLevel()
                    batteryHistoryDao.insertBatteryPoint(
                        BatteryHistoryEntity(
                            deviceId = localDeviceId,
                            batteryLevel = batt,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    
                    val activeList = mutableListOf<String>()
                    if (isAdvertising.value) activeList.add("Advertising")
                    if (isDiscovering.value) activeList.add("Discovery")
                    if (pttManager.isRecordingFlow.value) activeList.add("PTT Mic")
                    
                    diagnosticsDao.insertDiagnostics(
                        DiagnosticsEntity(
                            timestamp = System.currentTimeMillis(),
                            connectedPeersCount = peers.value.count { it.connected && it.deviceId != localDeviceId },
                            avgLatencyMs = averageLatencyMs.value,
                            packetLossRate = packetLossRate.value,
                            bandwidthBps = estimatedBandwidthBps.value,
                            batteryLevel = batt,
                            activeTransports = activeList.joinToString(", ")
                        )
                    )
                }
            }
        }

        // Typing indicator cleanup task
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val currentTyping = _typingPeers.value.toMutableMap()
                var changed = false
                typingTimestamps.forEach { (deviceId, timestamp) ->
                    if (now - timestamp > 3000) {
                        currentTyping.remove(deviceId)
                        typingTimestamps.remove(deviceId)
                        changed = true
                    }
                }
                if (changed) {
                    _typingPeers.value = currentTyping
                }
            }
        }

        // Auto restart nearby network if database indicates we are in a room and permissions are met
        viewModelScope.launch(Dispatchers.IO) {
            val activeRoom = roomDao.getRoomSync()
            if (activeRoom != null && permissionManager.checkAllRequiredPermissionsGranted()) {
                val pass = prefs.getRoomPassword(activeRoom.roomId)
                PacketSerializer.setRoomPassword(pass)
                
                launch(Dispatchers.Main) {
                    startNearbyAndGps(activeRoom.roomId)
                }
            }
        }
    }

    private fun logPacketTraffic(packet: SamekanPacket, direction: String) {
        viewModelScope.launch(Dispatchers.IO) {
            packetLogDao.insertPacketLog(
                PacketLogEntity(
                    packetId = packet.messageId,
                    roomId = packet.roomId,
                    senderId = packet.senderDeviceId,
                    type = packet.type.name,
                    direction = direction,
                    payloadSize = packet.payload.length,
                    hopCount = packet.hopCount,
                    timestamp = System.currentTimeMillis()
                )
            )

            // Update stats
            val existing = memberStatsDao.getStatsForMember(packet.senderDeviceId)
            if (existing != null) {
                val sentInc = if (direction == "SENT") 1 else 0
                val recInc = if (direction == "RECEIVED") 1 else 0
                memberStatsDao.insertMemberStats(
                    existing.copy(
                        packetsSent = existing.packetsSent + sentInc,
                        packetsReceived = existing.packetsReceived + recInc,
                        lastSeen = System.currentTimeMillis()
                    )
                )
            } else {
                memberStatsDao.insertMemberStats(
                    MemberStatsEntity(
                        deviceId = packet.senderDeviceId,
                        displayName = packet.senderDisplayName,
                        packetsSent = if (direction == "SENT") 1 else 0,
                        packetsReceived = if (direction == "RECEIVED") 1 else 0,
                        lastSeen = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun checkBluetoothStatus() {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        _isBluetoothEnabled.value = adapter?.isEnabled == true
    }

    fun updatePermissionStatus() {
        permissionManager.updatePermissionStatus()
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateDisplayName(name: String) {
        prefs.setDisplayName(name)
        localDisplayName.value = name
        syncRoomMembers()
    }

    fun updateGpsInterval(seconds: Int) {
        prefs.setGpsIntervalSeconds(seconds)
        gpsManager?.updateInterval(seconds)
    }

    fun createRoom(roomName: String, roomId: String, description: String = "", password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            roomDao.clearRoom()
            memberDao.clearMembers()
            messageDao.clearMessages()
            locationDao.clearLocations()
            locationHistoryDao.clearAllTrails()
            fileTransferDao.clearTransfers()
            sosHistoryDao.clearSosHistory()
            voiceHistoryDao.clearVoiceHistory()
            packetLogDao.clearPacketLogs()
            diagnosticsDao.clearDiagnostics()
            memberStatsDao.clearMemberStats()
            batteryHistoryDao.clearBatteryHistory()

            val passHash = if (!password.isNullOrBlank()) {
                android.util.Base64.encodeToString(password.toByteArray(), android.util.Base64.NO_WRAP)
            } else null

            val room = RoomEntity(
                roomId = roomId,
                roomName = roomName,
                description = description,
                passwordHash = passHash,
                creatorDeviceId = localDeviceId,
                hostDeviceId = localDeviceId,
                createdAt = System.currentTimeMillis(),
                status = "ACTIVE"
            )
            roomDao.insertRoom(room)
            prefs.setRoomPassword(roomId, password)
            PacketSerializer.setRoomPassword(password)

            memberDao.insertMember(
                MemberEntity(
                    deviceId = localDeviceId,
                    displayName = localDisplayName.value,
                    connected = true,
                    lastSeen = System.currentTimeMillis(),
                    role = "HOST"
                )
            )

            launch(Dispatchers.Main) {
                if (permissionManager.checkAllRequiredPermissionsGranted()) {
                    startNearbyAndGps(roomId)
                }
            }
        }
    }

    fun joinRoom(roomName: String, roomId: String, description: String = "", password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            roomDao.clearRoom()
            memberDao.clearMembers()
            messageDao.clearMessages()
            locationDao.clearLocations()
            locationHistoryDao.clearAllTrails()
            fileTransferDao.clearTransfers()
            sosHistoryDao.clearSosHistory()
            voiceHistoryDao.clearVoiceHistory()
            packetLogDao.clearPacketLogs()
            diagnosticsDao.clearDiagnostics()
            memberStatsDao.clearMemberStats()
            batteryHistoryDao.clearBatteryHistory()

            val passHash = if (!password.isNullOrBlank()) {
                android.util.Base64.encodeToString(password.toByteArray(), android.util.Base64.NO_WRAP)
            } else null

            val room = RoomEntity(
                roomId = roomId,
                roomName = roomName,
                description = description,
                passwordHash = passHash,
                creatorDeviceId = "",
                hostDeviceId = "",
                createdAt = System.currentTimeMillis(),
                status = "ACTIVE"
            )
            roomDao.insertRoom(room)
            prefs.setRoomPassword(roomId, password)
            PacketSerializer.setRoomPassword(password)

            memberDao.insertMember(
                MemberEntity(
                    deviceId = localDeviceId,
                    displayName = localDisplayName.value,
                    connected = true,
                    lastSeen = System.currentTimeMillis(),
                    role = "MEMBER"
                )
            )

            launch(Dispatchers.Main) {
                if (permissionManager.checkAllRequiredPermissionsGranted()) {
                    startNearbyAndGps(roomId)
                }
            }
        }
    }

    fun leaveRoom() {
        viewModelScope.launch(Dispatchers.IO) {
            stopNearbyAndGps()
            roomDao.clearRoom()
            memberDao.clearMembers()
            messageDao.clearMessages()
            locationDao.clearLocations()
            locationHistoryDao.clearAllTrails()
            fileTransferDao.clearTransfers()
            sosHistoryDao.clearSosHistory()
            voiceHistoryDao.clearVoiceHistory()
            packetLogDao.clearPacketLogs()
            diagnosticsDao.clearDiagnostics()
            memberStatsDao.clearMemberStats()
            batteryHistoryDao.clearBatteryHistory()
            PacketSerializer.clearRoomKey()
            _distanceWalked.value = 0f
            lastWalkedLocation = null
            _activeSosAlert.value = null
            stopSosAlertResources()
        }
    }

    fun kickMember(deviceId: String) {
        val room = currentRoom.value ?: return
        if (room.hostDeviceId != localDeviceId) return

        viewModelScope.launch(Dispatchers.IO) {
            memberDao.deleteMember(deviceId)
            syncRoomMembers()
        }
    }

    fun transferHost(newHostId: String) {
        val room = currentRoom.value ?: return
        if (room.hostDeviceId != localDeviceId) return

        viewModelScope.launch(Dispatchers.IO) {
            roomDao.updateHost(room.roomId, newHostId)
            memberDao.insertMember(
                memberDao.getMemberById(localDeviceId)!!.copy(role = "MEMBER")
            )
            memberDao.insertMember(
                memberDao.getMemberById(newHostId)!!.copy(role = "HOST")
            )
            syncRoomMembers()
        }
    }

    fun startNearbyAndGps(roomId: String) {
        if (!permissionManager.checkAllRequiredPermissionsGranted()) {
            Logger.warn(TAG, "Permissions not met. Deferring background ad-hoc network start.")
            return
        }

        nearbyConnectionManager.startNearbyNetwork(roomId)

        gpsManager = GpsManager(
            context = getApplication(),
            updateIntervalSeconds = prefs.getGpsIntervalSeconds(),
            onLocationUpdated = { location ->
                _localLocation.value = location
                calculateWalkedDistance(location)
                broadcastLocalLocation(location)
            }
        )

        viewModelScope.launch {
            gpsManager?.statusFlow?.collect {
                _gpsStatus.value = it
            }
        }

        gpsManager?.startLocationUpdates()
        Logger.info(TAG, "Nearby ad-hoc network and Fused Location active.")
    }

    fun stopNearbyAndGps() {
        nearbyConnectionManager.stopNearbyNetwork()
        gpsManager?.stopLocationUpdates()
        gpsManager = null
        _localLocation.value = null
        _gpsStatus.value = GpsStatus.Idle
        Logger.info(TAG, "Nearby network and Location listeners stopped.")
    }

    fun acceptConnection(endpointId: String) {
        nearbyConnectionManager.acceptPeer(endpointId)
        viewModelScope.launch {
            delay(1000)
            syncRoomMembers()
        }
    }

    fun rejectConnection(endpointId: String) {
        nearbyConnectionManager.rejectPeer(endpointId)
    }

    fun sendMessage(text: String, replyToId: String? = null) {
        val room = currentRoom.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val messageId = "MSG-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
            val message = MessageEntity(
                messageId = messageId,
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                text = text,
                timestamp = System.currentTimeMillis(),
                deliveryStatus = "SENDING",
                replyToId = replyToId,
                reactions = ""
            )
            messageDao.insertMessage(message)

            val packet = SamekanPacket(
                messageId = messageId,
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.TEXT,
                timestamp = message.timestamp,
                ttl = 4,
                payload = text,
                targetDeviceId = null, // broadcast chat
                priority = 1
            )

            logPacketTraffic(packet, "SENT")
            nearbyConnectionManager.broadcastPacket(packet)
        }
    }

    fun deleteMessageLocal(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.deleteMessage(messageId)
        }
    }

    fun pinMessage(messageId: String, pinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.updateMessagePinned(messageId, pinned)
        }
    }

    fun addMessageReaction(messageId: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val messagesList = messageDao.getMessagesFlow(currentRoom.value?.roomId ?: "").first()
            val message = messagesList.find { it.messageId == messageId } ?: return@launch
            val reactionsList = message.reactions.split(",").filter { it.isNotBlank() }.toMutableList()
            if (reactionsList.contains(emoji)) {
                reactionsList.remove(emoji)
            } else {
                reactionsList.add(emoji)
            }
            val reactionsString = reactionsList.joinToString(",")
            messageDao.updateMessageReactions(messageId, reactionsString)

            val room = currentRoom.value ?: return@launch
            val reactionPacket = SamekanPacket(
                messageId = "RE-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.CHAT_ACK,
                timestamp = System.currentTimeMillis(),
                ttl = 2,
                payload = "$messageId:$reactionsString",
                priority = 1
            )
            logPacketTraffic(reactionPacket, "SENT")
            nearbyConnectionManager.broadcastPacket(reactionPacket)
        }
    }

    fun sendTypingStatus(isTyping: Boolean) {
        val room = currentRoom.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val typingPayload = TypingPayload(isTyping)
            val json = PacketSerializer.serializeTypingPayload(typingPayload)
            val packet = SamekanPacket(
                messageId = "TYP-${localDeviceId}",
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.TYPING,
                timestamp = System.currentTimeMillis(),
                ttl = 1,
                payload = json,
                priority = 2
            )
            nearbyConnectionManager.broadcastPacket(packet)
        }
    }

    fun triggerSosAlert(emergencyType: String) {
        val room = currentRoom.value ?: return
        val loc = _localLocation.value
        Logger.warn(TAG, "SOS TRIGGERED: $emergencyType")

        viewModelScope.launch(Dispatchers.IO) {
            val sosPayload = SosPayload(
                emergencyType = emergencyType,
                latitude = loc?.latitude ?: 0.0,
                longitude = loc?.longitude ?: 0.0,
                altitude = loc?.altitude ?: 0.0,
                accuracy = loc?.accuracy ?: 0f,
                batteryLevel = getBatteryLevel(),
                heading = loc?.bearing ?: 0f,
                speed = loc?.speed ?: 0f
            )

            val packet = SamekanPacket(
                messageId = "SOS-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.SOS_ALERT,
                timestamp = System.currentTimeMillis(),
                ttl = 4,
                payload = PacketSerializer.serializeSosPayload(sosPayload),
                priority = 0
            )

            logPacketTraffic(packet, "SENT")
            nearbyConnectionManager.broadcastPacket(packet)

            sosHistoryDao.insertSosAlert(
                SosHistoryEntity(
                    messageId = packet.messageId,
                    roomId = room.roomId,
                    senderDeviceId = localDeviceId,
                    senderDisplayName = localDisplayName.value,
                    emergencyType = emergencyType,
                    latitude = sosPayload.latitude,
                    longitude = sosPayload.longitude,
                    altitude = sosPayload.altitude,
                    accuracy = sosPayload.accuracy,
                    batteryLevel = sosPayload.batteryLevel,
                    heading = sosPayload.heading,
                    speed = sosPayload.speed,
                    timestamp = packet.timestamp,
                    status = "ACTIVE"
                )
            )

            _activeSosAlert.value = SosAlertInfo(
                messageId = packet.messageId,
                senderId = localDeviceId,
                senderName = localDisplayName.value,
                emergencyType = emergencyType,
                latitude = sosPayload.latitude,
                longitude = sosPayload.longitude,
                altitude = sosPayload.altitude,
                accuracy = sosPayload.accuracy,
                batteryLevel = sosPayload.batteryLevel,
                timestamp = packet.timestamp,
                acknowledged = false
            )
        }
    }

    fun cancelSosAlert() {
        val current = _activeSosAlert.value
        if (current != null) {
            viewModelScope.launch(Dispatchers.IO) {
                sosHistoryDao.updateSosStatus(current.messageId, "RESOLVED")
            }
        }
        _activeSosAlert.value = null
        stopSosAlertResources()
        Logger.info(TAG, "SOS alert canceled locally.")
    }

    fun acknowledgeSos(messageId: String, senderId: String) {
        val room = currentRoom.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ackPacket = SamekanPacket(
                messageId = "ACK-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.SOS_ACK,
                timestamp = System.currentTimeMillis(),
                ttl = 4,
                payload = messageId,
                targetDeviceId = senderId,
                priority = 0
            )
            logPacketTraffic(ackPacket, "SENT")
            nearbyConnectionManager.broadcastPacket(ackPacket)

            sosHistoryDao.updateSosStatus(messageId, "ACKNOWLEDGED")

            val current = _activeSosAlert.value
            if (current != null && current.messageId == messageId) {
                _activeSosAlert.value = current.copy(acknowledged = true)
            }
            stopSosAlertResources()
        }
    }

    // SHA-256 Checksum generation
    private fun getFileChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun shareFile(file: File, fileName: String, fileType: String) {
        val room = currentRoom.value ?: return
        val fileId = "FL-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"

        val job = viewModelScope.launch(Dispatchers.IO) {
            val checksum = getFileChecksum(file)
            val fileSize = file.length()
            val totalChunks = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

            val dbTransfer = FileTransferEntity(
                fileId = fileId,
                fileName = fileName,
                fileType = fileType,
                absolutePath = file.absolutePath,
                isIncoming = false,
                progress = 0.0f,
                status = "SENDING",
                timestamp = System.currentTimeMillis(),
                senderId = localDeviceId,
                fileSize = fileSize,
                checksum = checksum,
                chunkIndex = 0,
                totalChunks = totalChunks
            )
            fileTransferDao.insertTransfer(dbTransfer)

            // Send header
            nearbyConnectionManager.sendFileHeader(fileId, fileName, fileType, fileSize, checksum, totalChunks, room.roomId)
            delay(200)

            val fileBytes = file.readBytes()
            for (i in 0 until totalChunks) {
                // Check if transfer was paused
                val current = fileTransferDao.getTransfersFlow().first().find { it.fileId == fileId }
                if (current?.status == "PAUSED" || current?.status == "FAILED") {
                    break
                }

                val offset = i * CHUNK_SIZE
                val length = Math.min(fileBytes.size - offset, CHUNK_SIZE)
                val chunkBytes = fileBytes.copyOfRange(offset, offset + length)
                val chunkBase64 = android.util.Base64.encodeToString(chunkBytes, android.util.Base64.NO_WRAP)

                nearbyConnectionManager.sendFileChunk(fileId, i, totalChunks, chunkBase64, room.roomId)

                val progress = (i + 1).toFloat() / totalChunks
                fileTransferDao.updateProgressAndChunk(fileId, progress, "SENDING", i)
                delay(80) // rate limit
            }

            // Mark completed
            val finalCheck = fileTransferDao.getTransfersFlow().first().find { it.fileId == fileId }
            if (finalCheck?.status == "SENDING") {
                fileTransferDao.updateProgress(fileId, 1.0f, "COMPLETED")
            }
            fileSendingJobs.remove(fileId)
        }
        fileSendingJobs[fileId] = job
    }

    fun pauseFileTransfer(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            fileSendingJobs[fileId]?.cancel()
            fileSendingJobs.remove(fileId)
            
            val transfers = fileTransferDao.getTransfersFlow().first()
            val transfer = transfers.find { it.fileId == fileId }
            if (transfer != null) {
                fileTransferDao.updateProgress(fileId, transfer.progress, "PAUSED")
            }
        }
    }

    fun resumeFileTransfer(fileId: String) {
        val room = currentRoom.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val transfers = fileTransferDao.getTransfersFlow().first()
            val transfer = transfers.find { it.fileId == fileId } ?: return@launch
            
            val file = File(transfer.absolutePath)
            if (!file.exists()) {
                fileTransferDao.updateProgress(fileId, transfer.progress, "FAILED")
                return@launch
            }

            val job = launch(Dispatchers.IO) {
                val totalChunks = transfer.totalChunks
                val startIndex = transfer.chunkIndex + 1
                val fileBytes = file.readBytes()

                fileTransferDao.updateProgress(fileId, transfer.progress, "SENDING")

                for (i in startIndex until totalChunks) {
                    val current = fileTransferDao.getTransfersFlow().first().find { it.fileId == fileId }
                    if (current?.status == "PAUSED" || current?.status == "FAILED") {
                        break
                    }

                    val offset = i * CHUNK_SIZE
                    val length = Math.min(fileBytes.size - offset, CHUNK_SIZE)
                    val chunkBytes = fileBytes.copyOfRange(offset, offset + length)
                    val chunkBase64 = android.util.Base64.encodeToString(chunkBytes, android.util.Base64.NO_WRAP)

                    nearbyConnectionManager.sendFileChunk(fileId, i, totalChunks, chunkBase64, room.roomId)

                    val progress = (i + 1).toFloat() / totalChunks
                    fileTransferDao.updateProgressAndChunk(fileId, progress, "SENDING", i)
                    delay(80)
                }

                val finalCheck = fileTransferDao.getTransfersFlow().first().find { it.fileId == fileId }
                if (finalCheck?.status == "SENDING") {
                    fileTransferDao.updateProgress(fileId, 1.0f, "COMPLETED")
                }
                fileSendingJobs.remove(fileId)
            }
            fileSendingJobs[fileId] = job
        }
    }

    private fun calculateWalkedDistance(location: Location) {
        if (lastWalkedLocation == null) {
            lastWalkedLocation = location
            return
        }
        val distance = lastWalkedLocation!!.distanceTo(location)
        if (location.accuracy < 25f && distance > 2f) {
            _distanceWalked.value += distance
            lastWalkedLocation = location
        }
    }

    private fun broadcastLocalLocation(location: Location) {
        val room = currentRoom.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val gpsPayload = GpsPayload(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                altitude = location.altitude,
                bearing = location.bearing,
                speed = location.speed,
                batteryLevel = getBatteryLevel()
            )

            locationDao.insertLocation(
                LocationEntity(
                    deviceId = localDeviceId,
                    displayName = localDisplayName.value,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = System.currentTimeMillis(),
                    altitude = location.altitude,
                    bearing = location.bearing,
                    speed = location.speed
                )
            )

            locationHistoryDao.insertTrailPoint(
                LocationHistoryEntity(
                    deviceId = localDeviceId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    bearing = location.bearing,
                    speed = location.speed,
                    accuracy = location.accuracy,
                    timestamp = System.currentTimeMillis()
                )
            )

            val packet = SamekanPacket(
                messageId = "GPS-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.GPS,
                timestamp = System.currentTimeMillis(),
                ttl = 1,
                payload = PacketSerializer.serializeGpsPayload(gpsPayload),
                priority = 2
            )

            logPacketTraffic(packet, "SENT")
            nearbyConnectionManager.broadcastPacket(packet)
        }
    }

    private fun syncRoomMembers() {
        val room = currentRoom.value ?: return
        if (room.hostDeviceId != localDeviceId) return

        viewModelScope.launch(Dispatchers.IO) {
            val dbMembers = memberDao.getMembersSync()
            val syncInfoList = dbMembers.map { MemberSyncInfo(it.deviceId, it.displayName, it.role) }
            val roomSyncPayload = RoomSyncPayload(
                description = room.description,
                passwordHash = room.passwordHash,
                hostDeviceId = room.hostDeviceId,
                members = syncInfoList
            )

            val packet = SamekanPacket(
                messageId = "SYNC-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.ROOM_SYNC,
                timestamp = System.currentTimeMillis(),
                ttl = 2,
                payload = PacketSerializer.serializeRoomSyncPayload(roomSyncPayload),
                priority = 1
            )
            logPacketTraffic(packet, "SENT")
            nearbyConnectionManager.broadcastPacket(packet)
        }
    }

    private fun handleReceivedPacket(packet: SamekanPacket) {
        val activeRoom = currentRoom.value ?: return
        if (packet.roomId != activeRoom.roomId) return

        viewModelScope.launch(Dispatchers.IO) {
            val existingMember = memberDao.getMemberById(packet.senderDeviceId)
            if (existingMember == null) {
                memberDao.insertMember(
                    MemberEntity(
                        deviceId = packet.senderDeviceId,
                        displayName = packet.senderDisplayName,
                        connected = true,
                        lastSeen = packet.timestamp
                    )
                )
            } else {
                memberDao.insertMember(
                    existingMember.copy(
                        displayName = packet.senderDisplayName,
                        connected = true,
                        lastSeen = packet.timestamp
                    )
                )
            }

            when (packet.type) {
                PacketType.TEXT -> {
                    val message = MessageEntity(
                        messageId = packet.messageId,
                        roomId = packet.roomId,
                        senderDeviceId = packet.senderDeviceId,
                        senderDisplayName = packet.senderDisplayName,
                        text = packet.payload,
                        timestamp = packet.timestamp,
                        deliveryStatus = "SENT",
                        replyToId = packet.targetDeviceId
                    )
                    messageDao.insertMessage(message)

                    // Send ACK
                    val ackPacket = SamekanPacket(
                        messageId = "ACK-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                        roomId = packet.roomId,
                        senderDeviceId = localDeviceId,
                        senderDisplayName = localDisplayName.value,
                        type = PacketType.CHAT_ACK,
                        timestamp = System.currentTimeMillis(),
                        ttl = 2,
                        payload = packet.messageId,
                        targetDeviceId = packet.senderDeviceId,
                        priority = 1
                    )
                    logPacketTraffic(ackPacket, "SENT")
                    nearbyConnectionManager.sendPacketToPeer(ackPacket, packet.senderDeviceId)
                }
                PacketType.GPS -> {
                    val gps = PacketSerializer.deserializeGpsPayload(packet.payload)
                    if (gps != null) {
                        val location = LocationEntity(
                            deviceId = packet.senderDeviceId,
                            displayName = packet.senderDisplayName,
                            latitude = gps.latitude,
                            longitude = gps.longitude,
                            accuracy = gps.accuracy,
                            timestamp = packet.timestamp,
                            altitude = gps.altitude,
                            bearing = gps.bearing,
                            speed = gps.speed
                        )
                        locationDao.insertLocation(location)

                        locationHistoryDao.insertTrailPoint(
                            LocationHistoryEntity(
                                deviceId = packet.senderDeviceId,
                                latitude = gps.latitude,
                                longitude = gps.longitude,
                                altitude = gps.altitude,
                                bearing = gps.bearing,
                                speed = gps.speed,
                                accuracy = gps.accuracy,
                                timestamp = packet.timestamp
                            )
                        )

                        val currentMem = memberDao.getMemberById(packet.senderDeviceId)
                        if (currentMem != null) {
                            memberDao.insertMember(
                                currentMem.copy(
                                    batteryLevel = gps.batteryLevel,
                                    gpsAccuracy = gps.accuracy,
                                    bearing = gps.bearing,
                                    speed = gps.speed,
                                    altitude = gps.altitude
                                )
                            )
                        }
                    }
                }
                PacketType.ROOM_SYNC -> {
                    val sync = PacketSerializer.deserializeRoomSyncPayload(packet.payload)
                    if (sync != null) {
                        val inMemberList = sync.members.any { it.deviceId == localDeviceId }
                        if (!inMemberList) {
                            Logger.warn(TAG, "We have been kicked from the room by the host.")
                            launch(Dispatchers.Main) {
                                leaveRoom()
                            }
                            return@launch
                        }

                        if (activeRoom.hostDeviceId != sync.hostDeviceId) {
                            roomDao.insertRoom(activeRoom.copy(hostDeviceId = sync.hostDeviceId))
                        }

                        val dbMembers = memberDao.getMembersSync()
                        for (syncMem in sync.members) {
                            val existing = dbMembers.find { it.deviceId == syncMem.deviceId }
                            if (existing == null) {
                                memberDao.insertMember(
                                    MemberEntity(
                                        deviceId = syncMem.deviceId,
                                        displayName = syncMem.displayName,
                                        connected = true,
                                        lastSeen = System.currentTimeMillis(),
                                        role = syncMem.role
                                    )
                                )
                            } else if (existing.role != syncMem.role || existing.displayName != syncMem.displayName) {
                                memberDao.insertMember(
                                    existing.copy(role = syncMem.role, displayName = syncMem.displayName)
                                )
                            }
                        }

                        for (dbMem in dbMembers) {
                            if (!sync.members.any { it.deviceId == dbMem.deviceId } && dbMem.deviceId != localDeviceId) {
                                memberDao.deleteMember(dbMem.deviceId)
                                locationDao.clearLocations()
                            }
                        }
                    }
                }
                PacketType.PTT_CHUNK -> {
                    val rawAudio = android.util.Base64.decode(packet.payload, android.util.Base64.NO_WRAP)
                    pttManager.playAudioChunk(packet.senderDisplayName, packet.sequenceNumber, rawAudio)
                }
                PacketType.SOS_ALERT -> {
                    val sos = PacketSerializer.deserializeSosPayload(packet.payload)
                    if (sos != null) {
                        _activeSosAlert.value = SosAlertInfo(
                            messageId = packet.messageId,
                            senderId = packet.senderDeviceId,
                            senderName = packet.senderDisplayName,
                            emergencyType = sos.emergencyType,
                            latitude = sos.latitude,
                            longitude = sos.longitude,
                            altitude = sos.altitude,
                            accuracy = sos.accuracy,
                            batteryLevel = sos.batteryLevel,
                            timestamp = packet.timestamp,
                            acknowledged = false
                        )
                        triggerAlarmAndVibration()

                        sosHistoryDao.insertSosAlert(
                            SosHistoryEntity(
                                messageId = packet.messageId,
                                roomId = packet.roomId,
                                senderDeviceId = packet.senderDeviceId,
                                senderDisplayName = packet.senderDisplayName,
                                emergencyType = sos.emergencyType,
                                latitude = sos.latitude,
                                longitude = sos.longitude,
                                altitude = sos.altitude,
                                accuracy = sos.accuracy,
                                batteryLevel = sos.batteryLevel,
                                heading = sos.heading,
                                speed = sos.speed,
                                timestamp = packet.timestamp,
                                status = "ACTIVE"
                            )
                        )
                    }
                }
                PacketType.SOS_ACK -> {
                    val current = _activeSosAlert.value
                    if (current != null && current.messageId == packet.payload) {
                        _activeSosAlert.value = current.copy(acknowledged = true)
                        stopSosAlertResources()
                    }
                    sosHistoryDao.updateSosStatus(packet.payload, "ACKNOWLEDGED")
                }
                PacketType.CHAT_ACK -> {
                    val parts = packet.payload.split(":", limit = 2)
                    if (parts.size == 2) {
                        val messageId = parts[0]
                        val reactions = parts[1]
                        messageDao.updateMessageReactions(messageId, reactions)
                    } else {
                        // Regular chat message ACK
                        messageDao.updateMessageStatus(packet.payload, "SENT")
                    }
                }
                PacketType.TYPING -> {
                    val typingPayload = PacketSerializer.deserializeTypingPayload(packet.payload)
                    if (typingPayload != null) {
                        val current = _typingPeers.value.toMutableMap()
                        if (typingPayload.isTyping) {
                            current[packet.senderDeviceId] = packet.senderDisplayName
                            typingTimestamps[packet.senderDeviceId] = System.currentTimeMillis()
                        } else {
                            current.remove(packet.senderDeviceId)
                            typingTimestamps.remove(packet.senderDeviceId)
                        }
                        _typingPeers.value = current
                    }
                }
                PacketType.FILE_HEADER -> {
                    val header = PacketSerializer.deserializeFileHeaderPayload(packet.payload)
                    if (header != null) {
                        val incomingFile = FileTransferEntity(
                            fileId = header.fileId,
                            fileName = header.fileName,
                            fileType = header.fileType,
                            absolutePath = "",
                            isIncoming = true,
                            progress = 0.0f,
                            status = "DOWNLOADING",
                            timestamp = System.currentTimeMillis(),
                            senderId = packet.senderDeviceId,
                            fileSize = header.fileSize,
                            checksum = header.checksum,
                            chunkIndex = 0,
                            totalChunks = header.totalChunks
                        )
                        fileTransferDao.insertTransfer(incomingFile)

                        // Create file placeholder in cache directory
                        val tempFile = File(getApplication<Application>().cacheDir, header.fileId)
                        if (tempFile.exists()) tempFile.delete()
                        tempFile.createNewFile()
                    }
                }
                PacketType.FILE_CHUNK -> {
                    val chunk = PacketSerializer.deserializeFileChunkPayload(packet.payload)
                    if (chunk != null) {
                        val tempFile = File(getApplication<Application>().cacheDir, chunk.fileId)
                        if (tempFile.exists()) {
                            val decoded = android.util.Base64.decode(chunk.data, android.util.Base64.NO_WRAP)
                            val raf = RandomAccessFile(tempFile, "rw")
                            raf.seek(chunk.chunkIndex * CHUNK_SIZE.toLong())
                            raf.write(decoded)
                            raf.close()

                            val progress = (chunk.chunkIndex + 1).toFloat() / chunk.totalChunks
                            
                            val isLast = chunk.chunkIndex == chunk.totalChunks - 1
                            if (isLast) {
                                // Calculate checksum
                                val localChecksum = getFileChecksum(tempFile)
                                val dbTransfers = fileTransferDao.getTransfersFlow().first()
                                val transfer = dbTransfers.find { it.fileId == chunk.fileId }
                                if (transfer != null && localChecksum == transfer.checksum) {
                                    // Move to permanent app files dir
                                    val destDir = File(getApplication<Application>().filesDir, "trek_shared_files")
                                    if (!destDir.exists()) destDir.mkdirs()
                                    val destFile = File(destDir, transfer.fileName)
                                    tempFile.renameTo(destFile)

                                    fileTransferDao.insertTransfer(
                                        transfer.copy(
                                            absolutePath = destFile.absolutePath,
                                            progress = 1.0f,
                                            status = "COMPLETED",
                                            chunkIndex = chunk.chunkIndex
                                        )
                                    )
                                } else {
                                    fileTransferDao.updateProgress(chunk.fileId, progress, "FAILED")
                                    tempFile.delete()
                                }
                            } else {
                                fileTransferDao.updateProgressAndChunk(chunk.fileId, progress, "DOWNLOADING", chunk.chunkIndex)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus = getApplication<Application>().registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    }

    private fun triggerAlarmAndVibration() {
        try {
            stopSosAlertResources()
            val alertUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = RingtoneManager.getRingtone(getApplication(), alertUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtonePlayer?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtonePlayer?.play()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 800, 400, 800, 400)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 800, 400), 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopSosAlertResources() {
        try {
            ringtonePlayer?.stop()
            ringtonePlayer = null
        } catch (e: Exception) {
            // ignore
        }
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopNearbyAndGps()
        stopSosAlertResources()
        pttManager.stopRecording()
        pttManager.stopPlayback()
    }
}
