package com.sentinel.ai.ui

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.sentinel.ai.ui.BuildConfig
import com.sentinel.ai.ui.navigation.SentinelNavGraph
import com.sentinel.ai.ui.theme.SentinelTheme
import com.sentinel.ai.ui.NotificationPermissionPolicy
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS denied by the user")
            }
        }

    // READ_CONTACTS is declared in the manifest but, being a dangerous permission, is never
    // granted unless requested at runtime. Without this request the app could never resolve a
    // sender identifier to a saved contact name, which was the root cause of every sender being
    // shown as "Unknown Contact" even when the sender existed in Android Contacts.
    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.w(TAG, "READ_CONTACTS denied by the user")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        maybeRequestContactsPermission()
        enableEdgeToEdge()
        setContent {
            SentinelTheme {
                val navController = rememberNavController()
                SentinelNavGraph(
                    navController = navController,
                    appVersion = BuildConfig.APP_VERSION
                )
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (NotificationPermissionPolicy.shouldRequestPermission(Build.VERSION.SDK_INT, granted)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybeRequestContactsPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!granted) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
