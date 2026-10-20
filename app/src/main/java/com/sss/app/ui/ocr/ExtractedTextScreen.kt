package com.sss.app.ui.ocr

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ExtractedTextScreen(
    documentId: Long,
    onBackClick: () -> Unit,
    viewModel: OcrViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val document by viewModel.currentDocument.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    val safPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null && document != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    TextPdfExporter.generatePdf(
                        context = context,
                        title = document!!.title,
                        content = document!!.content,
                        imageCount = document!!.sourceImageCount,
                        outputStream = out
                    )
                }
                Toast.makeText(context, "PDF saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to write PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->

        if (document == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading document...",
                    color = Color.White
                )
            }
        } else {
            val doc = document!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Study Notes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = saveStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val file = TextPdfExporter.generatePdfToCache(
                                    context = context,
                                    title = doc.title,
                                    content = doc.content,
                                    imageCount = doc.sourceImageCount
                                )
                                try {
                                    val fileUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Study Notes PDF"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Exported PDF to cache: ${file.name}", Toast.LENGTH_LONG).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Export PDF",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { showDeleteDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete document",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Document Title Input
                OutlinedTextField(
                    value = doc.title,
                    onValueChange = { viewModel.updateDocumentTitle(it) },
                    label = { Text("Document Title", color = Color.Gray) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // Source Info Badge
                Text(
                    text = "📄 ${doc.sourceImageCount} screenshot${if (doc.sourceImageCount == 1) "" else "s"} processed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Extracted Text Multiline Editor
                OutlinedTextField(
                    value = doc.content,
                    onValueChange = { viewModel.updateDocumentContent(it) },
                    label = { Text("Extracted Notes (Editable)", color = Color.Gray) },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 12.dp)
                )

                // Bottom Export Button
                Button(
                    onClick = {
                        val sanitized = doc.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_TITLE, "${sanitized}_Notes.pdf")
                        }
                        safPdfLauncher.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save as PDF Document",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    if (showDeleteDialog && document != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Document?") },
            text = { Text("This study document will be removed. Source screenshots will NOT be deleted.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteDocument(document!!) {
                            onBackClick()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
