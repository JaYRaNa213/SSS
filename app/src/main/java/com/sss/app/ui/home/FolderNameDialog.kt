package com.sss.app.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.sss.app.data.local.FolderEntity
import com.sss.app.domain.folder.FolderNameValidator
import com.sss.app.domain.folder.NameCheck

@Composable
fun FolderNameDialog(
    title: String,
    confirmLabel: String,
    existing: List<FolderEntity>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    ignoreId: Long? = null
) {
    var text by remember { mutableStateOf(initial) }
    val check = FolderNameValidator.check(text, existing, ignoreId)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val showError = text.isNotEmpty() && check is NameCheck.Error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= FolderNameValidator.MAX + 10) text = it },
                label = { Text("Folder name") },
                placeholder = { Text("e.g. Computer Networks") },
                singleLine = true,
                isError = showError,
                supportingText = {
                    Text(
                        if (showError) (check as NameCheck.Error).message
                        else "${FolderNameValidator.normalize(text).length}/${FolderNameValidator.MAX}"
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (check is NameCheck.Ok) onConfirm(check.name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(enabled = check is NameCheck.Ok, onClick = { if (check is NameCheck.Ok) onConfirm(check.name) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}