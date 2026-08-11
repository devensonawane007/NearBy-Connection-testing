package com.example.samekanprivatetrekroom.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM trek_rooms LIMIT 1")
    fun getRoomFlow(): Flow<RoomEntity?>

    @Query("SELECT * FROM trek_rooms LIMIT 1")
    suspend fun getRoomSync(): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity)

    @Query("DELETE FROM trek_rooms")
    suspend fun clearRoom()
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY displayName ASC")
    fun getMembersFlow(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members")
    suspend fun getMembersSync(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getMemberById(deviceId: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<MemberEntity>)

    @Query("UPDATE members SET connected = :connected WHERE deviceId = :deviceId")
    suspend fun updateConnectionStatus(deviceId: String, connected: Boolean)

    @Query("DELETE FROM members")
    suspend fun clearMembers()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesFlow(roomId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("DELETE FROM messages")
    suspend fun clearMessages()
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations")
    fun getLocationsFlow(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations")
    suspend fun getLocationsSync(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getLocationById(deviceId: String): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    @Query("DELETE FROM locations")
    suspend fun clearLocations()
}
