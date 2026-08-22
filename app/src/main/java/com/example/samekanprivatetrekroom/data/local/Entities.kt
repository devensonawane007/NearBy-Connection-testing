package com.example.samekanprivatetrekroom.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trek_rooms")
data class RoomEntity(
    @PrimaryKey val roomId: String,
    val roomName: String,
    val description: String = "",
    val passwordHash: String? = null,
    val creatorDeviceId: String,
    val hostDeviceId: String,
    val createdAt: Long,
    val status: String // ACTIVE, CLOSED
)

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val connected: Boolean,
    val lastSeen: Long,
    val role: String = "MEMBER", // HOST, MEMBER
    val batteryLevel: Int = 100,
    val gpsAccuracy: Float = 0f,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val altitude: Double = 0.0,
    val status: String = "Active"
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val roomId: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val text: String,
    val timestamp: Long,
    val deliveryStatus: String, // SENDING, SENT, FAILED, READ
    val replyToId: String? = null,
    val reactions: String = "", // Comma-separated or JSON list of reactions
    val isPinned: Boolean = false
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f
)

@Entity(tableName = "location_history")
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val accuracy: Float = 0f,
    val timestamp: Long
)

@Entity(tableName = "file_transfers")
data class FileTransferEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val fileType: String, // IMAGE, PDF, GPX, TEXT, ZIP
    val absolutePath: String,
    val isIncoming: Boolean,
    val progress: Float, // 0.0f to 1.0f
    val status: String, // SENDING, COMPLETED, FAILED, PAUSED, INTERRUPTED
    val timestamp: Long,
    val senderId: String,
    val fileSize: Long,
    val checksum: String = "",
    val chunkIndex: Int = 0,
    val totalChunks: Int = 0
)

@Entity(tableName = "sos_history")
data class SosHistoryEntity(
    @PrimaryKey val messageId: String,
    val roomId: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val emergencyType: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val batteryLevel: Int,
    val heading: Float,
    val speed: Float,
    val timestamp: Long,
    val status: String // ACTIVE, ACKNOWLEDGED, RESOLVED
)

@Entity(tableName = "voice_history")
data class VoiceHistoryEntity(
    @PrimaryKey val messageId: String,
    val roomId: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val durationMs: Long,
    val localFilePath: String,
    val timestamp: Long
)

@Entity(tableName = "packet_logs")
data class PacketLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packetId: String,
    val roomId: String,
    val senderId: String,
    val type: String,
    val direction: String, // SENT, RECEIVED, RELAYED
    val payloadSize: Int,
    val hopCount: Int,
    val timestamp: Long
)

@Entity(tableName = "diagnostics")
data class DiagnosticsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val connectedPeersCount: Int,
    val avgLatencyMs: Long,
    val packetLossRate: Float,
    val bandwidthBps: Long,
    val batteryLevel: Int,
    val activeTransports: String // Comma-separated list
)

@Entity(tableName = "member_stats")
data class MemberStatsEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val packetsDropped: Int = 0,
    val avgLatencyMs: Long = 0L,
    val batteryLevel: Int = 100,
    val lastSeen: Long
)

@Entity(tableName = "battery_history")
data class BatteryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val batteryLevel: Int,
    val timestamp: Long
)
