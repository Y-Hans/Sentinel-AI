package com.sentinel.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.sentinel.ai.ui.BuildConfig
import com.sentinel.ai.ui.navigation.SentinelNavGraph
import com.sentinel.ai.ui.navigation.Screen
import com.sentinel.ai.ui.protection.ProtectionControl
import com.sentinel.ai.ui.theme.SentinelTheme
import com.sentinel.ai.ui.theme.ThemePreferences
import com.sentinel.ai.ui.theme.rememberThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode = rememberThemeMode(this)
            SentinelTheme(mode = themeMode.value) {
                val navController = rememberNavController()
                SentinelNavGraph(
                    navController = navController,
                    startDestination = if (ProtectionControl.snapshot(this).missingPermissions.isEmpty()) {
                        Screen.Dashboard.route
                    } else {
                        Screen.PermissionSetup.route
                    },
                    themeMode = themeMode.value,
                    onThemeModeSelected = { ThemePreferences.set(this, it) },
                    appVersion = BuildConfig.APP_VERSION
                )
            }
        }
    }
}
