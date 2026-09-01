package com.sentinel.ai.ui.protection

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class ProtectionSnapshot(
    val protectionEnabled: Boolean = true,
    val guardServiceRunning: Boolean = false,
    val monitorServiceRunning: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val contactsPermissionGranted: Boolean = false,
    val missingPermissions: List<String> = emptyList()
)

object ProtectionControl {

    private const val TAG = "ProtectionControl"
    private const val PREFS_NAME = "sentinel_protection"
    private const val KEY_ENABLED = "protection_enabled"
    private const val SENTINEL_GUARD_SERVICE = "com.sentinel.ai.services.SentinelGuardService"
    private const val THREAT_MONITOR_SERVICE = "com.sentinel.ai.services.ThreatMonitorService"

    fun snapshot(context: Context): ProtectionSnapshot {
        val enabled = isProtectionEnabled(context)
        val guardRunning = isServiceRunning(context, SENTINEL_GUARD_SERVICE)
        val monitorRunning = isServiceRunning(context, THREAT_MONITOR_SERVICE)
        val listenerEnabled = isNotificationListenerEnabled(context)
        val notificationPermissionGranted = hasPostNotificationsPermission(context)
        val overlayPermissionGranted = Settings.canDrawOverlays(context)
        val contactsPermissionGranted = hasContactsPermission(context)
        val missingPermissions = buildList {
            if (!notificationPermissionGranted) add("Notification permission")
            if (!listenerEnabled) add("Notification listener access")
            if (!overlayPermissionGranted) add("Overlay permission")
            if (!contactsPermissionGranted) add("Contacts permission")
        }

        return ProtectionSnapshot(
            protectionEnabled = enabled,
            guardServiceRunning = guardRunning,
            monitorServiceRunning = monitorRunning,
            notificationListenerEnabled = listenerEnabled,
            notificationPermissionGranted = notificationPermissionGranted,
            overlayPermissionGranted = overlayPermissionGranted,
            contactsPermissionGranted = contactsPermissionGranted,
            missingPermissions = missingPermissions
        )
    }

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        sync(context)
    }

    fun isProtectionEnabled(context: Context): Boolean {
        return preferences(context).getBoolean(KEY_ENABLED, true)
    }

    fun sync(context: Context) {
        val enabled = isProtectionEnabled(context)
        if (enabled) {
            startService(context, SENTINEL_GUARD_SERVICE)
            startService(context, THREAT_MONITOR_SERVICE)
        } else {
            stopService(context, SENTINEL_GUARD_SERVICE)
            stopService(context, THREAT_MONITOR_SERVICE)
        }
    }

    private fun isServiceRunning(context: Context, className: String): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return activityManager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == className
        }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun startService(context: Context, className: String) {
        runCatching {
            val intent = Intent().setClassName(context, className)
            context.startService(intent)
        }.onFailure { error ->
            android.util.Log.w(TAG, "Optional background service start skipped or restricted: $className", error)
        }
    }

    private fun stopService(context: Context, className: String) {
        runCatching {
            val intent = Intent().setClassName(context, className)
            context.stopService(intent)
        }.onFailure { error ->
            android.util.Log.w(TAG, "Optional background service stop failed: $className", error)
        }
    }

    private fun preferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
