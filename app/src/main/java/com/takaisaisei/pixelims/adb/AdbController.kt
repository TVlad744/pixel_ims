package com.takaisaisei.pixelims.adb

import android.content.Context
import android.util.Log
import com.flyfishxu.kadb.Kadb
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

    private val packageName: String = context.applicationContext.packageName
    private val pairingKeyDir: String = context.applicationContext.filesDir.absolutePath

    suspend fun isAuthorized(port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Kadb.create(HOST, port, AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MS).use { kadb ->
                kadb.shell("echo 1").exitCode == 0
            }
        }.getOrDefault(false)
    }

    /**
     * Runs [com.takaisaisei.pixelims.BrokerInstrumentation] synchronously and waits for it to finish.
     *
     * `--no-restart` attaches instrumentation to the already-running app process.
     * With the process kept alive we can run attached (no `nohup &`).
     */
    suspend fun runApply(port: Int, slot: Int, clear: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Kadb.create(HOST, port, CONNECT_TIMEOUT_MS, APPLY_TIMEOUT_MS).use { kadb ->
                    val target = "$packageName/$packageName.BrokerInstrumentation"
                    val command = "am instrument -w --no-restart" +
                            " -e ${BrokerContract.ARG_CLEAR} $clear" +
                            " -e ${BrokerContract.ARG_SLOT} $slot" +
                            " $target"
                    val response = kadb.shell(command)
                    check(response.exitCode == 0) { "Exit code ${response.exitCode}: ${response.output}" }
                    // `am instrument` exits 0 even when the run reports a failure; surface that too.
                    check(!response.output.contains("INSTRUMENTATION_FAILED")) { response.output.trim() }
                }
            }
        }

    /** Pairs with the device's pairing endpoint. */
    suspend fun pair(port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { Kadb.pair(HOST, port, code, pairingKeyDir) }
    }

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

        private const val AUTH_TIMEOUT_MS = 3_000
        private const val POLL_TIMEOUT_MS = 5_000
        private const val CONNECT_TIMEOUT_MS = 10_000

        // Read timeout for the synchronous apply run: must outlast applying config + IMS reset.
        private const val APPLY_TIMEOUT_MS = 90_000

        private const val POLL_INTERVAL_MS = 3_000L
        private const val PAUSE_INTERVAL_MS = 1_000L
    }
}
