package com.sss.app.ui.theme

import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0A0A0B)
val Surface1 = Color(0xFF131316)
val Surface2 = Color(0xFF1A1A1E)
val Surface3 = Color(0xFF232328)
val Line = Color(0xFF2C2C32)
val LineSoft = Color(0xFF1D1D21)
val TextPrimary = Color(0xFFF5F5F6)
val TextSecondary = Color(0xFFB9B9C1)
val TextMuted = Color(0xFF8D8D97)

val Accent = Color(0xFFFEC84A)
val AccentSoft = Color(0xFF2E2713)
val AccentOn = Color(0xFF1A1405)
val Success = Color(0xFF3DDC97)
val Danger = Color(0xFFFF6B6B)
val DangerBg = Color(0xFF2A1618)
val DangerLine = Color(0xFF4A2A2D)
val Cream = Color(0xFFFAF5E9)

/** Legacy alias so old call sites still compile. Prefer Accent. */
val Lime = Accent

val FolderColors = listOf(
    Color(0xFFFEC84A), Color(0xFF1FB6FF), Color(0xFF22D68F), Color(0xFFFF5C7C), Color(0xFFA78BFA)
)

fun folderColor(index: Int): Color = FolderColors[index.mod(FolderColors.size)]