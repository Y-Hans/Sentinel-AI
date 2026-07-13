package com.sentinel.ai.ui.screens.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.ui.components.ActionButton
import com.sentinel.ai.ui.components.ElevatedSentinelCard
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.SettingRow
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.protection.ProtectionSnapshot
import com.sentinel.ai.ui.theme.SentinelSize
import com.sentinel.ai.ui.theme.SentinelSpacing
import com.sentinel.ai.ui.theme.SentinelThemeMode
import com.sentinel.ai.ui.theme.rememberWindowWidthClass

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    appVersion: String,
    selectedTheme: SentinelThemeMode,
    onThemeSelected: (SentinelThemeMode) -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val protection = uiState.protection
    val isCompact = rememberWindowWidthClass().isCompact
    var sentinelIsDefaultBrowser by remember(context) {
        mutableStateOf(isDefaultBrowser(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(SettingsUiAction.RefreshStatus)
                sentinelIsDefaultBrowser = isDefaultBrowser(context)
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
            .padding(
                horizontal = SentinelSpacing.ScreenHorizontal,
                vertical = SentinelSpacing.ScreenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.BetweenSections)
    ) {
        Text(
            text = "Control protection, permissions, and the app-wide appearance.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ElevatedSentinelCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (protection.protectionEnabled) "Protection Active" else "Protection Disabled",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(SentinelSpacing.XXS))
                        Text(
                            text = if (protection.protectionEnabled) {
                                "The UI reflects the live backend guard state."
                            } else {
                                "The shield is paused and the backend services are stopped."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = protection.protectionEnabled,
                        onCheckedChange = { viewModel.onAction(SettingsUiAction.SetGuardEnabled(it)) },
                        modifier = Modifier
                            .size(SentinelSize.MinTouchTarget)
                            .padding(SentinelSpacing.None)
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !sentinelIsDefaultBrowser) {
            SentinelCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Make Sentinel your default browser",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(SentinelSpacing.XXS))
                    Text(
                        text = "Android manages the default browser in system settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SentinelSpacing.MD))
                    ActionButton(
                        text = "Set Sentinel as Default Browser",
                        onClick = { requestDefaultBrowser(context) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        SentinelSectionHeader(
            title = "Notification permissions",
            subtitle = "Status is read-only here; the actual permission flow remains in the activity"
        )

        SettingRow(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = riskColor(RiskLevel.GREEN),
                    modifier = Modifier.size(SentinelSize.IconMedium)
                )
            },
            title = "Protection features",
            description = "Control notification, click, and text-selection protection",
            onClick = {
                context.startActivity(
                    Intent().setClassName(
                        context,
                        "com.sentinel.ai.ui.settings.SettingsActivity"
                    )
                )
            }
        )

        SettingRow(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SentinelSize.IconMedium)
                )
            },
            title = "Notification listener",
            description = if (protection.notificationListenerEnabled) "Available" else "Unavailable",
            trailing = null
        )
        SettingRow(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SentinelSize.IconMedium)
                )
            },
            title = "Permissions",
            description = if (protection.missingPermissions.isEmpty()) {
                "Notifications, overlay, and contacts are available"
            } else {
                protection.missingPermissions.joinToString()
            }
        )

        PermissionSettingsCard(context = context, isCompact = isCompact)

        SentinelSectionHeader(
            title = "Theme",
            subtitle = "Choose one appearance for the entire app"
        )
        SentinelCard {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.SM),
                verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)
            ) {
                SentinelThemeMode.entries.forEach { option ->
                    FilterChip(
                        selected = selectedTheme == option,
                        onClick = {
                            onThemeSelected(option)
                        },
                        label = { Text(option.name) },
                        modifier = Modifier.height(SentinelSize.MinTouchTarget)
                    )
                }
            }
        }

        SentinelSectionHeader(
            title = "More",
            subtitle = "Navigation and version information for the app shell"
        )
        SettingRow(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = riskColor(RiskLevel.GREEN),
                    modifier = Modifier.size(SentinelSize.IconMedium)
                )
            },
            title = appVersion,
            description = "Matches the Compose shell build"
        )
        SettingRow(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SentinelSize.IconMedium)
                )
            },
            title = "About Sentinel AI",
            description = "Mission, credits, and project context",
            onClick = onNavigateToAbout,
            trailing = {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
private fun PermissionSettingsCard(context: Context, isCompact: Boolean) {
    SentinelCard {
        if (isCompact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD)
            ) {
                PermissionSettingsCopy()
                ActionButton(
                    text = "Open app settings",
                    onClick = { openAppSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.MD)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    PermissionSettingsCopy()
                }
                ActionButton(
                    text = "Open app settings",
                    onClick = { openAppSettings(context) },
                    modifier = Modifier.height(SentinelSize.ButtonHeight)
                )
            }
        }
    }
}

@Composable
private fun PermissionSettingsCopy() {
    Text(
        text = "Open system notification settings",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(SentinelSpacing.XXS))
    Text(
        text = "Review runtime permissions in system settings",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun isDefaultBrowser(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return context.getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    }

    return false
}

private fun requestDefaultBrowser(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val activity = context as? Activity ?: return
        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            activity.startActivityForResult(intent, DEFAULT_BROWSER_REQUEST_CODE)
        }
    }
}

private const val DEFAULT_BROWSER_REQUEST_CODE = 1001
