package com.sss.app.ui.home

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.sss.app.R
import com.sss.app.capture.OverlayBubbleService
import com.sss.app.data.local.FolderWithCount
import com.sss.app.ui.capture.CaptureViewModel
import com.sss.app.ui.folder.AllScreenshotsViewModel
import com.sss.app.ui.permissions.PermissionExplainDialog
import com.sss.app.ui.permissions.PermissionHelper
import com.sss.app.ui.permissions.PermissionType
import com.sss.app.ui.theme.Bg
import com.sss.app.ui.theme.GhostButton
import com.sss.app.ui.theme.Lime
import com.sss.app.ui.theme.Line
import com.sss.app.ui.theme.LineSoft
import com.sss.app.ui.theme.MonoLabel
import com.sss.app.ui.theme.SssPrimaryButton
import com.sss.app.ui.theme.Surface1
import com.sss.app.ui.theme.Surface2
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.TextPrimary
import com.sss.app.ui.theme.TextSecondary
import com.sss.app.ui.theme.dashedBorder
import com.sss.app.util.DateLabels
import com.sss.app.util.findActivity
import com.sss.app.util.toImageModel

private enum class Dot { NONE, SELECTED, ACTIVE }

@Composable
fun HomeScreen(
    onOpenFolder: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenOnboarding: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    captureViewModel: CaptureViewModel = hiltViewModel(),
    allVm: AllScreenshotsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val folders by homeViewModel.foldersWithCount.collectAsState()
    val session by captureViewModel.activeSession.collectAsState()
    val others by allVm.allScreenshots.collectAsState()
    val settings by homeViewModel.settings.collectAsState()
    val isActive = session?.isActive == true

    var photos by remember { mutableStateOf(PermissionHelper.photosFull(context)) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf<PermissionType?>(null) }
    var pendingStart by remember { mutableStateOf<Long?>(null) }
    var photoPermanent by remember { mutableStateOf(false) }
    var overlayAsked by rememberSaveable { mutableStateOf(false) }

    // Refresh permission state + "Other" list every time the app comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                photos = PermissionHelper.photosFull(context)
                if (photos) allVm.loadAllScreenshots()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Keep the selection in sync with the running session and make sure the service (observer + bubble) runs.
    LaunchedEffect(session) {
        val s = session
        if (s != null && s.isActive) {
            selectedId = s.folderId
            OverlayBubbleService.showBubble(context)
        }
    }

    fun startNow(id: Long) {
        captureViewModel.startCapture(id)
        OverlayBubbleService.showBubble(context)
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        photos = PermissionHelper.photosFull(context)
        if (photos) {
            allVm.loadAllScreenshots()
            pendingStart?.let { id -> pendingStart = null; startNow(id) }
        } else {
            val act = context.findActivity()
            photoPermanent = act != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(act, PermissionHelper.photoPermissions().first())
        }
    }

    fun beginCapture(id: Long) {
        when {
            !PermissionHelper.photosFull(context) -> { pendingStart = id; explain = PermissionType.PHOTOS }
            !Settings.canDrawOverlays(context) && !overlayAsked -> {
                overlayAsked = true; pendingStart = id; explain = PermissionType.OVERLAY
            }
            else -> startNow(id)
        }
    }

    val selectedFolder = folders.firstOrNull { it.folder.id == selectedId }
    val activeFolder = folders.firstOrNull { it.folder.id == session?.folderId }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp)) {
            HomeHeader(onHelp = onOpenHelp, onSettings = onOpenSettings)

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item {
                    Spacer(Modifier.height(22.dp))
                    CaptureCard(
                        active = isActive,
                        paused = settings.capturePaused,
                        folderName = activeFolder?.folder?.name,
                        count = activeFolder?.screenshotCount ?: 0,
                        onTogglePause = homeViewModel::togglePause
                    )
                }
                if (!photos) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        PhotoBanner(onFix = { explain = PermissionType.PHOTOS })
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    MonoLabel("Folders")
                    Spacer(Modifier.height(4.dp))
                }
                if (folders.isEmpty()) {
                    item { EmptyFolders(onCreate = { showCreate = true }) }
                } else {
                    items(folders, key = { it.folder.id }) { item ->
                        val dot = when {
                            isActive && item.folder.id == session?.folderId -> Dot.ACTIVE
                            item.folder.id == selectedId -> Dot.SELECTED
                            else -> Dot.NONE
                        }
                        FolderRow(
                            item = item,
                            dot = dot,
                            onOpen = { onOpenFolder(item.folder.id) },
                            onSelect = {
                                if (isActive) { selectedId = item.folder.id; captureViewModel.startCapture(item.folder.id) }
                                else selectedId = if (selectedId == item.folder.id) null else item.folder.id
                            }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    OtherRow(count = others.size, onOpen = { onOpenFolder(-1L) })
                    Spacer(Modifier.height(12.dp))
                    GhostButton("+ New folder", onClick = { showCreate = true }, dashed = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            when {
                isActive -> SssPrimaryButton("Stop capture", danger = true, onClick = {
                    captureViewModel.stopCapture()
                    OverlayBubbleService.hideBubble(context)
                })
                selectedFolder != null -> SssPrimaryButton(
                    "Start capture in ${selectedFolder.folder.name}",
                    onClick = { beginCapture(selectedFolder.folder.id) }
                )
                else -> SssPrimaryButton("Select a folder", enabled = false, onClick = {})
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCreate) {
        FolderNameDialog(
            title = "Create new folder",
            confirmLabel = "Create",
            existing = folders.map { it.folder },
            onConfirm = { name -> showCreate = false; homeViewModel.createFolder(name) { } },
            onDismiss = { showCreate = false }
        )
    }

    explain?.let { type ->
        PermissionExplainDialog(
            type = type,
            permanentlyDenied = type == PermissionType.PHOTOS && photoPermanent,
            onConfirm = {
                explain = null
                when (type) {
                    PermissionType.PHOTOS ->
                        if (photoPermanent) PermissionHelper.openAppSettings(context)
                        else photoLauncher.launch(PermissionHelper.photoPermissions())
                    PermissionType.OVERLAY -> PermissionHelper.openOverlaySettings(context)
                    else -> {}
                }
            },
            onDismiss = {
                explain = null
                val p = pendingStart
                pendingStart = null
                // "Not now" for the floating button must not block capture.
                if (type == PermissionType.OVERLAY && p != null) startNow(p)
            }
        )
    }
}

@Composable
private fun HomeHeader(onHelp: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(TextPrimary),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "4S logo", modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onHelp) { Icon(Icons.Outlined.Info, contentDescription = "Help") }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun CaptureCard(active: Boolean, paused: Boolean, folderName: String?, count: Int, onTogglePause: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(20.dp)).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (active && !paused) Lime else TextMuted))
            Spacer(Modifier.width(8.dp))
            MonoLabel(
                when { !active -> "Not capturing"; paused -> "Paused"; else -> "Capturing now" }
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (active) (folderName ?: "") else "Pick a folder, then start",
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1
        )
        if (active) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MonoLabel("$count saved", color = if (paused) TextMuted else Lime)
                GhostButton(if (paused) "Resume" else "Pause", onClick = onTogglePause)
            }
        }
    }
}

