package com.sentinel.ai.agents

import com.sentinel.ai.agents.base.AgentInput
import com.sentinel.ai.agents.base.AgentResult
import com.sentinel.ai.core.event.ThreatEventBus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FUTURE EXTENSION POINT: MessageAgentCoordinator
 * Class: UNUSED BUT INTENTIONAL (Scaffolding)
 *
 * This class is a placeholder for future messaging-platform scanning capabilities.
 * It currently does not provide any active security functionality.
 * See Phase 4 roadmap for implementation details.
 */
@Singleton
class MessageAgentCoordinator @Inject constructor(
    private val threatEventBus: ThreatEventBus
) {
    suspend fun process(channel: String, content: String, timestamp: Long): AgentResult {
        return AgentResult(scanResult = null)
    }

    suspend fun dispatch(input: AgentInput.Message) {
        process(input.channel, input.content, input.timestamp)
    }
}
