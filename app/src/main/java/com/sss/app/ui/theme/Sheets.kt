package com.sss.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sss.app.data.local.FolderEntity
import com.sss.app.data.settings.SortOrder
import com.sss.app.domain.folder.FolderNameValidator
import com.sss.app.domain.folder.FolderTree
import com.sss.app.domain.folder.NameCheck
import com.sss.app.ui.common.RotateChoice
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SssSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1, contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Line) }
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(), content = content)
    }
}

@Composable
fun SheetHeader(title: String, onClose: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (onClose != null) RoundIconButton(Icons.Default.Close, "Close", onClose, container = Surface2)
    }
}

@Composable
fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface2), content = content)
}

@Composable
fun SheetRow(
    title: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, leading: (@Composable () -> Unit)? = null,
    value: String? = null, selected: Boolean = false, chevron: Boolean = false, danger: Boolean = false
) {
    val tint = if (danger) Danger else TextPrimary
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        else if (icon != null) { Icon(icon, null, tint = if (danger) Danger else TextSecondary, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp)) }
        Text(title, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
        if (value != null) { Text(value, color = TextMuted, fontSize = 14.sp); Spacer(Modifier.width(6.dp)) }
        if (selected) Icon(Icons.Default.Check, "Selected", tint = Accent, modifier = Modifier.size(22.dp))
        if (chevron) Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ColorDot(color: Color, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

// ---------------------------------------------------------------- SORT POPUP
@Composable
fun SortSheet(current: SortOrder, onSelect: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    SssSheet(onDismiss) {
        SheetHeader("Sort by", onDismiss)
        SheetGroup {
            SheetRow("Oldest first", { onSelect(SortOrder.OLDEST); onDismiss() }, icon = Icons.Default.ArrowUpward, selected = current == SortOrder.OLDEST)
            HorizontalDivider(color = Line)
            SheetRow("Newest first", { onSelect(SortOrder.NEWEST); onDismiss() }, icon = Icons.Default.ArrowDownward, selected = current == SortOrder.NEWEST)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- FOLDER PICKER (switch folder / move to)
data class PickItem(val id: Long, val name: String, val colorIndex: Int, val count: Int?, val depth: Int = 0)

@Composable
fun FolderPickerSheet(
    title: String, items: List<PickItem>, activeId: Long?,
    onPick: (Long) -> Unit, onNew: (() -> Unit)?, onDismiss: () -> Unit,
    onRoot: (() -> Unit)? = null          // adds a "Top level" row (used when moving a folder)
) {
    SssSheet(onDismiss) {
        SheetHeader(title, onDismiss)
        if (items.isEmpty() && onRoot == null) Text("No folders yet. Create one first.", color = TextMuted, modifier = Modifier.padding(vertical = 12.dp))
        Box(Modifier.heightIn(max = 420.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SheetGroup {
                    if (onRoot != null) {
                        SheetRow("Top level", onRoot, icon = Icons.Default.Home)
                        if (items.isNotEmpty()) HorizontalDivider(color = Line)
                    }
                    items.forEachIndexed { i, it ->
                        if (i > 0) HorizontalDivider(color = Line)
                        SheetRow(
                            it.name, { onPick(it.id) }, selected = it.id == activeId,
                            leading = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width((it.depth * 16).dp))
                                    ColorDot(folderColor(it.colorIndex), 12.dp)
                                }
                            },
                            value = it.count?.toString()
                        )
                    }
                }
            }
        }
        if (onNew != null) {
            Spacer(Modifier.height(12.dp))
            SheetGroup { SheetRow("New folder", onNew, icon = Icons.Outlined.CreateNewFolder) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- NEW FOLDER (with Location)
@Composable
fun NewFolderSheet(
    existing: List<FolderEntity>,
    initialParentId: Long? = null,
    showStartToggle: Boolean = true,
    onCreate: (name: String, colorIndex: Int, startCapture: Boolean, parentId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(existing.firstOrNull { it.id == initialParentId }?.colorIndex ?: 0) }
    var start by remember { mutableStateOf(showStartToggle) }
    var parentId by remember { mutableStateOf(initialParentId) }
    var menu by remember { mutableStateOf(false) }
    val check = FolderNameValidator.check(name, existing, parentId = parentId)
    val showError = name.isNotEmpty() && check is NameCheck.Error
    val rows = remember(existing) { FolderTree.flattenEntities(existing) }
    val parentLabel = parentId?.let { FolderTree.pathLabel(existing, it) } ?: "Top level"
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(350); runCatching { focus.requestFocus() } }

    SssSheet(onDismiss) {
        SheetHeader(if (initialParentId != null) "New subfolder" else "New folder", onDismiss)
        MonoLabel("Name")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name, onValueChange = { if (it.length <= FolderNameValidator.MAX + 10) name = it },
            singleLine = true, isError = showError, shape = RoundedCornerShape(22.dp),
            placeholder = { Text("e.g. Vocabulary") },
            trailingIcon = { MonoLabel("${FolderNameValidator.normalize(name).length}/${FolderNameValidator.MAX}", modifier = Modifier.padding(end = 14.dp)) },
            supportingText = if (showError) {{ Text((check as NameCheck.Error).message) }} else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (check is NameCheck.Ok) onCreate(check.name, color, start, parentId) }),
            colors = sssFieldColors(),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )

        Spacer(Modifier.height(18.dp))
        MonoLabel("Location")
        Spacer(Modifier.height(8.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface2)
                    .clickable { menu = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(parentLabel, modifier = Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowDropDown, null, tint = TextMuted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Top level") }, onClick = { parentId = null; menu = false })
                rows.filter { it.depth <= FolderTree.MAX_DEPTH - 2 }.forEach { r ->
                    DropdownMenuItem(
                        text = { Text("\u2003".repeat(r.depth) + r.item.name) },
                        onClick = { parentId = r.item.id; menu = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        MonoLabel("Colour")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FolderColors.forEachIndexed { i, c ->
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .border(2.dp, if (i == color) TextPrimary else Color.Transparent, CircleShape)
                        .clickable { color = i }.padding(5.dp).background(c, CircleShape)
                )
            }
        }
        if (showStartToggle) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface2).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Start capturing here", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("New screenshots will go to this folder", color = TextMuted, fontSize = 13.sp)
                }
                SssSwitch(start) { start = it }
            }
        }
        Spacer(Modifier.height(18.dp))
        SssPrimaryButton(
            if (initialParentId != null || parentId != null) "Create subfolder" else "Create folder",
            enabled = check is NameCheck.Ok,
            onClick = { if (check is NameCheck.Ok) onCreate(check.name, color, start, parentId) }
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- FOLDER MENU
@Composable
fun FolderMenuSheet(
    title: String, colorIndex: Int, subtitle: String, sortLabel: String, isOther: Boolean,
    onRename: () -> Unit, onNewSubfolder: () -> Unit, onMoveFolder: () -> Unit,
    onSort: () -> Unit, onSelectAll: () -> Unit, onMoveAll: () -> Unit,
    onPdf: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit
) {
    SssSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (isOther) Surface3 else folderColor(colorIndex)))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                MonoLabel(subtitle)
            }
        }
        SheetGroup {
            if (!isOther) {
                SheetRow("Rename", { onDismiss(); onRename() }, icon = Icons.Default.Edit, chevron = true)
                HorizontalDivider(color = Line)
                SheetRow("New subfolder", { onDismiss(); onNewSubfolder() }, icon = Icons.Default.CreateNewFolder, chevron = true)
                HorizontalDivider(color = Line)
                SheetRow("Move folder to...", { onDismiss(); onMoveFolder() }, icon = Icons.Default.Folder, chevron = true)
                HorizontalDivider(color = Line)
            }
            SheetRow("Sort by", { onDismiss(); onSort() }, icon = Icons.Default.SwapVert, value = sortLabel, chevron = true)
            HorizontalDivider(color = Line)
            SheetRow("Select all", { onDismiss(); onSelectAll() }, icon = Icons.Default.DoneAll, chevron = true)
            HorizontalDivider(color = Line)
            SheetRow("Move screenshots to...", { onDismiss(); onMoveAll() }, icon = Icons.Default.DriveFileMove, chevron = true)
            HorizontalDivider(color = Line)
            SheetRow("Create PDF", { onDismiss(); onPdf() }, icon = Icons.Default.PictureAsPdf, chevron = true)
        }
        if (!isOther) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DangerBg)) {
                SheetRow("Delete folder", { onDismiss(); onDelete() }, icon = Icons.Default.Delete, danger = true)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- ROTATE POPUP (many screenshots)
@Composable
fun RotateSheet(count: Int, onPick: (RotateChoice) -> Unit, onDismiss: () -> Unit) {
    SssSheet(onDismiss) {
        SheetHeader("Rotate $count screenshot${if (count == 1) "" else "s"}", onDismiss)
        SheetGroup {
            SheetRow("Rotate right 90\u00B7", { onPick(RotateChoice.By(90)); onDismiss() }, icon = Icons.Default.RotateRight)
            HorizontalDivider(color = Line)
            SheetRow("Rotate left 90\u00B7", { onPick(RotateChoice.By(-90)); onDismiss() }, icon = Icons.Default.RotateLeft)
            HorizontalDivider(color = Line)
            SheetRow("Rotate 180\u00B7", { onPick(RotateChoice.By(180)); onDismiss() }, icon = Icons.Default.Refresh)
            HorizontalDivider(color = Line)
            SheetRow("Original orientation (0\u00B7)", { onPick(RotateChoice.To(0)); onDismiss() }, icon = Icons.Default.Restore)
            HorizontalDivider(color = Line)
            SheetRow("Automatic", { onPick(RotateChoice.Auto); onDismiss() }, icon = Icons.Default.Restore, value = "default")
        }
        Spacer(Modifier.height(8.dp))
    }
}