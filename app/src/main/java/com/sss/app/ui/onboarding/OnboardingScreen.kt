package com.sss.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sss.app.ui.permissions.PermissionExplainDialog
import com.sss.app.ui.permissions.PermissionHelper
import com.sss.app.ui.permissions.PermissionType
import com.sss.app.ui.settings.SettingsViewModel
import com.sss.app.ui.theme.GhostButton
import com.sss.app.ui.theme.Line
import com.sss.app.ui.theme.MonoLabel
import com.sss.app.ui.theme.SssPrimaryButton
import com.sss.app.ui.theme.Surface1
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.TextPrimary
import com.sss.app.ui.theme.TextSecondary
import com.sss.app.util.findActivity
import kotlinx.coroutines.launch

private enum class Step { WELCOME, CORE_PERMISSIONS, NOTIFICATIONS, DONE }

private val Success = Color(0xFF4CAF50)

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps = remember {
        buildList {
            add(Step.WELCOME)
            add(Step.CORE_PERMISSIONS)
            if (Build.VERSION.SDK_INT >= 33) add(Step.NOTIFICATIONS)
            add(Step.DONE)
        }
    }
    val pager = rememberPagerState(pageCount = { steps.size })
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

    fun next() { scope.launch { if (pager.currentPage < steps.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) } }
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            HorizontalPager(pager, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
                StepPage(
                    step = steps[page],
                    tick = tick,
                    onAllow = { type ->
                        if (type == PermissionType.PHOTOS && PermissionHelper.photosPartial(context)) PermissionHelper.openAppSettings(context)
                        else explain = type
                    }
                )
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(steps.size) { i ->
                    Box(Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape).background(if (i == pager.currentPage) TextPrimary else Line))
                }
            }

            val step = steps[pager.currentPage]
            val allCoreGranted = PermissionHelper.photosFull(context) && PermissionHelper.overlay(context)
            val notifGranted = PermissionHelper.notifications(context)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step == Step.CORE_PERMISSIONS && !allCoreGranted) {
                    TextButton(onClick = { next() }) { Text("Skip") }
                } else if (step == Step.NOTIFICATIONS && !notifGranted) {
                    TextButton(onClick = { next() }) { Text("Skip") }
                } else {
                    Spacer(Modifier.width(72.dp))
                }

                if (step == Step.DONE) {
                    SssPrimaryButton("Start using 4S", modifier = Modifier.width(220.dp), onClick = { vm.finishOnboarding(); onDone() })
                } else {
                    SssPrimaryButton("Next", modifier = Modifier.width(140.dp), onClick = { next() })
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
private fun StepPage(step: Step, tick: Int, onAllow: (PermissionType) -> Unit) {
    val context = LocalContext.current
    val icon: ImageVector = when (step) {
        Step.WELCOME -> Icons.Default.Folder
        Step.CORE_PERMISSIONS -> Icons.Default.PhotoLibrary
        Step.NOTIFICATIONS -> Icons.Default.Notifications
        Step.DONE -> Icons.Default.CheckCircle
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))
        Box(Modifier.size(80.dp).clip(CircleShape).background(Surface1), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            when (step) {
                Step.WELCOME -> "Welcome to 4S"
                Step.CORE_PERMISSIONS -> "App Permissions"
                Step.NOTIFICATIONS -> "Notifications"
                Step.DONE -> "You're all set"
            },
            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))

        when (step) {
            Step.WELCOME -> {
                Text("Sort your study screenshots automatically.", color = TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                listOf(
                    "Create a folder for each subject",
                    "Start capture, then take screenshots in any app",
                    "Switch folders from the floating button, export PDFs and notes"
                ).forEach {
                    Text("\u2022  $it", color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Next, two quick permissions. You can skip any of them.", color = TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }

            Step.CORE_PERMISSIONS -> {
                Text("Allow these permissions so 4S can auto-sort your screenshots.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))

                PermissionCard(
                    type = PermissionType.PHOTOS,
                    tick = tick,
                    onAllow = onAllow
                )
                Spacer(Modifier.height(12.dp))
                PermissionCard(
                    type = PermissionType.OVERLAY,
                    tick = tick,
                    onAllow = onAllow
                )
            }

            Step.NOTIFICATIONS -> {
                Text("Shows a notification while capture is running so you can pause or stop.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                PermissionCard(
                    type = PermissionType.NOTIFICATIONS,
                    tick = tick,
                    onAllow = onAllow
                )
            }

            Step.DONE -> {
                listOf(PermissionType.PHOTOS, PermissionType.OVERLAY).let { list ->
                    val final = if (Build.VERSION.SDK_INT >= 33) list + PermissionType.NOTIFICATIONS else list
                    final.forEach { t ->
                        val ok = tick >= 0 && PermissionHelper.isGranted(context, t)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(if (ok) "Allowed" else "Skipped", color = if (ok) Success else TextMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("You can change these permissions anytime in Settings.", color = TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionCard(type: PermissionType, tick: Int, onAllow: (PermissionType) -> Unit) {
    val context = LocalContext.current
    val granted = tick >= 0 && PermissionHelper.isGranted(context, type)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (type) {
                    PermissionType.PHOTOS -> Icons.Default.PhotoLibrary
                    PermissionType.OVERLAY -> Icons.Default.Layers
                    PermissionType.NOTIFICATIONS -> Icons.Default.Notifications
                    else -> Icons.Default.Check
                },
                contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(type.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Allowed", color = Success, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                GhostButton(
                    if (type == PermissionType.PHOTOS && PermissionHelper.photosPartial(context)) "Allow all" else "Allow",
                    onClick = { onAllow(type) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(type.whatItDoes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}