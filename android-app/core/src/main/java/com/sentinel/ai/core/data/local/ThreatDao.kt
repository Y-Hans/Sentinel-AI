package com.sentinel.ai.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ThreatDao {
    @Query("SELECT * FROM threat_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentThreatRecords(limit: Int): List<ThreatRecordEntity>

    @Query("SELECT * FROM threat_records WHERE recordType = :recordType AND (timestamp < :cursorTimestamp OR (timestamp = :cursorTimestamp AND id < :cursorId)) ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getThreatRecordsBefore(recordType: String, cursorTimestamp: Long, cursorId: String, limit: Int): List<ThreatRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThreatRecord(record: ThreatRecordEntity)
}
