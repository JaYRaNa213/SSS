package com.sss.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import com.sss.app.data.local.ScreenshotMetaEntity
import kotlin.math.roundToInt

@Composable
fun CroppedImage(
    model: Any,
    meta: ScreenshotMetaEntity?,
    cropEnabled: Boolean,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
    contentDescription: String? = null
) {
    if (!cropEnabled || !MetaMath.hasCrop(meta)) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop
        )
        return
    }
    val m = meta!!
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val ow = constraints.maxWidth.toFloat()
        val oh = constraints.maxHeight.toFloat()
        val rw = m.imgW * (1f - m.cropLeft - m.cropRight)
        val rh = m.imgH * (1f - m.cropTop - m.cropBottom)
        val scale = if (fit) minOf(ow / rw, oh / rh) else maxOf(ow / rw, oh / rh)
        with(density) {
            Box(Modifier.requiredSize((rw * scale).toDp(), (rh * scale).toDp()).clipToBounds()) {
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size((m.imgW * scale).toDp(), (m.imgH * scale).toDp())
                        .offset { IntOffset((-m.cropLeft * m.imgW * scale).roundToInt(), (-m.cropTop * m.imgH * scale).roundToInt()) }
                ) {
                    AsyncImage(
                        model = model,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }
    }
}