package com.example.samekanprivatetrekroom.data.nearby

import android.content.Context
import com.example.samekanprivatetrekroom.domain.model.PacketType
import com.example.samekanprivatetrekroom.domain.model.SamekanPacket
import com.example.samekanprivatetrekroom.domain.serializer.PacketSerializer
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.example.samekanprivatetrekroom.data.local.Logger
import com.example.samekanprivatetrekroom.data.local.PermissionManager

class NearbyConnectionManager(
    private val context: Context,
    private val localDeviceId: String,
    private val localDisplayNameProvider: () -> String
) {
    companion object {
        private const val TAG = "NearbyConnMgr"
        private const val SERVICE_ID = "com.samekan.trekroom"
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.Default)

    // State flows
    private val _connectedPeers = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectedPeers: StateFlow<Map<String, ConnectionState>> = _connectedPeers.asStateFlow()

    private val _pendingRequests = MutableStateFlow<Map<String, PendingRequest>>(emptyMap())
    val pendingRequests: StateFlow<Map<String, PendingRequest>> = _pendingRequests.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Diagnostics State Flows
    private val _totalPacketsSent = MutableStateFlow(0)
    val totalPacketsSent: StateFlow<Int> = _totalPacketsSent.asStateFlow()

    private val _totalPacketsReceived = MutableStateFlow(0)
    val totalPacketsReceived: StateFlow<Int> = _totalPacketsReceived.asStateFlow()

    private val _droppedPackets = MutableStateFlow(0)
    val droppedPackets: StateFlow<Int> = _droppedPackets.asStateFlow()

    private val _totalRelays = MutableStateFlow(0)
    val totalRelays: StateFlow<Int> = _totalRelays.asStateFlow()

    private val _averageLatencyMs = MutableStateFlow(0L)
    val averageLatencyMs: StateFlow<Long> = _averageLatencyMs.asStateFlow()

    // Listeners/callbacks
    var onPacketReceivedListener: ((SamekanPacket) -> Unit)? = null
    var onPeerDisconnectedListener: ((deviceId: String) -> Unit)? = null
    var onFileTransferProgressListener: ((payloadId: Long, progress: Float, status: String, file: File?) -> Unit)? = null

    // Track active connection requests and backoffs
    private val activeConnections = ConcurrentHashMap<String, String>() // endpointId -> deviceId
    private val backoffAttempts = ConcurrentHashMap<String, Int>() // deviceId -> attempt count
    private val lastConnectionTime = ConcurrentHashMap<String, Long>() // deviceId -> timestamp
    private val discoveredEndpoints = ConcurrentHashMap<String, EndpointInfo>() // endpointId -> Info

    // SOS relay duplicate prevention & latencies
    private val processedSosIds = ConcurrentHashMap.newKeySet<String>()
    private val pingTimes = ConcurrentHashMap<String, Long>() // ping messageId -> start timestamp

    // Incoming file payloads cached by payload ID
    private val incomingFilePayloads = ConcurrentHashMap<Long, Payload>()

    private var currentRoomId: String? = null

    data class ConnectionState(
        val endpointId: String,
        val deviceId: String,
        val displayName: String,
        val roomId: String,
        val isConnected: Boolean
    )

    data class PendingRequest(
        val endpointId: String,
        val deviceId: String,
        val displayName: String,
        val roomId: String,
        val authenticationDigits: String,
        val isIncoming: Boolean
    )

    data class EndpointInfo(
        val endpointId: String,
        val deviceId: String,
        val displayName: String,
        val roomId: String
    )

    fun startNearbyNetwork(roomId: String) {
        val permissionManager = PermissionManager(context)
        if (!permissionManager.checkAllRequiredPermissionsGranted()) {
            Logger.warn(TAG, "Cannot start Nearby network. Permissions missing.")
            return
        }
        currentRoomId = roomId
        startAdvertising(roomId)
        startDiscovery(roomId)
    }

    fun stopNearbyNetwork() {
        stopAdvertising()
        stopDiscovery()
        disconnectAll()
        currentRoomId = null
        discoveredEndpoints.clear()
        _connectedPeers.value = emptyMap()
        _pendingRequests.value = emptyMap()
    }

    private fun getEndpointName(roomId: String): String {
        return "$localDeviceId:${localDisplayNameProvider()}:$roomId"
    }

    private fun startAdvertising(roomId: String) {
        if (_isAdvertising.value) return
        val endpointName = getEndpointName(roomId)
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        Logger.info(TAG, "Starting Advertising for room $roomId")
        connectionsClient.startAdvertising(
            endpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Logger.info(TAG, "Advertising started successfully for room $roomId")
            _isAdvertising.value = true
        }.addOnFailureListener { e ->
            Logger.error(TAG, "Advertising start failed", e)
            _isAdvertising.value = false
            scope.launch {
                delay(5000)
                if (currentRoomId == roomId) startAdvertising(roomId)
            }
        }
    }

    private fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
        Logger.info(TAG, "Advertising stopped")
    }

    private fun startDiscovery(roomId: String) {
        if (_isDiscovering.value) return
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        Logger.info(TAG, "Starting Discovery for room $roomId")
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Logger.info(TAG, "Discovery started successfully")
            _isDiscovering.value = true
        }.addOnFailureListener { e ->
            Logger.error(TAG, "Discovery start failed", e)
            _isDiscovering.value = false
            scope.launch {
                delay(5000)
                if (currentRoomId == roomId) startDiscovery(roomId)
            }
        }
    }

    private fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
        Logger.info(TAG, "Discovery stopped")
    }

    private fun disconnectAll() {
        connectionsClient.stopAllEndpoints()
        activeConnections.clear()
        Logger.info(TAG, "Disconnected all active endpoints.")
    }

    fun acceptPeer(endpointId: String) {
        Logger.info(TAG, "Accepting peer connection request: $endpointId")
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Logger.info(TAG, "Successfully accepted connection request from $endpointId")
                _pendingRequests.value = _pendingRequests.value - endpointId
            }
            .addOnFailureListener { e ->
                Logger.error(TAG, "Failed to accept connection from $endpointId", e)
                _pendingRequests.value = _pendingRequests.value - endpointId
            }
    }

    fun rejectPeer(endpointId: String) {
        Logger.info(TAG, "Rejecting peer connection request: $endpointId")
        connectionsClient.rejectConnection(endpointId)
            .addOnSuccessListener {
                Logger.info(TAG, "Successfully rejected connection request from $endpointId")
                _pendingRequests.value = _pendingRequests.value - endpointId
            }
            .addOnFailureListener { e ->
                Logger.error(TAG, "Failed to reject connection from $endpointId", e)
                _pendingRequests.value = _pendingRequests.value - endpointId
            }
    }

    fun broadcastPacket(packet: SamekanPacket) {
        val payloadBytes = PacketSerializer.serializePacket(packet)
        val payload = Payload.fromBytes(payloadBytes)
        val endpoints = _connectedPeers.value.keys.toList()
        if (endpoints.isNotEmpty()) {
            _totalPacketsSent.value++
            connectionsClient.sendPayload(endpoints, payload)
                .addOnSuccessListener {
                    Logger.debug(TAG, "Payload type ${packet.type} sent to ${endpoints.size} peers.")
                }
                .addOnFailureListener { e ->
                    Logger.error(TAG, "Failed to send payload to peers", e)
                }
        }
    }

    fun sendPing(roomId: String) {
        val pingId = UUID.randomUUID().toString()
        val packet = SamekanPacket(
            messageId = pingId,
            roomId = roomId,
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayNameProvider(),
            type = PacketType.PING,
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            payload = ""
        )
        pingTimes[pingId] = System.currentTimeMillis()
        broadcastPacket(packet)
    }

    fun sendFile(file: File, fileName: String, fileType: String, roomId: String): Long {
        val filePayload = Payload.fromFile(file)
        val payloadId = filePayload.id

        val headerJson = "{\"fileId\":\"$payloadId\",\"fileName\":\"$fileName\",\"fileType\":\"$fileType\",\"fileSize\":${file.length()}}"
        val headerPacket = SamekanPacket(
            messageId = "FH-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
            roomId = roomId,
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayNameProvider(),
            type = PacketType.FILE_HEADER,
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            payload = headerJson
        )
        broadcastPacket(headerPacket)

        val endpoints = _connectedPeers.value.keys.toList()
        if (endpoints.isNotEmpty()) {
            _totalPacketsSent.value++
            connectionsClient.sendPayload(endpoints, filePayload)
                .addOnSuccessListener {
                    Logger.info(TAG, "Shared file payload $payloadId successfully dispatched to peers.")
                }
                .addOnFailureListener { e ->
                    Logger.error(TAG, "Failed to dispatch file payload", e)
                }
        }
        return payloadId
    }

    fun relayPacket(packet: SamekanPacket, excludeEndpointId: String?) {
        val payloadBytes = PacketSerializer.serializePacket(packet)
        val payload = Payload.fromBytes(payloadBytes)
        val endpoints = _connectedPeers.value.keys.filter { it != excludeEndpointId }
        if (endpoints.isNotEmpty()) {
            _totalRelays.value++
            connectionsClient.sendPayload(endpoints, payload)
                .addOnSuccessListener {
                    Logger.info(TAG, "Relayed packet ${packet.messageId} to ${endpoints.size} peers.")
                }
                .addOnFailureListener { e ->
                    Logger.error(TAG, "Failed to relay packet", e)
                }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val endpointName = info.endpointName
            Logger.info(TAG, "Endpoint found: $endpointId ($endpointName)")

            val parts = endpointName.split(":")
            val deviceId = parts.getOrNull(0) ?: ""
            val displayName = parts.getOrNull(1) ?: ""
            val roomId = parts.getOrNull(2) ?: ""

            if (roomId.isEmpty() || roomId != currentRoomId) {
                Logger.info(TAG, "Discovered endpoint room ($roomId) does not match ours ($currentRoomId). Ignoring.")
                return
            }

            val endpointInfo = EndpointInfo(endpointId, deviceId, displayName, roomId)
            discoveredEndpoints[endpointId] = endpointInfo

            scope.launch {
                initiateConnectionWithBackoff(endpointInfo)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Logger.info(TAG, "Endpoint lost: $endpointId")
            discoveredEndpoints.remove(endpointId)
        }
    }

    private suspend fun initiateConnectionWithBackoff(endpoint: EndpointInfo) {
        val deviceId = endpoint.deviceId
        val endpointId = endpoint.endpointId

        if (_connectedPeers.value.values.any { it.deviceId == deviceId } ||
            _pendingRequests.value.values.any { it.deviceId == deviceId }
        ) {
            Logger.info(TAG, "Already connected or pending connection with $deviceId. Skipping request.")
            return
        }

        val attempts = backoffAttempts[deviceId] ?: 0
        val lastAttempt = lastConnectionTime[deviceId] ?: 0L
        val currentTime = System.currentTimeMillis()

        if (attempts > 0) {
            val backoffMs = getBackoffDelayMs(attempts)
            if (currentTime - lastAttempt < backoffMs) {
                Logger.debug(TAG, "Backing off connection request to $deviceId. Wait ${backoffMs/1000}s.")
                return
            }
        }

        Logger.info(TAG, "Requesting connection to ${endpoint.displayName} ($endpointId), attempt ${attempts + 1}")
        lastConnectionTime[deviceId] = currentTime
        backoffAttempts[deviceId] = attempts + 1

        val localEndpointName = getEndpointName(currentRoomId ?: "")
        connectionsClient.requestConnection(
            localEndpointName,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            Logger.error(TAG, "Request connection failed to $endpointId", e)
        }
    }

    private fun getBackoffDelayMs(attempts: Int): Long {
        return when (attempts) {
            1 -> 5000L
            2 -> 10000L
            3 -> 20000L
            4 -> 40000L
            else -> 60000L
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Logger.info(TAG, "Connection initiated: $endpointId (${info.endpointName})")

            val parts = info.endpointName.split(":")
            val deviceId = parts.getOrNull(0) ?: ""
            val displayName = parts.getOrNull(1) ?: ""
            val roomId = parts.getOrNull(2) ?: ""

            val endpointInfo = EndpointInfo(endpointId, deviceId, displayName, roomId)
            discoveredEndpoints[endpointId] = endpointInfo

            val isIncoming = !info.isIncomingConnection

            val request = PendingRequest(
                endpointId = endpointId,
                deviceId = deviceId,
                displayName = displayName,
                roomId = roomId,
                authenticationDigits = info.authenticationToken ?: "0000",
                isIncoming = isIncoming
            )

            _pendingRequests.value = _pendingRequests.value + (endpointId to request)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            _pendingRequests.value = _pendingRequests.value - endpointId
            val status = result.status.statusCode

            Logger.info(TAG, "Connection result for $endpointId: Status $status")

            val endpoint = discoveredEndpoints[endpointId]
            val deviceId = endpoint?.deviceId ?: ""
            val displayName = endpoint?.displayName ?: "Unknown"
            val roomId = endpoint?.roomId ?: ""

            if (status == ConnectionsStatusCodes.STATUS_OK) {
                Logger.info(TAG, "Connection SUCCESS to $endpointId ($displayName)")
                activeConnections[endpointId] = deviceId
                backoffAttempts.remove(deviceId)

                val state = ConnectionState(
                    endpointId = endpointId,
                    deviceId = deviceId,
                    displayName = displayName,
                    roomId = roomId,
                    isConnected = true
                )
                _connectedPeers.value = _connectedPeers.value + (endpointId to state)
            } else {
                Logger.warn(TAG, "Connection FAILURE to $endpointId: code $status")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Logger.info(TAG, "Disconnected: $endpointId")
            val state = _connectedPeers.value[endpointId]
            _connectedPeers.value = _connectedPeers.value - endpointId

            state?.let {
                onPeerDisconnectedListener?.invoke(it.deviceId)
                activeConnections.remove(endpointId)

                val endpointInfo = discoveredEndpoints[endpointId]
                if (endpointInfo != null && currentRoomId != null && currentRoomId == endpointInfo.roomId) {
                    scope.launch {
                        delay(2000)
                        initiateConnectionWithBackoff(endpointInfo)
                    }
                }
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes()
                if (bytes != null) {
                    val packet = PacketSerializer.deserializePacket(bytes)
                    if (packet != null) {
                        _totalPacketsReceived.value++
                        Logger.debug(TAG, "Packet received from $endpointId, type: ${packet.type}")

                        if (packet.type == PacketType.PING) {
                            val originalTime = pingTimes.remove(packet.messageId)
                            if (originalTime != null) {
                                val rtt = System.currentTimeMillis() - originalTime
                                _averageLatencyMs.value = if (_averageLatencyMs.value == 0L) rtt else (_averageLatencyMs.value + rtt) / 2
                            } else {
                                val pong = packet.copy(
                                    senderDeviceId = localDeviceId,
                                    senderDisplayName = localDisplayNameProvider()
                                )
                                broadcastPacket(pong)
                            }
                        }

                        if (packet.type == PacketType.SOS_ALERT) {
                            if (processedSosIds.contains(packet.messageId)) {
                                _droppedPackets.value++
                                return
                            }
                            processedSosIds.add(packet.messageId)
                            val decrementedTtl = packet.ttl - 1
                            if (decrementedTtl > 0) {
                                val relayedPacket = packet.copy(ttl = decrementedTtl)
                                relayPacket(relayedPacket, excludeEndpointId = endpointId)
                            }
                        }

                        onPacketReceivedListener?.invoke(packet)
                    } else {
                        _droppedPackets.value++
                    }
                }
            } else if (payload.type == Payload.Type.FILE) {
                incomingFilePayloads[payload.id] = payload
                Logger.info(TAG, "Incoming file transfer payload received with ID: ${payload.id}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val status = when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> "IN_PROGRESS"
                PayloadTransferUpdate.Status.SUCCESS -> "SUCCESS"
                PayloadTransferUpdate.Status.FAILURE -> "FAILURE"
                else -> "UNKNOWN"
            }
            val progress = if (update.totalBytes > 0) update.bytesTransferred.toFloat() / update.totalBytes else 0f

            if (status == "SUCCESS") {
                val filePayload = incomingFilePayloads.remove(payloadId)
                val file = filePayload?.asFile()?.asJavaFile()
                onFileTransferProgressListener?.invoke(payloadId, 1.0f, "COMPLETED", file)
            } else if (status == "FAILURE") {
                incomingFilePayloads.remove(payloadId)
                onFileTransferProgressListener?.invoke(payloadId, progress, "FAILED", null)
            } else {
                onFileTransferProgressListener?.invoke(payloadId, progress, "IN_PROGRESS", null)
            }
        }
    }
}
