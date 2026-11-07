package com.sss.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.local.ScreenshotMetaEntity
import com.sss.app.data.settings.AppSettings
import com.sss.app.ui.common.CroppedImage
import com.sss.app.ui.common.MetaMath
import com.sss.app.ui.folder.AllScreenshotsViewModel
import com.sss.app.ui.folder.FolderViewModel
import com.sss.app.ui.folder.sortedFor
import com.sss.app.ui.ocr.OcrStatus
import com.sss.app.ui.ocr.OcrViewModel
import com.sss.app.ui.theme.Accent
import com.sss.app.ui.theme.AccentOn
import com.sss.app.ui.theme.Bg
import com.sss.app.ui.theme.Danger
import com.sss.app.ui.theme.GhostButton
import com.sss.app.ui.theme.Line
import com.sss.app.ui.theme.MonoLabel
import com.sss.app.ui.theme.RoundIconButton
import com.sss.app.ui.theme.SssPrimaryButton
import com.sss.app.ui.theme.Surface1
import com.sss.app.ui.theme.Surface2
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.TextPrimary
import com.sss.app.util.MediaDelete
import com.sss.app.util.MediaIds
import com.sss.app.util.findActivity
import com.sss.app.util.toImageModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// ================================================================
// ZOOMABLE CONTAINER
// Pinch / pan only take over the gesture when 2 fingers are down or the content is already zoomed,
// so a one-finger swipe at 1x reaches the HorizontalPager. [contentAspect] lets rotated wide slides scale UP.
// ================================================================
@Composable
fun ZoomableContent(
    rotation: Float,
    contentAspect: Float?,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    fun resetZoom() { scale = 1f; offsetX = 0f; offsetY = 0f }

    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) { resetZoom(); onZoomChanged(false) } }
    LaunchedEffect(rotation) { resetZoom() }
    LaunchedEffect(scale, isCurrentPage) { if (isCurrentPage) onZoomChanged(scale > 1.02f) }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val sideways = rotation % 180f != 0f
        val containerAspect = if (ch > 0f) cw / ch else 1f
        val a = contentAspect ?: containerAspect
        val fw = if (a > containerAspect) cw else ch * a
        val fh = if (a > containerAspect) cw / a else ch
        val fitTarget = if (sideways && cw > 0f && ch > 0f && fw > 0f && fh > 0f) minOf(cw / fh, ch / fw) else 1f
        val fit by animateFloatAsState(fitTarget, label = "fit")
        val rot by animateFloatAsState(rotation, label = "rot")

        fun maxPan(s: Float, size: Float) = maxOf(0f, (size * s * fit - size) / 2f)

        val maxX = maxPan(scale, cw)
        val maxY = maxPan(scale, ch)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(cw, ch, fitTarget) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tap ->
                            if (scale > 1.05f) resetZoom()
                            else {
                                val target = 2.5f
                                scale = target
                                val mx = maxPan(target, cw)
                                val my = maxPan(target, ch)
                                offsetX = ((cw / 2f - tap.x) * (target - 1f)).coerceIn(-mx, mx)
                                offsetY = ((ch / 2f - tap.y) * (target - 1f)).coerceIn(-my, my)
                            }
                        }
                    )
                }
                .pointerInput(cw, ch, fitTarget) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2 || scale > 1.01f) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false).takeIf { it.isSpecified } ?: Offset.Zero
                                val old = scale
                                val new = (old * zoom).coerceIn(1f, 5f)
                                if (new <= 1.01f) resetZoom()
                                else {
                                    val k = new / old
                                    val cx = centroid.x - cw / 2f
                                    val cy = centroid.y - ch / 2f
                                    val mx = maxPan(new, cw)
                                    val my = maxPan(new, ch)
                                    offsetX = (offsetX * k - cx * (k - 1f) + pan.x).coerceIn(-mx, mx)
                                    offsetY = (offsetY * k - cy * (k - 1f) + pan.y).coerceIn(-my, my)
                                    scale = new
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale * fit, scaleY = scale * fit,
                    translationX = offsetX.coerceIn(-maxX, maxX), translationY = offsetY.coerceIn(-maxY, maxY),
                    rotationZ = rot
                ),
                contentAlignment = Alignment.Center
            ) { content() }
        }
    }
}

