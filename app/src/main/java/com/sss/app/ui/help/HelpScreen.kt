package com.sss.app.ui.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sss.app.ui.theme.GhostButton
import com.sss.app.ui.theme.MonoLabel
import com.sss.app.ui.theme.SssTopBar
import com.sss.app.ui.theme.TextMuted
import com.sss.app.ui.theme.TextSecondary

private val steps = listOf(
    "Create a folder" to "Make a folder for each subject, for example Computer Networks.",
    "Select it and tap Start capture" to "Tap the circle on the folder, then the button at the bottom.",
    "Take screenshots in any app" to "Open YouTube or any app. Every new screenshot is filed into the selected folder.",
    "Switch folders without leaving the video" to "Tap the floating 4S button to switch or create folders."
)

private val faq = listOf(
    "Why is a screenshot missing?" to "Capture must be running and photo access must be \"Allow all\". Screenshots taken while capture is paused or stopped appear in Other. Some phones close background apps: set battery to \"No restrictions\" for 4S.",
    "Why does the bubble show in my screenshots?" to "The button is drawn on top of the screen, so the system includes it. Make it smaller or fainter in Settings and move it to the screen edge. Auto-crop cannot remove it.",
    "How do I stop capture?" to "Tap Stop capture on the home screen, or Stop in the notification, or Stop in the bubble panel.",
    "Does deleting remove my photos?" to "Inside a folder, delete only removes the screenshot from 4S (the file stays in your gallery) and you can Undo. In Other, delete removes the original file after Android asks you to confirm.",
    "Where are PDFs saved?" to "In Downloads/4S. You can also open, share, rename or delete them anytime from the PDF button on the Home screen."
)

@Composable
fun HelpScreen(onBack: () -> Unit, onOpenPermissions: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            SssTopBar(title = "Help", onBack = onBack)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                MonoLabel("How 4S works", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                steps.forEachIndexed { i, (t, d) ->
                    Text("${i + 1}  $t", style = MaterialTheme.typography.titleMedium)
                    Text(d, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                MonoLabel("FAQ", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                faq.forEach { (q, a) ->
                    Text(q, style = MaterialTheme.typography.titleSmall)
                    Text(a, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                }
                GhostButton("Check permissions", onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}