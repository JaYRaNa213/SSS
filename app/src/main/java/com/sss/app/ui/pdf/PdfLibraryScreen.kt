package com.sss.app.ui.pdf

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sss.app.data.pdf.PdfItem
import com.sss.app.ui.theme.Accent
import com.sss.app.ui.theme.Bg
import com.sss.app.ui.theme.Danger
import com.sss.app.ui.theme.MonoLabel
import com.sss.app.ui.theme.RoundIconButton
import com.sss.app.ui.theme.SssTopBar
import com.sss.app.ui.theme.Surface1
import com.sss.app.ui.theme.Surface2
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.sssFieldColors
import com.sss.app.util.PdfActions
import com.sss.app.util.PdfFormat

@Composable
fun PdfLibraryScreen(
    onBack: () -> Unit,
    onOpenPdf: (Long) -> Unit,
    viewModel: PdfLibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val total by viewModel.total.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var sortMenu by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf<PdfItem?>(null) }
    var deleting by remember { mutableStateOf<PdfItem?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        snackbar.showSnackbar(m)
        viewModel.consumeMessage()
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }

    Scaffold(containerColor = Bg, snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            SssTopBar(title = "My PDFs", onBack = onBack)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    placeholder = { Text("Search PDFs") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { viewModel.query.value = "" }) {
                            Icon(Icons.Default.Clear, "Clear search")
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = sssFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    RoundIconButton(Icons.Default.SwapVert, "Sort", { sortMenu = true })
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        PdfSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(if (s == sort) "${s.label}  \u2713" else s.label) },
                                onClick = { viewModel.sort.value = s; sortMenu = false }
                            )
                        }
                    }
                }
            }

            MonoLabel(
                if (total == 1) "1 PDF" else "$total PDFs",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading && total == 0 -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                    total == 0 -> EmptyState(
                        "No PDFs yet",
                        "Open a folder, select screenshots and tap PDF. Your PDFs will appear here."
                    )
                    items.isEmpty() -> EmptyState("No match", "Try a different search.")
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items, key = { it.id }) { pdf ->
                            PdfRow(
                                pdf = pdf,
                                menuOpen = menuFor == pdf.id,
                                onOpenMenu = { menuFor = pdf.id },
                                onCloseMenu = { menuFor = null },
                                onOpen = { onOpenPdf(pdf.id) },
                                onShare = { PdfActions.share(context, pdf) },
                                onExternal = { PdfActions.openExternal(context, pdf) },
                                onRename = { renaming = pdf },
                                onDelete = { deleting = pdf }
                            )
                        }
                    }
                }
            }
        }
    }

    renaming?.let { pdf ->
        RenamePdfDialog(
            initial = pdf.title,
            onConfirm = { name -> renaming = null; viewModel.rename(pdf, name) },
            onDismiss = { renaming = null }
        )
    }
    deleting?.let { pdf ->
        DeletePdfDialog(
            name = pdf.name,
            onConfirm = {
                deleting = null
                viewModel.delete(pdf, onNeedsConfirm = { sender ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                })
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(body, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PdfRow(
    pdf: PdfItem,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExternal: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)
            .clickable(onClick = onOpen).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Surface2),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PictureAsPdf, null, tint = Accent) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pdf.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${PdfFormat.size(pdf.sizeBytes)} \u00B7 ${PdfFormat.date(pdf.modifiedMs)}", color = TextMuted, fontSize = 13.sp, maxLines = 1)
        }
        Box {
            IconButton(onClick = onOpenMenu) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onCloseMenu) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { onCloseMenu(); onOpen() })
                DropdownMenuItem(text = { Text("Share") }, onClick = { onCloseMenu(); onShare() })
                DropdownMenuItem(text = { Text("Open in another app") }, onClick = { onCloseMenu(); onExternal() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { onCloseMenu(); onRename() })
                DropdownMenuItem(text = { Text("Delete", color = Danger) }, onClick = { onCloseMenu(); onDelete() })
            }
        }
    }
}