// ================================================================
// SCREEN
// ================================================================
@Composable
fun ImageViewerScreen(
    folderId: Long,
    initialIndex: Int,
    onBack: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    allScreenshotsViewModel: AllScreenshotsViewModel,
    viewModel: FolderViewModel = hiltViewModel(),
    ocrViewModel: OcrViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isOther = folderId == -1L

    val settings by viewModel.settings.collectAsState()
    val metaMap by viewModel.metaMap.collectAsState()
    val folder by remember(folderId) { viewModel.folder(folderId) }.collectAsState(initial = null)
    val roomShots by remember(folderId) { viewModel.screenshots(folderId) }.collectAsState()
    val otherShots by allScreenshotsViewModel.allScreenshots.collectAsState()
    val ocrState by ocrViewModel.ocrUiState.collectAsState()
    val undo by viewModel.undo.collectAsState()

    val raw = if (isOther) otherShots else roomShots
    val order = if (isOther) settings.sortOther else settings.sortFolders
    val sorted = remember(raw, order) { raw.sortedFor(order) }   // SAME order as the grid -> same indexes

    // immersive: hide system bars while viewing
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(isOther) {
        if (isOther && allScreenshotsViewModel.allScreenshots.value.isEmpty()) allScreenshotsViewModel.loadAllScreenshots()
    }

    var hadItems by remember { mutableStateOf(false) }
    LaunchedEffect(sorted.size) {
        if (sorted.isNotEmpty()) hadItems = true else if (hadItems) onBack()
    }

    LaunchedEffect(ocrState.createdDocumentId) {
        val id = ocrState.createdDocumentId
        if (id != null) { ocrViewModel.resetOcrState(); onOpenDocument(id) }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undo?.token) {
        val s = undo ?: return@LaunchedEffect
        val r = withTimeoutOrNull(6_000) { snackbar.showSnackbar(s.message, actionLabel = "UNDO", duration = SnackbarDuration.Indefinite) }
        if (r == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        else { snackbar.currentSnackbarData?.dismiss(); viewModel.consumeUndo(s.token) }
    }

    var pendingOtherDelete by remember { mutableStateOf<ScreenshotEntity?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) allScreenshotsViewModel.loadAllScreenshots()
    }

    if (sorted.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ViewerPager(
            sorted = sorted,
            initialIndex = initialIndex,
            settings = settings,
            metaMap = metaMap,
            onBack = onBack,
            onDelete = { shot -> if (isOther) pendingOtherDelete = shot else viewModel.delete(listOf(shot)) },
            onRotate = { shot, eff -> viewModel.rotate(shot.filePath, eff) },
            onShare = { shot ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(shot.filePath))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share screenshot"))
            },
            onOcr = { shot ->
                ocrViewModel.extractTextFromImages(folderId, if (isOther) "Screenshot" else folder?.name ?: "Screenshot", listOf(Uri.parse(shot.filePath)))
            },
            onSaveCrop = { shot, t, b, l, r, all ->
                viewModel.saveCrop(shot.filePath, t, b, l, r, if (all) sorted.map { it.filePath } else null)
            }
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 160.dp))
    }

    pendingOtherDelete?.let { shot ->
        AlertDialog(
            onDismissRequest = { pendingOtherDelete = null },
            title = { Text("Delete from your phone?") },
            text = { Text("This is the original file. Android will ask you to confirm. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingOtherDelete = null
                    val sender = MediaDelete.delete(context, listOf(Uri.parse(shot.filePath)))
                    if (sender != null) deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    else allScreenshotsViewModel.loadAllScreenshots()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingOtherDelete = null }) { Text("Cancel") } }
        )
    }
    if (ocrState.status == OcrStatus.PROCESSING) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Extracting text...") },
            text = { LinearProgressIndicator(progress = { ocrState.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {}
        )
    }
}

