package com.sentinel.ai.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Muted semantic palette. Security colors are reserved for safe, warning, and dangerous states.
// ---------------------------------------------------------------------------------------------

val SentinelCyan = Color(0xFF8B949E)
val SentinelGreen = Color(0xFF66B486)
val SentinelYellow = Color(0xFFD2A451)
val SentinelRed = Color(0xFFD96B6B)
val SentinelCritical = SentinelRed

// ---------------------------------------------------------------------------------------------
// Neutral dark surfaces avoid pure black and keep elevation quiet.
// ---------------------------------------------------------------------------------------------

val SentinelBackground = Color(0xFF0B0C0E)
val SentinelSurface = Color(0xFF141619)
val SentinelSurfaceVariant = Color(0xFF1C1F23)
val SentinelOutline = Color(0xFF30343A)
val SentinelTextPrimary = Color(0xFFF1F2F4)
val SentinelTextSecondary = Color(0xFFA5AAB1)

// ---------------------------------------------------------------------------------------------
// Light theme surface & semantic tokens
//
// Calm, low-saturation Pixel-style neutrals. These only take effect in light mode; the default
// app theme remains dark, so no existing screen changes.
// ---------------------------------------------------------------------------------------------

val SentinelLightBackground = Color(0xFFF7F9FB)
val SentinelLightSurface = Color(0xFFFFFFFF)
val SentinelLightSurfaceVariant = Color(0xFFE4EAF1)
val SentinelLightOutline = Color(0xFFC4CEDA)
val SentinelLightTextPrimary = Color(0xFF16202C)
val SentinelLightTextSecondary = Color(0xFF4E5C6B)

// ---------------------------------------------------------------------------------------------
// Container tokens (used by both light and dark schemes for primary/secondary/tertiary roles)
// ---------------------------------------------------------------------------------------------

val SentinelPrimaryContainerDark = Color(0xFF25282D)
val SentinelOnPrimaryContainerDark = SentinelTextPrimary
val SentinelSecondaryContainerDark = Color(0xFF202328)
val SentinelOnSecondaryContainerDark = SentinelTextPrimary
val SentinelTertiaryContainerDark = Color(0xFF352224)
val SentinelOnTertiaryContainerDark = Color(0xFFF0C2C2)

val SentinelPrimaryContainerLight = Color(0xFFA6F0C6)
val SentinelOnPrimaryContainerLight = Color(0xFF00210F)
val SentinelSecondaryContainerLight = Color(0xFFCDEAF7)
val SentinelOnSecondaryContainerLight = Color(0xFF001F2B)
val SentinelTertiaryContainerLight = Color(0xFFFFD9EC)
val SentinelOnTertiaryContainerLight = Color(0xFF3A041F)

// Shared error tokens
val SentinelError = SentinelRed
val SentinelErrorContainerDark = Color(0xFF382123)
val SentinelOnErrorContainerDark = Color(0xFFF1C4C4)
val SentinelErrorContainerLight = Color(0xFFFFDAD6)
val SentinelOnErrorContainerLight = Color(0xFF410E0E)
