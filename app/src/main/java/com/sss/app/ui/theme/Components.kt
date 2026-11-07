package com.sss.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Kept name so old call sites restyle automatically. Now sentence case, no monospace. */
@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = TextMuted) {
    Text(text, modifier = modifier, color = color, fontWeight = FontWeight.Medium, fontSize = 13.sp)
}

/** Tiny caps caption ("SAVE TO"). */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = TextMuted) {
    Text(text.uppercase(), modifier = modifier, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.08.em)
}

fun Modifier.dashedBorder(color: Color, radius: Dp = 20.dp, strokeWidth: Dp = 1.5.dp): Modifier =
    this.drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        )
    }

/** Full width yellow pill. Same name as before, now with optional icon. */
@Composable
fun SssPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = if (danger) ButtonDefaults.buttonColors(
            containerColor = DangerBg, contentColor = Danger,
            disabledContainerColor = Surface3, disabledContentColor = TextMuted
        ) else ButtonDefaults.buttonColors(
            containerColor = Accent, contentColor = AccentOn,
            disabledContainerColor = Surface3, disabledContentColor = TextMuted
        ),
        border = if (danger) BorderStroke(1.dp, DangerLine) else null
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}

/** Compact yellow pill (wraps its content). */
@Composable
fun AccentPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        modifier.clip(CircleShape).background(if (enabled) Accent else Surface3)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        val c = if (enabled) AccentOn else TextMuted
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = c, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

/** Dark pill for secondary actions (Share, Copy, Pause...). */
@Composable
fun DarkPill(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
    container: Color = Surface3, content: Color = TextPrimary
) {
    Row(
        modifier.clip(CircleShape).background(container).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, null, tint = content, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
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
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 46.dp).then(if (dashed) Modifier.dashedBorder(Line, 16.dp) else Modifier),
        shape = RoundedCornerShape(16.dp),
        border = if (dashed) null else BorderStroke(1.dp, if (danger) DangerLine else Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) Danger else TextPrimary, disabledContentColor = TextMuted)
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

@Composable
fun RoundIconButton(
    icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    container: Color = Surface2, tint: Color = TextPrimary, size: Dp = 44.dp
) {
    Box(
        modifier.size(size).clip(CircleShape).background(container).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** Filter chip. Selected = white pill with dark text (like "All" in the mockup). */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(if (selected) TextPrimary else Surface2)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val c = if (selected) Bg else TextSecondary
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = c, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(AccentSoft).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(text, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun SssSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Accent, checkedThumbColor = AccentOn, checkedBorderColor = Accent,
            uncheckedTrackColor = Surface3, uncheckedThumbColor = TextMuted, uncheckedBorderColor = Line
        )
    )
}

@Composable
fun sssFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, unfocusedBorderColor = Line, errorBorderColor = Danger,
    focusedContainerColor = Surface2, unfocusedContainerColor = Surface2, errorContainerColor = Surface2,
    cursorColor = Accent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    focusedPlaceholderColor = TextMuted, unfocusedPlaceholderColor = TextMuted,
    focusedLabelColor = Accent, unfocusedLabelColor = TextMuted
)

/** Top bar used by inner screens (Folder, PDF, Help...). */
@Composable
fun SssTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        } else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (subtitle != null) MonoLabel(subtitle)
        }
        actions()
    }
}

data class BottomTab(val route: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

@Composable
fun SssBottomBar(tabs: List<BottomTab>, selected: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surface1)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LineSoft))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEach { t ->
                val sel = t.route == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onSelect(t.route) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.width(64.dp).height(32.dp).clip(CircleShape).background(if (sel) AccentSoft else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { Icon(if (sel) t.iconSelected else t.icon, t.label, tint = if (sel) Accent else TextMuted, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, fontSize = 12.sp, color = if (sel) TextPrimary else TextMuted, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}