@Composable
private fun PhotoBanner(onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Photo access needed to sort screenshots", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        GhostButton("Fix", onClick = onFix)
    }
}

@Composable
private fun FolderRow(item: FolderWithCount, dot: Dot, onOpen: () -> Unit, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            val path = item.latestScreenshotPath
            if (path != null) {
                AsyncImage(
                    model = path.toImageModel(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Folder, contentDescription = null, tint = TextMuted)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                "${item.screenshotCount} shots \u00B7 ${DateLabels.updated(item.lastUpdatedAt)}",
                style = MaterialTheme.typography.bodySmall, color = TextMuted
            )
        }
        Box(
            Modifier.size(48.dp).clickable(onClick = onSelect),
            contentAlignment = Alignment.Center
        ) {
            when (dot) {
                Dot.ACTIVE -> Box(Modifier.size(14.dp).clip(CircleShape).background(Lime))
                Dot.SELECTED -> Box(Modifier.size(14.dp).clip(CircleShape).border(3.dp, TextPrimary, CircleShape))
                Dot.NONE -> Box(Modifier.size(14.dp).clip(CircleShape).border(1.5.dp, Line, CircleShape))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LineSoft))
}

@Composable
private fun OtherRow(count: Int, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().dashedBorder(Line, 14.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).dashedBorder(Line, 10.dp), contentAlignment = Alignment.Center) { MonoLabel("auto") }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Other", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text("$count not sorted yet", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
private fun EmptyFolders(onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(40.dp), tint = TextMuted)
        Spacer(Modifier.height(12.dp))
        Text("Create your first folder", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("1  Create a folder", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text("2  Tap Start capture", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text("3  Take screenshots in any app", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        GhostButton("Create folder", onClick = onCreate)
    }
}