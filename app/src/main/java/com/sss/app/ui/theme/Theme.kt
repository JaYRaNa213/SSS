package com.sss.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SssColors = darkColorScheme(
    primary = Accent, onPrimary = AccentOn,
    primaryContainer = AccentSoft, onPrimaryContainer = Accent,
    secondary = TextSecondary, onSecondary = Bg,
    secondaryContainer = Surface3, onSecondaryContainer = TextPrimary,
    tertiary = Success, onTertiary = Bg,
    background = Bg, onBackground = TextPrimary,
    surface = Surface1, onSurface = TextPrimary,
    surfaceVariant = Surface2, onSurfaceVariant = TextMuted,
    surfaceContainerLowest = Bg, surfaceContainerLow = Surface1, surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2, surfaceContainerHighest = Surface3,
    outline = Line, outlineVariant = LineSoft,
    error = Danger, onError = Bg, errorContainer = DangerBg, onErrorContainer = Danger,
    inverseSurface = TextPrimary, inverseOnSurface = Bg
)

private val SssShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SSSTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SssColors, typography = Typography, shapes = SssShapes, content = content)
}