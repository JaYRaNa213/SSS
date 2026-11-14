package com.sss.app.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import com.sss.app.data.local.ScreenshotMetaEntity

/** What the "Rotate" popup can ask for. */
sealed interface RotateChoice {
    data class By(val degrees: Int) : RotateChoice   // relative to the current effective rotation
    data class To(val degrees: Int) : RotateChoice   // absolute
    data object Auto : RotateChoice                  // remove the manual value
}

/** Manual rotation saved for a screenshot, 0 when it follows "automatic". */
fun manualRotation(meta: ScreenshotMetaEntity?): Int = meta?.rotation?.takeIf { it >= 0 } ?: 0

/**
 * Grid thumbnail that shows a saved rotation. At 0 degrees it looks exactly like before (cropped to fill).
 * At other angles the picture is fitted and turned, using the same maths as the full-screen viewer.
 */
@Composable
fun RotatedImage(
    model: Any,
    meta: ScreenshotMetaEntity?,
    cropEnabled: Boolean,
    rotation: Int,
    modifier: Modifier = Modifier
) {
    if (rotation % 360 == 0) {
        CroppedImage(model = model, meta = meta, cropEnabled = cropEnabled, modifier = modifier)
        return
    }
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val containerAspect = if (ch > 0f) cw / ch else 1f
        val a = MetaMath.regionAspect(meta, cropEnabled) ?: containerAspect
        val fw = if (a > containerAspect) cw else ch * a
        val fh = if (a > containerAspect) cw / a else ch
        val sideways = rotation % 180 != 0
        val s = if (sideways && fw > 0f && fh > 0f) minOf(cw / fh, ch / fw) else 1f
        Box(Modifier.fillMaxSize().graphicsLayer(scaleX = s, scaleY = s, rotationZ = rotation.toFloat())) {
            CroppedImage(model = model, meta = meta, cropEnabled = cropEnabled, fit = true, modifier = Modifier.fillMaxSize())
        }
    }
}