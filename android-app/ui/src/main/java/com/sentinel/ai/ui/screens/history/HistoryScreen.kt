package com.sentinel.ai.ui.screens.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ui.components.EmptyState
import com.sentinel.ai.ui.components.InfoRow
import com.sentinel.ai.ui.components.RiskBadge
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelIndicatorDot
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.StatisticCard
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.theme.SentinelSize
import com.sentinel.ai.ui.theme.SentinelSpacing
import com.sentinel.ai.ui.util.SenderPresentation
import com.sentinel.ai.ui.util.resolveSenderPresentation
import com.sentinel.ai.ui.util.toAppLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class HistoryListEntry {
    data class DateHeader(val dateLabel: String) : HistoryListEntry()
    data class ThreatItem(val item: ScanResult) : HistoryListEntry()
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items = uiState.history
    val formatter = rememberHistoryFormatter()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = SentinelSpacing.ScreenHorizontal,
                vertical = SentinelSpacing.ScreenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.BetweenSections)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.XS)) {
            Text(
                text = "History",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "A clean audit trail of previous detections and review outcomes.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.MD),
            verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD)
        ) {
            StatisticCard(
                modifier = Modifier.weight(1f),
                title = "Entries",
                value = items.size.toString(),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SentinelSize.IconMedium)
                    )
                },
                subtitle = "Stored detections currently visible in memory"
            )
            StatisticCard(
                modifier = Modifier.weight(1f),
                title = "Critical",
                value = items.count { it.riskLevel == RiskLevel.CRITICAL }.toString(),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = riskColor(RiskLevel.CRITICAL),
                        modifier = Modifier.size(SentinelSize.IconMedium)
                    )
                },
                subtitle = "Requires urgent user attention"
            )
        }

        SentinelSectionHeader(
            title = "Detection log",
            subtitle = "Most recent items appear first"
        )

        if (items.isEmpty()) {
            EmptyState(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SentinelSize.IconLarge)
                    )
                },
                title = "No threat history yet",
                description = "The backend will populate this list as detections arrive."
            )
        } else {
            val sortedItems = remember(items) { items.sortedByDescending { it.timestamp } }
            val grouped = remember(sortedItems) {
                sortedItems.groupBy { formatter.format(Date(it.timestamp)) }
                    .toList()
                    .sortedByDescending { (dateLabel, _) -> dateLabel }
            }
            val flatEntries = remember(grouped) {
                grouped.flatMap { (dateLabel, dateItems) ->
                    listOf(HistoryListEntry.DateHeader(dateLabel)) + dateItems.map { HistoryListEntry.ThreatItem(it) }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SentinelSpacing.BetweenItems)
            ) {
                items(flatEntries, key = { entry ->
                    when (entry) {
                        is HistoryListEntry.DateHeader -> "date-${entry.dateLabel}"
                        is HistoryListEntry.ThreatItem -> entry.item.id
                    }
                }) { entry ->
                    when (entry) {
                        is HistoryListEntry.DateHeader -> {
                            Text(
                                text = entry.dateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(vertical = SentinelSpacing.SM)
                            )
                        }
                        is HistoryListEntry.ThreatItem -> {
                            HistoryItemRow(
                                item = entry.item,
                                appLabel = entry.item.source.toAppLabel(context),
                                senderPresentation = resolveSenderPresentation(
                                    context = context,
                                    senderDisplayName = entry.item.senderDisplayName,
                                    senderIdentifier = entry.item.senderIdentifier
                                ),
                                timestampLabel = formatter.format(Date(entry.item.timestamp)),
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryItemRow(
    item: ScanResult,
    appLabel: String,
    senderPresentation: SenderPresentation,
    timestampLabel: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.MD)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SentinelIndicatorDot(color = riskColor(item.riskLevel))
                Spacer(modifier = Modifier.size(SentinelSpacing.XS))
                Text(
                    text = senderPresentation.primaryText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            senderPresentation.secondaryText?.let { identifier ->
                Spacer(modifier = Modifier.height(SentinelSpacing.XXS))
                Text(
                    text = identifier,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(SentinelSpacing.XXS))
            Text(
                text = appLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            RiskBadge(riskLevel = item.riskLevel)
            if (timestampLabel.isNotBlank()) {
                Spacer(modifier = Modifier.height(SentinelSpacing.XXS))
                Text(
                    text = timestampLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun rememberHistoryFormatter(): SimpleDateFormat {
    return SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault())
}
