package com.sss.app.ui.pdf

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sss.app.data.local.ScreenshotEntity

@Composable
fun PdfExportDialog(
    folderName: String,
    screenshots: List<ScreenshotEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedIds by remember {
        mutableStateOf(screenshots.map { it.id }.toSet())
    }
    var isExporting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create PDF from Screenshots") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedIds.size} / ${screenshots.size} selected")
                    TextButton(
                        onClick = {
                            if (selectedIds.size == screenshots.size) {
                                selectedIds = emptySet()
                            } else {
                                selectedIds = screenshots.map { it.id }.toSet()
                            }
                        }
                    ) {
                        Text(if (selectedIds.size == screenshots.size) "Deselect All" else "Select All")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(screenshots) { screenshot ->
                        val isSelected = selectedIds.contains(screenshot.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (isSelected) {
                                        selectedIds - screenshot.id
                                    } else {
                                        selectedIds + screenshot.id
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + screenshot.id
                                    } else {
                                        selectedIds - screenshot.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Screenshot #${screenshot.sequenceNumber}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedIds.isNotEmpty() && !isExporting,
                onClick = {
                    isExporting = true
                    val selectedList = screenshots.filter { selectedIds.contains(it.id) }
                    PdfExportHelper.exportScreenshotsToPdf(
                        context = context,
                        folderName = folderName,
                        selectedScreenshots = selectedList,
                        onSuccess = { file ->
                            isExporting = false
                            onDismiss()
                            Toast.makeText(context, "PDF exported successfully!", Toast.LENGTH_SHORT).show()
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "PDF saved to cache: ${file.name}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onError = { e ->
                            isExporting = false
                            Toast.makeText(context, "Failed to export PDF: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            ) {
                Text(if (isExporting) "Generating..." else "Export PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
