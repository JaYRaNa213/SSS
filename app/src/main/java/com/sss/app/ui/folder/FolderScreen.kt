package com.sss.app.ui.folder

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.settings.SortOrder
import com.sss.app.ui.common.CroppedImage
import com.sss.app.ui.home.FolderNameDialog
import com.sss.app.ui.ocr.OcrStatus
import com.sss.app.ui.ocr.OcrViewModel
import com.sss.app.ui.theme.Bg
import com.sss.app.ui.theme.Danger
import com.sss.app.ui.theme.Line
import com.sss.app.ui.theme.MonoLabel
import com.sss.app.ui.theme.Surface1
import com.sss.app.ui.theme.Surface2
import com.sss.app.ui.theme.SssTopBar
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.TextPrimary
import com.sss.app.ui.theme.TextSecondary
import com.sss.app.util.MediaDelete
import com.sss.app.util.MediaIds
import com.sss.app.util.toImageModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderScreen(
    folderId: Long,
    onBack: () -> Unit,
    onOpenViewer: (Int) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onCreatePdf: () -> Unit,
    allVm: AllScreenshotsViewModel,
    viewModel: FolderViewModel = hiltViewModel(),
    ocrViewModel: OcrViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isOther = folderId == -1L

    val settings by viewModel.settings.collectAsState()
    val folder by remember(folderId) { viewModel.folder(folderId) }.collectAsState(initial = null)
    val roomShots by remember(folderId) { viewModel.screenshots(folderId) }.collectAsState()
    val otherShots by allVm.allScreenshots.collectAsState()
    val metaMap by viewModel.metaMap.collectAsState()
    val allFolders by viewModel.folders.collectAsState()
    val docs by remember(folderId) { viewModel.getDocumentsByFolder(folderId) }.collectAsState(initial = emptyList())
    val ocrState by ocrViewModel.ocrUiState.collectAsState()
    val analysis by viewModel.analysisProgress.collectAsState()

    val raw = if (isOther) otherShots else roomShots
    val order = if (isOther) settings.sortOther else settings.sortFolders
    val sorted = remember(raw, order) { raw.sortedFor(order) }
    val entries = remember(sorted, settings.dateHeaders) { buildEntries(sorted, settings.dateHeaders) }
    val title = if (isOther) "Other" else folder?.name ?: ""

    val selected = remember { mutableStateListOf<Long>() }
    val selecting = selected.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteFolder by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var pendingOtherDelete by remember { mutableStateOf<List<ScreenshotEntity>?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val undo by viewModel.undo.collectAsState()
    val gridState = rememberLazyGridState()

    BackHandler(enabled = selecting) { selected.clear() }

    LaunchedEffect(isOther) { if (isOther) allVm.loadAllScreenshots() }

    // background auto-crop for screenshots that have no analysis yet
    LaunchedEffect(sorted.size, settings.autoCrop) {
        if (settings.autoCrop && sorted.isNotEmpty()) viewModel.analyze(sorted.map { it.filePath })
    }

    LaunchedEffect(ocrState.createdDocumentId) {
        val docId = ocrState.createdDocumentId
        if (docId != null) {
            selected.clear()
            ocrViewModel.resetOcrState()
            onOpenDocument(docId)
        }
    }

    // "Deleted - UNDO" snackbar (6 seconds)
    LaunchedEffect(undo?.token) {
        val s = undo ?: return@LaunchedEffect
        val result = withTimeoutOrNull(6_000) {
            snackbar.showSnackbar(s.message, actionLabel = "UNDO", duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        else { snackbar.currentSnackbarData?.dismiss(); viewModel.consumeUndo(s.token) }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { selected.clear(); allVm.loadAllScreenshots() }
    }

    fun selectedShots(): List<ScreenshotEntity> = selected.mapNotNull { id -> sorted.firstOrNull { it.id == id } }
    fun toggleDay(ids: List<Long>) {
        if (ids.all { it in selected }) selected.removeAll(ids.toSet()) else ids.forEach { if (it !in selected) selected.add(it) }
    }

    fun share(targets: List<ScreenshotEntity>) {
        if (targets.isEmpty()) return
        val uris = ArrayList(targets.map { Uri.parse(it.filePath) })
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { type = "image/*"; putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share screenshots"))
    }

    fun openPdf(list: List<ScreenshotEntity>) {
        if (list.isEmpty()) return
        viewModel.openPdf(title.ifBlank { "Screenshots" }, list)
        onCreatePdf()
    }

    fun deleteSelected() {
        val targets = selectedShots()
        if (targets.isEmpty()) return
        if (isOther) pendingOtherDelete = targets
        else { viewModel.delete(targets); selected.clear() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {

            if (selecting) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, contentDescription = "Cancel selection") }
                    Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    val allSelected = selected.size == sorted.size
                    TextButton(onClick = {
                        if (allSelected) selected.clear() else { selected.clear(); selected.addAll(sorted.map { it.id }) }
                    }) { Text(if (allSelected) "Deselect all" else "Select all") }
                }
            } else {
                SssTopBar(
                    title = title,
                    subtitle = "${sorted.size} \u00B7 ${if (order == SortOrder.OLDEST) "oldest first" else "newest first"}",
                    onBack = onBack
                ) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (!isOther) DropdownMenuItem(text = { Text("Rename folder") }, onClick = { menuOpen = false; showRename = true })
                            DropdownMenuItem(text = { Text("Select all") }, onClick = {
                                menuOpen = false; selected.clear(); selected.addAll(sorted.map { it.id })
                            })
                            DropdownMenuItem(
                                text = { Text(if (order == SortOrder.OLDEST) "Sort: Oldest first  \u2713" else "Sort: Oldest first") },
                                onClick = { menuOpen = false; viewModel.setSort(isOther, SortOrder.OLDEST) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (order == SortOrder.NEWEST) "Sort: Newest first  \u2713" else "Sort: Newest first") },
                                onClick = { menuOpen = false; viewModel.setSort(isOther, SortOrder.NEWEST) }
                            )
                            DropdownMenuItem(text = { Text("Create PDF") }, onClick = { menuOpen = false; openPdf(sorted) })
                            if (!isOther) DropdownMenuItem(
                                text = { Text("Delete folder", color = Danger) },
                                onClick = { menuOpen = false; showDeleteFolder = true }
                            )
                        }
                    }
                }
            }

            analysis?.let { (done, total) -> MonoLabel("Analyzing screenshots $done/$total", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (sorted.isEmpty() && docs.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("No screenshots yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (isOther) "Screenshots taken while no folder is active appear here."
                            else "Start capture on the home screen and take screenshots.",
                            color = TextMuted, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(settings.gridColumns.coerceIn(2, 4)),
                        contentPadding = PaddingValues(start = 20.dp, end = 32.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (docs.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "notes-label") {
                                MonoLabel("Notes", modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                            }
                            items(docs, key = { "doc-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { doc ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface1)
                                        .border(1.dp, Line, RoundedCornerShape(12.dp)).clickable { onOpenDocument(doc.id) }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                        Text("${doc.sourceImageCount} screenshots processed", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                    }
                                    Text("Open", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        items(
                            entries,
                            key = { e -> when (e) { is GridEntry.Header -> "h-${e.key}"; is GridEntry.Shot -> e.shot.id } },
                            span = { e -> if (e is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
                        ) { e ->
                            when (e) {
                                is GridEntry.Header -> Row(
                                    Modifier.fillMaxWidth()
                                        .combinedClickable(onClick = { if (selecting) toggleDay(e.ids) }, onLongClick = { toggleDay(e.ids) })
                                        .padding(top = 14.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MonoLabel(e.label, color = TextPrimary)
                                    MonoLabel("${e.count}")
                                }
                                is GridEntry.Shot -> {
                                    val shot = e.shot
                                    val isSel = shot.id in selected
                                    Box(
                                        Modifier.aspectRatio(16f / 10f).clip(RoundedCornerShape(8.dp)).background(Surface2)
                                            .combinedClickable(
                                                onClick = {
                                                    if (selecting) { if (isSel) selected.remove(shot.id) else selected.add(shot.id) }
                                                    else onOpenViewer(e.index)
                                                },
                                                onLongClick = { if (!isSel) selected.add(shot.id) }
                                            )
                                    ) {
                                        CroppedImage(
                                            model = shot.filePath.toImageModel(),
                                            meta = metaMap[MediaIds.fromPath(shot.filePath)],
                                            cropEnabled = settings.autoCrop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isSel) {
                                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                                            Box(
                                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(TextPrimary),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(Icons.Default.Check, contentDescription = "Selected", tint = Bg, modifier = Modifier.size(14.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (sorted.size > 60) {
                        FastScrollRail(gridState, entries, Modifier.align(Alignment.CenterEnd))
                    }
                }
            }

            if (selecting) {
                Row(
                    Modifier.fillMaxWidth().background(Surface1).border(1.dp, Line).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BarAction(Icons.Default.PictureAsPdf, "PDF") { openPdf(selectedShots()) }
                    BarAction(Icons.Default.TextFields, "Text") {
                        val list = selectedShots()
                        ocrViewModel.extractTextFromImages(folderId, title.ifBlank { "Screenshots" }, list.map { Uri.parse(it.filePath) })
                    }
                    BarAction(Icons.Default.Share, "Share") { share(selectedShots()) }
                    BarAction(Icons.Default.Folder, "Move") { showMove = true }
                    BarAction(Icons.Default.Delete, "Delete", danger = true) { deleteSelected() }
                }
            }
        }
    }

    // ---- dialogs ------------------------------------------------------------------
    if (showRename) {
        FolderNameDialog(
            title = "Rename folder", confirmLabel = "Save", initial = folder?.name.orEmpty(),
            existing = allFolders, ignoreId = folderId,
            onConfirm = { name -> showRename = false; viewModel.rename(folderId, name) { } },
            onDismiss = { showRename = false }
        )
    }
    if (showDeleteFolder) {
        AlertDialog(
            onDismissRequest = { showDeleteFolder = false },
            title = { Text("Delete folder?") },
            text = { Text("The folder is removed from 4S. Its screenshots stay in your phone gallery.") },
            confirmButton = { TextButton(onClick = { showDeleteFolder = false; viewModel.deleteFolder(folderId) { onBack() } }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton(onClick = { showDeleteFolder = false }) { Text("Cancel") } }
        )
    }
    if (showMove) {
        val targets = allFolders.filter { it.id != folderId }
        AlertDialog(
            onDismissRequest = { showMove = false },
            title = { Text("Move to folder") },
            text = {
                if (targets.isEmpty()) Text("Create another folder first.")
                else Column {
                    targets.forEach { f ->
                        Text(
                            f.name, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showMove = false
                                val list = selectedShots()
                                selected.clear()
                                viewModel.moveTo(list, f.id, isOther) { if (isOther) allVm.loadAllScreenshots() }
                            }.padding(vertical = 14.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMove = false }) { Text("Cancel") } }
        )
    }
    pendingOtherDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingOtherDelete = null },
            title = { Text("Delete ${list.size} from your phone?") },
            text = { Text("These are the original files. Android will ask you to confirm. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingOtherDelete = null
                    val sender = MediaDelete.delete(context, list.map { Uri.parse(it.filePath) })
                    if (sender != null) deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    else { selected.clear(); allVm.loadAllScreenshots() }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingOtherDelete = null }) { Text("Cancel") } }
        )
    }
    if (ocrState.status == OcrStatus.PROCESSING) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Extracting text...") },
            text = {
                Column {
                    Text("Processing ${ocrState.completedImages} of ${ocrState.totalImages}")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { ocrState.progress }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) Danger else TextPrimary
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp).defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = c, modifier = Modifier.size(22.dp))
        Text(label, color = c, style = MaterialTheme.typography.labelMedium)
    }
}

/** Draggable scroll thumb with a date label. Shown only while scrolling/dragging, hides after 1.5 s. */
@Composable
private fun FastScrollRail(state: LazyGridState, entries: List<GridEntry>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var label by remember { mutableStateOf("") }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(dragging, scrolling) {
        if (dragging || scrolling) visible = true else { delay(1500); visible = false }
    }
    if (!visible && !dragging) return

    val total = entries.size.coerceAtLeast(2)
    BoxWithConstraints(
        modifier.fillMaxHeight().width(30.dp).padding(vertical = 8.dp).pointerInput(total) {
            detectVerticalDragGestures(
                onDragStart = { dragging = true },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false }
            ) { change, _ ->
                val f = (change.position.y / size.height).coerceIn(0f, 1f)
                dragFraction = f
                val idx = (f * (total - 1)).roundToInt()
                label = (entries.take(idx + 1).lastOrNull { it is GridEntry.Header } as? GridEntry.Header)?.label.orEmpty()
                scope.launch { state.scrollToItem(idx) }
            }
        }
    ) {
        val thumbH = 64.dp
        val fraction = if (dragging) dragFraction else state.firstVisibleItemIndex.toFloat() / (total - 1)
        val y = (maxHeight - thumbH) * fraction.coerceIn(0f, 1f)
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp).clip(RoundedCornerShape(4.dp)).background(Surface2))
        Box(Modifier.align(Alignment.TopEnd).offset(y = y).width(4.dp).height(thumbH).clip(RoundedCornerShape(4.dp)).background(TextSecondary))
        if (dragging && label.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = y + 16.dp)
                    .clip(RoundedCornerShape(8.dp)).background(TextPrimary).padding(horizontal = 10.dp, vertical = 6.dp)
            ) { MonoLabel(label, color = Bg) }
        }
    }
}