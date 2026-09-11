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
        runCatching { findMethod(telephony, "resetIms", preferredArity = 1)?.invoke(telephony, slot) }
            .onFailure { Log.e(TAG, "Failed to reset IMS for slot $slot", it) }
    }

    private fun override(subId: Int, overrides: PersistableBundle?): Boolean {
        val manager = carrierConfig ?: return false
        val method = findMethod(manager, "overrideConfig", preferredArity = 3) ?: return false
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

    /**
    * Finds a (possibly hidden) method by name, walking the class hierarchy and its interfaces.
    *
    * [preferredArity] disambiguates overloads: a method with exactly that many parameters wins.
    * Without it — or if nothing matches — the candidate with the most parameters is used, so
    * extended hidden signatures win over their legacy wrappers.
    */
    private fun findMethod(target: Any?, name: String, preferredArity: Int? = null): Method? {
        if (target == null) return null

        val candidates = LinkedHashSet<Method>()
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            clazz.declaredMethods.filterTo(candidates) { it.name == name }
            for (iface in clazz.interfaces) {
                iface.declaredMethods.filterTo(candidates) { it.name == name }
            }
            clazz = clazz.superclass
        }
        if (candidates.isEmpty()) return null

        val chosen = preferredArity?.let { arity ->
            candidates.firstOrNull { it.parameterTypes.size == arity }
        } ?: candidates.maxByOrNull { it.parameterTypes.size }

        return chosen?.apply { isAccessible = true }
    }

    private companion object {
        const val TAG = "CarrierConfigurator"
    }
}
