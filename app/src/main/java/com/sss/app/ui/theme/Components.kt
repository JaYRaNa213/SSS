package com.sss.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Small uppercase monospace label (section titles, counters). */
@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = TextMuted) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.08.em
    )
}

/** Dashed rounded border (Compose has none built in). */
fun Modifier.dashedBorder(color: Color, radius: Dp = 14.dp, strokeWidth: Dp = 1.5.dp): Modifier =
    this.drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        )
    }

@Composable
fun SssPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = if (danger) ButtonDefaults.buttonColors(
            containerColor = DangerBg, contentColor = Danger,
            disabledContainerColor = Surface3, disabledContentColor = TextMuted
        ) else ButtonDefaults.buttonColors(
            containerColor = TextPrimary, contentColor = Bg,
            disabledContainerColor = Surface3, disabledContentColor = TextMuted
        ),
        border = if (danger) BorderStroke(1.dp, DangerLine) else null
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1) }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dashed: Boolean = false,
    danger: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp).then(if (dashed) Modifier.dashedBorder(Line, 12.dp) else Modifier),
        shape = RoundedCornerShape(12.dp),
        border = if (dashed) null else BorderStroke(1.dp, if (danger) DangerLine else Line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) Danger else TextPrimary,
            disabledContentColor = TextMuted
        )
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

/** Top bar used by every inner screen. */
@Composable
fun SssTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (subtitle != null) MonoLabel(subtitle)
        }
        actions()
    }
}