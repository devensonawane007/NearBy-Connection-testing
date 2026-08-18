package com.example.samekanprivatetrekroom.domain.serializer

import com.example.samekanprivatetrekroom.domain.model.GpsPayload
import com.example.samekanprivatetrekroom.domain.model.SamekanPacket
import com.example.samekanprivatetrekroom.domain.model.RoomSyncPayload
import com.example.samekanprivatetrekroom.domain.model.SosPayload
import com.example.samekanprivatetrekroom.domain.model.FileHeaderPayload
import com.google.gson.Gson

object PacketSerializer {
    private val gson = Gson()

    fun serializePacket(packet: SamekanPacket): ByteArray {
        val json = gson.toJson(packet)
        return json.toByteArray(Charsets.UTF_8)
    }

    fun deserializePacket(bytes: ByteArray): SamekanPacket? {
        return try {
            val json = String(bytes, Charsets.UTF_8)
            gson.fromJson(json, SamekanPacket::class.java)
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
}
