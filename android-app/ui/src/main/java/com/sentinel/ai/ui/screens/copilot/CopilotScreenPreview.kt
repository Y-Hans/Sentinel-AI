package com.sentinel.ai.ui.screens.copilot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sentinel.ai.ui.theme.SentinelTheme

@Preview(name = "Dark", showBackground = true)
@Composable
private fun CopilotDarkPreview() {
    SentinelTheme(darkTheme = true, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            CopilotContent(
                uiState = CopilotUiState(),
                onAction = {}
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CopilotLightPreview() {
    SentinelTheme(darkTheme = false, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            CopilotContent(
                uiState = CopilotUiState(),
                onAction = {}
            )
        }
    }
}
