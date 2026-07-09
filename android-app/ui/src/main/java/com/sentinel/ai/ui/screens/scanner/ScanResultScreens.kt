package com.sentinel.ai.ui.screens.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ui.components.ActionButton
import com.sentinel.ai.ui.components.AnimatedSentinelShield
import com.sentinel.ai.ui.components.ButtonVariant
import com.sentinel.ai.ui.components.InfoRow
import com.sentinel.ai.ui.components.RiskState
import com.sentinel.ai.ui.components.SecondaryButton
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.ShieldState
import com.sentinel.ai.ui.components.ThreatExplanationCard
import com.sentinel.ai.ui.components.ThreatLevelChip
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.theme.SentinelFull
import com.sentinel.ai.ui.theme.SentinelSize
import com.sentinel.ai.ui.theme.SentinelSpacing

@Composable
fun UrlScanResultContent(
    result: ScanResult,
    onOpen: () -> Unit,
    onGoBack: () -> Unit,
    onBypass: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScanVerdictContent(
        subject = ScanSubject.Link,
        result = result,
        onOpen = onOpen,
        onGoBack = onGoBack,
        onBypass = onBypass,
        onScanAgain = onScanAgain,
        modifier = modifier
    )
}

@Composable
fun FileScanResultContent(
    result: ScanResult,
    onOpen: () -> Unit,
    onGoBack: () -> Unit,
    onBypass: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScanVerdictContent(
        subject = ScanSubject.File,
        result = result,
        onOpen = onOpen,
        onGoBack = onGoBack,
        onBypass = onBypass,
        onScanAgain = onScanAgain,
        modifier = modifier
    )
}

private enum class ScanSubject { Link, File }

@Composable
private fun ScanVerdictContent(
    subject: ScanSubject,
    result: ScanResult,
    onOpen: () -> Unit,
    onGoBack: () -> Unit,
    onBypass: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = riskStateOf(result.riskLevel)
    val shieldState = when (state) {
        RiskState.Safe -> ShieldState.Safe
        RiskState.Suspicious -> ShieldState.Warning
        RiskState.Dangerous -> ShieldState.Dangerous
        RiskState.Neutral -> ShieldState.Idle
        RiskState.Scanning -> ShieldState.Scanning
    }
    val accent = riskColor(state)
    val (headline, body) = verdictCopy(subject, state)
    val noun = if (subject == ScanSubject.Link) "link" else "file"
    val subjectLabel = if (subject == ScanSubject.Link) "Link" else "File"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SentinelSpacing.ScreenHorizontal, vertical = SentinelSpacing.ScreenVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.LG)
    ) {
        Box(
            modifier = Modifier
                .size(SentinelSize.IconXL * 2)
                .clip(SentinelFull)
                .background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedSentinelShield(
                state = shieldState,
                modifier = Modifier.size(SentinelSize.IconXL * 2),
                contentDescription = when (state) {
                    RiskState.Safe -> "Safe status"
                    RiskState.Suspicious -> "Suspicious status"
                    RiskState.Dangerous -> "Dangerous status"
                    RiskState.Neutral -> "Neutral status"
                    RiskState.Scanning -> "Scanning status"
                }
            )
        }

        ThreatLevelChip(state = state)

        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        SentinelCard(modifier = Modifier.fillMaxWidth()) {
            InfoRow(
                label = subjectLabel,
                value = result.source,
                showDivider = false
            )
        }

        ThreatExplanationCard(
            explanation = result.explanation,
            recommendation = recommendationFor(state)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)
        ) {
            when (state) {
                RiskState.Safe -> {
                    ActionButton(
                        text = "Open ${noun}",
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryButton(
                        text = "Scan another",
                        onClick = onScanAgain,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                RiskState.Suspicious -> {
                    ActionButton(
                        text = "Go back",
                        onClick = onGoBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryButton(
                        text = "Open anyway",
                        onClick = onBypass,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Outlined
                    )
                }

                else -> {
                    ActionButton(
                        text = "Back to safety",
                        onClick = onGoBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryButton(
                        text = "Open anyway",
                        onClick = onBypass,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Text
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.MD))
    }
}

private fun riskStateOf(level: RiskLevel): RiskState = when (level) {
    RiskLevel.GREEN -> RiskState.Safe
    RiskLevel.YELLOW -> RiskState.Suspicious
    RiskLevel.RED -> RiskState.Dangerous
    RiskLevel.CRITICAL -> RiskState.Dangerous
}

private fun verdictCopy(subject: ScanSubject, state: RiskState): Pair<String, String> {
    val noun = if (subject == ScanSubject.Link) "link" else "file"
    return when (state) {
        RiskState.Safe -> "This $noun looks safe" to
            "We didn't find anything harmful. You can open it with confidence."

        RiskState.Suspicious -> "Proceed with caution" to
            "This $noun has some risky signals. Double-check the source before you continue."

        RiskState.Dangerous -> "This $noun is dangerous" to
            "We found strong signs this is a scam or malware. Avoid opening it."

        RiskState.Neutral -> "Scan complete" to
            "Sentinel finished inspecting this $noun."

        RiskState.Scanning -> "Scanning" to
            "Sentinel is inspecting this $noun."
    }
}

private fun recommendationFor(state: RiskState): String = when (state) {
    RiskState.Safe -> "No action needed. You can proceed."
    RiskState.Suspicious -> "Verify the sender and avoid sharing personal information."
    RiskState.Dangerous -> "Do not open this. Block the sender and report it."
    RiskState.Neutral -> "Review the details if anything looks unusual."
    RiskState.Scanning -> "Wait for the scan to finish."
}
