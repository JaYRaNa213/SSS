package com.sss.app.capture

/**
 * ONE place that decides "is this MediaStore row a screenshot?".
 * Used by both ScreenshotObserver and AllScreenshotsRepository so they never disagree.
 *
 * The old keyword list ("screen", "capture", "shot") matched camera photos,
 * downloaded images, and anything with those letters in its path.
 */
object ScreenshotMatcher {

    fun isScreenshot(
        displayName: String?,
        relativePath: String?,
        bucketName: String?
    ): Boolean {
        val name = displayName.orEmpty()
        val path = relativePath.orEmpty()
        val bucket = bucketName.orEmpty()

        // Pixel / Samsung / Xiaomi / OnePlus / Vivo / Oppo all start with "Screenshot"
        val nameLooksRight =
            name.startsWith("screenshot", ignoreCase = true) ||
                    name.startsWith("screen_shot", ignoreCase = true) ||
                    name.startsWith("screen-shot", ignoreCase = true) ||
                    name.startsWith("screencap", ignoreCase = true)

        // Pictures/Screenshots, DCIM/Screenshots, ...
        val folderLooksRight =
            path.contains("screenshot", ignoreCase = true) ||
                    bucket.contains("screenshot", ignoreCase = true)

        return nameLooksRight || folderLooksRight
    }
}