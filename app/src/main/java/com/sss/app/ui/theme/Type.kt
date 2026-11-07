package com.sss.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val F = FontFamily.Default

val Typography = Typography(
    headlineLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.02).em),
    headlineSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = F, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)