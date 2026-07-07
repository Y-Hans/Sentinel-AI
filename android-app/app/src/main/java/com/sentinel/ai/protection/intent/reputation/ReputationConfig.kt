package com.sentinel.ai.protection.intent.reputation

data class ReputationConfig(
    val openPhishFeedUrl: String,
    val openPhishApiKey: String,
    val lookupTimeoutMs: Long
) {
    val isOpenPhishEnabled: Boolean
        get() = openPhishFeedUrl.isNotBlank()
}
