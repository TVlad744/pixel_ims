package com.takaisaisei.pixelims

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Bundle
import android.util.Log
import com.takaisaisei.pixelims.data.SettingsRepository
import com.takaisaisei.pixelims.domain.BrokerContract
import com.takaisaisei.pixelims.domain.toCarrierOverrides
import com.takaisaisei.pixelims.system.NotificationController
import com.takaisaisei.pixelims.telephony.PrivilegedCarrierConfigurator
import com.takaisaisei.pixelims.telephony.SubscriptionSlot
import com.takaisaisei.pixelims.telephony.TelephonyReflection
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Privileged runner launched via instrumentation. It adopts the shell permission
 * identity, applies (or clears) the carrier-config overrides for one slot and resets IMS.
 */
class BrokerInstrumentation : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val clear = arguments?.getString(BrokerContract.ARG_CLEAR).toBoolean()
        val slot = arguments?.getString(BrokerContract.ARG_SLOT)?.toIntOrNull() ?: 0
        val notify = arguments?.getString(BrokerContract.ARG_NOTIFY).toBoolean()
        Log.d(TAG, "BrokerInstrumentation starting (clear=$clear, slot=$slot, notify=$notify)")

        Thread {
            try {
                runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
                    .onFailure { Log.e(TAG, "Failed to apply HiddenApiBypass exemptions", it) }

                val uiAutomation = connectUiAutomation()
                uiAutomation?.adoptShellPermissionIdentity()
                    ?: Log.e(TAG, "UiAutomation unavailable; cannot adopt shell identity")
                try {
                    val acted = patch(clear, slot)
                    if (notify) reportResult(clear, acted)
                } finally {
                    runCatching { uiAutomation?.dropShellPermissionIdentity() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Instrumentation patch failed", e)
            } finally {
                finish(Activity.RESULT_OK, Bundle())
            }
        }.start()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        runCatching { super.finish(resultCode, results) }
            .onFailure { Log.e(TAG, "Instrumentation.finish() ignored safely", it) }
    }

    private fun connectUiAutomation(): UiAutomation? {
        repeat(UI_AUTOMATION_RETRIES) { attempt ->
            runCatching { uiAutomation }
                .onSuccess { if (it != null) return it }
                .onFailure {
                    Log.w(
                        TAG,
                        "UiAutomation connect failed (attempt ${attempt + 1})",
                        it
                    )
                }
            runCatching { Thread.sleep(UI_AUTOMATION_RETRY_DELAY_MS) }
        }
        return null
    }

    // Applies (or clears) the carrier-config overrides for the requested slot(s), then resets IMS.
    // Returns the subscriptions actually acted on so the caller can report their IMS state.
    private fun patch(clear: Boolean, slot: Int): List<SubscriptionSlot> {
        val settings = SettingsRepository(context)
        val configurator = PrivilegedCarrierConfigurator(context)
        val targetSlots =
            if (slot == BrokerContract.SLOT_ALL_BOOT) settings.bootSlots() else setOf(slot)
        val subs = configurator.activeSubscriptions().filter { it.slot in targetSlots }
        Log.d(TAG, "Acting on ${subs.size} SIM(s) in slots $targetSlots")

        for (sub in subs) {
            if (clear) {
                configurator.clearOverrides(sub.subId)
            } else {
                configurator.applyOverrides(
                    sub.subId,
                    settings.readConfig(sub.slot).toCarrierOverrides()
                )
            }
            configurator.resetIms(sub.slot)
        }
        return subs
    }

    private fun reportResult(clear: Boolean, subs: List<SubscriptionSlot>) {
        val notifications = NotificationController(context)
        var registered = subs.associate { it.slot to TelephonyReflection.isImsRegistered(it.subId) }
        val deadline = System.currentTimeMillis() + IMS_SETTLE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            registered = subs.associate { it.slot to TelephonyReflection.isImsRegistered(it.subId) }
            val settled = registered.values.all { it != clear }
            if (registered.isNotEmpty() && settled) break
            runCatching { Thread.sleep(IMS_POLL_INTERVAL_MS) }
        }
        runCatching { notifications.showApplyResult(clear, registered) }
            .onFailure { Log.e(TAG, "Failed to post result notification", it) }
    }

    private companion object {
        const val TAG = "IMSBrokerInst"
        const val UI_AUTOMATION_RETRIES = 5
        const val UI_AUTOMATION_RETRY_DELAY_MS = 500L

        const val IMS_SETTLE_TIMEOUT_MS = 30_000L
        const val IMS_POLL_INTERVAL_MS = 1_000L
    }
}
