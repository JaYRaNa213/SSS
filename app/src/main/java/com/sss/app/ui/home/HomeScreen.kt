package com.sss.app.ui.home

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.sss.app.R
import com.sss.app.capture.OverlayBubbleService
import com.sss.app.data.local.FolderWithCount
import com.sss.app.data.repository.FolderResult
import com.sss.app.ui.capture.CaptureViewModel
import com.sss.app.ui.folder.AllScreenshotsViewModel
import com.sss.app.ui.permissions.PermissionExplainDialog
import com.sss.app.ui.permissions.PermissionHelper
import com.sss.app.ui.permissions.PermissionType
import com.sss.app.ui.theme.*
import com.sss.app.util.DateLabels
import com.sss.app.util.findActivity
import com.sss.app.util.toImageModel
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenFolder: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    captureViewModel: CaptureViewModel = hiltViewModel(),
    allVm: AllScreenshotsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val folders by homeViewModel.foldersWithCount.collectAsState()
    val session by captureViewModel.activeSession.collectAsState()
    val others by allVm.allScreenshots.collectAsState()
    val settings by homeViewModel.settings.collectAsState()
    val today by homeViewModel.todayCount.collectAsState()
    val isActive = session?.isActive == true

    var photos by remember { mutableStateOf(PermissionHelper.photosFull(context)) }
    var showCreate by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf<PermissionType?>(null) }
    var pendingStart by remember { mutableStateOf<Long?>(null) }
    var photoPermanent by remember { mutableStateOf(false) }
    var overlayAsked by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(session) {
        val s = session
        if (s != null && s.isActive) OverlayBubbleService.showBubble(context)
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
            !Settings.canDrawOverlays(context) && !overlayAsked -> { overlayAsked = true; pendingStart = id; explain = PermissionType.OVERLAY }
            else -> startNow(id)
        }
    }

    val activeFolder = folders.firstOrNull { it.folder.id == session?.folderId }
    val greeting = remember {
        val h = LocalTime.now().hour
        if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.statusBars) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Accent), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), "4S logo",
                              Modifier.size(46.dp).graphicsLayer(scaleX = 1.2f, scaleY = 1.2f))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(greeting, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                        Text("Your study shots", style = MaterialTheme.typography.titleLarge)
                    }
                    RoundIconButton(Icons.Default.Settings, "Settings", onOpenSettings)
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                if (isActive) HeroActive(
                    folderName = activeFolder?.folder?.name.orEmpty(),
                    count = activeFolder?.screenshotCount ?: 0, today = today, paused = settings.capturePaused,
                    onStop = { captureViewModel.stopCapture(); OverlayBubbleService.hideBubble(context) },
                    onPause = homeViewModel::togglePause,
                    onSwitch = { showPicker = true }
                ) else HeroIdle(onStart = { if (folders.isEmpty()) showCreate = true else showPicker = true })
            }

            if (!photos) item {
                Spacer(Modifier.height(12.dp))
                PhotoBanner(onFix = { explain = PermissionType.PHOTOS })
            }

            item {
                Spacer(Modifier.height(14.dp))
                UnsortedCard(count = others.size, onOpen = { onOpenFolder(-1L) })
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Folders", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Row(Modifier.clip(CircleShape).clickable { showCreate = true }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CreateNewFolder, null, tint = Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New", color = Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (folders.isEmpty()) item { EmptyFolders(onCreate = { showCreate = true }) }
            else items(folders, key = { it.folder.id }) { item ->
                FolderCard(item, active = isActive && item.folder.id == session?.folderId, onOpen = { onOpenFolder(item.folder.id) })
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showPicker) FolderPickerSheet(
        title = "Save screenshots to",
        items = folders.map { PickItem(it.folder.id, it.folder.name, it.folder.colorIndex, it.screenshotCount) },
        activeId = if (isActive) session?.folderId else null,
        onPick = { id -> showPicker = false; beginCapture(id) },
        onNew = { showPicker = false; showCreate = true },
        onDismiss = { showPicker = false }
    )

    if (showCreate) NewFolderSheet(
        existing = folders.map { it.folder },
        onCreate = { name, color, start ->
            showCreate = false
            homeViewModel.createFolder(name, color) { r -> if (start && r is FolderResult.Success) beginCapture(r.id) }
        },
        onDismiss = { showCreate = false }
    )

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
                if (type == PermissionType.OVERLAY && p != null) startNow(p)
            }
        )
    }
}

