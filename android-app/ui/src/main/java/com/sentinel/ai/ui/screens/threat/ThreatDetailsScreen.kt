package com.sentinel.ai.ui.screens.threat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.Threat
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.ui.components.RiskBadge
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelPill
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.util.SenderPresentation
import com.sentinel.ai.ui.util.resolveSenderPresentation
import com.sentinel.ai.ui.util.toAppLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ThreatDetailsScreen(
    threatId: String,
    onBack: () -> Unit
) {
    val threat by ThreatJournal.observeThreat(threatId)
        .collectAsStateWithLifecycle(initialValue = ThreatJournal.threatFor(threatId))
    val context = LocalContext.current

    ThreatDetailsContent(
        threat = threat,
        sourceLabelResolver = { source -> source.toAppLabel(context) },
        senderPresentationResolver = { name, identifier ->
            resolveSenderPresentation(
                context = context,
                senderDisplayName = name,
                senderIdentifier = identifier
            )
        },
        onBack = onBack
    )
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreatDetailsContent(
    threat: Threat?,
    sourceLabelResolver: (String) -> String,
    senderPresentationResolver: (String?, String?) -> SenderPresentation,
    onBack: () -> Unit
) {
    val formatter = rememberTimestampFormatter()
    val threatRiskLevel = threat?.riskLevel ?: RiskLevel.GREEN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back"
                )
            }
            RiskBadge(riskLevel = threatRiskLevel)
        }

        Text(
            text = "Threat details",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "Deep-dive view of the selected detection with guidance for response.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (threat == null) {
            SentinelCard {
                Text(
                    text = "No threat details are available for this item yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onBack) {
                    Text(text = "Return to dashboard")
                }
            }
            return
        }

        val selectedThreat = threat
        val suspiciousIndicators = suspiciousIndicatorsFor(selectedThreat)
        val senderPresentation = senderPresentationResolver(
            selectedThreat.senderDisplayName,
            selectedThreat.senderIdentifier
        )

        SentinelCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SentinelPill(
                        label = "Risk score ${selectedThreat.riskScore.toInt()}",
                        accent = riskColor(selectedThreat.riskLevel)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = senderPresentation.primaryText,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    senderPresentation.secondaryText?.let { identifier ->
                        Text(
                            text = identifier,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = sourceLabelResolver(selectedThreat.source),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CircularProgressIndicator(
                    progress = selectedThreat.riskScore / 100f,
                    modifier = Modifier.size(74.dp),
                    color = riskColor(selectedThreat.riskLevel)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Message preview",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = selectedThreat.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = formatter.format(Date(selectedThreat.timestamp)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SentinelSectionHeader(
            title = "Threat explanation",
            subtitle = "Why this message was flagged by the security pipeline."
        )
        SentinelCard {
            Text(
                text = selectedThreat.explanation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SentinelSectionHeader(
            title = "Suspicious indicators",
            subtitle = "Signals that contributed to the risk score."
        )
        SentinelCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                suspiciousIndicators.forEach { indicator ->
                    SentinelPill(
                        label = indicator,
                        accent = riskColor(selectedThreat.riskLevel)
                    )
                }
            }
        }

        SentinelSectionHeader(
            title = "Recommended action",
            subtitle = "A practical next step for the user."
        )
        SentinelCard {
            Text(
                text = selectedThreat.recommendation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) {
                Text(text = "Return to dashboard")
            }
        }
    }
}

private fun suspiciousIndicatorsFor(threat: Threat): List<String> {
    val derivedIndicators = buildList {
        val explanation = threat.explanation.lowercase(Locale.getDefault())
        val content = threat.content.lowercase(Locale.getDefault())

        if ("urgent" in explanation || "urgent" in content || "pressure" in explanation) add("Urgency pressure")
        if ("otp" in explanation || "otp" in content || "code" in content) add("Code request")
        if ("payment" in explanation || "payment" in content || "transfer" in content) add("Payment request")
        if ("link" in explanation || "url" in explanation || "http" in content) add("Suspicious link")
        if ("authority" in explanation || "imperson" in explanation) add("Authority impersonation")
    }

    return derivedIndicators
        .distinct()
        .ifEmpty { listOf("Risk scoring triggered", "Message review recommended", "User verification needed") }
}

private fun rememberTimestampFormatter(): SimpleDateFormat {
    // Kept local to the screen so the UI stays self-contained and backend-free.
    return SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault())
}
