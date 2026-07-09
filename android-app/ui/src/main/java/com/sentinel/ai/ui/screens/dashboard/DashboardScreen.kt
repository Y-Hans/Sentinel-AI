package com.sentinel.ai.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.Alert
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.ui.components.ActionButton
import com.sentinel.ai.ui.components.AnimatedSentinelShield
import com.sentinel.ai.ui.components.ElevatedSentinelCard
import com.sentinel.ai.ui.components.InfoRow
import com.sentinel.ai.ui.components.QuickActionCard
import com.sentinel.ai.ui.components.RiskState
import com.sentinel.ai.ui.components.ScoreCard
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.ShieldState
import com.sentinel.ai.ui.components.StatisticCard
import com.sentinel.ai.ui.components.ThreatCard
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.protection.ProtectionSnapshot
import com.sentinel.ai.ui.theme.SentinelSize
import com.sentinel.ai.ui.theme.SentinelSpacing
import com.sentinel.ai.ui.util.SenderPresentation
import com.sentinel.ai.ui.util.resolveSenderPresentation
import com.sentinel.ai.ui.util.toAppLabel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    onThreatSelected: (String) -> Unit,
    onNavigateToScanner: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(DashboardUiAction.RefreshStatus)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DashboardContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onThreatSelected = onThreatSelected,
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToHistory = onNavigateToHistory,
        appLabelResolver = { source -> source.toAppLabel(context) },
        senderPresentationResolver = { name, identifier ->
            resolveSenderPresentation(
                context = context,
                senderDisplayName = name,
                senderIdentifier = identifier
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onAction: (DashboardUiAction) -> Unit,
    onThreatSelected: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToHistory: () -> Unit,
    appLabelResolver: (String) -> String,
    senderPresentationResolver: (String?, String?) -> SenderPresentation,
    modifier: Modifier = Modifier
) {
    val protection = uiState.protection
    val protectionState = protectionState(protection)
    val score = protectionScore(protection)
    val alerts = uiState.recentAlerts

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SentinelSpacing.ScreenHorizontal, vertical = SentinelSpacing.ScreenVertical),
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.XL),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = SentinelSpacing.XXL)
    ) {
        item {
            DashboardGreeting()
        }

        item {
            ProtectionHero(
                state = protectionState,
                score = score,
                protectionEnabled = protection.protectionEnabled,
                onToggle = { onAction(DashboardUiAction.ToggleGuard) }
            )
        }

        item {
            SentinelSectionHeader(
                title = "At a glance",
                subtitle = "What Sentinel is watching right now"
            )
        }

        item {
            StatisticsRow(
                threatCount = alerts.size,
                highCount = alerts.count { it.riskLevel == RiskLevel.RED },
                criticalCount = alerts.count { it.riskLevel == RiskLevel.CRITICAL }
            )
        }

        item {
            SentinelSectionHeader(
                title = "Quick actions",
                subtitle = "Start a scan or review past activity"
            )
        }

        item {
            QuickActions(
                onRunScan = onNavigateToScanner,
                onReviewHistory = onNavigateToHistory
            )
        }

        item {
            ProtectionSummaryCard(snapshot = protection)
        }

        item {
            SentinelSectionHeader(
                title = "Recent activity",
                subtitle = "The latest detections from your devices",
                actionLabel = "View all",
                onAction = onNavigateToHistory
            )
        }

        if (alerts.isEmpty()) {
            item {
                SentinelCard {
                    Text(
                        text = "No detections yet. Sentinel will surface suspicious messages and links here as they arrive.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(alerts.take(3), key = { it.threatId }) { alert ->
                RecentActivityItem(
                    alert = alert,
                    appLabel = appLabelResolver(alert.title),
                    senderPresentation = senderPresentationResolver(alert.senderDisplayName, alert.senderIdentifier),
                    onClick = { onThreatSelected(alert.threatId) }
                )
            }
        }
    }
}

@Composable
private fun DashboardGreeting() {
    val greeting = remember { greetingFor(LocalTime.now().hour) }
    Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.XS)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Here is your protection at a glance.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProtectionHero(
    state: RiskState,
    score: Int,
    protectionEnabled: Boolean,
    onToggle: () -> Unit
) {
    val shieldState = when (state) {
        RiskState.Safe -> ShieldState.Safe
        RiskState.Suspicious -> ShieldState.Warning
        RiskState.Dangerous -> ShieldState.Dangerous
        RiskState.Neutral -> ShieldState.Idle
        RiskState.Scanning -> ShieldState.Scanning
    }
    val accent = riskColor(state)
    val headline = when (state) {
        RiskState.Safe -> "You're protected"
        RiskState.Suspicious -> "Protection paused"
        RiskState.Dangerous -> "Protection off"
        RiskState.Neutral -> "Starting up"
        RiskState.Scanning -> "Scanning"
    }
    val description = when (state) {
        RiskState.Safe -> "Sentinel is actively watching your notifications and messages."
        RiskState.Suspicious -> "Turn protection back on to resume live monitoring."
        RiskState.Dangerous -> "Enable protection to start shielding your device."
        RiskState.Neutral -> "Services are syncing with the protection backend."
        RiskState.Scanning -> "Inspecting recent activity for threats."
    }

    ElevatedSentinelCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SentinelSpacing.LG)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedSentinelShield(
                    state = shieldState,
                    modifier = Modifier.size(SentinelSize.IconXL * 2),
                    contentDescription = null
                )
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            ScoreCard(
                score = score,
                trend = when (state) {
                    RiskState.Safe -> "Strong posture"
                    RiskState.Suspicious -> "Needs attention"
                    RiskState.Dangerous -> "At risk"
                    else -> null
                },
                supportingText = "Overall protection score"
            )

            ActionButton(
                text = if (protectionEnabled) "Pause protection" else "Resume protection",
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.Shield
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatisticsRow(
    threatCount: Int,
    highCount: Int,
    criticalCount: Int
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.MD),
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD),
        maxItemsInEachRow = 3
    ) {
        StatisticCard(
            modifier = Modifier.weight(1f),
            title = "Detections",
            value = threatCount.toString(),
            subtitle = "Total threats found",
            icon = { StatisticIcon(Icons.Filled.Warning, riskColor(RiskState.Suspicious)) }
        )
        StatisticCard(
            modifier = Modifier.weight(1f),
            title = "High risk",
            value = highCount.toString(),
            subtitle = "Needs review",
            icon = { StatisticIcon(Icons.Filled.PriorityHigh, riskColor(RiskState.Dangerous)) }
        )
        StatisticCard(
            modifier = Modifier.weight(1f),
            title = "Critical",
            value = criticalCount.toString(),
            subtitle = "Immediate action",
            icon = { StatisticIcon(Icons.Filled.Block, riskColor(RiskState.Dangerous)) }
        )
    }
}