@Composable
private fun HeroActive(
    folderName: String, count: Int, today: Int, paused: Boolean,
    onStop: () -> Unit, onPause: () -> Unit, onSwitch: () -> Unit
) {
    val tint = AccentOn.copy(alpha = 0.12f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Accent).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(CircleShape).background(tint).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(AccentOn))
                Spacer(Modifier.width(8.dp))
                Text(if (paused) "PAUSED" else "CAPTURING", color = AccentOn, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint).clickable(onClick = onStop), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Stop, "Stop capture", tint = AccentOn)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Saving new screenshots to", color = AccentOn.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        Text(folderName, color = AccentOn, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("$count saved", color = AccentOn.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
            Text("$today today", color = AccentOn.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroButton(if (paused) "Resume" else "Pause", if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, tint, AccentOn, AccentOn, onPause, Modifier.weight(1f))
            HeroButton("Switch", Icons.Default.SwapHoriz, AccentOn, TextPrimary, Accent, onSwitch, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroButton(label: String, icon: ImageVector, container: Color, content: Color, iconTint: Color, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.height(50.dp).clip(CircleShape).background(container).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = content, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun HeroIdle(onStart: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Surface1).border(1.dp, Line, RoundedCornerShape(28.dp)).padding(20.dp)) {
        Row(Modifier.clip(CircleShape).background(Surface3).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(TextMuted))
            Spacer(Modifier.width(8.dp))
            Text("NOT CAPTURING", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em)
        }
        Spacer(Modifier.height(16.dp))
        Text("Pick a folder, then start", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))
        SssPrimaryButton("Start capture", onClick = onStart, icon = Icons.Default.PlayArrow)
    }
}

@Composable
private fun UnsortedCard(count: Int, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().dashedBorder(Line, 20.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Inbox, null, tint = TextSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (count == 0) "Nothing unsorted" else "$count unsorted", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(if (count == 0) "New screenshots show up here" else "Tap to file them into a folder", color = TextMuted, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
    }
}

@Composable
private fun FolderCard(item: FolderWithCount, active: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface1).clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThumbStack(item.latestScreenshotPath, item.secondScreenshotPath)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(folderColor(item.folder.colorIndex), 9.dp)
                Spacer(Modifier.width(8.dp))
                Text(item.folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text("${item.screenshotCount} shots \u00B7 ${DateLabels.updated(item.lastUpdatedAt)}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (active) StatusPill("Active") else Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
    }
}

@Composable
private fun ThumbStack(first: String?, second: String?) {
    Box(Modifier.size(width = 72.dp, height = 52.dp)) {
        if (second != null) Thumb(second, Modifier.align(Alignment.CenterEnd))
        Thumb(first, Modifier.align(Alignment.CenterStart))
    }
}

@Composable
private fun Thumb(path: String?, modifier: Modifier) {
    Box(modifier.size(width = 42.dp, height = 52.dp).clip(RoundedCornerShape(11.dp)).background(Surface3).border(1.5.dp, Surface1, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        if (path != null) AsyncImage(path.toImageModel(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Folder, null, tint = TextMuted)
    }
}

@Composable
private fun PhotoBanner(onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Photo access needed to sort screenshots", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        AccentPill("Fix", onFix)
    }
}

@Composable
private fun EmptyFolders(onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Surface1), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CreateNewFolder, null, tint = Accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Create your first folder", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("One folder per subject, then start capture.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        AccentPill("Create folder", onCreate, icon = Icons.Outlined.CreateNewFolder)
    }
}