package com.takaisaisei.pixelims.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.flyfishxu.kadb.Kadb
import com.takaisaisei.pixelims.ApplyMode
import com.takaisaisei.pixelims.domain.BrokerContract
import com.takaisaisei.pixelims.domain.SlotQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Wraps every loopback ADB operation backed by [Kadb].
 */
class AdbController(context: Context) {

    private val appContext: Context = context.applicationContext
    private val packageName: String = appContext.packageName
    private val pairingKeyDir: String = appContext.filesDir.absolutePath

    suspend fun isAuthorized(port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Kadb.create(HOST, port, AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MS).use { kadb ->
                kadb.shell("echo 1").exitCode == 0
            }
        }.getOrDefault(false)
    }

    /**
     * Runs [com.takaisaisei.pixelims.BrokerInstrumentation] for [slot] (or every apply-on-boot slot
     * when [slot] is [BrokerContract.SLOT_ALL_BOOT]).
     *
     * The mechanism differs by platform version:
     * - **Android 14+**: `--no-restart` attaches to the already-running app process, so the run is
     *   synchronous, the UI survives and the result is returned directly.
     * - **Android < 14**: `--no-restart` does not exist and `am instrument` kills the
     *   app process, so the command is fired detached.
     */
    suspend fun runApply(port: Int, slot: Int, clear: Boolean, notify: Boolean): Result<Unit> =
        if (ApplyMode.detached) {
            runApplyDetached(port, slot, clear)
        } else {
            runApplyAttached(port, slot, clear, notify)
        }

    private suspend fun runApplyAttached(
        port: Int,
        slot: Int,
        clear: Boolean,
        notify: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Kadb.create(HOST, port, CONNECT_TIMEOUT_MS, APPLY_TIMEOUT_MS).use { kadb ->
                    val target = "$packageName/$packageName.BrokerInstrumentation"
                    val command = "am instrument -w --no-restart" +
                            " -e ${BrokerContract.ARG_CLEAR} $clear" +
                            " -e ${BrokerContract.ARG_SLOT} $slot" +
                            " -e ${BrokerContract.ARG_NOTIFY} $notify" +
                            " $target"
                    val response = kadb.shell(command)
                    check(response.exitCode == 0) { "Exit code ${response.exitCode}: ${response.output}" }
                    // `am instrument` exits 0 even when the run reports a failure; surface that too.
                    check(!response.output.contains("INSTRUMENTATION_FAILED")) { response.output.trim() }
                }
            }
        }

    private suspend fun runApplyDetached(port: Int, slot: Int, clear: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Kadb.create(HOST, port, CONNECT_TIMEOUT_MS, CONNECT_TIMEOUT_MS).use { kadb ->
                    val target = "$packageName/$packageName.BrokerInstrumentation"
                    val command = "nohup am instrument -w" +
                            " -e ${BrokerContract.ARG_CLEAR} $clear" +
                            " -e ${BrokerContract.ARG_SLOT} $slot" +
                            " -e ${BrokerContract.ARG_NOTIFY} true" +
                            " $target > /dev/null 2>&1 &"
                    val response = kadb.shell(command)
                    check(response.exitCode == 0) { "Exit code ${response.exitCode}: ${response.output}" }
                }
            }
        }

    /** Pairs with the device's pairing endpoint. */
    suspend fun pair(port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { Kadb.pair(HOST, port, code, pairingKeyDir) }
    }

    suspend fun grantPermissionsIfNeeded(port: Int) {
        val missing = GRANTABLE_PERMISSIONS.filterNot(::hasPermission)
        if (missing.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                Kadb.create(HOST, port, AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MS).use { kadb ->
                    missing.forEach { permission ->
                        val response = kadb.shell("pm grant $packageName $permission")
                        if (response.exitCode != 0) {
                            Log.w(TAG, "pm grant $permission failed: ${response.output.trim()}")
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Failed to self-grant permissions", it) }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Continuously runs the lightweight `ImsQueryTool` over ADB and reports each slot's state
     * via [onResult]. Loops until the coroutine is canceled;
     * pauses while [isPaused] is true to avoid contending with an active instrumentation run.
     */
    suspend fun pollImsStatus(
        port: Int,
        isPaused: () -> Boolean,
        onResult: (SlotQuery) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            var kadb: Kadb? = null
            try {
                while (true) {
                    coroutineContext.ensureActive()
                    if (isPaused()) {
                        delay(PAUSE_INTERVAL_MS.milliseconds)
                        continue
                    }
                    try {
                        val connection =
                            kadb ?: Kadb.create(HOST, port, POLL_TIMEOUT_MS, POLL_TIMEOUT_MS)
                                .also { kadb = it }
                        queryOnce(connection, onResult)
                    } catch (e: Exception) {
                        Log.d(TAG, "IMS poll error: ${e.message}")
                        runCatching { kadb?.close() }
                        kadb = null
                    }
                    delay(POLL_INTERVAL_MS.milliseconds)
                }
            } finally {
                runCatching { kadb?.close() }
            }
        }
    }

    private fun queryOnce(kadb: Kadb, onResult: (SlotQuery) -> Unit) {
        val pathResponse = kadb.shell("pm path $packageName")
        if (pathResponse.exitCode != 0) return
        val apkPath = pathResponse.output.trim().substringAfter("package:")
        if (apkPath.isEmpty()) return

        val query = "export CLASSPATH=$apkPath; app_process /system/bin $packageName.ImsQueryTool"
        val queryResponse = kadb.shell(query)
        if (queryResponse.exitCode != 0) return

        BrokerContract.decodeQuery(queryResponse.output).forEach(onResult)
    }

    companion object {
        private const val TAG = "AdbController"
        private const val HOST = "127.0.0.1"

        private val GRANTABLE_PERMISSIONS = listOf(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            Manifest.permission.READ_PHONE_STATE,
        )

        private const val AUTH_TIMEOUT_MS = 3_000
        private const val POLL_TIMEOUT_MS = 5_000
        private const val CONNECT_TIMEOUT_MS = 10_000

        // Read timeout for the synchronous apply run: must outlast applying config + IMS reset.
        private const val APPLY_TIMEOUT_MS = 90_000

        private const val POLL_INTERVAL_MS = 3_000L
        private const val PAUSE_INTERVAL_MS = 1_000L
    }
}
