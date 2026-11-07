package com.sss.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.sss.app.data.local.FolderEntity
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.data.local.StudyDocumentEntity
import com.sss.app.data.repository.FolderRepository
import com.sss.app.data.repository.ScreenshotRepository
import com.sss.app.data.repository.StudyDocumentRepository
import com.sss.app.ui.folder.AllScreenshotsViewModel
import com.sss.app.ui.theme.*
import com.sss.app.util.DateLabels
import com.sss.app.util.toImageModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    folderRepository: FolderRepository,
    screenshotRepository: ScreenshotRepository,
    studyDocumentRepository: StudyDocumentRepository
) : ViewModel() {
    val folders: StateFlow<List<FolderEntity>> =
        folderRepository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shots: StateFlow<List<ScreenshotEntity>> =
        screenshotRepository.getAllScreenshotsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val docs: StateFlow<List<StudyDocumentEntity>> =
        studyDocumentRepository.allDocuments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun LibraryScreen(
    onOpenFolder: (Long) -> Unit,
    onOpenDocument: (Long) -> Unit,
    allVm: AllScreenshotsViewModel,
    vm: LibraryViewModel = hiltViewModel()
) {
    val folders by vm.folders.collectAsState()
    val shots by vm.shots.collectAsState()
    val docs by vm.docs.collectAsState()
    val others by allVm.allScreenshots.collectAsState()
    LaunchedEffect(Unit) { allVm.loadAllScreenshots() }

    var filter by remember { mutableLongStateOf(-2L) }   // -2 all, -1 unsorted, else folder id
    var query by remember { mutableStateOf("") }

    val all = remember(shots, others) { (shots + others).sortedByDescending { it.capturedAt } }
    val visible = remember(all, filter) { if (filter == -2L) all else all.filter { it.folderId == filter } }
    val hits = remember(docs, query) {
        if (query.isBlank()) emptyList() else docs.filter { it.title.contains(query, true) || it.content.contains(query, true) }
    }

    Scaffold(containerColor = Bg, contentWindowInsets = WindowInsets.statusBars) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Library", style = MaterialTheme.typography.headlineMedium)
                MonoLabel("${all.size} screenshots on this device")
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search text in your notes") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                shape = RoundedCornerShape(22.dp), colors = sssFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterPill("All", filter == -2L, { filter = -2L }) }
                item { FilterPill("Unsorted \u00B7 ${others.size}", filter == -1L, { filter = -1L }) }
                items(folders, key = { it.id }) { f -> FilterPill(f.name, filter == f.id, { filter = f.id }) }
            }
            Spacer(Modifier.height(12.dp))

            if (query.isNotBlank()) {
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hits.isEmpty()) item { Text("No notes match \"$query\".", color = TextMuted) }
                    items(hits, key = { it.id }) { NoteCard(it) { onOpenDocument(it.id) } }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visible, key = { "${it.folderId}-${it.id}" }) { s ->
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Surface2).clickable { onOpenFolder(s.folderId) }) {
                            AsyncImage(s.filePath.toImageModel(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (s.folderId == -1L) {
                                Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Accent).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    Text("Unsorted", color = AccentOn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class NotesViewModel @Inject constructor(repo: StudyDocumentRepository) : ViewModel() {
    val docs: StateFlow<List<StudyDocumentEntity>> =
        repo.allDocuments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
private fun NoteCard(doc: StudyDocumentEntity, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface1).clickable(onClick = onClick).padding(16.dp)) {
        Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        MonoLabel("${doc.sourceImageCount} screenshots \u00B7 ${DateLabels.dayLabel(DateLabels.day(doc.updatedAt))}")
        Spacer(Modifier.height(8.dp))
        Text(doc.content.lineSequence().filter { it.isNotBlank() && !it.startsWith("---") }.take(3).joinToString(" "), color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NotesScreen(onOpenDocument: (Long) -> Unit, onChoose: () -> Unit, onHelp: () -> Unit, vm: NotesViewModel = hiltViewModel()) {
    val docs by vm.docs.collectAsState()
    Scaffold(containerColor = Bg, contentWindowInsets = WindowInsets.statusBars) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Notes", style = MaterialTheme.typography.headlineMedium)
                MonoLabel("Text you extract appears here")
            }
            if (docs.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(Modifier.size(120.dp).clip(RoundedCornerShape(34.dp)).background(Surface1), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Description, null, tint = TextMuted, modifier = Modifier.size(54.dp))
                        }
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.DocumentScanner, null, tint = AccentOn)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("No notes yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Pick a few screenshots and 4S will turn them into clean, editable study notes.", color = TextMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    AccentPill("Choose screenshots", onChoose)
                    TextButton(onClick = onHelp) { Text("How it works", color = TextMuted) }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(docs, key = { it.id }) { NoteCard(it) { onOpenDocument(it.id) } }
                }
            }
        }
    }
}