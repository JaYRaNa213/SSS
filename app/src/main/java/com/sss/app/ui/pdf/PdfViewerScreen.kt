package com.sss.app.ui.pdf

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sss.app.ui.theme.Accent
import com.sss.app.ui.theme.Danger
import com.sss.app.ui.theme.GhostButton
import com.sss.app.ui.theme.RoundIconButton
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.TextPrimary
import com.sss.app.util.PdfActions

@Composable
fun PdfViewerScreen(
    onBack: () -> Unit,
    viewerVm: PdfViewerViewModel = hiltViewModel(),
    actionsVm: PdfLibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewerVm.state.collectAsState()
    val message by actionsVm.message.collectAsState()
    val item = state.item

    var chrome by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) onBack()
    }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
        actionsVm.consumeMessage()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF1B1B1F))) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)

            state.error != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error.orEmpty(), color = TextPrimary)
                Spacer(Modifier.size(16.dp))
                GhostButton("Back", onClick = onBack)
            }

            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val screenW = maxWidth
                val density = LocalDensity.current
                // idempotent: tells the ViewModel how sharp to render pages
                viewerVm.setRenderWidth(with(density) { (screenW * 1.8f).roundToPx() })
                val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                Box(
                    Modifier
                        .fillMaxSize()
                        // single tap = show/hide bars, double tap = zoom in/out
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { chrome = !chrome },
                                onDoubleTap = { zoom = if (zoom > 1.05f) 1f else 2.5f }
                            )
                        }
                        // two-finger pinch zoom (one finger keeps scrolling the pages)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                do {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    if (ev.changes.count { it.pressed } >= 2) {
                                        val z = ev.calculateZoom()
                                        if (z != 1f) zoom = (zoom * z).coerceIn(1f, 4f)
                                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (ev.changes.any { it.pressed })
                            }
                        }
                        .horizontalScroll(hScroll, enabled = zoom > 1.02f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width(screenW * zoom).fillMaxHeight(),
                        contentPadding = PaddingValues(top = topInset + 64.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(count = state.aspects.size, key = { it }) { index ->
                            PdfPageView(index, state.aspects[index], viewerVm)
                        }
                    }
                }
            }
        }

        // top bar
        AnimatedVisibility(
            visible = chrome && state.error == null && !state.loading,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, container = Color.Black.copy(alpha = 0.5f))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            item?.title.orEmpty(), color = TextPrimary, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("$currentPage / ${state.aspects.size}", color = TextMuted, fontSize = 12.sp)
                    }
                    RoundIconButton(
                        Icons.Default.Share, "Share",
                        { item?.let { PdfActions.share(context, it) } },
                        container = Color.Black.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.size(8.dp))
                    Box {
                        RoundIconButton(Icons.Default.MoreVert, "More", { menuOpen = true }, container = Color.Black.copy(alpha = 0.5f))
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Fit to width") }, onClick = { menuOpen = false; zoom = 1f })
                            DropdownMenuItem(
                                text = { Text("Open in another app") },
                                onClick = { menuOpen = false; item?.let { PdfActions.openExternal(context, it) } }
                            )
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete", color = Danger) }, onClick = { menuOpen = false; deleting = true })
                        }
                    }
                }
            }
        }
    }

    if (renaming && item != null) {
        RenamePdfDialog(
            initial = item.title,
            onConfirm = { name ->
                renaming = false
                actionsVm.rename(item, name) { viewerVm.reloadItem() }
            },
            onDismiss = { renaming = false }
        )
    }
    if (deleting && item != null) {
        DeletePdfDialog(
            name = item.name,
            onConfirm = {
                deleting = false
                actionsVm.delete(
                    item,
                    onNeedsConfirm = { sender -> deleteLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
                    onDeleted = onBack
                )
            },
            onDismiss = { deleting = false }
        )
    }
}

@Composable
private fun PdfPageView(index: Int, aspect: Float, vm: PdfViewerViewModel) {
    var bmp by remember(index) { mutableStateOf(vm.cached(index)) }
    LaunchedEffect(index) { if (bmp == null) bmp = vm.render(index) }

    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(0.2f, 5f)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            val image = remember(b) { b.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Accent)
        }
    }
}