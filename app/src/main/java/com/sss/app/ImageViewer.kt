package com.sss.app

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.ui.folder.AllScreenshotsViewModel
import com.sss.app.ui.folder.FolderViewModel
import com.sss.app.util.MediaDelete

// ================================================================
// ZOOMABLE IMAGE
//
// WHY SWIPE WAS BROKEN:
// detectTransformGestures() consumes every finger movement as soon as it
// passes touch-slop - even at scale 1.0. HorizontalPager sits ABOVE this
// image, sees the movement already consumed, and never starts scrolling.
//
// FIX: custom gesture loop that only consumes events when
//   (a) two fingers are down (pinch), or
//   (b) the image is already zoomed in (pan).
// At scale 1.0 a one-finger swipe is left alone -> the pager gets it.
// ================================================================

@Composable
fun ZoomableImage(
    imageModel: Any,
    contentDescription: String?,
    rotation: Float,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun resetZoom() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            resetZoom()
            onZoomChanged(false)
        }
    }
    LaunchedEffect(rotation) { resetZoom() }
    LaunchedEffect(scale, isCurrentPage) {
        if (isCurrentPage) onZoomChanged(scale > 1.02f)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val isSideways = rotation % 180f != 0f
        val fitRotationScale =
            if (isSideways && containerWidth > 0f && containerHeight > 0f) {
                minOf(containerWidth / containerHeight, containerHeight / containerWidth)
            } else 1f

        fun maxPan(s: Float, size: Float): Float =
            maxOf(0f, (size * s * fitRotationScale - size) / 2f)

        val effectiveScale = scale * fitRotationScale
        val maxX = maxPan(scale, containerWidth)
        val maxY = maxPan(scale, containerHeight)
        val clampedX = offsetX.coerceIn(-maxX, maxX)
        val clampedY = offsetY.coerceIn(-maxY, maxY)

        Box(
            modifier = Modifier
                .fillMaxSize()
                // pointerInput keys include everything the lambda captured,
                // so rotation/size changes no longer leave stale values behind
                .pointerInput(containerWidth, containerHeight, fitRotationScale) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1.05f) {
                                resetZoom()
                            } else {
                                val target = 2.5f
                                scale = target
                                val mx = maxPan(target, containerWidth)
                                val my = maxPan(target, containerHeight)
                                offsetX = ((containerWidth / 2f - tap.x) * (target - 1f))
                                    .coerceIn(-mx, mx)
                                offsetY = ((containerHeight / 2f - tap.y) * (target - 1f))
                                    .coerceIn(-my, my)
                            }
                        }
                    )
                }
                .pointerInput(containerWidth, containerHeight, fitRotationScale) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingersDown = event.changes.count { it.pressed }

                            // Only take over the gesture for pinch or when already zoomed.
                            if (fingersDown >= 2 || scale > 1.01f) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                    .takeIf { it.isSpecified } ?: Offset.Zero

                                val oldScale = scale
                                val newScale = (oldScale * zoom).coerceIn(1f, 5f)

                                if (newScale <= 1.01f) {
                                    resetZoom()
                                } else {
                                    val k = newScale / oldScale
                                    val cx = centroid.x - containerWidth / 2f
                                    val cy = centroid.y - containerHeight / 2f
                                    val mx = maxPan(newScale, containerWidth)
                                    val my = maxPan(newScale, containerHeight)
                                    // keeps the point under your fingers fixed while zooming
                                    offsetX = (offsetX * k - cx * (k - 1f) + pan.x).coerceIn(-mx, mx)
                                    offsetY = (offsetY * k - cy * (k - 1f) + pan.y).coerceIn(-my, my)
                                    scale = newScale
                                }

                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = effectiveScale,
                        scaleY = effectiveScale,
                        translationX = clampedX,
                        translationY = clampedY,
                        rotationZ = rotation
                    ),
                onError = { state ->
                    Log.e("SSS_VIEWER", "IMAGE ERROR Model=$imageModel", state.result.throwable)
                }
            )
        }
    }
}

// ================================================================
// IMAGE VIEWER SCREEN
//
// Also fixes: opening a picture from "All Screenshots / Other" showed a blank
// screen, because this screen only read Room with folderId = -1 (no rows).
// ================================================================

