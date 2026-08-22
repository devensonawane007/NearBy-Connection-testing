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

    @Query("UPDATE trek_rooms SET hostDeviceId = :newHostId WHERE roomId = :roomId")
    suspend fun updateHost(roomId: String, newHostId: String)
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

    @Query("DELETE FROM members WHERE deviceId = :deviceId")
    suspend fun deleteMember(deviceId: String)

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

    @Query("UPDATE messages SET reactions = :reactions WHERE messageId = :messageId")
    suspend fun updateMessageReactions(messageId: String, reactions: String)

    @Query("UPDATE messages SET isPinned = :pinned WHERE messageId = :messageId")
    suspend fun updateMessagePinned(messageId: String, pinned: Boolean)

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND text LIKE '%' || :query || '%' ORDER BY timestamp ASC")
    fun searchMessages(roomId: String, query: String): Flow<List<MessageEntity>>

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

@Dao
interface LocationHistoryDao {
    @Query("SELECT * FROM location_history WHERE deviceId = :deviceId ORDER BY timestamp ASC")
    fun getTrailFlow(deviceId: String): Flow<List<LocationHistoryEntity>>

    @Query("SELECT * FROM location_history ORDER BY timestamp ASC")
    fun getAllTrailsFlow(): Flow<List<LocationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrailPoint(point: LocationHistoryEntity)

    @Query("DELETE FROM location_history WHERE deviceId = :deviceId")
    suspend fun clearTrailForMember(deviceId: String)

    @Query("DELETE FROM location_history")
    suspend fun clearAllTrails()
}

@Dao
interface FileTransferDao {
    @Query("SELECT * FROM file_transfers ORDER BY timestamp DESC")
    fun getTransfersFlow(): Flow<List<FileTransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: FileTransferEntity)

    @Query("UPDATE file_transfers SET progress = :progress, status = :status WHERE fileId = :fileId")
    suspend fun updateProgress(fileId: String, progress: Float, status: String)

    @Query("UPDATE file_transfers SET progress = :progress, status = :status, chunkIndex = :chunkIndex WHERE fileId = :fileId")
    suspend fun updateProgressAndChunk(fileId: String, progress: Float, status: String, chunkIndex: Int)

    @Query("DELETE FROM file_transfers")
    suspend fun clearTransfers()
}

@Dao
interface SosHistoryDao {
    @Query("SELECT * FROM sos_history ORDER BY timestamp DESC")
    fun getSosHistoryFlow(): Flow<List<SosHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSosAlert(alert: SosHistoryEntity)

    @Query("UPDATE sos_history SET status = :status WHERE messageId = :messageId")
    suspend fun updateSosStatus(messageId: String, status: String)

    @Query("DELETE FROM sos_history")
    suspend fun clearSosHistory()
}

@Dao
interface VoiceHistoryDao {
    @Query("SELECT * FROM voice_history ORDER BY timestamp DESC")
    fun getVoiceHistoryFlow(): Flow<List<VoiceHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceLog(voice: VoiceHistoryEntity)

    @Query("DELETE FROM voice_history")
    suspend fun clearVoiceHistory()
}

@Dao
interface PacketLogDao {
    @Query("SELECT * FROM packet_logs ORDER BY timestamp DESC LIMIT 200")
    fun getPacketLogsFlow(): Flow<List<PacketLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacketLog(log: PacketLogEntity)

    @Query("DELETE FROM packet_logs")
    suspend fun clearPacketLogs()
}

@Dao
interface DiagnosticsDao {
    @Query("SELECT * FROM diagnostics ORDER BY timestamp DESC LIMIT 100")
    fun getDiagnosticsFlow(): Flow<List<DiagnosticsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnostics(diag: DiagnosticsEntity)

    @Query("DELETE FROM diagnostics")
    suspend fun clearDiagnostics()
}

@Dao
interface MemberStatsDao {
    @Query("SELECT * FROM member_stats ORDER BY displayName ASC")
    fun getMemberStatsFlow(): Flow<List<MemberStatsEntity>>

    @Query("SELECT * FROM member_stats WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getStatsForMember(deviceId: String): MemberStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberStats(stats: MemberStatsEntity)

    @Query("DELETE FROM member_stats")
    suspend fun clearMemberStats()
}

@Dao
interface BatteryHistoryDao {
    @Query("SELECT * FROM battery_history WHERE deviceId = :deviceId ORDER BY timestamp ASC")
    fun getBatteryHistoryFlow(deviceId: String): Flow<List<BatteryHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryPoint(point: BatteryHistoryEntity)

    @Query("DELETE FROM battery_history")
    suspend fun clearBatteryHistory()
}
