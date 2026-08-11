package com.example.samekanprivatetrekroom.data.nearby

import android.content.Context
import android.util.Log
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
import java.util.concurrent.ConcurrentHashMap

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

    // Listeners/callbacks
    var onPacketReceivedListener: ((SamekanPacket) -> Unit)? = null
    var onPeerDisconnectedListener: ((deviceId: String) -> Unit)? = null

    // Track active connection requests and backoffs
    private val activeConnections = ConcurrentHashMap<String, String>() // endpointId -> deviceId
    private val backoffAttempts = ConcurrentHashMap<String, Int>() // deviceId -> attempt count
    private val lastConnectionTime = ConcurrentHashMap<String, Long>() // deviceId -> timestamp
    private val discoveredEndpoints = ConcurrentHashMap<String, EndpointInfo>() // endpointId -> Info

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

        connectionsClient.startAdvertising(
            endpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started successfully for room $roomId")
            _isAdvertising.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising start failed", e)
            _isAdvertising.value = false
            // Retry after delay
            scope.launch {
                delay(5000)
                if (currentRoomId == roomId) startAdvertising(roomId)
            }
        }
    }

    private fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
        Log.d(TAG, "Advertising stopped")
    }

    private fun startDiscovery(roomId: String) {
        if (_isDiscovering.value) return
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started successfully")
            _isDiscovering.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery start failed", e)
            _isDiscovering.value = false
            // Retry after delay
            scope.launch {
                delay(5000)
                if (currentRoomId == roomId) startDiscovery(roomId)
            }
        }
    }

    private fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
        Log.d(TAG, "Discovery stopped")
    }

    private fun disconnectAll() {
        connectionsClient.stopAllEndpoints()
        activeConnections.clear()
    }

    fun acceptPeer(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully accepted connection request from $endpointId")
                _pendingRequests.value = _pendingRequests.value - endpointId
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to accept connection from $endpointId", e)
                _pendingRequests.value = _pendingRequests.value - endpointId
            }
    }

    fun rejectPeer(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully rejected connection request from $endpointId")
                _pendingRequests.value = _pendingRequests.value - endpointId
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to reject connection from $endpointId", e)
                _pendingRequests.value = _pendingRequests.value - endpointId
            }
    }

    fun broadcastPacket(packet: SamekanPacket) {
        val payloadBytes = PacketSerializer.serializePacket(packet)
        val payload = Payload.fromBytes(payloadBytes)
        val endpoints = _connectedPeers.value.keys.toList()
        if (endpoints.isNotEmpty()) {
            connectionsClient.sendPayload(endpoints, payload)
                .addOnSuccessListener {
                    Log.d(TAG, "Payload sent to ${endpoints.size} peers")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send payload to peers", e)
                }
        }
    }

    // Callbacks
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val endpointName = info.endpointName
            Log.d(TAG, "Endpoint found: $endpointId ($endpointName)")

            val parts = endpointName.split(":")
            val deviceId = parts.getOrNull(0) ?: ""
            val displayName = parts.getOrNull(1) ?: ""
            val roomId = parts.getOrNull(2) ?: ""

            if (roomId.isEmpty() || roomId != currentRoomId) {
                Log.d(TAG, "Discovered endpoint is not in current room: $roomId vs $currentRoomId")
                return
            }

            val endpointInfo = EndpointInfo(endpointId, deviceId, displayName, roomId)
            discoveredEndpoints[endpointId] = endpointInfo

            // Trigger connection request if not already connected/pending
            scope.launch {
                initiateConnectionWithBackoff(endpointInfo)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            discoveredEndpoints.remove(endpointId)
        }
    }

    private suspend fun initiateConnectionWithBackoff(endpoint: EndpointInfo) {
        val deviceId = endpoint.deviceId
        val endpointId = endpoint.endpointId

        // Check if already connected or pending
        if (_connectedPeers.value.values.any { it.deviceId == deviceId } ||
            _pendingRequests.value.values.any { it.deviceId == deviceId }
        ) {
            Log.d(TAG, "Already connected/pending with $deviceId. Skipping request.")
            return
        }

        // Apply exponential backoff if previously failed
        val attempts = backoffAttempts[deviceId] ?: 0
        val lastAttempt = lastConnectionTime[deviceId] ?: 0L
        val currentTime = System.currentTimeMillis()

        if (attempts > 0) {
            val backoffMs = getBackoffDelayMs(attempts)
            if (currentTime - lastAttempt < backoffMs) {
                Log.d(TAG, "Backing off connection request to $deviceId. Wait ${backoffMs/1000}s.")
                return
            }
        }

        Log.d(TAG, "Initiating connection request to ${endpoint.displayName} ($endpointId), attempt ${attempts + 1}")
        lastConnectionTime[deviceId] = currentTime
        backoffAttempts[deviceId] = attempts + 1

        val localEndpointName = getEndpointName(currentRoomId ?: "")
        connectionsClient.requestConnection(
            localEndpointName,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            Log.e(TAG, "Request connection failed to $endpointId", e)
        }
    }

    private fun getBackoffDelayMs(attempts: Int): Long {
        return when (attempts) {
            1 -> 5000L      // 5 seconds
            2 -> 10000L     // 10 seconds
            3 -> 20000L     // 20 seconds
            4 -> 40000L     // 40 seconds
            else -> 60000L  // Max 60 seconds
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated: $endpointId (${info.endpointName})")

            val parts = info.endpointName.split(":")
            val deviceId = parts.getOrNull(0) ?: ""
            val displayName = parts.getOrNull(1) ?: ""
            val roomId = parts.getOrNull(2) ?: ""

            // Cache the info for later callbacks (essential for incoming connections)
            val endpointInfo = EndpointInfo(endpointId, deviceId, displayName, roomId)
            discoveredEndpoints[endpointId] = endpointInfo

            // Handle incoming vs outgoing request
            val isIncoming = !info.isIncomingConnection

            val request = PendingRequest(
                endpointId = endpointId,
                deviceId = deviceId,
                displayName = displayName,
                roomId = roomId,
                authenticationDigits = info.authenticationToken,
                isIncoming = isIncoming
            )

            _pendingRequests.value = _pendingRequests.value + (endpointId to request)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            _pendingRequests.value = _pendingRequests.value - endpointId
            val status = result.status.statusCode

            Log.d(TAG, "Connection result for $endpointId: Status $status")

            val endpoint = discoveredEndpoints[endpointId]
            val deviceId = endpoint?.deviceId ?: ""
            val displayName = endpoint?.displayName ?: "Unknown"
            val roomId = endpoint?.roomId ?: ""

            if (status == ConnectionsStatusCodes.STATUS_OK) {
                // Connection successful!
                Log.d(TAG, "Connection SUCCESS to $endpointId ($displayName)")
                activeConnections[endpointId] = deviceId
                backoffAttempts.remove(deviceId) // Reset backoff attempts on success

                val state = ConnectionState(
                    endpointId = endpointId,
                    deviceId = deviceId,
                    displayName = displayName,
                    roomId = roomId,
                    isConnected = true
                )
                _connectedPeers.value = _connectedPeers.value + (endpointId to state)
            } else {
                Log.e(TAG, "Connection FAILURE to $endpointId: code $status")
                // Connection failed or rejected. Backoff already incremented on initiation.
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected: $endpointId")
            val state = _connectedPeers.value[endpointId]
            _connectedPeers.value = _connectedPeers.value - endpointId

            state?.let {
                onPeerDisconnectedListener?.invoke(it.deviceId)
                // Remove from active connections
                activeConnections.remove(endpointId)

                // Schedule reconnection attempt
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
            val bytes = payload.asBytes()
            if (bytes != null) {
                val packet = PacketSerializer.deserializePacket(bytes)
                if (packet != null) {
                    Log.d(TAG, "Packet received from $endpointId, type: ${packet.type}")
                    onPacketReceivedListener?.invoke(packet)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No action needed for small byte payloads
        }
    }
}
