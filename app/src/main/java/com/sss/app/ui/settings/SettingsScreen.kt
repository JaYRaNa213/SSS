package com.sss.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sss.app.data.settings.SettingsKeys
import com.sss.app.data.settings.SortOrder
import com.sss.app.ui.permissions.PermissionHelper
import com.sss.app.ui.permissions.PermissionType
import com.sss.app.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenHelp: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()

    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    @Suppress("DEPRECATION")
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"

    Scaffold(containerColor = Bg, contentWindowInsets = WindowInsets.statusBars) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            SssTopBar(title = "Settings")
            LazyColumn(Modifier.padding(horizontal = 20.dp)) {
                item { Section("Bubble") }
                item {
                    Choice("Size", listOf("Small", "Medium", "Large"), s.bubbleSize) { vm.set(SettingsKeys.BUBBLE_SIZE, it) }
                    Text("Idle opacity", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Slider(value = s.bubbleOpacity, onValueChange = { vm.set(SettingsKeys.BUBBLE_OPACITY, it) }, valueRange = 0.2f..1f)
                    Toggle("Snap to screen edge", s.snapEdge) { vm.set(SettingsKeys.SNAP_EDGE, it) }
                    GhostButton("Reset bubble position", onClick = vm::resetBubblePosition)
                }
                item { Section("Capture") }
                item {
                    Toggle("Auto-crop screenshots", s.autoCrop, "Hides the status bar, navigation bar and black bars inside 4S. Original files are never changed.") { vm.set(SettingsKeys.AUTO_CROP, it) }
                    Toggle("Auto-rotate wide slides", s.autoRotateWide, "Shows wide slides turned sideways so they fill the screen.") { vm.set(SettingsKeys.AUTO_ROTATE, it) }
                    Toggle("Haptic tick on save", s.haptic) { vm.set(SettingsKeys.HAPTIC, it) }
                }
                item { Section("Folders") }
                item {
                    Choice("Default sort in folders", listOf("Oldest first", "Newest first"), s.sortFolders.ordinal) {
                        vm.set(SettingsKeys.SORT_FOLDERS, SortOrder.entries[it].name)
                    }
                    Choice("Grid columns", listOf("2", "3"), s.gridColumns - 2) { vm.set(SettingsKeys.GRID_COLUMNS, it + 2) }
                    Toggle("Date section headers", s.dateHeaders) { vm.set(SettingsKeys.DATE_HEADERS, it) }
                }
                item { Section("Text extraction") }
                item {
                    Choice("Remove unrelated text", listOf("Off", "Smart", "Strict"), s.ocrCleanup) { vm.set(SettingsKeys.OCR_CLEANUP, it) }
                    Text(
                        "Smart hides clocks, buttons, watermarks and text repeated on most screenshots. " +
                            "Hidden lines stay under \"Ignored text\" so you can add them back.",
                        color = TextMuted, style = MaterialTheme.typography.bodySmall
                    )
                }
                item { Section("PDF defaults") }
                item {
                    Choice("Quality", listOf("High", "Medium", "Low"), s.pdfQuality) { vm.set(SettingsKeys.PDF_QUALITY, it) }
                    Choice("Images per page", listOf("1", "2", "3"), s.pdfPerPage - 1) { vm.set(SettingsKeys.PDF_PER_PAGE, it + 1) }
                }
                item { Section("Permissions") }
                item {
                    PermissionType.entries.forEach { type ->
                        val ok = tick >= 0 && PermissionHelper.isGranted(context, type)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(type.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (ok) Text("Allowed", color = TextMuted)
                            else GhostButton("Fix", onClick = {
                                if (type == PermissionType.BATTERY) PermissionHelper.openBatterySettings(context)
                                else onOpenPermissions()
                            })
                        }
                    }
                }
                item { Section("About") }
                item {
                    Text("4S version $version", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    GhostButton("How 4S works", onClick = onOpenHelp, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Privacy policy", onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/privacy")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    MonoLabel(title, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
private fun Toggle(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        SssSwitch(checked, onChange)
    }
}

@Composable
private fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, t ->
                FilterChip(
                    selected = i == selected, onClick = { onSelect(i) }, label = { Text(t) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentSoft, selectedLabelColor = Accent)
                )
            }
        }
    }
}