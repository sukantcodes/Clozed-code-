package com.clozed.code.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Shared-storage access. From Android 11 (API 30) the agent needs "All files
 * access" to read and write the project folder outside app-scoped storage;
 * below that a plain WRITE_EXTERNAL_STORAGE grant is enough.
 */
object StorageAccess {

    private fun legacyPermission(): String = Manifest.permission.WRITE_EXTERNAL_STORAGE

    /** True when the app can read and write arbitrary files on shared storage. */
    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, legacyPermission()) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Opens the system screen where the user grants all-files access. On
     * Android 10 and below there is no such screen, so this returns false and
     * the caller should request the runtime permission instead.
     */
    fun openSettings(ctx: Context): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
            .setData(Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
    }

    /** The runtime permission to request on Android 10 and below. */
    fun legacyPermissionName(): String = legacyPermission()
}
