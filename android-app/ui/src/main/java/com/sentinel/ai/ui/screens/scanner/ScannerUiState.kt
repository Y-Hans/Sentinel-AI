package com.sentinel.ai.ui.screens.scanner

import com.sentinel.ai.core.model.ScanResult

enum class ScanType {
    TEXT,
    LINK,
    FILE
}

data class ScannerUiState(
    val scanInput: String = "",
    val scanType: ScanType = ScanType.TEXT,
    val isScanning: Boolean = false,
    val scanResult: ScanResult? = null,
    val error: String? = null,
    val currentTip: String? = null
)

sealed interface ScannerUiAction {
    data class UpdateInput(val text: String) : ScannerUiAction
    data class SetScanType(val type: ScanType) : ScannerUiAction
    data object RunScan : ScannerUiAction
    data object OpenResult : ScannerUiAction
    data object ClearResult : ScannerUiAction
}
