package com.sentinel.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SentinelGreen,
    secondary = SentinelCyan,
    tertiary = SentinelCritical,
    background = SentinelBackground,
    surface = SentinelSurface,
    surfaceVariant = SentinelSurfaceVariant,
    outline = SentinelOutline,
    onPrimary = SentinelBackground,
    onSecondary = SentinelBackground,
    onTertiary = SentinelBackground,
    onBackground = SentinelTextPrimary,
    onSurface = SentinelTextPrimary,
    onSurfaceVariant = SentinelTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = SentinelGreen,
    secondary = SentinelCyan,
    tertiary = SentinelCritical
)

@Composable
fun SentinelTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme || isSystemInDarkTheme()) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SentinelTypography,
        content = content
    )
}
