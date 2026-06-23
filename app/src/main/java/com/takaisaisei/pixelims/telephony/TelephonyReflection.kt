package com.takaisaisei.pixelims.telephony

import android.os.IBinder
import android.os.PersistableBundle

/**
 * Context-free reflective access to the privileged telephony interfaces.
 *
 * Deliberately depends only on `ServiceManager` reflection (no Android `Context`) so the exact same
 * code is reused by [com.takaisaisei.pixelims.ImsQueryTool], which runs in a bare `app_process`
 * JVM with no Application/Context available.
 */
object TelephonyReflection {

    /**
     * Number of SIM slots the device physically exposes, asked of the modem (the same value behind
     * `TelephonyManager.getActiveModemCount()`). Falls back to dual-SIM only if the query fails.
     */
    fun slotCount(): Int = runCatching {
        val telephony = asInterface(SERVICE_PHONE, ITELEPHONY) ?: return DEFAULT_SLOT_COUNT
        val clazz = Class.forName(ITELEPHONY)
        sequenceOf("getActiveModemCount", "getSupportedModemCount")
            .mapNotNull { name ->
                runCatching { clazz.getMethod(name).invoke(telephony) as? Int }.getOrNull()
            }
            .firstOrNull { it > 0 }
            ?: DEFAULT_SLOT_COUNT
    }.getOrDefault(DEFAULT_SLOT_COUNT)

    /** Resolves the active subscription id for a SIM [slot], or null when the slot is empty. */
    fun subIdForSlot(slot: Int): Int? = runCatching {
        val iSub = asInterface(SERVICE_ISUB, ISUB) ?: return null
        val method = Class.forName(ISUB)
            .getMethod("getSubId", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        (method.invoke(iSub, slot) as? Int)?.takeIf { it != INVALID_SUBSCRIPTION_ID }
    }.getOrNull()

    /** Returns whether IMS is currently registered for [subId]. */
    fun isImsRegistered(subId: Int): Boolean = runCatching {
        val telephony = asInterface(SERVICE_PHONE, ITELEPHONY) ?: return false
        val method = Class.forName(ITELEPHONY)
            .getMethod("isImsRegistered", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        method.invoke(telephony, subId) as Boolean
    }.getOrDefault(false)

    /**
     * Reads the *effective* carrier configuration for [subId] and returns the boolean value of each
     * requested [keys] entry. Runs unfiltered because the caller (the shell-uid `app_process`) holds
     * `READ_PRIVILEGED_PHONE_STATE`. Missing keys default to false.
     */
    fun readFeatureConfig(subId: Int, keys: Collection<String>): Map<String, Boolean> =
        runCatching {
            val loader =
                asInterface(SERVICE_CARRIER_CONFIG, ICARRIER_CONFIG_LOADER) ?: return emptyMap()
            val loaderClass = Class.forName(ICARRIER_CONFIG_LOADER)
            val bundle = (
                    runCatching {
                        loaderClass.getMethod(
                            "getConfigForSubIdWithFeature",
                            Int::class.javaPrimitiveType,
                            String::class.java,
                            String::class.java,
                        ).invoke(loader, subId, CALLER_PACKAGE, null)
                    }.getOrNull()
                        ?: runCatching {
                            loaderClass.getMethod(
                                "getConfigForSubId",
                                Int::class.javaPrimitiveType,
                                String::class.java,
                            ).invoke(loader, subId, CALLER_PACKAGE)
                        }.getOrNull()
                    ) as? PersistableBundle ?: return emptyMap()
            keys.associateWith { bundle.getBoolean(it, false) }
        }.getOrDefault(emptyMap())

    // Looks up a system service binder and wraps it in its AIDL stub interface.
    private fun asInterface(serviceName: String, interfaceName: String): Any? {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager
            .getMethod("getService", String::class.java)
            .invoke(null, serviceName) as? IBinder ?: return null
        val stub = Class.forName("$interfaceName\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
    }

    private const val INVALID_SUBSCRIPTION_ID = -1
    private const val DEFAULT_SLOT_COUNT = 2
    private const val SERVICE_ISUB = "isub"
    private const val SERVICE_PHONE = "phone"
    private const val SERVICE_CARRIER_CONFIG = "carrier_config"
    private const val ISUB = "com.android.internal.telephony.ISub"
    private const val ITELEPHONY = "com.android.internal.telephony.ITelephony"
    private const val ICARRIER_CONFIG_LOADER = "com.android.internal.telephony.ICarrierConfigLoader"

    // Caller package attributed for the privileged read; matches the shell uid we run under.
    private const val CALLER_PACKAGE = "com.android.shell"
}
