package com.example.samekanprivatetrekroom.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoomEntity::class,
        MemberEntity::class,
        MessageEntity::class,
        LocationEntity::class,
        LocationHistoryEntity::class,
        FileTransferEntity::class,
        SosHistoryEntity::class,
        VoiceHistoryEntity::class,
        PacketLogEntity::class,
        DiagnosticsEntity::class,
        MemberStatsEntity::class,
        BatteryHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun memberDao(): MemberDao
    abstract fun messageDao(): MessageDao
    abstract fun locationDao(): LocationDao
    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun fileTransferDao(): FileTransferDao
    abstract fun sosHistoryDao(): SosHistoryDao
    abstract fun voiceHistoryDao(): VoiceHistoryDao
    abstract fun packetLogDao(): PacketLogDao
    abstract fun diagnosticsDao(): DiagnosticsDao
    abstract fun memberStatsDao(): MemberStatsDao
    abstract fun batteryHistoryDao(): BatteryHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "samekan_trekroom_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
