package com.sentinel.ai.ui

import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.sentinel.ai.ui.BuildConfig
import com.sentinel.ai.core.feature.FeatureManager
import com.sentinel.ai.ui.navigation.SentinelNavGraph
import com.sentinel.ai.ui.navigation.Screen
import com.sentinel.ai.ui.theme.SentinelTheme
import com.sentinel.ai.ui.theme.ThemePreferences
import com.sentinel.ai.ui.theme.rememberThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences(ONBOARDING_PREFERENCES, MODE_PRIVATE)
        val firstLaunch = preferences.getBoolean(KEY_FIRST_LAUNCH, true)

        val isBrowserDefault = isDefaultBrowser()

        // 🔥 FIX: onboarding should also trigger if browser NOT default
        isPermissionOnboardingLaunch = firstLaunch || !isBrowserDefault

        if (firstLaunch) {
            preferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }

        enableEdgeToEdge()

        setContent {
            val themeMode = rememberThemeMode(this)

            SentinelTheme(mode = themeMode.value) {
                val navController = rememberNavController()

                SentinelNavGraph(
                    navController = navController,
                    startDestination = if (isPermissionOnboardingLaunch) {
                        Screen.PermissionSetup.route
                    } else {
                        Screen.Dashboard.route
                    },
                    themeMode = themeMode.value,
                    onThemeModeSelected = { ThemePreferences.set(this, it) },
                    appVersion = BuildConfig.APP_VERSION
                )
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()

        // 🔥 FIX: re-check browser after returning from settings
        if (!isDefaultBrowser()) {
            isPermissionOnboardingLaunch = true
        }

        showIncompleteProtectionWarningOnce()
    }

    private fun showIncompleteProtectionWarningOnce() {
        if (warningShownThisProcess || isPermissionOnboardingLaunch) return

        val protection = com.sentinel.ai.ui.protection.ProtectionControl.snapshot(this)

        val missing = buildList {
            addAll(protection.missingPermissions)

            // 🔥 FIX: include browser in warning
            if (FeatureManager.isClickEnabled() && !isDefaultBrowser()) {
                add("Default browser not set")
            }
        }.distinct()

        if (missing.isEmpty()) return

        warningShownThisProcess = true

        AlertDialog.Builder(this)
            .setTitle("Protection incomplete:")
            .setMessage(missing.joinToString(separator = "\n") { "• $it" })
            .setPositiveButton("Fix Now") { _, _ ->
                startActivity(
                    Intent().setClassName(
                        this,
                        "com.sentinel.ai.ui.settings.SettingsActivity"
                    )
                )
            }
            .setNegativeButton("Ignore", null)
            .show()
    }

    private fun isDefaultBrowser(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        }

        @Suppress("DEPRECATION")
        return packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            0
        )?.activityInfo?.packageName == packageName
    }

    private companion object {
        const val ONBOARDING_PREFERENCES = "sentinel_onboarding"
        const val KEY_FIRST_LAUNCH = "first_launch"
        var warningShownThisProcess = false
    }

    private var isPermissionOnboardingLaunch = false
}