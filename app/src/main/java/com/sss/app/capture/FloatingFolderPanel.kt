package com.sss.app.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import coil3.compose.AsyncImage
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.repository.FolderRepository
import com.sss.app.data.repository.FolderResult
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.SessionRepository
import com.sss.app.data.settings.AppSettings
import com.sss.app.data.settings.SettingsKeys
import com.sss.app.data.settings.SettingsRepository
import com.sss.app.domain.folder.FolderNameValidator
import com.sss.app.domain.folder.NameCheck
import com.sss.app.ui.theme.GhostButton
import com.sss.app.ui.theme.Lime
import com.sss.app.ui.theme.Line
import com.sss.app.ui.theme.MonoLabel
import com.sss.app.ui.theme.Surface1
import com.sss.app.ui.theme.Surface2
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.TextPrimary
import com.sss.app.util.toImageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun FloatingFolderPanel(
    folderRepository: FolderRepository,
    sessionRepository: SessionRepository,
    screenshotRepository: ScreenshotRepository,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onStop: () -> Unit,
    onFocusable: (Boolean) -> Unit,
    onRemoveLast: (ScreenshotEntity) -> Unit
) {
    val folders by remember(folderRepository) { folderRepository.foldersWithCount }.collectAsState(initial = emptyList())
    val session by remember(sessionRepository) { sessionRepository.observeActiveSession() }.collectAsState(initial = null)
    val settings by remember(settingsRepository) { settingsRepository.settings }.collectAsState(initial = AppSettings())
    val activeId = session?.folderId
    val active = folders.firstOrNull { it.folder.id == activeId }
    val shots by remember(activeId) {
        if (activeId != null) screenshotRepository.getScreenshotsByFolder(activeId) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val last = shots.maxByOrNull { it.capturedAt }

    var moving by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    LaunchedEffect(creating) {
        onFocusable(creating)            // keyboard only works while the window is focusable
        if (creating) { delay(150); focus.requestFocus() }
    }

    Surface(
        shape = RoundedCornerShape(20.dp), color = Surface1, shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp).heightIn(max = maxH)) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (settings.capturePaused) TextMuted else Lime))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(active?.folder?.name ?: "4S", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    MonoLabel(
                        (if (settings.capturePaused) "Paused" else "Capturing") + " \u00B7 ${active?.screenshotCount ?: 0}",
                        color = if (settings.capturePaused) TextMuted else Lime
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close panel", tint = TextPrimary)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)

            // last screenshot
            if (last != null && !creating) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = last.filePath.toImageModel(), contentDescription = "Last screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(6.dp)).background(Surface2)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Last screenshot", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    TextButton(onClick = { moving = !moving }) { Text(if (moving) "Cancel" else "Move") }
                    TextButton(onClick = { onRemoveLast(last) }) { Text("Remove") }
                }
            }

            MonoLabel(if (moving) "Move last to..." else "Folders", modifier = Modifier.padding(bottom = 4.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(folders, key = { it.folder.id }) { f ->
                    val isActive = f.folder.id == activeId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (isActive && !moving) Surface2 else Surface1)
                            .clickable {
                                scope.launch {
                                    if (moving && last != null) {
                                        screenshotRepository.moveScreenshots(listOf(last), f.folder.id)
                                        moving = false
                                    } else {
                                        sessionRepository.switchActiveFolder(f.folder.id)
                                        onClose()
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(f.folder.name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        MonoLabel("${f.screenshotCount}")
                        if (isActive) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(Lime)) }
                    }
                }
            }

            // new folder
            Spacer(Modifier.height(6.dp))
            if (creating) {
                val check = FolderNameValidator.check(newName, folders.map { it.folder })
                val shownError = error ?: (check as? NameCheck.Error)?.message?.takeIf { newName.isNotEmpty() }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it; error = null }, singleLine = true,
                    label = { Text("Folder name") }, placeholder = { Text("e.g. Computer Networks") },
                    isError = shownError != null,
                    supportingText = { Text(shownError ?: "${FolderNameValidator.normalize(newName).length}/${FolderNameValidator.MAX}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (check is NameCheck.Ok) scope.launch {
                            when (val r = folderRepository.createFolder(newName)) {
                                is FolderResult.Success -> { sessionRepository.switchActiveFolder(r.id); creating = false; onClose() }
                                is FolderResult.Invalid -> error = r.message
                            }
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { creating = false; newName = ""; error = null }) { Text("Cancel") }
                    TextButton(
                        enabled = check is NameCheck.Ok,
                        onClick = {
                            scope.launch {
                                when (val r = folderRepository.createFolder(newName)) {
                                    is FolderResult.Success -> { sessionRepository.switchActiveFolder(r.id); creating = false; onClose() }
                                    is FolderResult.Invalid -> error = r.message
                                }
                            }
                        }
                    ) { Text("Create") }
                }
            } else {
                GhostButton("+ New folder", onClick = { creating = true }, dashed = true, modifier = Modifier.fillMaxWidth())
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GhostButton(
                    if (settings.capturePaused) "Resume" else "Pause", modifier = Modifier.weight(1f),
                    onClick = { scope.launch { settingsRepository.set(SettingsKeys.CAPTURE_PAUSED, !settings.capturePaused) } }
                )
                GhostButton("Stop", onClick = onStop, danger = true, modifier = Modifier.weight(1f))
                GhostButton("Open", onClick = onOpenApp, modifier = Modifier.weight(1f))
            }
        }
    }
}