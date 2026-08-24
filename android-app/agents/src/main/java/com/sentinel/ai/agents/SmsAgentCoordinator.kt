package com.sentinel.ai.agents

import com.sentinel.ai.agents.base.AgentInput
import com.sentinel.ai.agents.base.AgentResult
import com.sentinel.ai.core.event.ThreatEventBus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FUTURE EXTENSION POINT: SmsAgentCoordinator
 * Class: UNUSED BUT INTENTIONAL (Scaffolding)
 *
 * This class is a placeholder for future SMS-scanning capabilities.
 * It currently does not provide any active security functionality or SMS interception.
 * See Phase 4 roadmap for implementation details.
 */
@Singleton
class SmsAgentCoordinator @Inject constructor(
    private val threatEventBus: ThreatEventBus
) {
    suspend fun process(sender: String, body: String, timestamp: Long): AgentResult {
        // Placeholder: dispatch to backend via use case in a later phase.
        return AgentResult(scanResult = null)
    }

    suspend fun dispatch(input: AgentInput.Sms) {
        process(input.sender, input.body, input.timestamp)
    }
}
