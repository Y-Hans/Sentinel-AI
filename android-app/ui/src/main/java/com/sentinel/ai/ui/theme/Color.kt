package com.sentinel.ai.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Brand & Risk palette
//
// These raw tokens are referenced directly across the UI (risk badges, indicators, accents).
// They are intentionally stable and must not change values, otherwise existing screens would
// shift in appearance.
// ---------------------------------------------------------------------------------------------

val SentinelCyan = Color(0xFF5BE7FF)
val SentinelGreen = Color(0xFF26D07C)
val SentinelYellow = Color(0xFFF6C453)
val SentinelRed = Color(0xFFFF6B6B)
val SentinelCritical = Color(0xFFFF4D9D)

// ---------------------------------------------------------------------------------------------
// Dark theme surface & semantic tokens
//
// Kept verbatim from the original design so the default (dark) appearance is unchanged.
// ---------------------------------------------------------------------------------------------

val SentinelBackground = Color(0xFF06111D)
val SentinelSurface = Color(0xFF0D1726)
val SentinelSurfaceVariant = Color(0xFF12233A)
val SentinelOutline = Color(0xFF29435E)
val SentinelTextPrimary = Color(0xFFEAF4FF)
val SentinelTextSecondary = Color(0xFFA9BED6)

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

val SentinelPrimaryContainerDark = Color(0xFF0E3A28)
val SentinelOnPrimaryContainerDark = Color(0xFFA6F0C6)
val SentinelSecondaryContainerDark = Color(0xFF08293A)
val SentinelOnSecondaryContainerDark = Color(0xFFA9E8FF)
val SentinelTertiaryContainerDark = Color(0xFF3A0F2A)
val SentinelOnTertiaryContainerDark = Color(0xFFFFB3DA)

val SentinelPrimaryContainerLight = Color(0xFFA6F0C6)
val SentinelOnPrimaryContainerLight = Color(0xFF00210F)
val SentinelSecondaryContainerLight = Color(0xFFCDEAF7)
val SentinelOnSecondaryContainerLight = Color(0xFF001F2B)
val SentinelTertiaryContainerLight = Color(0xFFFFD9EC)
val SentinelOnTertiaryContainerLight = Color(0xFF3A041F)

// Shared error tokens
val SentinelError = Color(0xFFE5484D)
val SentinelErrorContainerDark = Color(0xFF410E0E)
val SentinelOnErrorContainerDark = Color(0xFFFFDAD6)
val SentinelErrorContainerLight = Color(0xFFFFDAD6)
val SentinelOnErrorContainerLight = Color(0xFF410E0E)
