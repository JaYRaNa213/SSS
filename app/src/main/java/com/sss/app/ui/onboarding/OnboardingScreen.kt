package com.sss.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sss.app.ui.permissions.PermissionExplainDialog
import com.sss.app.ui.permissions.PermissionHelper
import com.sss.app.ui.permissions.PermissionType
import com.sss.app.ui.settings.SettingsViewModel
import com.sss.app.ui.theme.*
import com.sss.app.util.findActivity
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })
    var tick by remember { mutableIntStateOf(0) }
    var explain by remember { mutableStateOf<PermissionType?>(null) }
    var photoPermanent by remember { mutableStateOf(false) }
    var notifPermanent by remember { mutableStateOf(false) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (!PermissionHelper.photosFull(context)) {
            val act = context.findActivity()
            photoPermanent = act != null && !ActivityCompat.shouldShowRequestPermissionRationale(act, PermissionHelper.photoPermissions().first())
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (!granted && Build.VERSION.SDK_INT >= 33) {
            val act = context.findActivity()
            notifPermanent = act != null && !ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun next() { scope.launch { if (pager.currentPage < 1) pager.animateScrollToPage(1) } }
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(0) } }

    fun ask(type: PermissionType) {
        if (type == PermissionType.PHOTOS && PermissionHelper.photosPartial(context)) PermissionHelper.openAppSettings(context) else explain = type
    }

    Scaffold(containerColor = Bg) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            HorizontalPager(pager, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
                if (page == 0) PhotosPage() else FloatingPage(tick) { ask(it) }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (pager.currentPage == 0) {
                    val ok = tick >= 0 && PermissionHelper.photosFull(context)
                    SssPrimaryButton(if (ok) "Continue" else if (PermissionHelper.photosPartial(context)) "Allow all in settings" else "Allow access", onClick = { if (ok) next() else ask(PermissionType.PHOTOS) })
                    if (!ok) TextButton(onClick = { next() }, modifier = Modifier.fillMaxWidth()) { Text("Not now", color = TextMuted) }
                } else {
                    SssPrimaryButton("Start using 4S", onClick = { vm.finishOnboarding(); onDone() })
                    Text("You can change permissions any time in Settings.", color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
        }
    }

    explain?.let { type ->
        PermissionExplainDialog(
            type = type,
            permanentlyDenied = (type == PermissionType.PHOTOS && photoPermanent) || (type == PermissionType.NOTIFICATIONS && notifPermanent),
            onConfirm = {
                explain = null
                when (type) {
                    PermissionType.PHOTOS -> if (photoPermanent) PermissionHelper.openAppSettings(context) else photoLauncher.launch(PermissionHelper.photoPermissions())
                    PermissionType.OVERLAY -> PermissionHelper.openOverlaySettings(context)
                    PermissionType.NOTIFICATIONS -> if (notifPermanent) PermissionHelper.openAppSettings(context)
                        else if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    PermissionType.BATTERY -> PermissionHelper.openBatterySettings(context)
                }
            },
            onDismiss = { explain = null }
        )
    }
}

@Composable
private fun PhotosPage() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        FanOfCards()
        Spacer(Modifier.height(22.dp))
        Text("Let 4S organise your screenshots", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("We need access to your photos to find screenshots and file them into folders.", color = TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        FeatureRow(Icons.Outlined.FolderOpen, "Auto-sorted", "New screenshots go straight into your active folder.")
        FeatureRow(Icons.Outlined.DocumentScanner, "Text from images", "Turn handwritten notes into editable text.")
        FeatureRow(Icons.Default.Lock, "Stays on device", "Nothing is uploaded unless you share it.")
    }
}

@Composable
private fun FanOfCards() {
    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.TopCenter) {
        listOf(-12f to (-70).dp, 12f to 70.dp, 0f to 0.dp).forEach { (rot, dx) ->
            Box(
                Modifier.padding(start = dx.coerceAtLeast(0.dp), end = (-dx).coerceAtLeast(0.dp)).size(width = 120.dp, height = 160.dp)
                    .graphicsLayer { rotationZ = rot }.clip(RoundedCornerShape(18.dp)).background(if (rot == 0f) Cream else Surface2)
                    .border(1.dp, Line, RoundedCornerShape(18.dp)).padding(14.dp)
            ) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    repeat(6) { i -> Box(Modifier.fillMaxWidth(if (i % 3 == 0) 0.5f else 0.9f).height(6.dp).clip(CircleShape).background(if (rot == 0f) Accent.copy(alpha = 0.7f) else Surface3)) }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).size(56.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PhotoLibrary, null, tint = AccentOn)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(desc, color = TextMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun FloatingPage(tick: Int, onAllow: (PermissionType) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Layers, null, tint = AccentOn, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Two quick things", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text("These make the floating 4S button and the capture notification work. You can skip either one.", color = TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PermCard(Icons.Default.Layers, PermissionType.OVERLAY, tick >= 0 && PermissionHelper.isGranted(context, PermissionType.OVERLAY), onAllow)
        if (Build.VERSION.SDK_INT >= 33) {
            Spacer(Modifier.height(12.dp))
            PermCard(Icons.Default.Notifications, PermissionType.NOTIFICATIONS, tick >= 0 && PermissionHelper.isGranted(context, PermissionType.NOTIFICATIONS), onAllow)
        }
    }
}

@Composable
private fun PermCard(icon: ImageVector, type: PermissionType, granted: Boolean, onAllow: (PermissionType) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface1).border(1.dp, Line, RoundedCornerShape(24.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent)
            Spacer(Modifier.width(12.dp))
            Text(type.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (granted) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Allowed", color = Success, fontWeight = FontWeight.SemiBold)
            } else AccentPill("Allow", { onAllow(type) })
        }
        Spacer(Modifier.height(8.dp))
        Text(type.whatItDoes, color = TextMuted, fontSize = 14.sp)
    }
}