@Composable
private fun StatisticIcon(imageVector: ImageVector, tint: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(SentinelSize.IconMedium)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    onRunScan: () -> Unit,
    onReviewHistory: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.MD),
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD),
        maxItemsInEachRow = 2
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = {
                Icon(
                    Icons.Filled.Radar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(SentinelSize.IconLarge)
                )
            },
            title = "Run live scan",
            subtitle = "Check a link or file now",
            onClick = onRunScan
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(SentinelSize.IconLarge)
                )
            },
            title = "Review history",
            subtitle = "Browse past detections",
            onClick = onReviewHistory
        )
    }
}

@Composable
private fun ProtectionSummaryCard(snapshot: ProtectionSnapshot) {
    val missing = snapshot.missingPermissions
    SentinelCard {
        Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)) {
            Text(
                text = "Protection summary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(SentinelSpacing.XXS))
            InfoRow(
                label = "Shield",
                value = if (snapshot.protectionEnabled) "Active" else "Disabled",
                icon = {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SentinelSize.IconMedium)
                    )
                }
            )
            InfoRow(
                label = "Notification listener",
                value = if (snapshot.notificationListenerEnabled) "Available" else "Unavailable",
                icon = {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SentinelSize.IconMedium)
                    )
                }
            )
            InfoRow(
                label = "Permissions",
                value = if (missing.isEmpty()) "All granted" else "${missing.size} missing",
                icon = {
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SentinelSize.IconMedium)
                    )
                },
                showDivider = false
            )
        }
    }
}

@Composable
private fun RecentActivityItem(
    alert: Alert,
    appLabel: String,
    senderPresentation: SenderPresentation,
    onClick: () -> Unit
) {
    ThreatCard(
        title = senderPresentation.primaryText,
        source = appLabel,
        riskLevel = alert.riskLevel,
        timestampLabel = formatTimestamp(alert.timestamp),
        description = alert.summary,
        onClick = onClick
    )
}

private fun protectionState(snapshot: ProtectionSnapshot): RiskState = when {
    !snapshot.protectionEnabled -> RiskState.Suspicious
    snapshot.guardServiceRunning && snapshot.monitorServiceRunning -> RiskState.Safe
    else -> RiskState.Neutral
}

private fun protectionScore(snapshot: ProtectionSnapshot): Int = when {
    !snapshot.protectionEnabled -> 18
    snapshot.guardServiceRunning && snapshot.monitorServiceRunning -> 94
    else -> 62
}

private fun greetingFor(hour: Int): String = when {
    hour < 12 -> "Good morning"
    hour < 17 -> "Good afternoon"
    hour < 21 -> "Good evening"
    else -> "Good night"
}

private fun formatTimestamp(timestamp: Long): String {
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrDefault("Recent")
}
