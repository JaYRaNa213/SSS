package com.sss.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SSSDarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,

    secondary = LightGray,
    onSecondary = Black,

    background = Black,
    onBackground = White,

    surface = DarkSurface,
    onSurface = White,

    surfaceVariant = DarkCard,
    onSurfaceVariant = LightGray,

    outline = DarkBorder
)

@Composable
fun SSSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SSSDarkColorScheme,
        typography = Typography,
        content = content
    )
}