package com.example.samekanprivatetrekroom.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trek_rooms")
data class RoomEntity(
    @PrimaryKey val roomId: String,
    val roomName: String,
    val creatorDeviceId: String,
    val createdAt: Long,
    val status: String // ACTIVE, CLOSED
)

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val connected: Boolean,
    val lastSeen: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val roomId: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val text: String,
    val timestamp: Long,
    val deliveryStatus: String // SENDING, SENT, FAILED
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)
