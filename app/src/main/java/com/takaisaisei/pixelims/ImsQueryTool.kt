package com.takaisaisei.pixelims

import com.takaisaisei.pixelims.domain.BrokerContract
import com.takaisaisei.pixelims.domain.Feature
import com.takaisaisei.pixelims.telephony.TelephonyReflection

/**
 * Minimal `app_process` entry point launched over ADB to read IMS state with shell privileges.
 *
 * Runs in a bare JVM (no Android `Context`), so it relies only on the context-free
 * [TelephonyReflection] and reports results on stdout using [BrokerContract].
 */
object ImsQueryTool {
    @JvmStatic
    fun main(args: Array<String>) {
        for (slot in 0 until TelephonyReflection.slotCount()) {
            // Only emit lines for slots that actually hold a SIM, so the UI can show one tab per
            // present SIM and never surface another slot's data.
            val subId = TelephonyReflection.subIdForSlot(slot) ?: continue
            println(BrokerContract.encodeIms(slot, TelephonyReflection.isImsRegistered(subId)))

            val features = Feature.displayed
            val effective =
                TelephonyReflection.readFeatureConfig(subId, features.map { it.effectiveKey })
            features.forEach { feature ->
                println(
                    BrokerContract.encodeFeature(
                        slot,
                        feature,
                        effective[feature.effectiveKey] == true
                    )
                )
            }
        }
    }
}
