package com.sss.app.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sss.app.ui.theme.MonoLabel

@Composable
fun PermissionExplainDialog(
    type: PermissionType,
    permanentlyDenied: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow ${type.title}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MonoLabel("What it does")
                Text(type.whatItDoes, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                MonoLabel("Why 4S needs it")
                Text(type.whyNeeded, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                MonoLabel("Privacy")
                Text(PermissionType.PRIVACY, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                MonoLabel(if (permanentlyDenied) "How to enable" else "Next step")
                Text(
                    if (permanentlyDenied) "Android will not ask again. Open app settings, tap Permissions, and allow it there. ${type.settingsHint}"
                    else type.settingsHint,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (permanentlyDenied) "Open app settings" else "Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}