package com.sentinel.ai.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelMetricCard
import com.sentinel.ai.ui.components.SentinelPill
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.riskColor

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    appVersion: String,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTheme by rememberSaveable { mutableStateOf("Dark") }
    val context = LocalContext.current
    val protection = uiState.protection

    // Permission grants (notification listener access, overlay, contacts, POST_NOTIFICATIONS)
    // happen in the system Settings app, external to this screen's lifecycle. Without observing
    // ON_RESUME, SettingsUiState is only ever recomputed once at ViewModel construction time, so
    // returning from Settings after granting a permission left the UI showing stale state until
    // the user pressed a manual refresh action. Mirrors the same pattern already used by
    // DashboardScreen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(SettingsUiAction.RefreshStatus)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "App controls and informational preferences for the UI layer only.",
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
                        label = if (protection.protectionEnabled) "Protection Active" else "Protection Disabled",
                        accent = riskColor(if (protection.protectionEnabled) RiskLevel.GREEN else RiskLevel.YELLOW)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Protection status",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (protection.protectionEnabled) {
                            "The UI reflects the live backend guard state."
                        } else {
                            "The shield is paused and the backend services are stopped."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = protection.protectionEnabled,
                    onCheckedChange = { viewModel.onAction(SettingsUiAction.SetGuardEnabled(it)) }
                )
            }
        }

        SentinelSectionHeader(
            title = "Notification permissions",
            subtitle = "Status is read-only here; the actual permission flow remains in the activity."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelMetricCard(
                label = "Listener",
                value = if (protection.notificationListenerEnabled) "Available" else "Unavailable",
                accent = if (protection.notificationListenerEnabled) riskColor(RiskLevel.GREEN) else riskColor(RiskLevel.YELLOW),
                supportingText = "Notification listener access for the live backend."
            )
            SentinelMetricCard(
                label = "Required permissions",
                value = if (protection.missingPermissions.isEmpty()) "Granted" else "Missing",
                accent = if (protection.missingPermissions.isEmpty()) riskColor(RiskLevel.GREEN) else riskColor(RiskLevel.YELLOW),
                supportingText = if (protection.missingPermissions.isEmpty()) {
                    "Notifications, overlay, contacts, and full-screen alerts are available."
                } else {
                    protection.missingPermissions.joinToString()
                }
            )
        }
        SentinelCard {
            Text(
                text = "Open system notification settings if you need to review permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { openAppSettings(context) }) {
                Text(text = "Open app settings")
            }
        }
        if (!protection.fullScreenIntentPermissionGranted) {
            SentinelCard {
                Text(
                    text = "Full-screen critical alerts are off",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "On this Android version, full-screen alerts require a one-time " +
                        "permission grant. Until it's on, critical warnings will only show as " +
                        "a normal notification instead of taking over the screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { openFullScreenIntentSettings(context) }) {
                    Text(text = "Turn on full-screen alerts")
                }
            }
        }

        SentinelSectionHeader(
            title = "Theme",
            subtitle = "UI-only theme chooser. The app remains dark by design."
        )
        SentinelCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("Dark", "Neon", "System").forEach { option ->
                    FilterChip(
                        selected = selectedTheme == option,
                        onClick = {
                            selectedTheme = option
                            // TODO: wire this to a real persisted preference if theme switching becomes productized.
                        },
                        label = { Text(text = option) }
                    )
                }
            }
        }

        SentinelSectionHeader(
            title = "More",
            subtitle = "Navigation and version information for the app shell."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelMetricCard(
                label = "Version",
                value = appVersion,
                accent = riskColor(RiskLevel.GREEN),
                supportingText = "Matches the Compose shell build."
            )
            SentinelMetricCard(
                label = "About",
                value = "Open",
                accent = riskColor(RiskLevel.CRITICAL),
                supportingText = "Mission, credits, and project context."
            )
        }

        SentinelCard {
            Text(
                text = "Need more context about the project?",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The About screen explains the mission statement, hackathon framing, and credits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onNavigateToAbout) {
                Text(text = "Open About")
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    // UI-only affordance to let the user inspect runtime permissions in system settings.
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openFullScreenIntentSettings(context: Context) {
    // Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT is API 34+; the permission concept does
    // not exist on older versions (it's implicitly granted there), so fall back to app settings.
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
}
