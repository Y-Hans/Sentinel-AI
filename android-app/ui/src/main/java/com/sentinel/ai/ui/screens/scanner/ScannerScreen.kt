package com.sentinel.ai.ui.screens.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ui.components.ActionButton
import com.sentinel.ai.ui.components.AnimatedSentinelShield
import com.sentinel.ai.ui.components.ElevatedSentinelCard
import com.sentinel.ai.ui.components.ScanProgressIndicator
import com.sentinel.ai.ui.components.ScanStep
import com.sentinel.ai.ui.components.ScanStepState
import com.sentinel.ai.ui.components.ShieldState
import com.sentinel.ai.ui.components.SecondaryButton
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.screens.scanner.ScanType.FILE
import com.sentinel.ai.ui.screens.scanner.ScanType.LINK
import com.sentinel.ai.ui.screens.scanner.ScanType.TEXT
import com.sentinel.ai.ui.theme.SentinelMotion
import com.sentinel.ai.ui.theme.SentinelSize
import com.sentinel.ai.ui.theme.SentinelSpacing
import kotlinx.coroutines.delay

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScannerContent(
        uiState = uiState,
        onAction = viewModel::onAction
    )
}

@Composable
fun ScannerContent(
    uiState: ScannerUiState,
    onAction: (ScannerUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf(ScanPhase.Input) }
    var result by remember { mutableStateOf<ScanResult?>(null) }

    when (phase) {
        ScanPhase.Input -> ScanInputContent(
            scanType = uiState.scanType,
            scanInput = uiState.scanInput,
            onInputChange = { onAction(ScannerUiAction.UpdateInput(it)) },
            onTypeChange = { onAction(ScannerUiAction.SetScanType(it)) },
            onRunScan = {
                onAction(ScannerUiAction.RunScan)
                phase = ScanPhase.Scanning
            },
            modifier = modifier
        )

        ScanPhase.Scanning -> LiveScanContent(
            scanType = uiState.scanType,
            onComplete = {
                result = sampleScanResult(uiState.scanType, uiState.scanInput)
                phase = ScanPhase.Result
            },
            modifier = modifier
        )

        ScanPhase.Result -> {
            val scanResult = result ?: sampleScanResult(uiState.scanType, uiState.scanInput)
            val onScanAgain: () -> Unit = {
                result = null
                phase = ScanPhase.Input
            }
            when (uiState.scanType) {
                LINK -> UrlScanResultContent(
                    result = scanResult,
                    onOpen = onScanAgain,
                    onGoBack = onScanAgain,
                    onBypass = onScanAgain,
                    onScanAgain = onScanAgain,
                    modifier = modifier
                )

                FILE -> FileScanResultContent(
                    result = scanResult,
                    onOpen = onScanAgain,
                    onGoBack = onScanAgain,
                    onBypass = onScanAgain,
                    onScanAgain = onScanAgain,
                    modifier = modifier
                )

                TEXT -> UrlScanResultContent(
                    result = scanResult,
                    onOpen = onScanAgain,
                    onGoBack = onScanAgain,
                    onBypass = onScanAgain,
                    onScanAgain = onScanAgain,
                    modifier = modifier
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScanInputContent(
    scanType: ScanType,
    scanInput: String,
    onInputChange: (String) -> Unit,
    onTypeChange: (ScanType) -> Unit,
    onRunScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SentinelSpacing.ScreenHorizontal, vertical = SentinelSpacing.ScreenVertical),
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.BetweenSections)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                Icons.Filled.Radar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SentinelSize.IconLarge)
            )
            Text(
                text = "Live scan",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Check a link or file against Sentinel's threat intelligence before you open it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ElevatedSentinelCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.SM),
                    verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)
                ) {
                    FilterChip(
                        selected = scanType == LINK,
                        onClick = { onTypeChange(LINK) },
                        label = { Text("Link") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                modifier = Modifier.size(SentinelSize.IconSmall)
                            )
                        }
                    )
                    FilterChip(
                        selected = scanType == FILE,
                        onClick = { onTypeChange(FILE) },
                        label = { Text("File") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Radar,
                                contentDescription = null,
                                modifier = Modifier.size(SentinelSize.IconSmall)
                            )
                        }
                    )
                }

                OutlinedTextField(
                    value = scanInput,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (scanType == FILE) "File path or name" else "Paste a link") },
                    placeholder = { Text(if (scanType == FILE) "document.apk" else "https://example.com") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )

                ActionButton(
                    text = "Run live scan",
                    onClick = onRunScan,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Filled.Radar
                )
            }
        }
    }
}

@Composable
internal fun LiveScanContent(
    scanType: ScanType,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = scanStepsFor(scanType)
    var currentStep by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        repeat(steps.size) { step ->
            currentStep = step
            delay(SentinelMotion.DurationExtraLong.toLong())
        }
        delay(500)
        onComplete()
    }

    val stage = if (currentStep + 1 > steps.size) steps.size else currentStep + 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SentinelSpacing.ScreenHorizontal, vertical = SentinelSpacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.LG)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedSentinelShield(
                state = ShieldState.Scanning,
                modifier = Modifier.size(SentinelSize.IconXL * 2),
                contentDescription = "Scanning in progress"
            )
        }

        Text(
            text = "Scanning",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Sentinel is inspecting this carefully. You don't need to do anything.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        ScanProgressIndicator(
            progress = stage.toFloat() / steps.size,
            title = "Stage $stage of ${steps.size}",
            subtitle = "Working through each check with care"
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD)
        ) {
            steps.forEachIndexed { index, title ->
                val state = when {
                    index < currentStep -> ScanStepState.Completed
                    index == currentStep -> ScanStepState.Active
                    else -> ScanStepState.Pending
                }
                ScanStep(
                    stepNumber = index + 1,
                    title = title,
                    state = state
                )
            }
        }
    }
}

private fun scanStepsFor(scanType: ScanType): List<String> = buildList {
    add("Prepare a safe environment")
    add(if (scanType == FILE) "Inspect the file signature" else "Inspect the link structure")
    add("Match known threat patterns")
    add("Cross-check against reputation data")
    add("Finalize the verdict")
}

private fun sampleScanResult(type: ScanType, input: String): ScanResult {
    val subject = input.takeIf { it.isNotBlank() } ?: when (type) {
        FILE -> "document.apk"
        else -> "https://example.com"
    }
    val noun = if (type == FILE) "file" else "link"
    return ScanResult(
        id = "demo-scan",
        source = subject,
        riskLevel = RiskLevel.GREEN,
        riskScore = 0.04f,
        explanation = "No threats were found in this $noun. It matches Sentinel's safe patterns.",
        timestamp = System.currentTimeMillis()
    )
}

private enum class ScanPhase {
    Input,
    Scanning,
    Result
}
