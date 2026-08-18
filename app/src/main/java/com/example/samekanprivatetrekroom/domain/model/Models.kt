package com.example.samekanprivatetrekroom.domain.model

enum class PacketType {
    TEXT,
    GPS,
    PING,
    ROOM_SYNC,
    PTT_CHUNK,
    SOS_ALERT,
    SOS_ACK,
    CHAT_ACK,
    CHAT_READ,
    FILE_HEADER
}

data class SamekanPacket(
    val messageId: String,
    val roomId: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val type: PacketType,
    val timestamp: Long,
    val ttl: Int,
    val payload: String,
    val targetDeviceId: String? = null
)

data class GpsPayload(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val batteryLevel: Int = 100
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
    val role: String = "MEMBER",
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

data class RoomSyncPayload(
    val description: String,
    val passwordHash: String?,
    val hostDeviceId: String,
    val members: List<MemberSyncInfo>
)

data class MemberSyncInfo(
    val deviceId: String,
    val displayName: String,
    val role: String
)

data class SosPayload(
    val emergencyType: String, // Lost, Injury, Wildlife, Weather, Other
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val batteryLevel: Int
)

data class FileHeaderPayload(
    val fileId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long
)

data class RoomQrData(
    val roomId: String,
    val roomName: String
)

