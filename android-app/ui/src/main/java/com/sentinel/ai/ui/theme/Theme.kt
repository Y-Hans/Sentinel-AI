package com.sentinel.ai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// ---------------------------------------------------------------------------------------------
// Sentinel dark color scheme: neutral controls with semantic risk colors kept out of chrome.
// ---------------------------------------------------------------------------------------------

private val DarkColorScheme = darkColorScheme(
    primary = SentinelTextPrimary,
    onPrimary = SentinelBackground,
    primaryContainer = SentinelPrimaryContainerDark,
    onPrimaryContainer = SentinelOnPrimaryContainerDark,
    secondary = SentinelTextSecondary,
    onSecondary = SentinelBackground,
    secondaryContainer = SentinelSecondaryContainerDark,
    onSecondaryContainer = SentinelOnSecondaryContainerDark,
    tertiary = SentinelYellow,
    onTertiary = SentinelBackground,
    tertiaryContainer = SentinelTertiaryContainerDark,
    onTertiaryContainer = SentinelOnTertiaryContainerDark,
    error = SentinelError,
    onError = SentinelBackground,
    errorContainer = SentinelErrorContainerDark,
    onErrorContainer = SentinelOnErrorContainerDark,
    background = SentinelBackground,
    onBackground = SentinelTextPrimary,
    surface = SentinelSurface,
    onSurface = SentinelTextPrimary,
    surfaceVariant = SentinelSurfaceVariant,
    onSurfaceVariant = SentinelTextSecondary,
    outline = SentinelOutline,
    outlineVariant = SentinelOutline.copy(alpha = 0.5f),
    surfaceTint = SentinelSurface
)

// ---------------------------------------------------------------------------------------------
// Sentinel light color scheme
//
// Calm, accessible Pixel-style light palette. Only active when light mode is selected.
// ---------------------------------------------------------------------------------------------

private val LightColorScheme = lightColorScheme(
    primary = SentinelGreen,
    onPrimary = SentinelBackground,
    primaryContainer = SentinelPrimaryContainerLight,
    onPrimaryContainer = SentinelOnPrimaryContainerLight,
    secondary = SentinelCyan,
    onSecondary = SentinelBackground,
    secondaryContainer = SentinelSecondaryContainerLight,
    onSecondaryContainer = SentinelOnSecondaryContainerLight,
    tertiary = SentinelCritical,
    onTertiary = SentinelBackground,
    tertiaryContainer = SentinelTertiaryContainerLight,
    onTertiaryContainer = SentinelOnTertiaryContainerLight,
    error = SentinelError,
    onError = SentinelLightSurface,
    errorContainer = SentinelErrorContainerLight,
    onErrorContainer = SentinelOnErrorContainerLight,
    background = SentinelLightBackground,
    onBackground = SentinelLightTextPrimary,
    surface = SentinelLightSurface,
    onSurface = SentinelLightTextPrimary,
    surfaceVariant = SentinelLightSurfaceVariant,
    onSurfaceVariant = SentinelLightTextSecondary,
    outline = SentinelLightOutline,
    outlineVariant = SentinelLightOutline.copy(alpha = 0.5f),
    surfaceTint = SentinelGreen
)

// Kept for stored preference compatibility; it now resolves to the same restrained visual system.
private val NeonColorScheme = darkColorScheme(
    primary = SentinelTextPrimary,
    onPrimary = SentinelBackground,
    primaryContainer = SentinelSecondaryContainerDark,
    onPrimaryContainer = SentinelOnSecondaryContainerDark,
    secondary = SentinelTextSecondary,
    onSecondary = SentinelBackground,
    secondaryContainer = SentinelTertiaryContainerDark,
    onSecondaryContainer = SentinelOnTertiaryContainerDark,
    tertiary = SentinelYellow,
    onTertiary = SentinelBackground,
    tertiaryContainer = SentinelPrimaryContainerDark,
    onTertiaryContainer = SentinelOnPrimaryContainerDark,
    error = SentinelError,
    onError = SentinelBackground,
    errorContainer = SentinelErrorContainerDark,
    onErrorContainer = SentinelOnErrorContainerDark,
    background = SentinelBackground,
    onBackground = SentinelTextPrimary,
    surface = SentinelSurface,
    onSurface = SentinelTextPrimary,
    surfaceVariant = SentinelSurfaceVariant,
    onSurfaceVariant = SentinelTextSecondary,
    outline = SentinelOutline,
    outlineVariant = SentinelOutline.copy(alpha = 0.5f),
    surfaceTint = SentinelSurface
)

/**
 * Sentinel application theme.
 *
 * @param darkTheme when true (the default) the dark scheme is used.
 * @param dynamicColor when true and the device is Android 12+ (API 31), the system Material You
 *        dynamic color scheme is used; otherwise the static Sentinel light/dark schemes apply.
 */
@Composable
fun SentinelTheme(
    mode: SentinelThemeMode = SentinelThemeMode.Dark,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val colorScheme = when {
        mode == SentinelThemeMode.Dark -> DarkColorScheme
        mode == SentinelThemeMode.Neon -> NeonColorScheme
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (systemIsDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        systemIsDark -> DarkColorScheme
        else -> LightColorScheme
    }

    SentinelMaterialTheme(colorScheme = colorScheme, content = content)
}

/** Preview-only compatibility overload for existing static light and dark previews. */
@Composable
fun SentinelTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    SentinelMaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun SentinelMaterialTheme(
    colorScheme: androidx.compose.material3.ColorScheme,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SentinelTypography,
        shapes = SentinelShapes,
        content = content
    )
}
