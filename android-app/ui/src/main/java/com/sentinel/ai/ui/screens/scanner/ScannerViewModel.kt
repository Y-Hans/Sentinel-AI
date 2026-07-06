package com.sentinel.ai.ui.screens.scanner

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onAction(action: ScannerUiAction) {
        when (action) {
            is ScannerUiAction.UpdateInput -> _uiState.update { it.copy(scanInput = action.text) }
            is ScannerUiAction.SetScanType -> _uiState.update { it.copy(scanType = action.type) }
            ScannerUiAction.RunScan -> runScan()
        }
    }

    private fun runScan() {
        // Placeholder: invoke analyze use case in a later phase.
    }
}
