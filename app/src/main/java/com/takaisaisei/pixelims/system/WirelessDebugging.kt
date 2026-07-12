package com.takaisaisei.pixelims.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.takaisaisei.pixelims.system.WirelessDebugging.recover
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Recovers wireless debugging when its `adbd` daemon fails to start after a reboot.
 *
 * On some Pixels, `persist.adb.tls_server.enable` stays `1` across reboots but `adbd` remains
 * stopped, so `AdbDebuggingManager` never publishes a connect port.
 * Manually toggling wireless debugging fixes it; [recover] does the same thing by
 * rewriting the `adb_wifi_enabled` global setting, which re-runs the framework enable path.
 */
object WirelessDebugging {

    fun canToggle(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.applicationContext.contentResolver, ADB_WIFI_ENABLED, 0) == 1

    suspend fun recover(context: Context): Boolean {
        if (!canToggle(context)) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS not granted; cannot recover wireless debugging")
            return false
        }
        if (!isEnabled(context)) {
            Log.d(TAG, "Wireless debugging disabled by the user; not forcing it on")
            return false
        }
        val resolver = context.applicationContext.contentResolver
        return runCatching {
            Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, 0)
            delay(TOGGLE_DELAY_MS.milliseconds)
            Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, 1)
            Log.d(TAG, "Toggled $ADB_WIFI_ENABLED to restart wireless adbd")
            true
        }.onFailure { Log.e(TAG, "Failed to toggle $ADB_WIFI_ENABLED", it) }.getOrDefault(false)
    }

    private const val TAG = "WirelessDebugging"

    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private const val TOGGLE_DELAY_MS = 1_000L
}
