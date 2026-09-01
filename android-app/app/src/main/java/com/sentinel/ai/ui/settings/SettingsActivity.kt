package com.sentinel.ai.ui.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.sentinel.ai.core.feature.FeatureManager
import com.sentinel.ai.core.browser.BrowserOption
import com.sentinel.ai.core.browser.BrowserSelectionPolicy
import com.sentinel.ai.core.preferences.BrowserPreferenceRepository
import com.sentinel.ai.ui.theme.SentinelTheme
import com.sentinel.ai.ui.theme.rememberThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    private var state by mutableStateOf(FeatureState())
    private var showDefaultBrowserDialog by mutableStateOf(false)
    private var awaitingNotificationAccess = false
    private var awaitingDefaultBrowser = false
    private var showBrowserSelectionDialog by mutableStateOf(false)
    private var browserPreference by mutableStateOf(BrowserPreferenceRepository.ASK_EVERY_TIME)

    @Inject lateinit var browserPreferenceRepository: BrowserPreferenceRepository
    @Inject lateinit var browserSelectionPolicy: BrowserSelectionPolicy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshState()
        setContent {
            val themeMode = rememberThemeMode(this)
            SentinelTheme(mode = themeMode.value) {
                FeatureSettingsScreen(
                    state = state,
                    currentBrowserPreference = browserPreference,
                    onBack = ::finish,
                    onNotificationChanged = ::setNotificationEnabled,
                    onClickChanged = ::setClickEnabled,
                    onTextChanged = {
                        FeatureManager.setTextEnabled(it)
                        refreshState()
                    },
                    onChangeBrowserPreference = { showBrowserSelectionDialog = true }
                )
                if (showDefaultBrowserDialog) {
                    AlertDialog(
                        onDismissRequest = { showDefaultBrowserDialog = false },
                        title = { Text("Default browser required") },
                        text = { Text("To enable this feature, set Sentinel as your default browser") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDefaultBrowserDialog = false
                                awaitingDefaultBrowser = true
                                requestDefaultBrowser()
                            }) { Text("Continue") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDefaultBrowserDialog = false }) { Text("Cancel") }
                        }
                    )
                }
                if (showBrowserSelectionDialog) {
                    BrowserSelectionDialog(
                        browsers = browserSelectionPolicy.availableBrowsers(this@SettingsActivity),
                        currentPreference = browserPreference,
                        onDismiss = { showBrowserSelectionDialog = false },
                        onSelect = { packageName ->
                            browserPreferenceRepository.setPreferredBrowser(
                                packageName.takeUnless { it == BrowserPreferenceRepository.ASK_EVERY_TIME }
                            )
                            showBrowserSelectionDialog = false
                            refreshState()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (awaitingNotificationAccess && hasNotificationAccess()) {
            FeatureManager.setNotificationEnabled(true)
            awaitingNotificationAccess = false
        }
        if (awaitingDefaultBrowser && isDefaultBrowser()) {
            FeatureManager.setClickEnabled(true)
            awaitingDefaultBrowser = false
        }
        refreshState()
    }

    private fun setNotificationEnabled(enabled: Boolean) {
        if (!enabled) {
            awaitingNotificationAccess = false
            FeatureManager.setNotificationEnabled(false)
        } else if (hasNotificationAccess()) {
            FeatureManager.setNotificationEnabled(true)
        } else {
            awaitingNotificationAccess = true
            openNotificationAccessSettings()
        }
        refreshState()
    }

    private fun setClickEnabled(enabled: Boolean) {
        if (!enabled) {
            awaitingDefaultBrowser = false
            FeatureManager.setClickEnabled(false)
        } else if (isDefaultBrowser()) {
            FeatureManager.setClickEnabled(true)
        } else {
            showDefaultBrowserDialog = true
        }
        refreshState()
    }

    private fun refreshState() {
        state = FeatureState(
            notificationEnabled = FeatureManager.isNotificationEnabled(),
            clickEnabled = FeatureManager.isClickEnabled(),
            textEnabled = FeatureManager.isTextEnabled()
        )
        browserPreference = browserPreferenceRepository.getRawPreference()
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun isDefaultBrowser(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        }
        return false
    }

    private fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                if (launchSettingsIntent(intent)) return
            }
        }
        val opened = launchSettingsIntent(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        if (!opened) openAppSettings()
    }

    private fun openNotificationAccessSettings() {
        val opened = launchSettingsIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        if (!opened) openAppSettings()
    }

    private fun openAppSettings() {
        launchSettingsIntent(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun launchSettingsIntent(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

private data class FeatureState(
    val notificationEnabled: Boolean = true,
    val clickEnabled: Boolean = true,
    val textEnabled: Boolean = true
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FeatureSettingsScreen(
    state: FeatureState,
    currentBrowserPreference: String,
    onBack: () -> Unit,
    onNotificationChanged: (Boolean) -> Unit,
    onClickChanged: (Boolean) -> Unit,
    onTextChanged: (Boolean) -> Unit,
    onChangeBrowserPreference: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protection features") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Enable only the protection features you want.", style = MaterialTheme.typography.bodyLarge)
            FeatureToggle("Notification Protection", "Detect scams from incoming messages", "Requires notification access", state.notificationEnabled, onNotificationChanged)
            FeatureToggle("Click Protection", "Scan links before opening", "Requires Sentinel as the default browser", state.clickEnabled, onClickChanged)
            FeatureToggle("Text Selection", "Analyze selected text", null, state.textEnabled, onTextChanged)
            Spacer(Modifier.height(16.dp))
            Text("Preferences", style = MaterialTheme.typography.titleLarge)
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onChangeBrowserPreference)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Preferred Browser", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (currentBrowserPreference == BrowserPreferenceRepository.ASK_EVERY_TIME) "Ask every time" else currentBrowserPreference,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserSelectionDialog(
    browsers: List<BrowserOption>,
    currentPreference: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Preferred Browser") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                BrowserChoice(
                    label = "Ask every time",
                    selected = currentPreference == BrowserPreferenceRepository.ASK_EVERY_TIME,
                    onClick = { onSelect(BrowserPreferenceRepository.ASK_EVERY_TIME) }
                )
                browsers.forEach { browser ->
                    BrowserChoice(
                        label = browser.label,
                        selected = currentPreference == browser.packageName,
                        onClick = { onSelect(browser.packageName) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BrowserChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FeatureToggle(
    title: String,
    description: String,
    requirement: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
                requirement?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
