package com.sentinel.ai.protection.intent

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.link.BrowserLauncher
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.IntentPayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import com.sentinel.ai.ui.theme.SentinelTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Loading activity that invokes threat analysis on incoming intent payloads
 * and displays the result (Safe / Suspicious / Malicious).
 */
@AndroidEntryPoint
class ScanLoadingActivity : ComponentActivity() {

    @Inject
    lateinit var threatAnalyzer: IntentThreatAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val payloadType = intent.getStringExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE)
        val payloadValue = intent.getStringExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE)
        val fromViewIntent = intent.getBooleanExtra(IntentPayloadExtras.EXTRA_FROM_VIEW_INTENT, false)

        val payload = when (payloadType) {
            IntentPayloadExtras.TYPE_URL -> payloadValue?.let { UrlPayload(it) }
            IntentPayloadExtras.TYPE_FILE -> payloadValue?.let { FilePayload(Uri.parse(it)) }
            else -> null
        }

        setContent {
            SentinelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var uiState by remember { mutableStateOf<ScanUiState>(ScanUiState.Loading) }

                    LaunchedEffect(payload) {
                        if (payload == null) {
                            uiState = ScanUiState.Error("Unsupported or missing payload data.")
                            return@LaunchedEffect
                        }
                        try {
                            delay(1000) // Delay to display "Analyzing..." state clearly to the user
                            val result = threatAnalyzer.analyze(payload)
                            uiState = ScanUiState.Success(result, payload)
                        } catch (e: Exception) {
                            Log.e(TAG, "Analysis failed", e)
                            uiState = ScanUiState.Error(e.message ?: "Failed to perform analysis.")
                        }
                    }

                    when (val state = uiState) {
                        is ScanUiState.Loading -> {
                            val displayType = when (payloadType) {
                                IntentPayloadExtras.TYPE_URL -> "URL"
                                IntentPayloadExtras.TYPE_FILE -> "FILE"
                                else -> "Payload"
                            }
                            ScanLoadingContent(payloadType = displayType)
                        }
                        is ScanUiState.Success -> {
                            ScanResultContent(
                                result = state.result,
                                payload = state.payload,
                                fromViewIntent = fromViewIntent,
                                onClose = { finish() },
                                onOpenUrl = { url ->
                                    val launched = openUrlInBrowser(url)
                                    Log.d("ScanLoadingActivity", "Browser launched: $launched")
                                    launched
                                }
                            )
                        }
                        is ScanUiState.Error -> {
                            ScanErrorContent(
                                message = state.message,
                                onClose = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openUrlInBrowser(url: String) = BrowserLauncher().launch(this, url)

    private companion object {
        const val TAG = "ScanLoadingActivity"
    }
}

private sealed interface ScanUiState {
    data object Loading : ScanUiState
    data class Success(val result: ScanResult, val payload: IntentPayload) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

@Composable
private fun ScanLoadingContent(payloadType: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sentinel AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Preparing $payloadType payload",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "Analyzing...",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )
        CircularProgressIndicator(
            modifier = Modifier
                .padding(top = 20.dp)
                .size(64.dp)
        )
    }
}

@Composable
private fun ScanResultContent(
    result: ScanResult,
    payload: IntentPayload,
    fromViewIntent: Boolean,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Boolean
) {
    val (statusLabel, statusColor) = when (result.decision) {
        ProtectionDecision.ALLOW -> "No high-risk evidence" to MaterialTheme.colorScheme.primary
        ProtectionDecision.WARN -> "Suspicious" to MaterialTheme.colorScheme.secondary
        ProtectionDecision.BLOCK -> "Blocked" to MaterialTheme.colorScheme.tertiary
    }

    val itemLabel = when (payload) {
        is UrlPayload -> "Scanned URL"
        is FilePayload -> "Scanned File"
    }

    val itemValue = when (payload) {
        is UrlPayload -> payload.url
        is FilePayload -> payload.uri.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Analysis Result",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = statusLabel.uppercase(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = statusColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Risk Score: ${(result.riskScore).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = result.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = itemLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = itemValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(48.dp))

        val canContinueToUrl = payload is UrlPayload &&
            result.decision == ProtectionDecision.ALLOW &&
            fromViewIntent
        val buttonText = if (canContinueToUrl) "Continue to website" else "Close"
        val buttonAction = {
            if (canContinueToUrl) {
                val launched = onOpenUrl((payload as UrlPayload).url)

                if (launched) {
                    onClose()
                } else {
                    Log.e("ScanLoadingActivity", "Browser launch failed")
                }
            } else {
                onClose()
            }

            // ✅ IMPORTANT: force Unit return
            Unit
        }

        Button(
            onClick = buttonAction,
            colors = ButtonDefaults.buttonColors(containerColor = statusColor),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun ScanErrorContent(
    message: String,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Scan Failed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                text = "Close",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
