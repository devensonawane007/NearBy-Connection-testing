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
    val reactions: String = "" // Comma-separated or JSON list of reactions
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
    val timestamp: Long
)

@Entity(tableName = "file_transfers")
data class FileTransferEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val fileType: String, // IMAGE, PDF, GPX, TEXT
    val absolutePath: String,
    val isIncoming: Boolean,
    val progress: Float, // 0.0f to 1.0f
    val status: String, // SENDING, COMPLETED, FAILED
    val timestamp: Long,
    val senderId: String,
    val fileSize: Long
)
