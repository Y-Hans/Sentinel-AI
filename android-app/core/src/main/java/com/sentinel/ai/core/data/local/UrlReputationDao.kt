package com.sentinel.ai.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UrlReputationDao {
    @Query("SELECT * FROM url_reputation WHERE target = :target AND type = :type LIMIT 1")
    suspend fun getReputation(target: String, type: String): UrlReputationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReputation(entity: UrlReputationEntity)
}
