package com.sss.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {

    fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Full photo access (what screenshot detection needs). */
    fun photosFull(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) granted(ctx, Manifest.permission.READ_MEDIA_IMAGES)
        else granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Android 14 "Selected photos only". */
    fun photosPartial(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 && !photosFull(ctx) && granted(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)

    fun battery(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    fun isGranted(ctx: Context, type: PermissionType): Boolean = when (type) {
        PermissionType.PHOTOS -> photosFull(ctx)
        PermissionType.OVERLAY -> overlay(ctx)
        PermissionType.NOTIFICATIONS -> notifications(ctx)
        PermissionType.BATTERY -> battery(ctx)
    }

    fun openAppSettings(ctx: Context) = ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun openOverlaySettings(ctx: Context) = ctx.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    /** Opens the battery-optimization list (no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission: Google Play restricts it). */
    fun openBatterySettings(ctx: Context) = ctx.startActivity(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}