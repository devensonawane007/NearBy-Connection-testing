package com.example.samekanprivatetrekroom.presentation.viewmodel

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.samekanprivatetrekroom.data.local.*
import com.example.samekanprivatetrekroom.data.nearby.NearbyConnectionManager
import com.example.samekanprivatetrekroom.domain.model.*
import com.example.samekanprivatetrekroom.domain.serializer.PacketSerializer
import com.example.samekanprivatetrekroom.location.GpsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class TrekRoomViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "TrekRoomVM"
    }

    private val db = AppDatabase.getDatabase(application)
    private val roomDao = db.roomDao()
    private val memberDao = db.memberDao()
    private val messageDao = db.messageDao()
    private val locationDao = db.locationDao()

    val prefs = PreferenceHelper(application)

    // Nearby Connection Manager
    private val nearbyConnectionManager = NearbyConnectionManager(
        context = application,
        localDeviceId = prefs.getDeviceId(),
        localDisplayNameProvider = { prefs.getDisplayName() }
    )

    // GPS Manager
    private var gpsManager: GpsManager? = null

    // State flows
    private val _localLocation = MutableStateFlow<Location?>(null)
    val localLocation: StateFlow<Location?> = _localLocation.asStateFlow()

    val localDeviceId = prefs.getDeviceId()
    val localDisplayName = MutableStateFlow(prefs.getDisplayName())

    val currentRoom: StateFlow<RoomEntity?> = roomDao.getRoomFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isAdvertising = nearbyConnectionManager.isAdvertising
    val isDiscovering = nearbyConnectionManager.isDiscovering
    val pendingRequests = nearbyConnectionManager.pendingRequests

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
                connected = nearbyPeer?.isConnected == true,
                lastSeen = dbMember.lastSeen
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Chat messages (filtered by current joined room)
    val chatMessages: StateFlow<List<MessageEntity>> = currentRoom.flatMapLatest { room ->
        if (room != null) {
            messageDao.getMessagesFlow(room.roomId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Member locations (GPS coordinates stored in DB)
    val memberLocations: StateFlow<List<LocationEntity>> = locationDao.getLocationsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Set up nearby packet receiver
        nearbyConnectionManager.onPacketReceivedListener = { packet ->
            handleReceivedPacket(packet)
        }

        // Set up peer disconnect listener
        nearbyConnectionManager.onPeerDisconnectedListener = { deviceId ->
            viewModelScope.launch(Dispatchers.IO) {
                memberDao.updateConnectionStatus(deviceId, false)
            }
        }

        // Auto restart nearby network if database indicates we are in a room
        viewModelScope.launch(Dispatchers.IO) {
            val activeRoom = roomDao.getRoomSync()
            if (activeRoom != null) {
                launch(Dispatchers.Main) {
                    startNearbyAndGps(activeRoom.roomId)
                }
            }
        }
    }

    fun updateDisplayName(name: String) {
        prefs.setDisplayName(name)
        localDisplayName.value = name
    }

    fun updateGpsInterval(seconds: Int) {
        prefs.setGpsIntervalSeconds(seconds)
        gpsManager?.updateInterval(seconds)
    }

    fun createRoom(roomName: String, roomId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear prior data
            roomDao.clearRoom()
            memberDao.clearMembers()
            messageDao.clearMessages()
            locationDao.clearLocations()

            val room = RoomEntity(
                roomId = roomId,
                roomName = roomName,
                creatorDeviceId = localDeviceId,
                createdAt = System.currentTimeMillis(),
                status = "ACTIVE"
            )
            roomDao.insertRoom(room)

            // Insert self as first member
            memberDao.insertMember(
                MemberEntity(
                    deviceId = localDeviceId,
                    displayName = localDisplayName.value,
                    connected = true,
                    lastSeen = System.currentTimeMillis()
                )
            )

            launch(Dispatchers.Main) {
                startNearbyAndGps(roomId)
            }
        }
    }

    fun joinRoom(roomName: String, roomId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            roomDao.clearRoom()
            memberDao.clearMembers()
            messageDao.clearMessages()
            locationDao.clearLocations()

            val room = RoomEntity(
                roomId = roomId,
                roomName = roomName,
                creatorDeviceId = "", // Creator is other device
                createdAt = System.currentTimeMillis(),
                status = "ACTIVE"
            )
            roomDao.insertRoom(room)

            // Insert self
            memberDao.insertMember(
                MemberEntity(
                    deviceId = localDeviceId,
                    displayName = localDisplayName.value,
                    connected = true,
                    lastSeen = System.currentTimeMillis()
                )
            )

            launch(Dispatchers.Main) {
                startNearbyAndGps(roomId)
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
        }
    }

    private fun startNearbyAndGps(roomId: String) {
        nearbyConnectionManager.startNearbyNetwork(roomId)

        // Initialize and start GPS tracking
        gpsManager = GpsManager(
            context = getApplication(),
            updateIntervalSeconds = prefs.getGpsIntervalSeconds(),
            onLocationUpdated = { location ->
                _localLocation.value = location
                broadcastLocalLocation(location)
            }
        )
        gpsManager?.startLocationUpdates()
    }

    private fun stopNearbyAndGps() {
        nearbyConnectionManager.stopNearbyNetwork()
        gpsManager?.stopLocationUpdates()
        gpsManager = null
        _localLocation.value = null
    }

    fun acceptConnection(endpointId: String) {
        nearbyConnectionManager.acceptPeer(endpointId)
    }

    fun rejectConnection(endpointId: String) {
        nearbyConnectionManager.rejectPeer(endpointId)
    }

    fun sendMessage(text: String) {
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
                deliveryStatus = "SENDING"
            )
            messageDao.insertMessage(message)

            val packet = SamekanPacket(
                messageId = messageId,
                roomId = room.roomId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName.value,
                type = PacketType.TEXT,
                timestamp = message.timestamp,
                ttl = 1,
                payload = text
            )

            nearbyConnectionManager.broadcastPacket(packet)

            // Update status as SENT once dispatched to Nearby Connections buffer
            messageDao.updateMessageStatus(messageId, "SENT")
        }
    }

    private fun broadcastLocalLocation(location: Location) {
        val room = currentRoom.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val gpsPayload = GpsPayload(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy
            )

            // Update own location in DB
            locationDao.insertLocation(
                LocationEntity(
                    deviceId = localDeviceId,
                    displayName = localDisplayName.value,
                    latitude = location.latitude,
                    longitude = location.longitude,
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
                payload = PacketSerializer.serializeGpsPayload(gpsPayload)
            )

            nearbyConnectionManager.broadcastPacket(packet)
        }
    }

    private fun handleReceivedPacket(packet: SamekanPacket) {
        val activeRoom = currentRoom.value ?: return
        if (packet.roomId != activeRoom.roomId) return

        viewModelScope.launch(Dispatchers.IO) {
            // First ensure the sender is recorded as a member of this room
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
                        deliveryStatus = "SENT"
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
                            timestamp = packet.timestamp
                        )
                        locationDao.insertLocation(location)
                    }
                }
                PacketType.PING -> {
                    // Handled implicitly by updating lastSeen
                }
                else -> {
                    Log.d(TAG, "Unhandled packet type received: ${packet.type}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopNearbyAndGps()
    }
}
