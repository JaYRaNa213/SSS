package com.sss.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val GraphiteColors = darkColorScheme(
    primary = TextPrimary, onPrimary = Bg,
    primaryContainer = Surface3, onPrimaryContainer = TextPrimary,
    secondary = TextSecondary, onSecondary = Bg,
    secondaryContainer = Surface3, onSecondaryContainer = TextPrimary,
    tertiary = Lime, onTertiary = Bg,
    background = Bg, onBackground = TextPrimary,
    surface = Surface1, onSurface = TextPrimary,
    surfaceVariant = Surface2, onSurfaceVariant = TextMuted,
    surfaceContainerLowest = Bg, surfaceContainerLow = Surface1, surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2, surfaceContainerHighest = Surface3,
    outline = Line, outlineVariant = LineSoft,
    error = Danger, onError = Bg, errorContainer = DangerBg, onErrorContainer = Danger,
    inverseSurface = TextPrimary, inverseOnSurface = Bg
)

private val GraphiteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SSSTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GraphiteColors, typography = Typography, shapes = GraphiteShapes, content = content)
}