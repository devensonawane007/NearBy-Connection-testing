package com.example.samekanprivatetrekroom.domain.serializer

import com.example.samekanprivatetrekroom.domain.model.*
import com.example.samekanprivatetrekroom.data.local.CryptoHelper
import com.google.gson.Gson
import javax.crypto.spec.SecretKeySpec

object PacketSerializer {
    private val gson = Gson()
    
    @Volatile
    private var activeSecretKey: SecretKeySpec? = null

    // Set the encryption key derived from custom room password
    fun setRoomPassword(password: String?) {
        activeSecretKey = if (!password.isNullOrBlank()) {
            CryptoHelper.deriveKey(password)
        } else {
            null
        }
    }

    // Clear active key on room exit
    fun clearRoomKey() {
        activeSecretKey = null
    }

    fun serializePacket(packet: SamekanPacket): ByteArray {
        val key = activeSecretKey ?: CryptoHelper.deriveKey(packet.roomId) // Fallback to roomId-derived key
        val encryptedPayload = CryptoHelper.encrypt(packet.payload, key)
        val encryptedPacket = packet.copy(payload = encryptedPayload)
        
        val json = gson.toJson(encryptedPacket)
        return json.toByteArray(Charsets.UTF_8)
    }

    fun deserializePacket(bytes: ByteArray): SamekanPacket? {
        return try {
            val json = String(bytes, Charsets.UTF_8)
            val encryptedPacket = gson.fromJson(json, SamekanPacket::class.java) ?: return null
            
            val key = activeSecretKey ?: CryptoHelper.deriveKey(encryptedPacket.roomId)
            val decryptedPayload = CryptoHelper.decrypt(encryptedPacket.payload, key)
            
            encryptedPacket.copy(payload = decryptedPayload)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun serializeGpsPayload(gps: GpsPayload): String = gson.toJson(gps)
    fun deserializeGpsPayload(json: String): GpsPayload? = try {
        gson.fromJson(json, GpsPayload::class.java)
    } catch (e: Exception) { null }

    fun serializeRoomSyncPayload(payload: RoomSyncPayload): String = gson.toJson(payload)
    fun deserializeRoomSyncPayload(json: String): RoomSyncPayload? = try {
        gson.fromJson(json, RoomSyncPayload::class.java)
    } catch (e: Exception) { null }

    fun serializeSosPayload(payload: SosPayload): String = gson.toJson(payload)
    fun deserializeSosPayload(json: String): SosPayload? = try {
        gson.fromJson(json, SosPayload::class.java)
    } catch (e: Exception) { null }

    fun serializeFileHeaderPayload(payload: FileHeaderPayload): String = gson.toJson(payload)
    fun deserializeFileHeaderPayload(json: String): FileHeaderPayload? = try {
        gson.fromJson(json, FileHeaderPayload::class.java)
    } catch (e: Exception) { null }

    fun serializeFileChunkPayload(payload: FileChunkPayload): String = gson.toJson(payload)
    fun deserializeFileChunkPayload(json: String): FileChunkPayload? = try {
        gson.fromJson(json, FileChunkPayload::class.java)
    } catch (e: Exception) { null }

    fun serializeTypingPayload(payload: TypingPayload): String = gson.toJson(payload)
    fun deserializeTypingPayload(json: String): TypingPayload? = try {
        gson.fromJson(json, TypingPayload::class.java)
    } catch (e: Exception) { null }
}
