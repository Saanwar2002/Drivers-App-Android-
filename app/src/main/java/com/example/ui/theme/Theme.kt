package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AnyRollerDarkColorScheme = darkColorScheme(
    primary = AnyRollerGreen,
    onPrimary = AnyRollerBgPrimary,
    primaryContainer = AnyRollerGreenDeep,
    onPrimaryContainer = AnyRollerTextPrimary,
    secondary = AnyRollerBlue,
    onSecondary = AnyRollerTextPrimary,
    secondaryContainer = AnyRollerBgTertiary,
    onSecondaryContainer = AnyRollerTextSecondary,
    tertiary = AnyRollerPurple,
    onTertiary = AnyRollerTextPrimary,
    background = AnyRollerBgPrimary,
    onBackground = AnyRollerTextPrimary,
    surface = AnyRollerBgSecondary,
    onSurface = AnyRollerTextPrimary,
    surfaceVariant = AnyRollerBgTertiary,
    onSurfaceVariant = AnyRollerTextMuted,
    outline = AnyRollerBorder,
    error = AnyRollerRed,
    onError = AnyRollerTextPrimary
)

// Dark cockpit Google Map styling matching DriverTerminal.tsx night-driving aesthetic
const val ANYROLLER_DARK_MAP_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#121318"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8a92a6"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#0d0d0f"}]},
  {"featureType":"administrative","elementType":"geometry.stroke","stylers":[{"color":"#24262e"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#181a20"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#12211b"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#232630"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#16181f"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#2f3444"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1a1d26"}]},
  {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#1c1f28"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0b1623"}]}
]
"""

@Composable
fun AnyRollerDriverTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AnyRollerDarkColorScheme,
        typography = Typography,
        content = content
    )
}
