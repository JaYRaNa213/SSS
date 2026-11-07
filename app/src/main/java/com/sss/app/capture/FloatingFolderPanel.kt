package com.sss.app.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.sss.app.ui.theme.*
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
    val paused = settings.capturePaused
    val shots by remember(activeId) {
        if (activeId != null) screenshotRepository.getScreenshotsByFolder(activeId) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val last = shots.maxByOrNull { it.capturedAt }

    var moving by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    LaunchedEffect(creating) {
        onFocusable(creating)
        if (creating) { delay(150); focus.requestFocus() }
    }

    Surface(
        shape = RoundedCornerShape(26.dp), color = Surface1, shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp).heightIn(max = maxH)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (paused) TextMuted else Success))
                Spacer(Modifier.width(8.dp))
                Text(if (paused) "Paused" else "Capturing", color = if (paused) TextMuted else Success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Default.Close, "Close panel", onClose, size = 34.dp)
            }
            Spacer(Modifier.height(12.dp))

            if (last != null && !creating) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = last.filePath.toImageModel(), contentDescription = "Last screenshot", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(Surface2)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Last screenshot", Modifier.weight(1f), color = TextMuted, fontSize = 13.sp)
                    TextButton(onClick = { moving = !moving }) { Text(if (moving) "Cancel" else "Move", color = Accent) }
                    TextButton(onClick = { onRemoveLast(last) }) { Text("Remove", color = Danger) }
                }
            }

            CapsLabel(if (moving) "Move last to" else "Save to", modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(folders, key = { it.folder.id }) { f ->
                    val isActive = f.folder.id == activeId && !paused && !moving
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (isActive) AccentSoft else Color.Transparent)
                            .clickable {
                                scope.launch {
                                    if (moving && last != null) {
                                        screenshotRepository.moveScreenshots(listOf(last), f.folder.id); moving = false
                                    } else {
                                        sessionRepository.switchActiveFolder(f.folder.id)
                                        settingsRepository.set(SettingsKeys.CAPTURE_PAUSED, false)
                                        onClose()
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorDot(folderColor(f.folder.colorIndex), 9.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(f.folder.name, Modifier.weight(1f), maxLines = 1, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium, color = if (isActive) TextPrimary else TextSecondary)
                        if (isActive) Icon(Icons.Default.Check, "Active", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                }
                if (!moving) item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (paused) AccentSoft else Color.Transparent)
                            .clickable { scope.launch { settingsRepository.set(SettingsKeys.CAPTURE_PAUSED, true); onClose() } }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorDot(TextMuted, 9.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Unsorted", Modifier.weight(1f), fontWeight = if (paused) FontWeight.SemiBold else FontWeight.Medium, color = if (paused) TextPrimary else TextSecondary)
                        if (paused) Icon(Icons.Default.Check, "Active", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (creating) {
                val check = FolderNameValidator.check(newName, folders.map { it.folder })
                val shownError = error ?: (check as? NameCheck.Error)?.message?.takeIf { newName.isNotEmpty() }
                fun create() = scope.launch {
                    when (val r = folderRepository.createFolder(newName)) {
                        is FolderResult.Success -> { sessionRepository.switchActiveFolder(r.id); creating = false; onClose() }
                        is FolderResult.Invalid -> error = r.message
                    }
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it; error = null }, singleLine = true,
                    placeholder = { Text("Folder name") }, isError = shownError != null, shape = RoundedCornerShape(20.dp), colors = sssFieldColors(),
                    supportingText = { Text(shownError ?: "${FolderNameValidator.normalize(newName).length}/${FolderNameValidator.MAX}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (check is NameCheck.Ok) create() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { creating = false; newName = ""; error = null }) { Text("Cancel", color = TextMuted) }
                    TextButton(enabled = check is NameCheck.Ok, onClick = { create() }) { Text("Create", color = Accent) }
                }
            } else {
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkPill(
                        if (paused) "Resume" else "Pause",
                        { scope.launch { settingsRepository.set(SettingsKeys.CAPTURE_PAUSED, !paused) } },
                        Modifier.weight(1f), icon = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause
                    )
                    DarkPill("Folder", { creating = true }, Modifier.weight(1f), icon = Icons.Default.Add, container = Surface2)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onOpenApp) { Text("Open app", color = TextSecondary) }
                    TextButton(onClick = onStop) { Text("Stop", color = Danger) }
                }
            }
        }
    }
}