@Composable
fun ImageViewerScreen(
    folderId: Long,
    initialIndex: Int,
    onBackClick: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
    // pass the SAME instance the grid uses (see PATCHES.md) so indexes match
    allScreenshotsViewModel: AllScreenshotsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isOther = folderId == -1L

    val roomFlow = remember(folderId) { viewModel.screenshots(folderId) }
    val roomShots by roomFlow.collectAsState()
    val otherShots by allScreenshotsViewModel.allScreenshots.collectAsState()
    val screenshots = if (isOther) otherShots else roomShots

    LaunchedEffect(isOther) {
        if (isOther && allScreenshotsViewModel.allScreenshots.value.isEmpty()) {
            allScreenshotsViewModel.loadAllScreenshots()
        }
    }

    // leave the screen automatically after the LAST image is deleted
    var hadItems by remember { mutableStateOf(false) }
    LaunchedEffect(screenshots.size) {
        if (screenshots.isNotEmpty()) hadItems = true
        else if (hadItems) onBackClick()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            allScreenshotsViewModel.loadAllScreenshots()
        }
    }

    val onDelete: (ScreenshotEntity) -> Unit = { shot ->
        if (isOther) {
            // real file on the device -> needs the system confirmation dialog
            val sender = MediaDelete.delete(context, listOf(Uri.parse(shot.filePath)))
            if (sender != null) {
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } else {
                allScreenshotsViewModel.loadAllScreenshots()
            }
        } else {
            viewModel.deleteScreenshot(shot)
        }
    }

    if (screenshots.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        return
    }

    ViewerPager(
        screenshots = screenshots,
        initialIndex = initialIndex,
        onBackClick = onBackClick,
        onDelete = onDelete
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerPager(
    screenshots: List<ScreenshotEntity>,
    initialIndex: Int,
    onBackClick: () -> Unit,
    onDelete: (ScreenshotEntity) -> Unit
) {
    // Only composed when the list is NOT empty, so the pager is always created
    // with a valid initial page (no conditional remember after an early return).
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, screenshots.lastIndex),
        pageCount = { screenshots.size }
    )

    var isCurrentPageZoomed by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScreenshotEntity?>(null) }

    // rotation is remembered per screenshot ID (the old code used the page index,
    // so after a delete the rotation jumped to the wrong picture)
    val rotationMap = remember { mutableStateMapOf<Long, Float>() }

    LaunchedEffect(pagerState.currentPage) { isCurrentPageZoomed = false }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentPageZoomed,
                key = { page -> screenshots.getOrNull(page)?.id ?: page },
                modifier = Modifier.fillMaxSize()
            ) { page ->

                val screenshot = screenshots.getOrNull(page) ?: return@HorizontalPager

                val imageModel = remember(screenshot.filePath) {
                    if (screenshot.filePath.startsWith("content://") ||
                        screenshot.filePath.startsWith("file://")
                    ) Uri.parse(screenshot.filePath) else screenshot.filePath
                }

                ZoomableImage(
                    imageModel = imageModel,
                    contentDescription = "Screenshot #${screenshot.sequenceNumber}",
                    rotation = rotationMap[screenshot.id] ?: 0f,
                    isCurrentPage = pagerState.currentPage == page,
                    onZoomChanged = { zoomed: Boolean ->
                        if (pagerState.currentPage == page) isCurrentPageZoomed = zoomed
                    }
                )
            }

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x88000000))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Text(
                    text = "${pagerState.currentPage + 1} / ${screenshots.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val current = screenshots.getOrNull(pagerState.currentPage)
                            if (current != null) {
                                rotationMap[current.id] =
                                    ((rotationMap[current.id] ?: 0f) + 90f) % 360f
                            }
                        }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate image", tint = Color.White)
                    }

                    IconButton(
                        onClick = { pendingDelete = screenshots.getOrNull(pagerState.currentPage) }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete screenshot", tint = Color.White)
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this screenshot?") },
            text = { Text("This screenshot will be removed.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        onDelete(target)
                        pendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}
