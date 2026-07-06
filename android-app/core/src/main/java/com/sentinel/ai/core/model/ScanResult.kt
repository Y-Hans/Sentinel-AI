package com.sentinel.ai.core.model

data class ScanResult(
    val id: String,
    val source: String,
    val senderDisplayName: String? = null,
    val senderIdentifier: String? = null,
    val riskLevel: RiskLevel,
    val riskScore: Float,
    val explanation: String,
    val timestamp: Long
)
