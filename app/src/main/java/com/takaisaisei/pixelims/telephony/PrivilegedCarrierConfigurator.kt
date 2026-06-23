package com.takaisaisei.pixelims.telephony

import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import java.lang.reflect.Method

/** A SIM subscription paired with its physical slot index. */
data class SubscriptionSlot(val subId: Int, val slot: Int)

/**
 * Context-bound privileged operations that require the shell permission identity adopted by
 * [com.takaisaisei.pixelims.BrokerInstrumentation]: overriding carrier config and resetting IMS.
 */
class PrivilegedCarrierConfigurator(context: Context) {

    private val carrierConfig = context.getSystemService(CarrierConfigManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val subscriptions = context.getSystemService(SubscriptionManager::class.java)

    fun activeSubscriptions(): List<SubscriptionSlot> =
        subscriptions?.activeSubscriptionInfoList
            ?.map { SubscriptionSlot(it.subscriptionId, it.simSlotIndex) }
            .orEmpty()

    fun applyOverrides(subId: Int, overrides: PersistableBundle): Boolean =
        override(subId, overrides)

    fun clearOverrides(subId: Int): Boolean = override(subId, null)

    fun resetIms(slot: Int) {
        runCatching { findMethod(telephony, "resetIms")?.invoke(telephony, slot) }
            .onFailure { Log.e(TAG, "Failed to reset IMS for slot $slot", it) }
    }

    private fun override(subId: Int, overrides: PersistableBundle?): Boolean {
        val manager = carrierConfig ?: return false
        val method = findMethod(manager, "overrideConfig") ?: return false
        return runCatching {
            // Older signature: overrideConfig(subId, bundle); newer: overrideConfig(subId, bundle, persistent)
            if (method.parameterTypes.size == 3) {
                method.invoke(manager, subId, overrides, true)
            } else {
                method.invoke(manager, subId, overrides)
            }
            true
        }.onFailure { Log.e(TAG, "Failed to override carrier config for sub $subId", it) }
            .getOrDefault(false)
    }

    // Finds a (possibly hidden) method by name walking the class hierarchy and interfaces.
    private fun findMethod(target: Any?, name: String): Method? {
        if (target == null) return null
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            clazz.declaredMethods.firstOrNull { it.name == name }
                ?.let { return it.apply { isAccessible = true } }
            clazz = clazz.superclass
        }
        for (iface in target.javaClass.interfaces) {
            iface.declaredMethods.firstOrNull { it.name == name }
                ?.let { return it.apply { isAccessible = true } }
        }
        return null
    }

    private companion object {
        const val TAG = "CarrierConfigurator"
    }
}
