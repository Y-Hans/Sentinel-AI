package com.sentinel.ai.agents

import com.sentinel.ai.agents.base.AgentInput
import com.sentinel.ai.agents.base.AgentResult
import com.sentinel.ai.core.event.ThreatEventBus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkAgentCoordinator @Inject constructor(
    private val threatEventBus: ThreatEventBus
) {
    suspend fun process(url: String): AgentResult {
        return AgentResult(scanResult = null)
    }

    suspend fun dispatch(input: AgentInput.Link) {
        process(input.url)
    }
}
