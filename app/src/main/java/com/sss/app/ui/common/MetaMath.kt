package com.sss.app.ui.common

import com.sss.app.data.local.ScreenshotMetaEntity

object MetaMath {
    /** Width / height of the visible (cropped) region, or null if the image size is not known yet. */
    fun regionAspect(m: ScreenshotMetaEntity?, cropOn: Boolean): Float? {
        if (m == null || m.imgW <= 0 || m.imgH <= 0) return null
        val l = if (cropOn) m.cropLeft else 0f
        val r = if (cropOn) m.cropRight else 0f
        val t = if (cropOn) m.cropTop else 0f
        val b = if (cropOn) m.cropBottom else 0f
        val w = m.imgW * (1f - l - r)
        val h = m.imgH * (1f - t - b)
        if (w <= 0f || h <= 0f) return null
        return w / h
    }

    fun isWide(aspect: Float?): Boolean = aspect != null && aspect >= 1.2f

    /** Saved rotation if the user set one, otherwise 90 for wide slides when "auto-rotate wide slides" is on. */
    fun effectiveRotation(m: ScreenshotMetaEntity?, cropOn: Boolean, autoRotate: Boolean): Int {
        if (m != null && m.rotation >= 0) return m.rotation
        return if (autoRotate && isWide(regionAspect(m, cropOn))) 90 else 0
    }

    fun hasCrop(m: ScreenshotMetaEntity?): Boolean =
        m != null && m.imgW > 0 && (m.cropTop + m.cropBottom + m.cropLeft + m.cropRight) > 0.001f
}