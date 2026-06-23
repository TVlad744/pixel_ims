package com.takaisaisei.pixelims.boot

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.takaisaisei.pixelims.adb.AdbController
import com.takaisaisei.pixelims.adb.AdbDiscovery
import com.takaisaisei.pixelims.adb.AdbEndpoint
import com.takaisaisei.pixelims.data.SettingsRepository
import com.takaisaisei.pixelims.system.ConnectivityMonitor
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class ReapplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext)
        val slots = settings.bootSlots()
        if (slots.isEmpty()) return Result.success()

        // Wireless Debugging only lives on Wi-Fi/LAN. The CONNECTED constraint also passes on cellular,
        // so bail cheaply (and let WorkManager retry) until we're actually on Wi-Fi - no point waiting
        // out the mDNS window on mobile data.
        if (!ConnectivityMonitor(applicationContext).isWifiConnected()) {
            Log.d(TAG, "Not on Wi-Fi yet; will retry")
            return Result.retry()
        }

        val discovery = AdbDiscovery(applicationContext)
        val adb = AdbController(applicationContext)

        val port = withTimeoutOrNull(DISCOVERY_WINDOW_MS.milliseconds) {
            discovery.discover().filterIsInstance<AdbEndpoint.Connect>().first().port
        }
        if (port == null) {
            Log.d(TAG, "No connect endpoint within window; will retry")
            return Result.retry()
        }
        if (!adb.isAuthorized(port)) {
            Log.d(TAG, "ADB not authorized on port $port; will retry")
            return Result.retry()
        }

        var allOk = true
        for (slot in slots.sorted()) {
            adb.runApply(port, slot, clear = false).onFailure {
                Log.e(TAG, "Reapply failed for slot $slot", it)
                allOk = false
            }
        }
        return if (allOk) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "reapply_on_boot"
        private const val TAG = "ReapplyWorker"
        private const val DISCOVERY_WINDOW_MS = 60_000L
    }
}
