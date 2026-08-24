package com.sentinel.ai.agents

import com.sentinel.ai.agents.base.AgentInput
import com.sentinel.ai.agents.base.AgentResult
import com.sentinel.ai.core.event.ThreatEventBus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FUTURE EXTENSION POINT: CallAgentCoordinator
 * Class: UNUSED BUT INTENTIONAL (Scaffolding)
 *
 * This class is a placeholder for future call-scanning capabilities.
 * It currently does not provide any active security functionality or call interception.
 * See Phase 4 roadmap for implementation details.
 */
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
