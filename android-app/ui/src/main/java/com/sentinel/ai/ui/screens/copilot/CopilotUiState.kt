package com.sentinel.ai.ui.screens.copilot

data class CopilotMessage(
    val id: String,
    val content: String,
    val isFromUser: Boolean
)

data class CopilotUiState(
    val messages: List<CopilotMessage> = emptyList(),
    val inputText: String = "",
    val isTyping: Boolean = false,
    val error: String? = null
)

sealed interface CopilotUiAction {
    data class UpdateInput(val text: String) : CopilotUiAction
    data object SendMessage : CopilotUiAction
    data object ClearHistory : CopilotUiAction
}
