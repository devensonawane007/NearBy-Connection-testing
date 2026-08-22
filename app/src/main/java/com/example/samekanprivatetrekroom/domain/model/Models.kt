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
    FILE_HEADER,
    FILE_CHUNK,
    TYPING
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
    val targetDeviceId: String? = null,
    val hopCount: Int = 0,
    val sequenceNumber: Long = 0L,
    val priority: Int = 1 // 0 = HIGH (SOS), 1 = MEDIUM (Voice/Chat), 2 = LOW (GPS/Logs)
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
    val role: String = "MEMBER", // HOST, MEMBER
    val rssi: Int? = null,
    val batteryLevel: Int = 100,
    val latencyMs: Long = 0L
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
    val emergencyType: String, // Lost, Injury, Wildlife, Weather, Medical, Fall, Fire, Flood, Landslide, Avalanche, Other
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val batteryLevel: Int,
    val heading: Float = 0f,
    val speed: Float = 0f
)

data class FileHeaderPayload(
    val fileId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val checksum: String = "",
    val totalChunks: Int = 0
)

data class FileChunkPayload(
    val fileId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: String // Base64 encoded chunk data
)

data class TypingPayload(
    val isTyping: Boolean
)

data class RoomQrData(
    val roomId: String,
    val roomName: String
)
