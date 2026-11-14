package com.sss.app

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sss.app.data.settings.SettingsRepository
import com.sss.app.ui.folder.AllScreenshotsViewModel
import com.sss.app.ui.folder.FolderScreen
import com.sss.app.ui.help.HelpScreen
import com.sss.app.ui.home.HomeScreen
import com.sss.app.ui.library.LibraryScreen
import com.sss.app.ui.library.NotesScreen
import com.sss.app.ui.ocr.ExtractedTextScreen
import com.sss.app.ui.onboarding.OnboardingScreen
import com.sss.app.ui.pdf.PdfFlowScreen
import com.sss.app.ui.pdf.PdfLibraryScreen
import com.sss.app.ui.pdf.PdfViewerScreen
import com.sss.app.ui.settings.SettingsScreen
import com.sss.app.ui.theme.Bg
import com.sss.app.ui.theme.BottomTab
import com.sss.app.ui.theme.SSSTheme
import com.sss.app.ui.theme.SssBottomBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private val tabs = listOf(
    BottomTab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    BottomTab("library", "Library", Icons.Outlined.GridView, Icons.Filled.GridView),
    BottomTab("notes", "Notes", Icons.Outlined.Description, Icons.Filled.Description),
    BottomTab("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    private var startRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()          // must be before super.onCreate
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        // keep the (black, themed) splash only until we know where to start - never longer than 1 second
        splash.setKeepOnScreenCondition { startRoute == null }
        lifecycleScope.launch {
            val done = withTimeoutOrNull(1_000) { settingsRepository.settings.first().onboardingDone } ?: true
            startRoute = if (done) "home" else "onboarding"
        }

        setContent {
            SSSTheme {
                val route = startRoute
                if (route != null) SssApp(route)
                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
    }
}

@Composable
fun SssApp(startRoute: String) {
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val showBar = tabs.any { it.route == route }

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (showBar) SssBottomBar(tabs, route, ::goTab) }
    ) { inner ->
        NavHost(
            navController = nav, startDestination = startRoute,
            modifier = Modifier.padding(bottom = if (showBar) inner.calculateBottomPadding() else 0.dp)
        ) {
            composable("onboarding") {
                OnboardingScreen(onDone = {
                    if (nav.previousBackStackEntry != null) nav.popBackStack()
                    else nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                })
            }

            composable("home") {
                HomeScreen(
                    onOpenFolder = { id -> nav.navigate("folder/$id") },
                    onOpenSettings = { goTab("settings") },
                    onOpenPdfs = { nav.navigate("pdfs") }
                )
            }

            composable("library") {
                val homeEntry = remember(it) { nav.getBackStackEntry("home") }
                val allVm: AllScreenshotsViewModel = hiltViewModel(homeEntry)
                LibraryScreen(
                    onOpenFolder = { id -> nav.navigate("folder/$id") },
                    onOpenDocument = { id -> nav.navigate("extracted-text/$id") },
                    allVm = allVm
                )
            }

            composable("notes") {
                NotesScreen(
                    onOpenDocument = { id -> nav.navigate("extracted-text/$id") },
                    onChoose = { goTab("library") },
                    onHelp = { nav.navigate("help") }
                )
            }

            composable("folder/{folderId}") { entry ->
                val folderId = entry.arguments?.getString("folderId")?.toLongOrNull() ?: return@composable
                val homeEntry = remember(entry) { nav.getBackStackEntry("home") }
                val allVm: AllScreenshotsViewModel = hiltViewModel(homeEntry)
                FolderScreen(
                    folderId = folderId,
                    onBack = { nav.popBackStack() },
                    onOpenFolder = { id -> nav.navigate("folder/$id") },
                    onOpenAncestor = { id -> nav.navigate("folder/$id") { popUpTo("home") } },
                    onOpenViewer = { index, sub -> nav.navigate("viewer/$folderId/$index/${if (sub) 1 else 0}") },
                    onOpenDocument = { id -> nav.navigate("extracted-text/$id") },
                    onCreatePdf = { nav.navigate("pdf") },
                    allVm = allVm
                )
            }

            composable("viewer/{folderId}/{index}/{sub}") { entry ->
                val folderId = entry.arguments?.getString("folderId")?.toLongOrNull() ?: return@composable
                val index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0
                val sub = entry.arguments?.getString("sub") == "1"
                val homeEntry = remember(entry) { nav.getBackStackEntry("home") }
                val allVm: AllScreenshotsViewModel = hiltViewModel(homeEntry)
                ImageViewerScreen(
                    folderId = folderId, initialIndex = index, includeSub = sub,
                    onBack = { nav.popBackStack() },
                    onOpenDocument = { id -> nav.navigate("extracted-text/$id") },
                    allScreenshotsViewModel = allVm
                )
            }

            composable("extracted-text/{documentId}") { entry ->
                val id = entry.arguments?.getString("documentId")?.toLongOrNull() ?: return@composable
                ExtractedTextScreen(documentId = id, onBackClick = { nav.popBackStack() })
            }

            composable("pdf") {
                PdfFlowScreen(
                    onBack = { nav.popBackStack() },
                    onViewPdf = { id -> nav.navigate("pdf-viewer/$id") { popUpTo("pdf") { inclusive = true } } },
                    onOpenLibrary = { nav.navigate("pdfs") { popUpTo("pdf") { inclusive = true } } }
                )
            }

            composable("pdfs") {
                PdfLibraryScreen(
                    onBack = { nav.popBackStack() },
                    onOpenPdf = { id -> nav.navigate("pdf-viewer/$id") }
                )
            }

            composable(
                "pdf-viewer/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) {
                PdfViewerScreen(onBack = { nav.popBackStack() })
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenPermissions = { nav.navigate("onboarding") },
                    onOpenHelp = { nav.navigate("help") }
                )
            }

            composable("help") {
                HelpScreen(onBack = { nav.popBackStack() }, onOpenPermissions = { nav.navigate("onboarding") })
            }
        }
    }
}