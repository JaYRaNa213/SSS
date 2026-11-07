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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.settings.SortOrder
import com.sss.app.ui.common.CroppedImage
import com.sss.app.ui.home.FolderNameDialog
import com.sss.app.ui.ocr.OcrStatus
import com.sss.app.ui.ocr.OcrViewModel
import com.sss.app.ui.theme.*
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
    val scope = rememberCoroutineScope()
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
    val title = if (isOther) "Unsorted" else folder?.name ?: ""
    val colorIndex = folder?.colorIndex ?: 0

    val selected = remember { mutableStateListOf<Long>() }
    var selectMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteFolder by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var pendingOtherDelete by remember { mutableStateOf<List<ScreenshotEntity>?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val undo by viewModel.undo.collectAsState()
    val gridState = rememberLazyGridState()

    fun exitSelect() { selected.clear(); selectMode = false }
    BackHandler(enabled = selectMode) { exitSelect() }

    LaunchedEffect(isOther) { if (isOther) allVm.loadAllScreenshots() }
    LaunchedEffect(sorted.size, settings.autoCrop) {
        if (settings.autoCrop && sorted.isNotEmpty()) viewModel.analyze(sorted.map { it.filePath })
    }
    LaunchedEffect(ocrState.createdDocumentId) {
        val docId = ocrState.createdDocumentId
        if (docId != null) { exitSelect(); ocrViewModel.resetOcrState(); onOpenDocument(docId) }
    }
    LaunchedEffect(undo?.token) {
        val s = undo ?: return@LaunchedEffect
        val result = withTimeoutOrNull(6_000) { snackbar.showSnackbar(s.message, actionLabel = "UNDO", duration = SnackbarDuration.Indefinite) }
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        else { snackbar.currentSnackbarData?.dismiss(); viewModel.consumeUndo(s.token) }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { exitSelect(); allVm.loadAllScreenshots() }
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
    fun extract(list: List<ScreenshotEntity>) {
        if (list.isEmpty()) return
        ocrViewModel.extractTextFromImages(folderId, title.ifBlank { "Screenshots" }, list.map { Uri.parse(it.filePath) })
    }
    fun deleteSelected() {
        val targets = selectedShots()
        if (targets.isEmpty()) return
        if (isOther) pendingOtherDelete = targets else { viewModel.delete(targets); exitSelect() }
    }

    Scaffold(containerColor = Bg, snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {

            // ---------------- header
            if (selectMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { exitSelect() }) { Icon(Icons.Default.Close, "Cancel selection") }
                    Column(Modifier.weight(1f)) {
                        Text(if (selected.isEmpty()) "Select items" else "${selected.size} selected", style = MaterialTheme.typography.titleLarge)
                        MonoLabel("of ${sorted.size} in $title")
                    }
                    val allSel = selected.size == sorted.size && sorted.isNotEmpty()
                    Row(
                        Modifier.padding(end = 12.dp).clip(CircleShape).background(Surface2)
                            .clickable { if (allSel) selected.clear() else { selected.clear(); selected.addAll(sorted.map { it.id }) } }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (allSel) "None" else "All", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        MonoLabel("${sorted.size} screenshots")
                    }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterPill("All", true, {}) }
                    item {
                        FilterPill(if (order == SortOrder.OLDEST) "Oldest" else "Newest", false, { showSort = true }, icon = Icons.Default.SwapVert)
                    }
                    if (docs.isNotEmpty()) item {
                        FilterPill("Notes \u00B7 ${docs.size}", false, { scope.launch { gridState.animateScrollToItem(0) } })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            analysis?.let { (done, total) -> MonoLabel("Analyzing screenshots $done/$total", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

            // ---------------- grid
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
                        contentPadding = PaddingValues(start = 20.dp, end = 28.dp, bottom = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (docs.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "notes-label") {
                                Text("Notes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                            }
                            items(docs, key = { "doc-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { doc ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)
                                        .clickable { onOpenDocument(doc.id) }.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                        Text("${doc.sourceImageCount} screenshots processed", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                    }
                                    Text("Open", color = Accent, fontWeight = FontWeight.SemiBold)
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
                                        .combinedClickable(onClick = { if (selectMode) toggleDay(e.ids) }, onLongClick = { selectMode = true; toggleDay(e.ids) })
                                        .padding(top = 16.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(e.label, style = MaterialTheme.typography.titleSmall)
                                    MonoLabel("${e.count}")
                                }
                                is GridEntry.Shot -> {
                                    val shot = e.shot
                                    val isSel = shot.id in selected
                                    Box(
                                        Modifier.aspectRatio(16f / 10f)          // change to 3f / 4f for tall cells like the mockup
                                            .clip(RoundedCornerShape(12.dp)).background(Surface2)
                                            .then(if (isSel) Modifier.border(2.5.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
                                            .combinedClickable(
                                                onClick = {
                                                    if (selectMode) { if (isSel) selected.remove(shot.id) else selected.add(shot.id) }
                                                    else onOpenViewer(e.index)
                                                },
                                                onLongClick = { selectMode = true; if (!isSel) selected.add(shot.id) }
                                            )
                                    ) {
                                        CroppedImage(
                                            model = shot.filePath.toImageModel(), meta = metaMap[MediaIds.fromPath(shot.filePath)],
                                            cropEnabled = settings.autoCrop, modifier = Modifier.fillMaxSize()
                                        )
                                        if (selectMode) {
                                            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                                                .background(if (isSel) Accent else Color.Black.copy(alpha = 0.35f))
                                                .then(if (isSel) Modifier else Modifier.border(2.dp, TextPrimary, CircleShape)),
                                                contentAlignment = Alignment.Center
                                            ) { if (isSel) Icon(Icons.Default.Check, "Selected", tint = AccentOn, modifier = Modifier.size(16.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (sorted.size > 60) FastScrollRail(gridState, entries, Modifier.align(Alignment.CenterEnd))
                }

                // ---------------- floating bars
                if (!selectMode && sorted.isNotEmpty()) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).clip(CircleShape).background(Surface1)
                            .border(1.dp, Line, CircleShape).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccentPill("Extract all", { extract(sorted) }, icon = Icons.Outlined.DocumentScanner)
                        Row(
                            Modifier.clip(CircleShape).clickable { selectMode = true }.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Select", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (selectMode) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp)).background(Surface1).border(1.dp, Line, RoundedCornerShape(28.dp)).padding(12.dp)
                    ) {
                        SssPrimaryButton("Extract text from ${selected.size}", enabled = selected.isNotEmpty(), icon = Icons.Outlined.DocumentScanner, onClick = { extract(selectedShots()) })
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            BarAction(Icons.Default.DriveFileMove, "Move") { if (selected.isNotEmpty()) showMove = true }
                            BarAction(Icons.Default.PictureAsPdf, "PDF") { openPdf(selectedShots()) }
                            BarAction(Icons.Default.Share, "Share") { share(selectedShots()) }
                            BarAction(Icons.Default.Delete, "Delete", danger = true) { deleteSelected() }
                        }
                    }
                }
            }
        }
    }

    // ---------------- sheets + dialogs
    if (showSort) SortSheet(order, { viewModel.setSort(isOther, it) }, { showSort = false })
    if (showMenu) FolderMenuSheet(
        title = title, colorIndex = colorIndex, subtitle = "${sorted.size} screenshots", sortLabel = if (order == SortOrder.OLDEST) "Oldest first" else "Newest first",
        isOther = isOther,
        onRename = { showRename = true }, onSort = { showSort = true },
        onSelectAll = { selectMode = true; selected.clear(); selected.addAll(sorted.map { it.id }) },
        onMoveAll = { selectMode = true; selected.clear(); selected.addAll(sorted.map { it.id }); showMove = true },
        onPdf = { openPdf(sorted) }, onDelete = { showDeleteFolder = true }, onDismiss = { showMenu = false }
    )
    if (showMove) FolderPickerSheet(
        title = "Move to folder",
        items = allFolders.filter { it.id != folderId }.map { PickItem(it.id, it.name, it.colorIndex, null) },
        activeId = null, onNew = null, onDismiss = { showMove = false },
        onPick = { target ->
            showMove = false
            val list = selectedShots()
            exitSelect()
            viewModel.moveTo(list, target, isOther) { if (isOther) allVm.loadAllScreenshots() }
        }
    )
    if (showRename) FolderNameDialog(
        title = "Rename folder", confirmLabel = "Save", initial = folder?.name.orEmpty(), existing = allFolders, ignoreId = folderId,
        onConfirm = { name -> showRename = false; viewModel.rename(folderId, name) { } }, onDismiss = { showRename = false }
    )
    if (showDeleteFolder) AlertDialog(
        onDismissRequest = { showDeleteFolder = false },
        title = { Text("Delete folder?") },
        text = { Text("The folder is removed from 4S. Its screenshots stay in your phone gallery.") },
        confirmButton = { TextButton(onClick = { showDeleteFolder = false; viewModel.deleteFolder(folderId) { onBack() } }) { Text("Delete", color = Danger) } },
        dismissButton = { TextButton(onClick = { showDeleteFolder = false }) { Text("Cancel") } }
    )
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
                    else { exitSelect(); allVm.loadAllScreenshots() }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingOtherDelete = null }) { Text("Cancel") } }
        )
    }
    if (ocrState.status == OcrStatus.PROCESSING) AlertDialog(
        onDismissRequest = {},
        title = { Text("Extracting text...") },
        text = {
            Column {
                Text("Processing ${ocrState.completedImages} of ${ocrState.totalImages}")
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { ocrState.progress }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Surface3)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun BarAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) Danger else TextPrimary
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = c, modifier = Modifier.size(22.dp))
        Text(label, color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Draggable scroll thumb with a date label. Visible only while scrolling. */
@Composable
private fun FastScrollRail(state: LazyGridState, entries: List<GridEntry>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var label by remember { mutableStateOf("") }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(dragging, scrolling) { if (dragging || scrolling) visible = true else { delay(1500); visible = false } }
    if (!visible && !dragging) return

    val total = entries.size.coerceAtLeast(2)
    BoxWithConstraints(
        modifier.fillMaxHeight().width(30.dp).padding(vertical = 8.dp).pointerInput(total) {
            detectVerticalDragGestures(
                onDragStart = { dragging = true }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }
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
        Box(Modifier.align(Alignment.TopEnd).offset(y = y).width(4.dp).height(thumbH).clip(RoundedCornerShape(4.dp)).background(Accent))
        if (dragging && label.isNotEmpty()) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = y + 16.dp).clip(RoundedCornerShape(10.dp)).background(Accent).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(label, color = AccentOn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}