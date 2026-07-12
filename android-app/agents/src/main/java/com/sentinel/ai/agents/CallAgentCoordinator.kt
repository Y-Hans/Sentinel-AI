package com.sentinel.ai.agents

import com.sentinel.ai.agents.base.AgentInput
import com.sentinel.ai.agents.base.AgentResult
import com.sentinel.ai.core.event.ThreatEventBus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallAgentCoordinator @Inject constructor(
    private val threatEventBus: ThreatEventBus
) {
    suspend fun process(number: String, timestamp: Long): AgentResult {
        return AgentResult(scanResult = null)
    }

    suspend fun dispatch(input: AgentInput.Call) {
        process(input.number, input.timestamp)
    }
}
