package com.example.samekanprivatetrekroom.domain.model

enum class PacketType {
    TEXT,
    GPS,
    PING,
    ROOM_JOIN,
    ROOM_STATUS
}

data class SamekanPacket(
    val messageId: String,
    val roomId: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val type: PacketType,
    val timestamp: Long,
    val ttl: Int,
    val payload: String
)

data class GpsPayload(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float
)

data class TrekRoom(
    val roomId: String,
    val roomName: String,
    val creatorDeviceId: String,
    val createdAt: Long,
    val status: String // ACTIVE, CLOSED
)

data class Peer(
    val endpointId: String,
    val deviceId: String,
    val displayName: String,
    val roomId: String,
    val connected: Boolean,
    val lastSeen: Long,
    val rssi: Int? = null
)

data class MemberLocation(
    val deviceId: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)
