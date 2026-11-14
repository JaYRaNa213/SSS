package com.sss.app.ui.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sss.app.domain.ocr.NoteFormat
import com.sss.app.ui.theme.*

@Composable
fun ExtractedTextScreen(documentId: Long, onBackClick: () -> Unit, viewModel: OcrViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val document by viewModel.currentDocument.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()
    var showDelete by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showMarkers by rememberSaveable { mutableStateOf(true) }
    var showIgnored by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(documentId) { viewModel.loadDocument(documentId) }

    val safPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val d = document
        if (uri != null && d != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    TextPdfExporter.generatePdf(context, d.title, d.content, d.sourceImageCount, out)
                }
                Toast.makeText(context, "PDF saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to write PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(containerColor = Bg) { inner ->
        val doc = document
        if (doc == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { Text("Loading...", color = TextMuted) }
            return@Scaffold
        }

        val parts = remember(doc.content) { NoteFormat.split(doc.content) }
        val main = parts.first
        val ignored = parts.second
        val blocks = remember(main) { NoteFormat.parse(main) }
        var field by remember(doc.id) { mutableStateOf(TextFieldValue(main)) }
        val transformation = remember { NoteVisualTransformation(TextMuted, Accent) }
        val failed = Regex("\\[OCR Failed").findAll(main).count()
        val read = (doc.sourceImageCount - failed).coerceAtLeast(0)

        fun commit(v: TextFieldValue) {
            field = v
            if (v.text != main) viewModel.updateDocumentContent(NoteFormat.join(v.text, ignored))
        }

        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
            // ---- top bar
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Extracted text", style = MaterialTheme.typography.titleLarge)
                    MonoLabel(saveStatus)
                }
                RoundIconButton(Icons.Default.Delete, "Delete document", { showDelete = true }, tint = Danger, container = DangerBg)
                Spacer(Modifier.width(10.dp))
                AccentPill("Save", { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); onBackClick() })
            }

            // ---- title
            MonoLabel("Title")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = doc.title, onValueChange = { viewModel.updateDocumentTitle(it) }, singleLine = true,
                shape = RoundedCornerShape(20.dp), colors = sssFieldColors(),
                textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            // ---- status row
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Success))
                Spacer(Modifier.width(8.dp))
                Text("$read of ${doc.sourceImageCount} read", color = Success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (failed > 0) { Spacer(Modifier.width(14.dp)); Text("$failed failed", color = Danger, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                Spacer(Modifier.weight(1f))
                if (ignored.isNotEmpty()) Row(
                    Modifier.clip(CircleShape).background(Surface3).clickable2 { showIgnored = !showIgnored }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VisibilityOff, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${ignored.size} ignored", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ---- ignored text panel
            if (showIgnored && ignored.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 190.dp).clip(RoundedCornerShape(16.dp)).background(Surface2)
                        .verticalScroll(rememberScrollState()).padding(12.dp)
                ) {
                    Text("Left out of the notes. Tap Add back to return a line to its screenshot.", color = TextMuted, fontSize = 12.sp)
                    ignored.forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("#${item.page}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                            Text(item.text, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2)
                            TextButton(onClick = {
                                val newContent = NoteFormat.restore(doc.content, item)
                                viewModel.updateDocumentContent(newContent)
                                field = TextFieldValue(NoteFormat.split(newContent).first)
                            }) { Text("Add back", color = Accent) }
                        }
                    }
                }
            }

            // ---- mode row
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterPill("Read", !editing, { editing = false })
                FilterPill("Edit", editing, { editing = true })
                Spacer(Modifier.weight(1f))
                if (!editing) FilterPill(if (showMarkers) "Hide labels" else "Show labels", false, { showMarkers = !showMarkers })
            }
            if (editing) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("H1" to "# ", "H2" to "## ", "H3" to "### ", "Bullet" to "- ", "1." to "1. ", "Normal" to "").forEach { (label, prefix) ->
                        FilterPill(label, false, { commit(applyLinePrefix(field, prefix)) })
                    }
                }
            }

            // ---- content
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface1)) {
                if (editing) {
                    OutlinedTextField(
                        value = field, onValueChange = { commit(it) },
                        visualTransformation = transformation,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
                            cursorColor = Accent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        ),
                        textStyle = TextStyle(fontSize = 15.sp),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    NoteView(blocks, showMarkers, Modifier.fillMaxSize())
                }
            }

            // ---- bottom bar
            val plain = NoteFormat.toPlain(main)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkPill("Copy", {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(doc.title, plain))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }, icon = Icons.Default.ContentCopy)
                DarkPill("Share", {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, plain); putExtra(Intent.EXTRA_SUBJECT, doc.title) }
                    context.startActivity(Intent.createChooser(i, "Share notes"))
                }, icon = Icons.Default.Share)
                DarkPill("PDF", {
                    val name = doc.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                    safPdfLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf"; putExtra(Intent.EXTRA_TITLE, "${name}_Notes.pdf")
                    })
                }, Modifier.weight(1f), icon = Icons.Default.PictureAsPdf, container = TextPrimary, content = Bg)
            }
        }
    }

    if (showDelete && document != null) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Delete notes?") },
        text = { Text("The notes are removed. Your screenshots are not deleted.") },
        confirmButton = { TextButton(onClick = { showDelete = false; viewModel.deleteDocument(document!!) { onBackClick() } }) { Text("Delete", color = Danger) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
    )
}

private fun Modifier.clickable2(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))