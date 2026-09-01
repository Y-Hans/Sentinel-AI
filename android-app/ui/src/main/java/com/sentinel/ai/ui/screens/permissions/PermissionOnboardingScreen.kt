package com.sentinel.ai.ui.screens.permissions

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sentinel.ai.ui.components.ActionButton
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.protection.ProtectionControl
import com.sentinel.ai.ui.protection.ProtectionSnapshot
import com.sentinel.ai.ui.theme.SentinelSize
import com.sentinel.ai.ui.theme.SentinelSpacing

@Composable
fun PermissionOnboardingScreen(
    onPermissionsComplete: () -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(ProtectionControl.snapshot(context)) }
    var defaultBrowserSet by remember { mutableStateOf(isDefaultBrowser(context)) }
    val refresh = {
        snapshot = ProtectionControl.snapshot(context)
        defaultBrowserSet = isDefaultBrowser(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val browserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allConfigured = snapshot.missingPermissions.isEmpty() && defaultBrowserSet
    LaunchedEffect(allConfigured) {
        if (allConfigured) {
            ProtectionControl.sync(context)
            onPermissionsComplete()
        }
    }

    val completeSetup = {
        ProtectionControl.sync(context)
        onPermissionsComplete()
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
        Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.XS)) {
            Text(
                text = "Set up Sentinel",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Sentinel needs a few permissions and link interception to monitor threats and alert you when action is needed.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PermissionRows(
            snapshot = snapshot,
            isDefaultBrowser = defaultBrowserSet,
            onRequestNotifications = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onRequestContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
            onOpenListenerSettings = { openNotificationListenerSettings(context) },
            onOpenOverlaySettings = { openOverlaySettings(context) },
            onRequestDefaultBrowser = {
                requestDefaultBrowser(context) { intent -> browserLauncher.launch(intent) }
            }
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ActionButton(
                text = if (allConfigured) "Continue to Home" else "Get Started",
                onClick = completeSetup,
                modifier = Modifier.fillMaxWidth()
            )
            if (!allConfigured) {
                TextButton(
                    onClick = completeSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Skip for now",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRows(
    snapshot: ProtectionSnapshot,
    isDefaultBrowser: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestContacts: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestDefaultBrowser: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.BetweenItems)) {
        PermissionRow(
            title = "Notifications",
            explanation = "Show urgent security warnings",
            granted = snapshot.notificationPermissionGranted,
            onGrant = onRequestNotifications
        )
        PermissionRow(
            title = "Notification access",
            explanation = "Monitor incoming notifications for threats",
            granted = snapshot.notificationListenerEnabled,
            onGrant = onOpenListenerSettings
        )
        PermissionRow(
            title = "Overlay alerts",
            explanation = "Display urgent warnings above other apps",
            granted = snapshot.overlayPermissionGranted,
            onGrant = onOpenOverlaySettings
        )
        PermissionRow(
            title = "Contacts",
            explanation = "Recognize known senders in alerts",
            granted = snapshot.contactsPermissionGranted,
            onGrant = onRequestContacts
        )
        PermissionRow(
            title = "Default browser",
            explanation = "Sentinel needs to receive web links so it can scan them before they open.",
            granted = isDefaultBrowser,
            onGrant = onRequestDefaultBrowser,
            actionText = "Set Sentinel AI as default browser"
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    explanation: String,
    granted: Boolean,
    onGrant: () -> Unit,
    actionText: String = "Grant permission"
) {
    SentinelCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.Security else Icons.Filled.Info,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (granted) "Granted" else "Not Granted",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                ActionButton(
                    text = actionText,
                    onClick = onGrant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SentinelSpacing.XXS)
                )
            }
        }
    }
}

private fun isDefaultBrowser(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    }
    @Suppress("DEPRECATION")
    return context.packageManager.resolveActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        0
    )?.activityInfo?.packageName == context.packageName
}

private fun requestDefaultBrowser(context: Context, launcher: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            launcher(intent)
            return
        }
    }
    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (!launchSettingsIntent(context, intent)) {
        openAppSettings(context)
    }
}

private fun openNotificationListenerSettings(context: Context) {
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

private fun openAppSettings(context: Context) {
    launchSettingsIntent(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )
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