@Composable
private fun ViewerPager(
    sorted: List<ScreenshotEntity>,
    initialIndex: Int,
    settings: AppSettings,
    metaMap: Map<Long, ScreenshotMetaEntity>,
    onBack: () -> Unit,
    onDelete: (ScreenshotEntity) -> Unit,
    onRotate: (ScreenshotEntity, Int) -> Unit,
    onShare: (ScreenshotEntity) -> Unit,
    onOcr: (ScreenshotEntity) -> Unit,
    onSaveCrop: (ScreenshotEntity, Float, Float, Float, Float, Boolean) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, sorted.lastIndex),
        pageCount = { sorted.size }
    )
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    val showOriginal = remember { mutableStateListOf<Long>() }

    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    val current = sorted.getOrNull(pagerState.currentPage)
    val currentMeta = current?.let { metaMap[MediaIds.fromPath(it.filePath)] }
    val currentOriginal = current != null && current.id in showOriginal
    val currentCropOn = settings.autoCrop && !currentOriginal
    val currentRotation = if (currentOriginal) (currentMeta?.rotation?.takeIf { it >= 0 } ?: 0)
    else MetaMath.effectiveRotation(currentMeta, currentCropOn, settings.autoRotateWide)

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !zoomed,
        key = { page -> sorted.getOrNull(page)?.id ?: page },
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val shot = sorted.getOrNull(page) ?: return@HorizontalPager
        val meta = metaMap[MediaIds.fromPath(shot.filePath)]
        val original = shot.id in showOriginal
        val cropOn = settings.autoCrop && !original
        val rot = if (original) (meta?.rotation?.takeIf { it >= 0 } ?: 0)
        else MetaMath.effectiveRotation(meta, cropOn, settings.autoRotateWide)
        ZoomableContent(
            rotation = rot.toFloat(),
            contentAspect = MetaMath.regionAspect(meta, cropOn),
            isCurrentPage = pagerState.currentPage == page,
            onZoomChanged = { z -> if (pagerState.currentPage == page) zoomed = z },
            onTap = { chrome = !chrome }
        ) {
            CroppedImage(
                model = shot.filePath.toImageModel(), meta = meta, cropEnabled = cropOn,
                fit = true, contentDescription = "Screenshot ${page + 1}", modifier = Modifier.fillMaxSize()
            )
        }
    }

    // ---- top chrome: round buttons + counter pill
    AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, container = Color.Black.copy(alpha = 0.5f))
                Spacer(Modifier.weight(1f))
                Text(
                    "${pagerState.currentPage + 1} / ${sorted.size}", fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                Box {
                    RoundIconButton(Icons.Default.MoreVert, "More", { menuOpen = true }, container = Color.Black.copy(alpha = 0.5f))
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (currentOriginal) "Show cropped" else "Show original") },
                            onClick = { menuOpen = false; current?.let { if (it.id in showOriginal) showOriginal.remove(it.id) else showOriginal.add(it.id) } }
                        )
                        DropdownMenuItem(
                            text = { Text("Adjust crop") },
                            enabled = currentMeta != null && currentMeta.imgW > 0,
                            onClick = { menuOpen = false; showAdjust = true }
                        )
                    }
                }
            }
        }
    }

    // ---- bottom chrome: filmstrip + floating action pill
    val filmState = rememberLazyListState()
    val viewerScope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { filmState.animateScrollToItem((pagerState.currentPage - 2).coerceAtLeast(0)) }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LazyRow(state = filmState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(60.dp)) {
                    itemsIndexed(sorted, key = { _, s -> s.id }) { i, s ->
                        val cur = i == pagerState.currentPage
                        AsyncImage(
                            model = s.filePath.toImageModel(), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.padding(vertical = if (cur) 0.dp else 6.dp).size(width = 44.dp, height = if (cur) 60.dp else 48.dp)
                                .clip(RoundedCornerShape(9.dp)).background(Surface2)
                                .then(if (cur) Modifier.border(2.dp, Accent, RoundedCornerShape(9.dp)) else Modifier)
                                .clickable { viewerScope.launch { pagerState.animateScrollToPage(i) } }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(32.dp)).background(Surface1.copy(alpha = 0.94f)).border(1.dp, Line, RoundedCornerShape(32.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerAction(Icons.Default.Share, "Share") { current?.let(onShare) }
                    Row(
                        Modifier.clip(CircleShape).background(Accent).clickable { current?.let(onOcr) }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.DocumentScanner, "Extract", tint = AccentOn, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Extract", color = AccentOn, fontWeight = FontWeight.Bold)
                    }
                    ViewerAction(Icons.Default.RotateRight, "Rotate") { current?.let { onRotate(it, currentRotation) } }
                    ViewerAction(Icons.Default.Delete, "Delete", danger = true) { current?.let(onDelete) }
                }
            }
        }
    }

    if (showAdjust && current != null && currentMeta != null) {
        AdjustCropDialog(
            path = current.filePath, meta = currentMeta, total = sorted.size,
            onDismiss = { showAdjust = false },
            onSave = { t, b, l, r, all -> showAdjust = false; onSaveCrop(current, t, b, l, r, all) }
        )
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) Danger else TextPrimary
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).defaultMinSize(minWidth = 60.dp, minHeight = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = c, modifier = Modifier.size(22.dp))
        Text(label, color = c, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** Manual crop: four sliders + live preview. "Apply to all" copies the same crop to every screenshot of this list. */
@Composable
private fun AdjustCropDialog(
    path: String,
    meta: ScreenshotMetaEntity,
    total: Int,
    onDismiss: () -> Unit,
    onSave: (Float, Float, Float, Float, Boolean) -> Unit
) {
    var t by remember { mutableFloatStateOf(meta.cropTop) }
    var b by remember { mutableFloatStateOf(meta.cropBottom) }
    var l by remember { mutableFloatStateOf(meta.cropLeft) }
    var r by remember { mutableFloatStateOf(meta.cropRight) }
    var all by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Text("Adjust crop", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                    CroppedImage(
                        model = path.toImageModel(),
                        meta = meta.copy(cropTop = t, cropBottom = b, cropLeft = l, cropRight = r),
                        cropEnabled = true, fit = true, modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(8.dp))
                CropSlider("Top", t) { t = it }
                CropSlider("Bottom", b) { b = it }
                CropSlider("Left", l) { l = it }
                CropSlider("Right", r) { r = it }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Apply to all $total screenshots here", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = all, onCheckedChange = { all = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Reset", onClick = { t = 0f; b = 0f; l = 0f; r = 0f }, modifier = Modifier.weight(1f))
                    GhostButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                SssPrimaryButton("Save", onClick = { onSave(t, b, l, r, all) })
            }
        }
    }
}

@Composable
private fun CropSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MonoLabel(label, modifier = Modifier.size(width = 64.dp, height = 20.dp), color = TextMuted)
        Slider(value = value, onValueChange = onChange, valueRange = 0f..0.45f, modifier = Modifier.weight(1f))
    }
}