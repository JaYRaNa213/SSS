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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sss.app.data.settings.SettingsRepository
import com.sss.app.ui.folder.AllScreenshotsViewModel
import com.sss.app.ui.folder.FolderScreen
import com.sss.app.ui.help.HelpScreen
import com.sss.app.ui.home.HomeScreen
import com.sss.app.ui.ocr.ExtractedTextScreen
import com.sss.app.ui.onboarding.OnboardingScreen
import com.sss.app.ui.pdf.PdfFlowScreen
import com.sss.app.ui.settings.SettingsScreen
import com.sss.app.ui.theme.SSSTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

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

    NavHost(navController = nav, startDestination = startRoute) {

        composable("onboarding") {
            OnboardingScreen(onDone = {
                if (nav.previousBackStackEntry != null) nav.popBackStack()
                else nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            })
        }

        composable("home") {
            HomeScreen(
                onOpenFolder = { id -> nav.navigate("folder/$id") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenHelp = { nav.navigate("help") },
                onOpenOnboarding = { nav.navigate("onboarding") }
            )
        }

        // folder name is observed from the database, so a rename updates the title immediately
        composable("folder/{folderId}") { entry ->
            val folderId = entry.arguments?.getString("folderId")?.toLongOrNull() ?: return@composable
            val homeEntry = remember(entry) { nav.getBackStackEntry("home") }
            val allVm: AllScreenshotsViewModel = hiltViewModel(homeEntry)   // same list as Home and the Viewer
            FolderScreen(
                folderId = folderId,
                onBack = { nav.popBackStack() },
                onOpenViewer = { index -> nav.navigate("viewer/$folderId/$index") },
                onOpenDocument = { id -> nav.navigate("extracted-text/$id") },
                onCreatePdf = { nav.navigate("pdf") },
                allVm = allVm
            )
        }

        composable("viewer/{folderId}/{index}") { entry ->
            val folderId = entry.arguments?.getString("folderId")?.toLongOrNull() ?: return@composable
            val index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0
            val homeEntry = remember(entry) { nav.getBackStackEntry("home") }
            val allVm: AllScreenshotsViewModel = hiltViewModel(homeEntry)
            ImageViewerScreen(
                folderId = folderId,
                initialIndex = index,
                onBack = { nav.popBackStack() },
                onOpenDocument = { id -> nav.navigate("extracted-text/$id") },
                allScreenshotsViewModel = allVm
            )
        }

        composable("extracted-text/{documentId}") { entry ->
            val id = entry.arguments?.getString("documentId")?.toLongOrNull() ?: return@composable
            ExtractedTextScreen(documentId = id, onBackClick = { nav.popBackStack() })
        }

        composable("pdf") { PdfFlowScreen(onBack = { nav.popBackStack() }) }

        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, onOpenPermissions = { nav.navigate("onboarding") })
        }

        composable("help") {
            HelpScreen(onBack = { nav.popBackStack() }, onOpenPermissions = { nav.navigate("onboarding") })
        }
    }
}