package com.sentinel.ai.ui.screens.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.ai.core.data.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onAction(action: ScannerUiAction) {
        when (action) {
            is ScannerUiAction.UpdateInput -> _uiState.update { it.copy(scanInput = action.text) }
            is ScannerUiAction.SetScanType -> _uiState.update { it.copy(scanType = action.type) }
            ScannerUiAction.RunScan -> runScan()
            ScannerUiAction.ClearResult -> _uiState.update { it.copy(scanResult = null, error = null) }
        }
    }

    private fun runScan() {
        val input = _uiState.value.scanInput.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(error = "Enter a link or file path before scanning.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanResult = null, error = null) }
            runCatching {
                when (_uiState.value.scanType) {
                    ScanType.FILE -> scanRepository.scanFile(Uri.parse(input))
                    ScanType.LINK, ScanType.TEXT -> scanRepository.scanLink(input)
                }
            }.onSuccess { result ->
                _uiState.update { it.copy(isScanning = false, scanResult = result) }
            }.onFailure { error ->
                _uiState.update { it.copy(isScanning = false, error = error.message ?: "Scan failed") }
            }
        }
    }
}
