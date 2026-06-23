package com.takaisaisei.pixelims

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Bundle
import android.util.Log
import com.takaisaisei.pixelims.data.SettingsRepository
import com.takaisaisei.pixelims.domain.BrokerContract
import com.takaisaisei.pixelims.domain.toCarrierOverrides
import com.takaisaisei.pixelims.telephony.PrivilegedCarrierConfigurator
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
        Log.d(TAG, "BrokerInstrumentation starting (clear=$clear, slot=$slot)")

        Thread {
            try {
                runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
                    .onFailure { Log.e(TAG, "Failed to apply HiddenApiBypass exemptions", it) }

                val uiAutomation = connectUiAutomation()
                uiAutomation?.adoptShellPermissionIdentity()
                    ?: Log.e(TAG, "UiAutomation unavailable; cannot adopt shell identity")
                try {
                    patch(clear, slot)
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

    // Applies (or clears) the carrier-config overrides for the requested slot only, then resets IMS.
    private fun patch(clear: Boolean, slot: Int) {
        val settings = SettingsRepository(context)
        val configurator = PrivilegedCarrierConfigurator(context)
        val subs = configurator.activeSubscriptions().filter { it.slot == slot }
        Log.d(TAG, "Acting on ${subs.size} SIM(s) in slot $slot")

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
    }

    private companion object {
        const val TAG = "IMSBrokerInst"
        const val UI_AUTOMATION_RETRIES = 5
        const val UI_AUTOMATION_RETRY_DELAY_MS = 500L
    }
}
