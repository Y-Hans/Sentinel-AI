package com.sentinel.ai.ui.screens.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    val refresh = { snapshot = ProtectionControl.snapshot(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snapshot.missingPermissions) {
        if (snapshot.missingPermissions.isEmpty()) {
            ProtectionControl.sync(context)
            onPermissionsComplete()
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
        Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.XS)) {
            Text(
                text = "Set up Sentinel",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Sentinel needs a few permissions to monitor threats and alert you when action is needed.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PermissionRows(
            snapshot = snapshot,
            onRequestNotifications = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onRequestContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
            onOpenListenerSettings = { openNotificationListenerSettings(context) },
            onOpenOverlaySettings = { openOverlaySettings(context) }
        )

        if (snapshot.missingPermissions.isEmpty()) {
            ActionButton(
                text = "Continue to Home",
                onClick = onPermissionsComplete,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "Grant each item above. Sentinel will continue as soon as setup is complete.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRows(
    snapshot: ProtectionSnapshot,
    onRequestNotifications: () -> Unit,
    onRequestContacts: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SentinelSpacing.BetweenItems)) {
        PermissionRow("Notifications", "Show urgent security warnings", snapshot.notificationPermissionGranted, onRequestNotifications)
        PermissionRow("Notification access", "Monitor incoming notifications for threats", snapshot.notificationListenerEnabled, onOpenListenerSettings)
        PermissionRow("Overlay alerts", "Display urgent warnings above other apps", snapshot.overlayPermissionGranted, onOpenOverlaySettings)
        PermissionRow("Contacts", "Recognize known senders in alerts", snapshot.contactsPermissionGranted, onRequestContacts)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    explanation: String,
    granted: Boolean,
    onGrant: () -> Unit
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
                    text = "Grant permission",
                    onClick = onGrant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SentinelSpacing.XXS)
                )
            }
        }
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
