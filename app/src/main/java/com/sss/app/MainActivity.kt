package com.sss.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.sss.app.data.local.FolderEntity
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

    val folders by homeViewModel.folders.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        // ---------------------------------------------------------
        // HOME
        // ---------------------------------------------------------

        composable("home") {

            HomeScreen(
                folders = folders,

                onAddFolder = { folderName ->
                    homeViewModel.addFolder(folderName)
                },

                onFolderClick = { folderId, folderName ->
                    navController.navigate(
                        "folder/$folderId/$folderName"
                    )

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
                }
            )
        }

        // ---------------------------------------------------------
        // CAPTURE
        // ---------------------------------------------------------

        composable("capture") {

            CaptureScreen(
                folders = folders,

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
    folders: List<FolderEntity>,
    onAddFolder: (String) -> Unit,
    onFolderClick: (Long, String) -> Unit,
    onStartCapture: () -> Unit
) {

    var showAddFolderDialog by remember {
        mutableStateOf(false)
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
                    items = folders,
                    key = { folder ->
                        folder.id
                    }
                ) { folder ->

                    FolderCard(
                        folder = folder,

                        onClick = {
                            onFolderClick(
                                folder.id,
                                folder.name
                            )
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

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(requiredPermission)
        }
    }

    var selectedFolder by remember {
        mutableStateOf<FolderEntity?>(null)
    }

    var showFolderDialog by remember {
        mutableStateOf(false)
    }
    val activeSession by viewModel.activeSession.collectAsState()

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

                        onBackClick()
                    }
                    .padding(bottom = 30.dp)
            )

            Text(
                text = "Capture Session",

                style =
                    MaterialTheme.typography.titleLarge,

                fontWeight = FontWeight.SemiBold
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

            if (selectedFolder == null) {

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
                            text = selectedFolder!!.name,

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
                    text = "Capture is ready.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Session ID: ${activeSession?.id ?: "None"}"
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Screenshots will be assigned to this folder."
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        viewModel.stopCapture()
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
                                        selectedFolder = folder
                                        viewModel.startCapture(folder.id)
                                        showFolderDialog = false
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

@Composable
fun FolderScreen(
    folderId: Long,
    folderName: String,
    onBackClick: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {

    val screenshots by viewModel
        .screenshots(folderId)
        .collectAsState()

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
                        modifier = Modifier.fillMaxSize(),
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
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = "Screenshots will appear here"
                        )

                        Spacer(
                            modifier = Modifier.height(20.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.addTestScreenshot(folderId)
                            }
                        ) {
                            Text("Add Test Screenshot")
                        }
                    }

                } else {

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        Text(
                            text = "${screenshots.size} screenshots",
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

                            items(
                                items = screenshots,
                                key = { screenshot -> screenshot.id }
                            ) { screenshot ->

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    AsyncImage(
                                        model = screenshot.filePath,
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
}

// ================================================================
// FOLDER CARD
// ================================================================

@Composable
fun FolderCard(
    folder: FolderEntity,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),

            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text = "📁",

                style =
                    MaterialTheme.typography
                        .headlineSmall
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text = folder.name,

                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text = "0 screenshots",

                style =
                    MaterialTheme.typography
                        .bodySmall,

                color = Color.Gray
            )
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