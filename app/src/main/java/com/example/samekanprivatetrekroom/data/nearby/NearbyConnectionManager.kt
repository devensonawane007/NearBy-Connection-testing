package com.example.samekanprivatetrekroom.data.nearby

import android.content.Context
import com.example.samekanprivatetrekroom.domain.model.*
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
        private const val ACK_TIMEOUT_MS = 3000L
        private const val MAX_ACK_RETRIES = 3
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

    private val _estimatedBandwidthBps = MutableStateFlow(0L)
    val estimatedBandwidthBps: StateFlow<Long> = _estimatedBandwidthBps.asStateFlow()

    private val _packetLossRate = MutableStateFlow(0f)
    val packetLossRate: StateFlow<Float> = _packetLossRate.asStateFlow()

    private val _estimatedTransport = MutableStateFlow("BLE")
    val estimatedTransport: StateFlow<String> = _estimatedTransport.asStateFlow()

    // Listeners/callbacks
    var onPacketReceivedListener: ((SamekanPacket) -> Unit)? = null
    var onPacketFailedListener: ((SamekanPacket) -> Unit)? = null
    var onPacketAckedListener: ((String) -> Unit)? = null
    var onPeerDisconnectedListener: ((deviceId: String) -> Unit)? = null
    var onFileTransferProgressListener: ((payloadId: Long, progress: Float, status: String, file: File?) -> Unit)? = null

    // Track active connection requests and backoffs
    private val activeConnections = ConcurrentHashMap<String, String>() // endpointId -> deviceId
    private val backoffAttempts = ConcurrentHashMap<String, Int>() // deviceId -> attempt count
    private val lastConnectionTime = ConcurrentHashMap<String, Long>() // deviceId -> timestamp
    private val discoveredEndpoints = ConcurrentHashMap<String, EndpointInfo>() // endpointId -> Info

    // Duplicate detection and reliability
    private val duplicatePacketCache = ConcurrentHashMap.newKeySet<String>()
    private val pingTimes = ConcurrentHashMap<String, Long>() // ping messageId -> start timestamp

    // Reliable delivery queue
    data class PendingAck(
        val packet: SamekanPacket,
        val targetEndpointIds: List<String>,
        var attempts: Int = 1,
        var lastSentTime: Long = System.currentTimeMillis()
    )
    private val pendingAcks = ConcurrentHashMap<String, PendingAck>()

    // Incoming file payloads cached by payload ID
    private val incomingFilePayloads = ConcurrentHashMap<Long, Payload>()

    private var currentRoomId: String? = null
    
    // Throughput monitor variables
    private var bytesSentInWindow = 0L
    private var windowStartTime = System.currentTimeMillis()

    data class ConnectionState(
        val endpointId: String,
        val deviceId: String,
        val displayName: String,
        val roomId: String,
        val isConnected: Boolean,
        val rssi: Int = -50,
        val latencyMs: Long = 0L
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

    init {
        startAckRetransmissionLoop()
        startBandwidthMonitoringLoop()
    }

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
        pendingAcks.clear()
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
            bytesSentInWindow += payloadBytes.size
            
            // Queue reliable packets for ACK tracking
            if (isReliablePacket(packet)) {
                pendingAcks[packet.messageId] = PendingAck(
                    packet = packet,
                    targetEndpointIds = endpoints,
                    attempts = 1,
                    lastSentTime = System.currentTimeMillis()
                )
            }

            connectionsClient.sendPayload(endpoints, payload)
                .addOnSuccessListener {
                    Logger.debug(TAG, "Payload type ${packet.type} sent to ${endpoints.size} peers.")
                }
                .addOnFailureListener { e ->
                    Logger.error(TAG, "Failed to send payload to peers", e)
                }
        }
    }

    fun sendPacketToPeer(packet: SamekanPacket, targetEndpointId: String) {
        val payloadBytes = PacketSerializer.serializePacket(packet)
        val payload = Payload.fromBytes(payloadBytes)
        _totalPacketsSent.value++
        bytesSentInWindow += payloadBytes.size

        if (isReliablePacket(packet)) {
            pendingAcks[packet.messageId] = PendingAck(
                packet = packet,
                targetEndpointIds = listOf(targetEndpointId),
                attempts = 1,
                lastSentTime = System.currentTimeMillis()
            )
        }

        connectionsClient.sendPayload(targetEndpointId, payload)
            .addOnSuccessListener {
                Logger.debug(TAG, "Unicast packet type ${packet.type} dispatched to $targetEndpointId.")
            }
            .addOnFailureListener { e ->
                Logger.error(TAG, "Failed to send unicast payload to $targetEndpointId", e)
            }
    }

    private fun isReliablePacket(packet: SamekanPacket): Boolean {
        return when (packet.type) {
            PacketType.TEXT,
            PacketType.SOS_ALERT,
            PacketType.FILE_HEADER,
            PacketType.FILE_CHUNK -> true
            else -> false
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

    fun sendFileHeader(fileId: String, fileName: String, fileType: String, fileSize: Long, checksum: String, totalChunks: Int, roomId: String) {
        val headerPayload = FileHeaderPayload(fileId, fileName, fileType, fileSize, checksum, totalChunks)
        val jsonPayload = PacketSerializer.serializeFileHeaderPayload(headerPayload)
        
        val headerPacket = SamekanPacket(
            messageId = "FH-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
            roomId = roomId,
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayNameProvider(),
            type = PacketType.FILE_HEADER,
            timestamp = System.currentTimeMillis(),
            ttl = 2,
            payload = jsonPayload
        )
        broadcastPacket(headerPacket)
    }

    fun sendFileChunk(fileId: String, chunkIndex: Int, totalChunks: Int, dataBase64: String, roomId: String) {
        val chunkPayload = FileChunkPayload(fileId, chunkIndex, totalChunks, dataBase64)
        val jsonPayload = PacketSerializer.serializeFileChunkPayload(chunkPayload)
        
        val chunkPacket = SamekanPacket(
            messageId = "FC-${fileId}-${chunkIndex}",
            roomId = roomId,
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayNameProvider(),
            type = PacketType.FILE_CHUNK,
            timestamp = System.currentTimeMillis(),
            ttl = 2,
            payload = jsonPayload
        )
        broadcastPacket(chunkPacket)
    }

    fun relayPacket(packet: SamekanPacket, excludeEndpointId: String?) {
        val payloadBytes = PacketSerializer.serializePacket(packet)
        val payload = Payload.fromBytes(payloadBytes)
        val endpoints = _connectedPeers.value.keys.filter { it != excludeEndpointId }
        if (endpoints.isNotEmpty()) {
            _totalRelays.value++
            connectionsClient.sendPayload(endpoints, payload)
                .addOnSuccessListener {
                    Logger.info(TAG, "Mesh relay: Forwarded ${packet.messageId} to ${endpoints.size} peers. Hop: ${packet.hopCount}")
                }
                .addOnFailureListener { e ->
                    Logger.error(TAG, "Failed to relay packet in mesh", e)
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

                        // Loop prevention: check duplicate cache
                        if (duplicatePacketCache.contains(packet.messageId)) {
                            _droppedPackets.value++
                            updatePacketLossDiagnostics()
                            return
                        }
                        duplicatePacketCache.add(packet.messageId)

                        // Process ping RTT diagnostics
                        if (packet.type == PacketType.PING) {
                            val originalTime = pingTimes.remove(packet.messageId)
                            if (originalTime != null) {
                                val rtt = System.currentTimeMillis() - originalTime
                                _averageLatencyMs.value = if (_averageLatencyMs.value == 0L) rtt else (_averageLatencyMs.value + rtt) / 2
                                updateEstimatedTransport(rtt)
                                updatePeerLatency(endpointId, rtt)
                            } else {
                                // Reply to ping immediately
                                val pong = packet.copy(
                                    senderDeviceId = localDeviceId,
                                    senderDisplayName = localDisplayNameProvider()
                                )
                                sendPacketToPeer(pong, endpointId)
                            }
                        }

                        // Process reliable packet acknowledgements
                        if (packet.type == PacketType.CHAT_ACK) {
                            val parts = packet.payload.split(":", limit = 2)
                            val messageId = parts.getOrNull(0) ?: packet.payload
                            removePendingAck(messageId)
                        } else if (packet.type == PacketType.SOS_ACK) {
                            removePendingAck(packet.payload)
                        }

                        // Relaying via mesh (TTL decrement)
                        if (packet.ttl > 1) {
                            val decrementedTtl = packet.ttl - 1
                            val incrementedHop = packet.hopCount + 1
                            val relayedPacket = packet.copy(
                                ttl = decrementedTtl,
                                hopCount = incrementedHop
                            )
                            relayPacket(relayedPacket, excludeEndpointId = endpointId)
                        }

                        // Process packet locally if destined for us
                        if (packet.targetDeviceId == null || packet.targetDeviceId == localDeviceId) {
                            onPacketReceivedListener?.invoke(packet)
                        }
                    } else {
                        _droppedPackets.value++
                        updatePacketLossDiagnostics()
                    }
                }
            } else if (payload.type == Payload.Type.FILE) {
                incomingFilePayloads[payload.id] = payload
                Logger.info(TAG, "Incoming stream payload received with ID: ${payload.id}")
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

    private fun startAckRetransmissionLoop() {
        scope.launch {
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val retransmits = mutableListOf<PendingAck>()

                pendingAcks.forEach { (msgId, pending) ->
                    if (now - pending.lastSentTime >= ACK_TIMEOUT_MS) {
                        if (pending.attempts >= MAX_ACK_RETRIES) {
                            Logger.warn(TAG, "Reliable packet ${pending.packet.messageId} expired. Retries exhausted.")
                            pendingAcks.remove(msgId)
                            _droppedPackets.value++
                            updatePacketLossDiagnostics()
                            onPacketFailedListener?.invoke(pending.packet)
                        } else {
                            pending.attempts++
                            pending.lastSentTime = now
                            retransmits.add(pending)
                        }
                    }
                }

                retransmits.forEach { pending ->
                    Logger.info(TAG, "Retransmitting packet ${pending.packet.messageId} (Attempt ${pending.attempts})")
                    val payloadBytes = PacketSerializer.serializePacket(pending.packet)
                    val payload = Payload.fromBytes(payloadBytes)
                    pending.targetEndpointIds.forEach { epId ->
                        connectionsClient.sendPayload(epId, payload)
                    }
                }
            }
        }
    }

    private fun removePendingAck(messageId: String) {
        val removed = pendingAcks.remove(messageId)
        if (removed != null) {
            Logger.debug(TAG, "ACK received. Packet $messageId cleared from pending queue.")
            onPacketAckedListener?.invoke(messageId)
        }
    }

    private fun startBandwidthMonitoringLoop() {
        scope.launch {
            while (true) {
                delay(5000)
                val now = System.currentTimeMillis()
                val durationSec = (now - windowStartTime) / 1000f
                if (durationSec > 0) {
                    val bps = (bytesSentInWindow * 8 / durationSec).toLong()
                    _estimatedBandwidthBps.value = bps
                }
                bytesSentInWindow = 0L
                windowStartTime = now
            }
        }
    }

    private fun updatePacketLossDiagnostics() {
        val total = _totalPacketsReceived.value + _droppedPackets.value
        if (total > 0) {
            _packetLossRate.value = _droppedPackets.value.toFloat() / total
        }
    }

    private fun updateEstimatedTransport(rtt: Long) {
        _estimatedTransport.value = when {
            rtt < 30 -> "Wi-Fi Direct (Excellent)"
            rtt < 120 -> "Bluetooth Classic (Good)"
            else -> "Bluetooth LE (Fair)"
        }
    }

    private fun updatePeerLatency(endpointId: String, latency: Long) {
        val current = _connectedPeers.value
        val peer = current[endpointId]
        if (peer != null) {
            _connectedPeers.value = current + (endpointId to peer.copy(latencyMs = latency))
        }
    }
}
