package com.sentinel.ai.ui.screens.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.Alert
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.ui.components.ActionButton
import com.sentinel.ai.ui.components.AnimatedSentinelShield
import com.sentinel.ai.ui.components.EmptyState
import com.sentinel.ai.ui.components.ElevatedSentinelCard
import com.sentinel.ai.ui.components.InfoRow
import com.sentinel.ai.ui.components.QuickActionCard
import com.sentinel.ai.ui.components.RiskState
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.ShieldState
import com.sentinel.ai.ui.components.StatusChip
import com.sentinel.ai.ui.components.MetricCard
import com.sentinel.ai.ui.components.SentinelCard
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
            QuickActions(
                onRunScan = onNavigateToScanner,
                onReviewHistory = onNavigateToHistory
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
                EmptyState(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(SentinelSize.IconLarge)
                        )
                    },
                    title = "No detections yet",
                    description = "Sentinel will surface suspicious messages and links here as they arrive."
                )
            }
        } else {
            items(alerts.take(3), key = { it.threatId }) { alert ->
                RecentActivityItem(
                    alert = alert,
                    appLabel = appLabelResolver(alert.title),
                    senderPresentation = senderPresentationResolver(alert.senderDisplayName, alert.senderIdentifier),
                    onClick = { onThreatSelected(alert.threatId) },
                    modifier = Modifier
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Here is your protection at a glance.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
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
    val headline = when (state) {
        RiskState.Safe -> "Active protection"
        RiskState.Suspicious -> "Protection paused"
        RiskState.Dangerous -> "Protection needs attention"
        RiskState.Neutral -> "Preparing protection"
        RiskState.Scanning -> "Protection scan in progress"
    }
    val description = when (state) {
        RiskState.Safe -> "Monitoring incoming notifications and messages in real time."
        RiskState.Suspicious -> "Live monitoring is paused until you resume protection."
        RiskState.Dangerous -> "Enable protection to resume monitoring your device."
        RiskState.Neutral -> "Checking that Sentinel services are ready to monitor threats."
        RiskState.Scanning -> "Scanning recent activity for suspicious behavior."
    }

    ElevatedSentinelCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        val breathingTransition = rememberInfiniteTransition(label = "hero-shield-breathing")
        val shieldScale by breathingTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hero-shield-scale"
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            HeroSecurityPattern()
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = CircleShape
                ) {
                    Box(
                        modifier = Modifier.size(SentinelSize.IconXL * 3),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedSentinelShield(
                            state = shieldState,
                            modifier = Modifier
                                .size(SentinelSize.IconXL * 2)
                                .scale(shieldScale),
                            contentDescription = headline
                        )
                    }
                }
                StatusChip(state = state)
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                HeroInformationStrip(
                    score = score,
                    protectionEnabled = protectionEnabled,
                    state = state
                )
                ActionButton(
                    text = if (protectionEnabled) "Pause protection" else "Resume protection",
                    onClick = onToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SentinelSpacing.XS),
                    leadingIcon = Icons.Filled.Shield
                )
            }
        }
    }
}

@Composable
private fun HeroSecurityPattern() {
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseRadius = size.minDimension * 0.24f
        repeat(3) { index ->
            drawCircle(
                color = accent,
                radius = baseRadius * (index + 1),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun HeroInformationStrip(
    score: Int,
    protectionEnabled: Boolean,
    state: RiskState
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HeroMetric(
            label = "Protection score",
            value = "$score/100",
            modifier = Modifier.weight(1f)
        )
        HeroMetric(
            label = "Monitoring",
            value = if (protectionEnabled) "Active" else "Paused",
            modifier = Modifier.weight(1f)
        )
        HeroMetric(
            label = "Engine",
            value = if (state == RiskState.Safe) "Online" else "Checking",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.XXS)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "Detections",
            value = threatCount.toString(),
            state = RiskState.Suspicious,
            supportingText = "Total threats found"
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "High risk",
            value = highCount.toString(),
            state = RiskState.Dangerous,
            supportingText = "Needs review"
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "Critical",
            value = criticalCount.toString(),
            state = RiskState.Dangerous,
            supportingText = "Immediate action"
        )
    }
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
                    Icons.Filled.Search,
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
                    Icons.Filled.AccessTime,
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
    Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)) {
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
                    Icons.Filled.Error,
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
                    Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SentinelSize.IconMedium)
                )
            },
            showDivider = false
        )
    }
}

@Composable
private fun RecentActivityItem(
    alert: Alert,
    appLabel: String,
    senderPresentation: SenderPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThreatCard(
        modifier = modifier,
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
