package com.sentinel.ai.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ThreatDao {
    @Query("SELECT * FROM threat_records ORDER BY timestamp DESC")
    suspend fun getAllThreatRecords(): List<ThreatRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThreatRecord(record: ThreatRecordEntity)
}
