package com.sentinel.ai.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "url_reputation")
data class UrlReputationEntity(
    @PrimaryKey val target: String,
    val type: String, // "URL" or "DOMAIN"
    val verdict: String,
    val confidence: Float,
    val firstSeenTimestamp: Long,
    val lastSeenTimestamp: Long,
    val scanCount: Int,
    val latestHeuristicScore: Float,
    val latestMlScore: Float,
    val latestFinalScore: Float,
    val reasons: String
)
