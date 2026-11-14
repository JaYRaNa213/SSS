package com.sss.app.ui.pdf

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sss.app.ui.common.CroppedImage
import com.sss.app.ui.theme.*
import com.sss.app.util.MediaIds
import com.sss.app.util.toImageModel
import java.util.Locale

@Composable
fun PdfFlowScreen(
    onBack: () -> Unit,
    onViewPdf: (Long) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    vm: PdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val metaMap by vm.metaMap.collectAsState()
    val run = vm.run
    val finalName = vm.finalName()
    val base = PdfSaver.sanitize(vm.name)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            SssTopBar(title = "Create PDF", subtitle = vm.folderName, onBack = onBack)

            LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                // 1 NAME
                item {
                    MonoLabel("1  Name", modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    OutlinedTextField(
                        value = vm.name, onValueChange = { vm.name = it }, singleLine = true,
                        label = { Text("File name") }, suffix = { Text(".pdf") },
                        isError = base.isBlank(),
                        supportingText = {
                            Text(
                                when {
                                    base.isBlank() -> "Enter a file name"
                                    finalName != base -> "Already exists - will save as $finalName.pdf"
                                    else -> "Saved to Downloads/4S"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 2 OPTIONS
                item {
                    MonoLabel("2  Options", modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                    OptionRow("Images per page", listOf("1", "2", "3"), vm.options.perPage - 1) { vm.options = vm.options.copy(perPage = it + 1) }
                    OptionRow("Page size", listOf("A4", "Fit to image"), if (vm.options.a4) 0 else 1) { vm.options = vm.options.copy(a4 = it == 0) }
                    if (vm.options.perPage >= 2 && !vm.options.a4) {
                        Text("\"Fit to image\" only applies to 1 image per page. This PDF will be A4.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    OptionRow("Quality", PdfQuality.entries.map { it.label }, vm.options.quality.ordinal) { vm.options = vm.options.copy(quality = PdfQuality.entries[it]) }
                    OptionRow("Page order", PdfOrder.entries.map { it.label }, vm.options.order.ordinal) { vm.setOrder(PdfOrder.entries[it]) }
                    SwitchRow("Use auto-crop", vm.options.useCrop) { vm.options = vm.options.copy(useCrop = it) }
                    SwitchRow("Small page margin", vm.options.margin) { vm.options = vm.options.copy(margin = it) }
                }
                // 3 PREVIEW
                item {
                    MonoLabel(
                        "3  Images \u00B7 ${vm.pages.size} \u00B7 ${vm.sheetCount()} pages \u00B7 ~${String.format(Locale.US, "%.1f", vm.estimateMb())} MB",
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                    )
                    if (vm.pages.isEmpty()) Text("No pages left.", color = TextMuted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(vm.pages, key = { _, s -> s.id }) { i, shot ->
                            Column(Modifier.width(110.dp)) {
                                Box(
                                    Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)).background(Surface2).border(1.dp, Line, RoundedCornerShape(10.dp))
                                ) {
                                    CroppedImage(
                                        model = shot.filePath.toImageModel(), meta = metaMap[MediaIds.fromPath(shot.filePath)],
                                        cropEnabled = vm.options.useCrop, fit = true, modifier = Modifier.fillMaxSize()
                                    )
                                    Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(CircleShape).background(Accent).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text("${i + 1}", color = AccentOn, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { vm.move(i, -1) }, enabled = i > 0) { Text("\u2039") }
                                    TextButton(onClick = { vm.remove(i) }) { Text("\u2715") }
                                    TextButton(onClick = { vm.move(i, 1) }, enabled = i < vm.pages.lastIndex) { Text("\u203A") }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                SssPrimaryButton(
                    "Create PDF \u00B7 ${vm.sheetCount()} page${if (vm.sheetCount() == 1) "" else "s"}",
                    enabled = base.isNotBlank() && vm.pages.isNotEmpty() && run !is PdfRun.Running,
                    onClick = vm::create
                )
            }
        }
    }

    when (run) {
        is PdfRun.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Creating PDF") },
            text = {
                Column {
                    Text("Adding image ${run.done} of ${run.total}")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { if (run.total == 0) 0f else run.done.toFloat() / run.total }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Surface3)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::cancel) { Text("Cancel") } }
        )
        is PdfRun.Done -> {
            val saved = run.saved
            var resolvedId by remember(saved) { mutableStateOf<Long?>(runCatching { android.content.ContentUris.parseId(saved.uri) }.getOrNull()?.takeIf { it > 0 }) }
            LaunchedEffect(saved) {
                if (resolvedId == null) {
                    resolvedId = vm.resolvePdfId(saved)
                }
            }
            AlertDialog(
                onDismissRequest = vm::dismissResult,
                title = { Text("PDF saved") },
                text = {
                    Column {
                        Text("${saved.name} \u00B7 ${String.format(Locale.US, "%.1f", saved.sizeBytes / 1_048_576f)} MB")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val id = resolvedId
                            if (id != null) {
                                SssPrimaryButton(
                                    "Read now",
                                    onClick = { vm.dismissResult(); onViewPdf(id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            GhostButton(
                                "All PDFs",
                                onClick = { vm.dismissResult(); onOpenLibrary() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Share", modifier = Modifier.fillMaxWidth(), onClick = {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, saved.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(i, "Share PDF"))
                        })
                    }
                },
                confirmButton = { TextButton(onClick = { vm.dismissResult(); onBack() }) { Text("Done") } }
            )
        }
        is PdfRun.Failed -> AlertDialog(
            onDismissRequest = vm::dismissResult,
            title = { Text("Could not create PDF") },
            text = { Text(run.message) },
            confirmButton = { TextButton(onClick = vm::dismissResult) { Text("OK") } }
        )
        PdfRun.Idle -> Unit
    }
}

@Composable
private fun OptionRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, text ->
                FilterChip(
                    selected = i == selected, onClick = { onSelect(i) }, label = { Text(text) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentSoft, selectedLabelColor = Accent)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SssSwitch(checked, onChange)
    }
}