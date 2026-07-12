package com.sentinel.ai.ui.screens.copilot

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class CopilotViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CopilotUiState())
    val uiState: StateFlow<CopilotUiState> = _uiState.asStateFlow()

    fun onAction(action: CopilotUiAction) {
        when (action) {
            is CopilotUiAction.UpdateInput -> _uiState.update { it.copy(inputText = action.text) }
            CopilotUiAction.SendMessage -> sendMessage()
            CopilotUiAction.ClearHistory -> _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    private fun sendMessage() {
        // Placeholder: invoke copilot use case in a later phase.
    }
}
