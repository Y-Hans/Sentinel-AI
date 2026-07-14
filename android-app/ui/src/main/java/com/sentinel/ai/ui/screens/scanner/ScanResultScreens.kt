package com.sentinel.ai.ui.screens.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ui.components.PremiumPanel
import com.sentinel.ai.ui.components.PremiumSectionTitle
import com.sentinel.ai.ui.components.RiskState
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.theme.SentinelSpacing

@Composable
fun UrlScanResultContent(
    result: ScanResult,
    onOpen: () -> Unit,
    onGoBack: () -> Unit,
    onBypass: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
    displaySource: String = result.source
) {
    ScanVerdictContent(
        subject = ScanSubject.Link,
        result = result,
        displaySource = displaySource,
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
    modifier: Modifier = Modifier,
    displaySource: String = result.source
) {
    ScanVerdictContent(
        subject = ScanSubject.File,
        result = result,
        displaySource = displaySource,
        onOpen = onOpen,
        onGoBack = onGoBack,
        onBypass = onBypass,
        onScanAgain = onScanAgain,
        modifier = modifier
    )
}

private enum class ScanSubject { Link, File }

@Composable
@Suppress("UNUSED_PARAMETER")
private fun ScanVerdictContent(
    subject: ScanSubject,
    result: ScanResult,
    displaySource: String,
    onOpen: () -> Unit,
    onGoBack: () -> Unit,
    onBypass: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val verdict = verdictFor(result.decision, subject)
    val evidence = remember(result) { evidenceFor(result) }
    val score = result.riskScore.toInt().coerceIn(0, 100)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = SentinelSpacing.LG),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(verdict.color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = verdict.color,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.MD))
        Text(
            text = verdict.label,
            style = MaterialTheme.typography.displaySmall,
            color = verdict.color,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(SentinelSpacing.XS))
        Text(
            text = verdict.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SentinelSpacing.XS))
        Text(
            text = "Risk score $score / 100",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(SentinelSpacing.LG))
        PremiumPanel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (subject == ScanSubject.Link) "Link" else "File",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displaySource,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.LG))
        PremiumSectionTitle(
            text = "Why this result",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(SentinelSpacing.XS))
        PremiumPanel {
            Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)) {
                evidence.forEach { item ->
                    EvidenceRow(text = item, color = verdict.color)
                }
            }
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.LG))
        Button(
            onClick = onGoBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (result.decision == ProtectionDecision.BLOCK) {
                    verdict.color
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                contentColor = if (result.decision == ProtectionDecision.BLOCK) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Text(
                text = if (result.decision == ProtectionDecision.BLOCK) "Block and close" else "Close",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.XS))
        OutlinedButton(
            onClick = if (result.decision == ProtectionDecision.ALLOW) onOpen else onBypass,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            )
        ) {
            Text(
                text = if (result.decision == ProtectionDecision.ALLOW) "Continue" else "Continue anyway",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.LG))
    }
}

@Composable
private fun EvidenceRow(text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class VerdictUi(
    val label: String,
    val message: String,
    val color: Color
)

private fun verdictFor(decision: ProtectionDecision, subject: ScanSubject): VerdictUi {
    val noun = if (subject == ScanSubject.Link) "link" else "file"
    return when (decision) {
        ProtectionDecision.ALLOW -> VerdictUi(
            label = "SAFE",
            message = "No known threats were found in this $noun.",
            color = riskColor(RiskState.Safe)
        )
        ProtectionDecision.WARN -> VerdictUi(
            label = "WARNING",
            message = "This $noun contains signals that need your attention.",
            color = riskColor(RiskState.Suspicious)
        )
        ProtectionDecision.BLOCK -> VerdictUi(
            label = "DANGEROUS",
            message = "Strong threat signals were found. Do not continue.",
            color = riskColor(RiskState.Dangerous)
        )
    }
}

private fun evidenceFor(result: ScanResult): List<String> {
    val reasons = result.reasons
        .map { it.message.trim() }
        .filter { it.isNotBlank() }

    if (reasons.isNotEmpty()) return reasons.distinct().take(4)

    val explanation = result.explanation
        .split(';', '\n')
        .map { it.trim().trimEnd('.') }
        .filter { it.isNotBlank() }
        .map { "$it." }
        .take(4)

    return explanation.ifEmpty {
        listOf("The scan completed without additional technical details.")
    }
}
