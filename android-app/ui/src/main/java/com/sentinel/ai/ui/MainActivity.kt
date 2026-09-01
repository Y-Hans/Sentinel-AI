package com.sentinel.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.sentinel.ai.ui.BuildConfig
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

        isPermissionOnboardingLaunch = firstLaunch

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
                    onPermissionOnboardingComplete = ::completePermissionOnboarding,
                    appVersion = BuildConfig.APP_VERSION
                )
            }
        }
    }

    private fun completePermissionOnboarding() {
        if (!isPermissionOnboardingLaunch) return

        getSharedPreferences(ONBOARDING_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
        isPermissionOnboardingLaunch = false
    }

    private companion object {
        const val ONBOARDING_PREFERENCES = "sentinel_onboarding"
        const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private var isPermissionOnboardingLaunch = false
}
