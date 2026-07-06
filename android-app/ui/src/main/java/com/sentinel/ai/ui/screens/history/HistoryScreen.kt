package com.sentinel.ai.ui.screens.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ui.components.RiskBadge
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelIndicatorDot
import com.sentinel.ai.ui.components.SentinelMetricCard
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
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items = uiState.history
    val formatter = rememberHistoryFormatter()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "History",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "A clean audit trail of previous detections and review outcomes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelMetricCard(
                label = "Entries",
                value = items.size.toString(),
                accent = riskColor(RiskLevel.GREEN),
                supportingText = "Stored detections currently visible in memory."
            )
            SentinelMetricCard(
                label = "Critical",
                value = items.count { it.riskLevel == RiskLevel.CRITICAL }.toString(),
                accent = riskColor(RiskLevel.CRITICAL),
                supportingText = "Requires urgent user attention."
            )
        }

        SentinelSectionHeader(
            title = "Detection log",
            subtitle = "Most recent items appear first."
        )

        if (items.isEmpty()) {
            SentinelCard {
                Text(
                    text = "No threat history yet. The backend will populate this list as detections arrive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items.sortedByDescending { it.timestamp }) { item ->
                    HistoryItemCard(
                        item = item,
                        appLabel = item.source.toAppLabel(context),
                        senderPresentation = resolveSenderPresentation(
                            context = context,
                            senderDisplayName = item.senderDisplayName,
                            senderIdentifier = item.senderIdentifier
                        ),
                        timestampLabel = formatter.format(Date(item.timestamp))
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: ScanResult,
    appLabel: String,
    senderPresentation: SenderPresentation,
    timestampLabel: String
) {
    SentinelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SentinelIndicatorDot(color = riskColor(item.riskLevel))
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = senderPresentation.primaryText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                senderPresentation.secondaryText?.let { identifier ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = identifier,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = timestampLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RiskBadge(riskLevel = item.riskLevel)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Risk score: ${item.riskScore.toInt()}",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private fun rememberHistoryFormatter(): SimpleDateFormat {
    return SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault())
}
