package com.sss.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.sss.app.capture.OverlayBubbleService
import com.sss.app.data.local.FolderEntity
import com.sss.app.data.local.FolderWithCount
import com.sss.app.data.local.ScreenshotEntity
import com.sss.app.ui.capture.CaptureViewModel
import com.sss.app.ui.folder.FolderViewModel
import com.sss.app.ui.home.HomeViewModel
import com.sss.app.ui.theme.SSSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SSSTheme {
                SssApp()
            }
        }
    }
}

@Composable
fun SssApp() {

    val navController = rememberNavController()

    val homeViewModel: HomeViewModel = hiltViewModel()

    val foldersWithCount by homeViewModel.foldersWithCount.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        // ---------------------------------------------------------
        // HOME
        // ---------------------------------------------------------

        composable("home") {

            HomeScreen(
                foldersWithCount = foldersWithCount,

                onAddFolder = { folderName ->
                    homeViewModel.addFolder(folderName)
                },

                onFolderClick = { folderId, folderName ->
                    navController.navigate("folder/$folderId/$folderName")
                },

                onFolderDelete = { folder ->
                    homeViewModel.deleteFolder(folder)
                },

                onStartCapture = {
                    navController.navigate("capture")
                }
            )
        }

        // ---------------------------------------------------------
        // FOLDER
        // ---------------------------------------------------------

        composable("folder/{folderId}/{folderName}") { backStackEntry ->
            val folderId = backStackEntry.arguments
                ?.getString("folderId")
                ?.toLongOrNull()
                ?: return@composable

            val folderName =
                backStackEntry.arguments
                    ?.getString("folderName")
                    ?: "Folder"

            FolderScreen(
                folderId = folderId,
                folderName = folderName,

                onBackClick = {
                    navController.popBackStack()
                },

                onScreenshotClick = { index ->
                    navController.navigate("viewer/$folderId/$index")
                },

                onStartCaptureForFolder = {
                    navController.navigate("capture")
                }
            )
        }

        // ---------------------------------------------------------
        // IMAGE VIEWER
        // ---------------------------------------------------------

        composable("viewer/{folderId}/{initialIndex}") { backStackEntry ->
            val folderId = backStackEntry.arguments
                ?.getString("folderId")
                ?.toLongOrNull()
                ?: return@composable

            val initialIndex = backStackEntry.arguments
                ?.getString("initialIndex")
                ?.toIntOrNull()
                ?: 0

            ImageViewerScreen(
                folderId = folderId,
                initialIndex = initialIndex,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        // ---------------------------------------------------------
        // CAPTURE
        // ---------------------------------------------------------

        composable("capture") {

            CaptureScreen(
                folders = foldersWithCount.map { it.folder },

                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}


// ================================================================
// HOME SCREEN
// ================================================================

@Composable
fun HomeScreen(
    foldersWithCount: List<FolderWithCount>,
    onAddFolder: (String) -> Unit,
    onFolderClick: (Long, String) -> Unit,
    onFolderDelete: (FolderEntity) -> Unit,
    onStartCapture: () -> Unit
) {

    var showAddFolderDialog by remember {
        mutableStateOf(false)
    }

    var folderToDelete by remember {
        mutableStateOf<FolderWithCount?>(null)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SSS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text = "My Folders",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),

                modifier = Modifier.weight(1f),

                contentPadding = PaddingValues(
                    bottom = 16.dp
                ),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp),

                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                items(
                    items = foldersWithCount,
                    key = { item -> item.folder.id }
                ) { item ->

                    FolderCard(
                        folderWithCount = item,

                        onClick = {
                            onFolderClick(
                                item.folder.id,
                                item.folder.name
                            )
                        },

                        onLongClick = {
                            folderToDelete = item
                        }
                    )
                }

                item {

                    AddFolderCard(
                        onClick = {
                            showAddFolderDialog = true
                        }
                    )
                }
            }

            Button(
                onClick = onStartCapture,

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .height(52.dp)
            ) {

                Text(
                    text = "Start Capture"
                )
            }
        }
    }

    if (showAddFolderDialog) {

        AddFolderDialog(

            onDismiss = {
                showAddFolderDialog = false
            },

            onCreate = { folderName ->

                onAddFolder(folderName)

                showAddFolderDialog = false
            }
        )
    }

    if (folderToDelete != null) {
        val target = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete folder?") },
            text = {
                Text("This folder contains ${target.screenshotCount} screenshot${if (target.screenshotCount == 1) "" else "s"}. " +
                        "Deleting this folder will remove all its saved screenshots from SSS.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onFolderDelete(target.folder)
                        folderToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { folderToDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


// ================================================================
// CAPTURE SCREEN
// ================================================================

@Composable
fun CaptureScreen(
    folders: List<FolderEntity>,
    onBackClick: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {

    val context = LocalContext.current
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                requiredPermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    var selectedFolder by remember {
        mutableStateOf<FolderEntity?>(null)
    }

    var showFolderDialog by remember {
        mutableStateOf(false)
    }

    var pendingFolder by remember {
        mutableStateOf<FolderEntity?>(null)
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Settings.canDrawOverlays(context)) {
            pendingFolder?.let { folder ->
                selectedFolder = folder
                viewModel.startCapture(folder.id)
                OverlayBubbleService.showBubble(context)
                pendingFolder = null
                showFolderDialog = false
            }
        } else {
            pendingFolder = null
        }
    }
    val activeSession by viewModel.activeSession.collectAsState()
    val activeSessionCount by viewModel.activeSessionScreenshotCount.collectAsState()

    LaunchedEffect(activeSession, folders) {
        if (activeSession != null && selectedFolder == null) {
            selectedFolder = folders.find { it.id == activeSession!!.folderId }
        }
        if (activeSession != null && Settings.canDrawOverlays(context)) {
            OverlayBubbleService.showBubble(context)
        }
    }

    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {

            Text(
                text = "←  Capture",

                style =
                    MaterialTheme.typography.headlineSmall,

                fontWeight = FontWeight.Bold,

                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (activeSession != null) {
                            viewModel.stopCapture()
                            OverlayBubbleService.hideBubble(context)
                        }
                        onBackClick()
                    }
                    .padding(bottom = 30.dp)
            )

            Text(
                text = if (activeSession != null) "Capture Active" else "Capture Session",

                style =
                    MaterialTheme.typography.titleLarge,

                fontWeight = FontWeight.SemiBold,
                color = if (activeSession != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            if (!hasPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Storage Permission Required",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Storage permission is required to detect screenshots taken on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(requiredPermission)
                            }
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            if (selectedFolder == null && activeSession == null) {

                Text(
                    text = "No folder selected",

                    style =
                        MaterialTheme.typography.titleMedium
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Select a folder to begin capturing screenshots."
                )

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Button(
                    onClick = {

                        showFolderDialog = true
                    }
                ) {

                    Text(
                        text = "Select Folder"
                    )
                }

            } else {

                val folderName = selectedFolder?.name ?: "Selected Folder"

                Text(
                    text = "Active Folder",

                    style =
                        MaterialTheme.typography.titleMedium
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),

                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation = 3.dp
                        )
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text = "📁",

                            style =
                                MaterialTheme.typography
                                    .headlineSmall
                        )

                        Text(
                            text = folderName,

                            modifier =
                                Modifier.padding(
                                    start = 12.dp
                                ),

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Text(
                    text = if (activeSession != null) "🟢 Capture is active in background" else "Capture is ready.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Screenshots captured: $activeSessionCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Screenshots taken anywhere on your device will automatically be saved into this folder."
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        viewModel.stopCapture()
                        OverlayBubbleService.hideBubble(context)
                        selectedFolder = null
                        onBackClick()
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {

                    Text(
                        text = "Stop Capture"
                    )
                }
            }
        }
    }

    if (showFolderDialog) {

        AlertDialog(

            onDismissRequest = {

                showFolderDialog = false
            },

            title = {

                Text(
                    text = "Select Folder"
                )
            },

            text = {

                Column {

                    if (folders.isEmpty()) {

                        Text(
                            text = "No folders available."
                        )

                    } else {

                        folders.forEach { folder ->

                            Text(
                                text = "📁  ${folder.name}",

                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (Settings.canDrawOverlays(context)) {
                                            selectedFolder = folder
                                            viewModel.startCapture(folder.id)
                                            showFolderDialog = false
                                            OverlayBubbleService.showBubble(context)
                                        } else {
                                            pendingFolder = folder
                                            showFolderDialog = false
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            overlayPermissionLauncher.launch(intent)
                                        }
                                    }
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        showFolderDialog = false
                    }
                ) {

                    Text(
                        text = "Cancel"
                    )
                }
            }
        )
    }
}


// ================================================================
// FOLDER SCREEN
// ================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderScreen(
    folderId: Long,
    folderName: String,
    onBackClick: () -> Unit,
    onScreenshotClick: (Int) -> Unit,
    onStartCaptureForFolder: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {

    val screenshotsFlow = remember(folderId) {
        viewModel.screenshots(folderId)
    }
    val screenshots by screenshotsFlow.collectAsState()

    var screenshotToDelete by remember {
        mutableStateOf<ScreenshotEntity?>(null)
    }

    var screenshotForAction by remember {
        mutableStateOf<Pair<ScreenshotEntity, Int>?>(null)
    }

    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Text(
                text = "←  $folderName",

                style = MaterialTheme.typography.headlineSmall,

                fontWeight = FontWeight.Bold,

                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onBackClick()
                    }
                    .padding(20.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {

                if (screenshots.isEmpty()) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Text(
                            text = "📷",
                            style = MaterialTheme.typography.displaySmall
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text = "No screenshots yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = "Start a capture session and take screenshots to add them to this folder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )

                        Spacer(
                            modifier = Modifier.height(24.dp)
                        )

                        Button(
                            onClick = onStartCaptureForFolder
                        ) {
                            Text("Start Capture")
                        }
                    }

                } else {

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        Text(
                            text = "${screenshots.size} screenshot${if (screenshots.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            itemsIndexed(
                                items = screenshots,
                                key = { _, screenshot -> screenshot.id }
                            ) { index, screenshot ->

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .combinedClickable(
                                            onClick = {
                                                onScreenshotClick(index)
                                            },
                                            onLongClick = {
                                                screenshotForAction = Pair(screenshot, index)
                                            }
                                        ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    val imageModel = remember(screenshot.filePath) {
                                        if (screenshot.filePath.startsWith("content://") || screenshot.filePath.startsWith("file://")) {
                                            Uri.parse(screenshot.filePath)
                                        } else {
                                            screenshot.filePath
                                        }
                                    }

                                    AsyncImage(
                                        model = imageModel,
                                        contentDescription = "Screenshot #${screenshot.sequenceNumber}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (screenshotForAction != null) {
        val (screenshot, index) = screenshotForAction!!
        AlertDialog(
            onDismissRequest = { screenshotForAction = null },
            title = { Text("Screenshot #${screenshot.sequenceNumber}") },
            text = { Text("Choose an action for this screenshot.") },
            confirmButton = {
                Button(
                    onClick = {
                        screenshotForAction = null
                        onScreenshotClick(index)
                    }
                ) {
                    Text("View")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        screenshotForAction = null
                        screenshotToDelete = screenshot
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (screenshotToDelete != null) {
        val target = screenshotToDelete!!
        AlertDialog(
            onDismissRequest = { screenshotToDelete = null },
            title = { Text("Delete screenshot?") },
            text = { Text("This screenshot will be removed from this folder.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteScreenshot(target)
                        screenshotToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { screenshotToDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


// ================================================================
// IMAGE VIEWER SCREEN
// ================================================================

@Composable
fun ImageViewerScreen(
    folderId: Long,
    initialIndex: Int,
    onBackClick: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {

    val screenshotsFlow = remember(folderId) {
        viewModel.screenshots(folderId)
    }
    val screenshots by screenshotsFlow.collectAsState()

    var screenshotToDelete by remember { mutableStateOf<ScreenshotEntity?>(null) }

    val validIndex = if (screenshots.isNotEmpty()) {
        initialIndex.coerceIn(0, screenshots.size - 1)
    } else {
        0
    }

    Log.d("SSS_VIEWER", "viewer composition index=$validIndex")

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

            val screenshot = screenshots.getOrNull(validIndex)

            if (screenshot != null) {
                val imageModel = remember(screenshot.filePath) {
                    if (screenshot.filePath.startsWith("content://") || screenshot.filePath.startsWith("file://")) {
                        Uri.parse(screenshot.filePath)
                    } else {
                        screenshot.filePath
                    }
                }

                Log.d("SSS_VIEWER", "loading URI=${screenshot.filePath}")

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Screenshot #${screenshot.sequenceNumber}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = {
                            Log.d("SSS_VIEWER", "IMAGE SUCCESS URI=${screenshot.filePath}")
                        },
                        onError = { errorState ->
                            Log.e(
                                "SSS_VIEWER",
                                "IMAGE ERROR URI=${screenshot.filePath}",
                                errorState.result.throwable
                            )
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "← Back",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onBackClick() }
                        .padding(8.dp)
                )

                if (screenshots.isNotEmpty()) {
                    Text(
                        text = "${validIndex + 1} / ${screenshots.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "🗑 Delete",
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            val currentScreenshot = screenshots.getOrNull(validIndex)
                            if (currentScreenshot != null) {
                                screenshotToDelete = currentScreenshot
                            }
                        }
                        .padding(8.dp)
                )
            }
        }
    }

    if (screenshotToDelete != null) {
        val target = screenshotToDelete!!
        AlertDialog(
            onDismissRequest = { screenshotToDelete = null },
            title = { Text("Delete screenshot?") },
            text = { Text("This screenshot will be removed from this folder.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteScreenshot(target)
                        screenshotToDelete = null
                        onBackClick()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { screenshotToDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


// ================================================================
// FOLDER CARD
// ================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    folderWithCount: FolderWithCount,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 3.dp
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (!folderWithCount.latestScreenshotPath.isNullOrEmpty()) {
                val thumbnailModel = remember(folderWithCount.latestScreenshotPath) {
                    val path = folderWithCount.latestScreenshotPath
                    if (path != null && (path.startsWith("content://") || path.startsWith("file://"))) {
                        Uri.parse(path)
                    } else {
                        path
                    }
                }

                Card(
                    modifier = Modifier
                        .size(56.dp)
                        .aspectRatio(1f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    AsyncImage(
                        model = thumbnailModel,
                        contentDescription = folderWithCount.folder.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Text(
                    text = "📁",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = folderWithCount.folder.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${folderWithCount.screenshotCount} screenshot${if (folderWithCount.screenshotCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}


// ================================================================
// ADD FOLDER CARD
// ================================================================

@Composable
fun AddFolderCard(
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable {

                onClick()
            },

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 3.dp
            )
    ) {

        Box(
            modifier =
                Modifier.fillMaxSize(),

            contentAlignment =
                Alignment.Center
        ) {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    text = "+",

                    style =
                        MaterialTheme.typography
                            .headlineMedium
                )

                Text(
                    text = "Add Folder",

                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
    }
}


// ================================================================
// ADD FOLDER DIALOG
// ================================================================

@Composable
fun AddFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {

    var folderName by remember {
        mutableStateOf("")
    }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {

            Text(
                text = "Create New Folder"
            )
        },

        text = {

            OutlinedTextField(

                value = folderName,

                onValueChange = {

                    folderName = it
                },

                label = {

                    Text(
                        text = "Folder name"
                    )
                },

                singleLine = true
            )
        },

        confirmButton = {

            Button(

                onClick = {

                    val name =
                        folderName.trim()

                    if (name.isNotEmpty()) {

                        onCreate(name)
                    }
                }
            ) {

                Text(
                    text = "Create"
                )
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    text = "Cancel"
                )
            }
        }
    )
}