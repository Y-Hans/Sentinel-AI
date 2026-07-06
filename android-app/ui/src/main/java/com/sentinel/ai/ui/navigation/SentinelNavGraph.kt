package com.sentinel.ai.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.sentinel.ai.ui.screens.about.AboutScreen
import com.sentinel.ai.ui.screens.alert.AlertScreen
import com.sentinel.ai.ui.screens.copilot.CopilotScreen
import com.sentinel.ai.ui.screens.dashboard.DashboardScreen
import com.sentinel.ai.ui.screens.history.HistoryScreen
import com.sentinel.ai.ui.screens.scanner.ScannerScreen
import com.sentinel.ai.ui.screens.settings.SettingsScreen
import com.sentinel.ai.ui.screens.threat.ThreatDetailsScreen
import com.sentinel.ai.ui.theme.SentinelSurface

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Home", Icons.Filled.Home),
    BottomNavItem(Screen.History, "History", Icons.Filled.History),
    BottomNavItem(Screen.Settings, "Settings", Icons.Filled.Settings),
    BottomNavItem(Screen.About, "About", Icons.Filled.Info)
)

@Composable
fun SentinelNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route,
    appVersion: String = "1.0.0"
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            val showBottomBar = currentRoute?.startsWith("threat_details") != true
            if (showBottomBar) {
                SentinelBottomBar(
                    currentRoute = currentRoute ?: startDestination,
                    onDestinationSelected = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        SentinelNavHost(
            navController = navController,
            paddingValues = paddingValues,
            startDestination = startDestination,
            appVersion = appVersion
        )
    }
}

@Composable
private fun SentinelBottomBar(
    currentRoute: String,
    onDestinationSelected: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = SentinelSurface.copy(alpha = 0.96f)
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationSelected(item.screen) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(text = item.label)
                }
            )
        }
    }
}

@Composable
private fun SentinelNavHost(
    navController: NavHostController,
    paddingValues: PaddingValues,
    startDestination: String,
    appVersion: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = androidx.compose.ui.Modifier.padding(paddingValues)
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onThreatSelected = { threatId ->
                    navController.navigate(Screen.ThreatDetails.createRoute(threatId))
                }
            )
        }
        composable(
            route = Screen.ThreatDetails.route,
            arguments = listOf(navArgument(Screen.ThreatDetails.argumentName) { type = NavType.StringType })
        ) { backStackEntry ->
            ThreatDetailsScreen(
                threatId = backStackEntry.arguments?.getString(Screen.ThreatDetails.argumentName).orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                appVersion = appVersion,
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                appVersion = appVersion
            )
        }
        composable(Screen.Alerts.route) {
            AlertScreen()
        }
        composable(Screen.Scanner.route) {
            ScannerScreen()
        }
        composable(Screen.Copilot.route) {
            CopilotScreen()
        }
    }
}
