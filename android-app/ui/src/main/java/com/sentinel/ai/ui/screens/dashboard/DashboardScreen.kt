package com.sentinel.ai.ui.screens.dashboard

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.Alert
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.ui.components.RiskBadge
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelIndicatorDot
import com.sentinel.ai.ui.components.SentinelMetricCard
import com.sentinel.ai.ui.components.SentinelPill
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.util.SenderPresentation
import com.sentinel.ai.ui.util.resolveSenderPresentation
import com.sentinel.ai.ui.util.toAppLabel

@Composable
fun DashboardScreen(
    onThreatSelected: (String) -> Unit,
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
private fun DashboardContent(
    uiState: DashboardUiState,
    onAction: (DashboardUiAction) -> Unit,
    onThreatSelected: (String) -> Unit,
    appLabelResolver: (String) -> String,
    senderPresentationResolver: (String?, String?) -> SenderPresentation
) {
    val alerts = uiState.recentAlerts
    val threatCount = alerts.size
    val criticalCount = alerts.count { it.riskLevel == RiskLevel.CRITICAL }
    val highCount = alerts.count { it.riskLevel == RiskLevel.RED }
    val topThreat = alerts.maxByOrNull { it.timestamp }
    val protectionEnabled = uiState.protection.protectionEnabled
    val protectionOperational = protectionEnabled &&
        uiState.protection.guardServiceRunning &&
        uiState.protection.monitorServiceRunning
    val listenerAvailable = uiState.protection.notificationListenerEnabled
    val missingPermissions = uiState.protection.missingPermissions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Sentinel AI",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "Real-time protection dashboard for scam detection and threat triage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SentinelCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SentinelPill(
                        label = if (protectionEnabled) "Protection Active" else "Protection Disabled",
                        accent = if (protectionEnabled) riskColor(RiskLevel.GREEN) else riskColor(RiskLevel.YELLOW)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when {
                            !protectionEnabled -> "Shield status: Disabled"
                            protectionOperational -> "Shield status: Online"
                            else -> "Shield status: Starting"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append("Monitoring notifications and message patterns for social-engineering signals.")
                            if (missingPermissions.isNotEmpty()) {
                                append(" Required permissions missing: ")
                                append(missingPermissions.joinToString())
                                append(".")
                            }
                            if (protectionEnabled && !protectionOperational) {
                                append(" Backend protection services are still syncing.")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CircularProgressIndicator(
                    progress = when {
                        protectionOperational -> 1f
                        protectionEnabled -> 0.6f
                        else -> 0.25f
                    },
                    modifier = Modifier.size(68.dp),
                    color = if (protectionEnabled) riskColor(RiskLevel.GREEN) else riskColor(RiskLevel.YELLOW)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SentinelMetricCard(
                    label = "Threats detected",
                    value = threatCount.toString(),
                    accent = riskColor(RiskLevel.YELLOW),
                    supportingText = "Actual detections surfaced by the backend."
                )
                SentinelMetricCard(
                    label = "High-risk threats",
                    value = highCount.toString(),
                    accent = riskColor(RiskLevel.RED),
                    supportingText = "Messages requiring careful review."
                )
                SentinelMetricCard(
                    label = "Critical threats",
                    value = criticalCount.toString(),
                    accent = riskColor(RiskLevel.CRITICAL),
                    supportingText = "Highest-risk backend detections."
                )
            }
        }

        SentinelSectionHeader(
            title = "Threat summary",
            subtitle = "A quick view of the current risk posture."
        )
        SentinelCard {
            if (topThreat != null) {
                val senderPresentation = senderPresentationResolver(
                    topThreat.senderDisplayName,
                    topThreat.senderIdentifier
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        RiskBadge(riskLevel = topThreat.riskLevel)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = senderPresentation.primaryText,
                            style = MaterialTheme.typography.titleLarge
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
                            text = appLabelResolver(topThreat.title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = topThreat.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${alerts.count { it.riskLevel != RiskLevel.GREEN }} risky",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            } else {
                Text(
                    text = "No detections recorded yet. The backend will populate this card when events arrive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SentinelSectionHeader(
            title = "Recent detections",
            subtitle = "Tap any card for a full threat breakdown."
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (alerts.isEmpty()) {
                SentinelCard {
                    Text(
                        text = "No threat events yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                alerts.forEach { alert ->
                    AlertPreviewCard(
                        alert = alert,
                        appLabel = appLabelResolver(alert.title),
                        senderPresentation = senderPresentationResolver(
                            alert.senderDisplayName,
                            alert.senderIdentifier
                        ),
                        onClick = { onThreatSelected(alert.threatId) }
                    )
                }
            }
        }

        SentinelSectionHeader(
            title = "Quick statistics",
            subtitle = "Operational signals that help you assess coverage at a glance."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelMetricCard(
                label = "Protection status",
                value = if (protectionEnabled) "Active" else "Disabled",
                accent = if (protectionEnabled) riskColor(RiskLevel.GREEN) else riskColor(RiskLevel.YELLOW),
                supportingText = if (protectionOperational) {
                    "Matches the shared backend protection state."
                } else {
                    "Uses the shared backend protection switch and shows service readiness separately."
                }
            )
            SentinelMetricCard(
                label = "Listener",
                value = if (listenerAvailable) "Available" else "Unavailable",
                accent = if (listenerAvailable) riskColor(RiskLevel.GREEN) else riskColor(RiskLevel.YELLOW),
                supportingText = "Reflects notification listener access."
            )
        }

        SentinelSectionHeader(
            title = "Scan and protection",
            subtitle = "Operational status cards for the current session."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelCard {
                Text(
                    text = "Live scan engine",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Active notification inspection with risk scoring and event correlation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { onAction(DashboardUiAction.RefreshStatus) }) {
                    Text(text = "Refresh status")
                }
            }
            SentinelCard {
                Text(
                    text = "Protection mode",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (listenerAvailable) {
                        "Notification listener available, threat pipeline ready, and scam warnings enabled."
                    } else {
                        "Notification listener unavailable. Enable access in system settings to resume live monitoring."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { onAction(DashboardUiAction.ToggleGuard) }) {
                    Text(text = if (protectionEnabled) "Pause shield" else "Resume shield")
                }
            }
        }
    }
}

@Composable
private fun AlertPreviewCard(
    alert: Alert,
    appLabel: String,
    senderPresentation: SenderPresentation,
    onClick: () -> Unit
) {
    SentinelCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SentinelIndicatorDot(color = riskColor(alert.riskLevel))
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
                    text = alert.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RiskBadge(riskLevel = alert.riskLevel)
        }
    }
}
