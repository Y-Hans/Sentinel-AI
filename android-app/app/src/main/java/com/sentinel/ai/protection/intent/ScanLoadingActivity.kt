package com.sentinel.ai.protection.intent

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.sentinel.ai.core.data.ScanRepository
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.validation.UrlInputValidator
import com.sentinel.ai.core.browser.BrowserLauncher
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.IntentPayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import com.sentinel.ai.ui.components.SecurityTipProvider
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

    private var payload: IntentPayload? = null
    private var payloadType: String? = null
    private var invalidUrl = false
    private var contentRendered = false

    @Inject
    lateinit var scanRepository: ScanRepository

    @Inject
    lateinit var securityTipProvider: SecurityTipProvider

    @Inject
    lateinit var browserLauncher: BrowserLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        payloadType = intent.getStringExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE)
        val payloadValue = intent.getStringExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE)
        val parsedPayload = when (payloadType) {
            IntentPayloadExtras.TYPE_URL -> payloadValue
                ?.takeIf(UrlInputValidator::isValid)
                ?.let { UrlPayload(it.trim()) }
            IntentPayloadExtras.TYPE_FILE -> payloadValue?.let { FilePayload(Uri.parse(it)) }
            else -> null
        }
        payload = parsedPayload
        invalidUrl = payloadType == IntentPayloadExtras.TYPE_URL && payload == null
        Log.d(ML_TAG, "payload.javaClass.name=${parsedPayload?.javaClass?.name ?: "null"}")
        if (parsedPayload is UrlPayload) {
            val safeUrl = com.sentinel.ai.core.utils.UrlLogger.redactUrl(parsedPayload.url)
            Log.d(ML_TAG, "payload.url=$safeUrl")
        } else {
            Log.d(ML_TAG, "Skipping ML: payload is not UrlPayload")
        }

    }

    override fun onStart() {
        super.onStart()
        if (contentRendered) return
        contentRendered = true

        val scanPayload = payload
        val scanPayloadType = payloadType
        val scanInvalidUrl = invalidUrl

        setContent {
            SentinelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var uiState by remember { mutableStateOf<ScanUiState>(ScanUiState.Loading(securityTipProvider.getRandomTip())) }

                    LaunchedEffect(scanPayload) {
                        if (scanPayload == null) {
                            uiState = ScanUiState.Error(
                                if (scanInvalidUrl) "Enter a valid URL" else "Unsupported or missing payload data."
                            )
                            return@LaunchedEffect
                        }
                        try {
                            delay(1000) // Delay to display "Analyzing..." state clearly to the user
                            val result = when (scanPayload) {
                                is UrlPayload -> scanRepository.scanLink(scanPayload.url)
                                is FilePayload -> scanRepository.scanFile(scanPayload.uri)
                            }

                            if (shouldAutoLaunch(result, scanPayload)) {
                                val launched = openUrlInBrowser((scanPayload as UrlPayload).url)
                                if (launched) {
                                    finish()
                                } else {
                                    uiState = ScanUiState.Error("No application available to open this link.")
                                }
                            } else {
                                uiState = ScanUiState.Success(result, scanPayload)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Analysis failed", e)
                            uiState = ScanUiState.Error(e.message ?: "Failed to perform analysis.")
                        }
                    }

                    when (val state = uiState) {
                        is ScanUiState.Loading -> {
                            val displayType = when (scanPayloadType) {
                                IntentPayloadExtras.TYPE_URL -> "URL"
                                IntentPayloadExtras.TYPE_FILE -> "FILE"
                                else -> "Payload"
                            }
                            ScanLoadingContent(payloadType = displayType, currentTip = state.tip)
                        }
                        is ScanUiState.Success -> {
                            ScanResultContent(
                                result = state.result,
                                payload = state.payload,
                                onClose = { finish() },
                                onOpenUrl = { url ->
                                    val launched = openUrlInBrowser(url)
                                    if (launched) {
                                        finish()
                                    }
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

    private fun openUrlInBrowser(url: String) = browserLauncher.launch(url)

    private companion object {
        const val TAG = "ScanLoadingActivity"
        const val ML_TAG = "ML_DEBUG"
    }
}

internal fun shouldAutoLaunch(result: ScanResult, payload: IntentPayload): Boolean =
    payload is UrlPayload &&
        result.riskLevel == RiskLevel.GREEN &&
        result.decision == ProtectionDecision.ALLOW

private sealed interface ScanUiState {
    data class Loading(val tip: String) : ScanUiState
    data class Success(val result: ScanResult, val payload: IntentPayload) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

@Composable
private fun ScanLoadingContent(payloadType: String, currentTip: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

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

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Security Tip",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Tip: $currentTip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScanResultContent(
    result: ScanResult,
    payload: IntentPayload,
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

        val continueButtonText = if (payload is UrlPayload) {
            continueButtonText(result.decision)
        } else {
            null
        }
        val buttonText = continueButtonText ?: "Close"
        val buttonAction = {
            if (continueButtonText != null) {
                val launched = onOpenUrl((payload as UrlPayload).url)

                if (!launched) {
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

internal fun continueButtonText(decision: ProtectionDecision): String? = when (decision) {
    ProtectionDecision.ALLOW -> "Continue"
    ProtectionDecision.WARN -> "Continue Anyway"
    ProtectionDecision.BLOCK -> null
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
