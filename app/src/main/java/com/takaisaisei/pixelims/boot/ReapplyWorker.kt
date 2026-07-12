package com.takaisaisei.pixelims.boot

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.takaisaisei.pixelims.ApplyMode
import com.takaisaisei.pixelims.adb.AdbController
import com.takaisaisei.pixelims.adb.AdbDiscovery
import com.takaisaisei.pixelims.adb.AdbEndpoint
import com.takaisaisei.pixelims.data.SettingsRepository
import com.takaisaisei.pixelims.domain.BrokerContract
import com.takaisaisei.pixelims.system.ConnectivityMonitor
import com.takaisaisei.pixelims.system.WirelessDebugging
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class ReapplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext)
        if (settings.bootSlots().isEmpty()) return Result.success()

        // On Android < 14 apply is detached: `am instrument` force-stops this very worker after
        // dispatch, which WorkManager treats as a failure and reschedules. Skip if we already fired
        // during this boot to avoid a loop.
        val detached = ApplyMode.detached
        if (detached && settings.bootApplyAlreadyFired()) {
            Log.d(TAG, "Boot re-apply already dispatched this boot; skipping")
            return Result.success()
        }

        // Wireless Debugging only lives on Wi-Fi/LAN. The CONNECTED constraint also passes on cellular,
        // so bail cheaply (and let WorkManager retry) until we're actually on Wi-Fi - no point waiting
        // out the mDNS window on mobile data.
        if (!ConnectivityMonitor(applicationContext).isWifiConnected()) {
            Log.d(TAG, "Not on Wi-Fi yet; will retry")
            return Result.retry()
        }

        val discovery = AdbDiscovery(applicationContext)
        val adb = AdbController(applicationContext)

        var port = discoverPort(discovery)
        if (port == null) {
            // No connect port published. On some devices adbd stays stopped after boot despite
            // wireless debugging being enabled; recover it and retry.
            if (WirelessDebugging.recover(applicationContext)) {
                Log.d(TAG, "Recovered wireless debugging; re-discovering")
                port = discoverPort(discovery)
            }
        }
        if (port == null) {
            Log.d(TAG, "No connect endpoint within window; will retry")
            return Result.retry()
        }
        if (!adb.isAuthorized(port)) {
            Log.d(TAG, "ADB not authorized on port $port; will retry")
            return Result.retry()
        }

        if (detached) {
            // Mark before firing: the force-stop may kill us before we can return.
            settings.markBootApplyFired()
            adb.runApply(port, BrokerContract.SLOT_ALL_BOOT, clear = false, notify = true)
            return Result.success()
        }
        return adb.runApply(port, BrokerContract.SLOT_ALL_BOOT, clear = false, notify = true).fold(
            onSuccess = { Result.success() },
            onFailure = {
                Log.e(TAG, "Boot re-apply failed", it)
                Result.retry()
            },
        )
    }

    private suspend fun discoverPort(discovery: AdbDiscovery): Int? =
        withTimeoutOrNull(DISCOVERY_WINDOW_MS.milliseconds) {
            discovery.discover().filterIsInstance<AdbEndpoint.Connect>().first().port
        }

    companion object {
        const val WORK_NAME = "reapply_on_boot"
        private const val TAG = "ReapplyWorker"
        private const val DISCOVERY_WINDOW_MS = 15_000L
    }
}
