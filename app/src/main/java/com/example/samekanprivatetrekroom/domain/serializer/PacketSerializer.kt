package com.example.samekanprivatetrekroom.domain.serializer

import com.example.samekanprivatetrekroom.domain.model.GpsPayload
import com.example.samekanprivatetrekroom.domain.model.SamekanPacket
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

    fun serializeGpsPayload(gps: GpsPayload): String {
        return gson.toJson(gps)
    }

    fun deserializeGpsPayload(json: String): GpsPayload? {
        return try {
            gson.fromJson(json, GpsPayload::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
