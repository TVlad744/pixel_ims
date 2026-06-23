package com.takaisaisei.pixelims.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.takaisaisei.pixelims.data.SettingsRepository

/**
 * Enqueues [ReapplyWorker] after a reboot, but only when the user has opted at least one SIM into
 * "Apply on boot" - otherwise there is nothing to do, and we stay completely idle.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (SettingsRepository(context).bootSlots().isEmpty()) return

        val request = OneTimeWorkRequestBuilder<ReapplyWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(ReapplyWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
