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
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class TrekRoomViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "TrekRoomViewModel"
    }

    private val db = AppDatabase.getDatabase(application)
    private val roomDao = db.roomDao()
    private val memberDao = db.memberDao()
    private val messageDao = db.messageDao()
    private val locationDao = db.locationDao()
    private val locationHistoryDao = db.locationHistoryDao()
    private val fileTransferDao = db.fileTransferDao()

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

    // Diagnostics stats
    val totalPacketsSent = nearbyConnectionManager.totalPacketsSent
    val totalPacketsReceived = nearbyConnectionManager.totalPacketsReceived
    val droppedPackets = nearbyConnectionManager.droppedPackets
    val totalRelays = nearbyConnectionManager.totalRelays
    val averageLatencyMs = nearbyConnectionManager.averageLatencyMs

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
                role = dbMember.role
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
            handleReceivedPacket(packet)
        }

        // Set up peer disconnect listener
        nearbyConnectionManager.onPeerDisconnectedListener = { deviceId ->
            Logger.info(TAG, "Peer disconnected in database update: $deviceId")
            viewModelScope.launch(Dispatchers.IO) {
                memberDao.updateConnectionStatus(deviceId, false)
            }
        }

        // Setup PTT recorded chunk callback
        pttManager.setOnChunkRecordedListener { compressedAudio ->
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
                    payload = android.util.Base64.encodeToString(compressedAudio, android.util.Base64.NO_WRAP)
                )
                nearbyConnectionManager.broadcastPacket(packet)
            }
        }

        // Setup file transfer progress callback
        nearbyConnectionManager.onFileTransferProgressListener = { payloadId, progress, status, file ->
            viewModelScope.launch(Dispatchers.IO) {
                val dbTransfers = fileTransferDao.getTransfersFlow().first()
                val transfer = dbTransfers.find { it.fileId == payloadId.toString() }
                if (transfer != null) {
                    val updatedStatus = if (status == "COMPLETED") "COMPLETED" else if (status == "FAILED") "FAILED" else "SENDING"
                    fileTransferDao.updateProgress(
                        fileId = payloadId.toString(),
                        progress = progress,
                        status = updatedStatus
                    )
                } else if (file != null) {
                    val incomingFile = FileTransferEntity(
                        fileId = payloadId.toString(),
                        fileName = file.name,
                        fileType = "IMAGE",
                        absolutePath = file.absolutePath,
                        isIncoming = true,
                        progress = 1.0f,
                        status = "COMPLETED",
                        timestamp = System.currentTimeMillis(),
                        senderId = "Unknown",
                        fileSize = file.length()
                    )
                    fileTransferDao.insertTransfer(incomingFile)
                }
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

        // Auto restart nearby network if database indicates we are in a room and permissions are met
        viewModelScope.launch(Dispatchers.IO) {
            val activeRoom = roomDao.getRoomSync()
            if (activeRoom != null && permissionManager.checkAllRequiredPermissionsGranted()) {
                launch(Dispatchers.Main) {
                    startNearbyAndGps(activeRoom.roomId)
                }
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

        // Collect GPS Manager status updates
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
                ttl = 2,
                payload = text,
                targetDeviceId = replyToId
            )

            nearbyConnectionManager.broadcastPacket(packet)
            messageDao.updateMessageStatus(messageId, "SENT")
        }
    }

    fun deleteMessageLocal(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.deleteMessage(messageId)
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
                payload = "$messageId:$reactionsString"
            )
            nearbyConnectionManager.broadcastPacket(reactionPacket)
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
                batteryLevel = getBatteryLevel()
            )

            val packet = SamekanPacket(
                messageId = "SOS-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.SOS_ALERT,
                timestamp = System.currentTimeMillis(),
                ttl = 4,
                payload = PacketSerializer.serializeSosPayload(sosPayload)
            )

            nearbyConnectionManager.broadcastPacket(packet)

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
                ttl = 2,
                payload = messageId,
                targetDeviceId = senderId
            )
            nearbyConnectionManager.broadcastPacket(ackPacket)

            val current = _activeSosAlert.value
            if (current != null && current.messageId == messageId) {
                _activeSosAlert.value = current.copy(acknowledged = true)
            }
            stopSosAlertResources()
        }
    }

    fun shareFile(file: File, fileName: String, fileType: String) {
        val room = currentRoom.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val payloadId = nearbyConnectionManager.sendFile(file, fileName, fileType, room.roomId)
            val dbTransfer = FileTransferEntity(
                fileId = payloadId.toString(),
                fileName = fileName,
                fileType = fileType,
                absolutePath = file.absolutePath,
                isIncoming = false,
                progress = 0.0f,
                status = "SENDING",
                timestamp = System.currentTimeMillis(),
                senderId = localDeviceId,
                fileSize = file.length()
            )
            fileTransferDao.insertTransfer(dbTransfer)
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
                payload = PacketSerializer.serializeGpsPayload(gpsPayload)
            )

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
                payload = PacketSerializer.serializeRoomSyncPayload(roomSyncPayload)
            )
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
                    pttManager.playAudioChunk(packet.senderDisplayName, rawAudio)
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
                    }
                }
                PacketType.SOS_ACK -> {
                    val current = _activeSosAlert.value
                    if (current != null && current.messageId == packet.payload) {
                        _activeSosAlert.value = current.copy(acknowledged = true)
                        stopSosAlertResources()
                    }
                }
                PacketType.CHAT_ACK -> {
                    val parts = packet.payload.split(":", limit = 2)
                    if (parts.size == 2) {
                        val messageId = parts[0]
                        val reactions = parts[1]
                        messageDao.updateMessageReactions(messageId, reactions)
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
                            status = "SENDING",
                            timestamp = System.currentTimeMillis(),
                            senderId = packet.senderDeviceId,
                            fileSize = header.fileSize
                        )
                        fileTransferDao.insertTransfer(incomingFile)
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
