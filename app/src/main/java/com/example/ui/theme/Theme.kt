package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TvDarkColorScheme = darkColorScheme(
    primary = TvSkyBlue,
    onPrimary = TvBackgroundDark,
    primaryContainer = TvSkyBlueDark,
    onPrimaryContainer = TvTextPrimary,
    secondary = TvEmerald,
    onSecondary = TvBackgroundDark,
    tertiary = TvAmber,
    background = TvBackgroundDark,
    onBackground = TvTextPrimary,
    surface = TvSurfaceDark,
    onSurface = TvTextPrimary,
    surfaceVariant = TvSurfaceVariantDark,
    onSurfaceVariant = TvTextSecondary,
    outline = TvBorderSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = Typography,
        content = content
    )
}
