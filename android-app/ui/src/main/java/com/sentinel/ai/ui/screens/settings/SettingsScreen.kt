package com.sentinel.ai.ui.screens.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sentinel.ai.ui.components.PremiumListRow
import com.sentinel.ai.ui.components.PremiumPanel
import com.sentinel.ai.ui.components.PremiumSectionTitle
import com.sentinel.ai.ui.components.RiskState
import com.sentinel.ai.ui.components.StatusDot
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.protection.ProtectionSnapshot
import com.sentinel.ai.ui.theme.SentinelSpacing
import com.sentinel.ai.ui.theme.SentinelThemeMode

@Composable
fun SettingsScreen(
    appVersion: String,
    selectedTheme: SentinelThemeMode,
    onThemeSelected: (SentinelThemeMode) -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var sentinelIsDefaultBrowser by remember(context) {
        mutableStateOf(isDefaultBrowser(context))
    }

    DisposableEffect(lifecycleOwner, viewModel, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(SettingsUiAction.RefreshStatus)
                sentinelIsDefaultBrowser = isDefaultBrowser(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsContent(
        appVersion = appVersion,
        protection = uiState.protection,
        selectedTheme = selectedTheme,
        sentinelIsDefaultBrowser = sentinelIsDefaultBrowser,
        onProtectionChanged = {
            viewModel.onAction(SettingsUiAction.SetGuardEnabled(it))
        },
        onOpenProtectionSettings = { openProtectionSettings(context) },
        onOpenNotificationAccessSettings = { openNotificationAccessSettings(context) },
        onOpenOverlaySettings = { openOverlaySettings(context) },
        onOpenContactsSettings = { openAppSettings(context) },
        onOpenDefaultAppsSettings = { openDefaultAppsSettings(context) },
        onUseSystemThemeChanged = { useSystem ->
            onThemeSelected(if (useSystem) SentinelThemeMode.System else SentinelThemeMode.Dark)
        },
        onNavigateToAbout = onNavigateToAbout
    )
}

@Composable
private fun SettingsContent(
    appVersion: String,
    protection: ProtectionSnapshot,
    selectedTheme: SentinelThemeMode,
    sentinelIsDefaultBrowser: Boolean,
    onProtectionChanged: (Boolean) -> Unit,
    onOpenProtectionSettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenContactsSettings: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onUseSystemThemeChanged: (Boolean) -> Unit,
    onNavigateToAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = SentinelSpacing.MD),
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)
    ) {
        Text(
            text = "Manage how Sentinel protects this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(SentinelSpacing.XS))
        PremiumSectionTitle(text = "Protection features")
        PremiumPanel {
            PremiumListRow(
                title = "Real-time protection",
                description = if (protection.protectionEnabled) {
                    "Monitoring links and messages"
                } else {
                    "Protection is paused"
                },
                leading = { SettingsIcon(Icons.Filled.Security) },
                trailing = {
                    Switch(
                        checked = protection.protectionEnabled,
                        onCheckedChange = onProtectionChanged
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PremiumListRow(
                title = "Protection controls",
                description = "Choose where Sentinel can check content",
                leading = { SettingsIcon(Icons.Filled.AdminPanelSettings) },
                trailing = { Chevron() },
                onClick = onOpenProtectionSettings
            )
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.XS))
        PremiumSectionTitle(text = "Permissions")
        PremiumPanel {
            PermissionRow(
                title = "Notification access",
                description = "Required to scan incoming messages",
                granted = protection.notificationListenerEnabled,
                icon = Icons.Filled.Notifications,
                onClick = onOpenNotificationAccessSettings
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionRow(
                title = "Display over other apps",
                description = "Required to show urgent alerts",
                granted = protection.overlayPermissionGranted,
                icon = Icons.Filled.PhoneAndroid,
                onClick = onOpenOverlaySettings
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionRow(
                title = "Contacts",
                description = "Required to recognize known senders",
                granted = protection.contactsPermissionGranted,
                icon = Icons.Filled.Lock,
                onClick = onOpenContactsSettings
            )
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.XS))
        PremiumSectionTitle(text = "General")
        PremiumPanel {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PremiumListRow(
                    title = "Default browser",
                    description = "Lets Sentinel check links before opening",
                    leading = { SettingsIcon(Icons.Filled.PhoneAndroid) },
                    trailing = { PermissionState(granted = sentinelIsDefaultBrowser) },
                    onClick = onOpenDefaultAppsSettings
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            PremiumListRow(
                title = "Use device appearance",
                description = "Follow the system light or dark setting",
                leading = { SettingsIcon(Icons.Filled.PhoneAndroid) },
                trailing = {
                    Switch(
                        checked = selectedTheme == SentinelThemeMode.System,
                        onCheckedChange = onUseSystemThemeChanged
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PremiumListRow(
                title = "About Sentinel AI",
                description = "Version $appVersion",
                leading = { SettingsIcon(Icons.Filled.Info) },
                trailing = { Chevron() },
                onClick = onNavigateToAbout
            )
        }

        Spacer(modifier = Modifier.height(SentinelSpacing.LG))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    PremiumListRow(
        title = title,
        description = description,
        leading = { SettingsIcon(icon) },
        trailing = { PermissionState(granted = granted) },
        onClick = onClick
    )
}

@Composable
private fun PermissionState(granted: Boolean) {
    val color = if (granted) riskColor(RiskState.Safe) else riskColor(RiskState.Suspicious)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.XS)
    ) {
        StatusDot(color = color)
        Text(
            text = if (granted) "Granted" else "Not Granted",
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun SettingsIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
    )
}

private fun openProtectionSettings(context: Context) {
    context.startActivity(
        Intent().setClassName(
            context,
            "com.sentinel.ai.ui.settings.SettingsActivity"
        )
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    launchSettingsIntent(context, intent)
}

private fun openDefaultAppsSettings(context: Context) {
    val opened = launchSettingsIntent(
        context,
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    )
    if (!opened) openAppSettings(context)
}

private fun openNotificationAccessSettings(context: Context) {
    val opened = launchSettingsIntent(
        context,
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    )
    if (!opened) openAppSettings(context)
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    if (!launchSettingsIntent(context, intent)) openAppSettings(context)
}

private fun launchSettingsIntent(context: Context, intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun isDefaultBrowser(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return context.getSystemService(RoleManager::class.java)
        ?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
}
