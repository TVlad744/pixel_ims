package com.takaisaisei.pixelims.domain

import java.util.EnumMap

/**
 * The single, typed source of truth for every cross-process string contract between the UI process,
 * [com.takaisaisei.pixelims.BrokerInstrumentation] and [com.takaisaisei.pixelims.ImsQueryTool].
 *
 * Pure Kotlin with no Android dependencies, so it can be referenced from the context-free
 * `app_process` entry point as well as from the app and the instrumentation.
 *
 * ## ImsQueryTool stdout protocol
 * One line per fact, so the UI can aggregate them into a [SlotQuery] per slot:
 * ```
 * IMS:<slot>:<bool>                 // IMS registration for the slot
 * FEAT:<slot>:<flagKey>:<bool>      // effective carrier-config value for one feature
 * ```
 */
object BrokerContract {

    /** `am instrument -e <key> <value>` argument: whether to clear overrides instead of applying. */
    const val ARG_CLEAR = "clear"

    /** `am instrument -e <key> <value>` argument: the single SIM slot to act on. */
    const val ARG_SLOT = "slot"

    private const val IMS_PREFIX = "IMS:"
    private const val FEATURE_PREFIX = "FEAT:"

    fun encodeIms(slot: Int, registered: Boolean): String = "$IMS_PREFIX$slot:$registered"

    fun encodeFeature(slot: Int, feature: Feature, enabled: Boolean): String =
        "$FEATURE_PREFIX$slot:${feature.key}:$enabled"

    /** Aggregates the multi-line query [output] into one [SlotQuery] per slot it mentions. */
    fun decodeQuery(output: String): List<SlotQuery> {
        val ims = HashMap<Int, Boolean>()
        val features = HashMap<Int, MutableMap<Feature, Boolean>>()

        output.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith(IMS_PREFIX) -> {
                    val parts = line.removePrefix(IMS_PREFIX).split(":")
                    if (parts.size == 2) {
                        parts[0].toIntOrNull()?.let { slot -> ims[slot] = parts[1].toBoolean() }
                    }
                }

                line.startsWith(FEATURE_PREFIX) -> {
                    val parts = line.removePrefix(FEATURE_PREFIX).split(":")
                    if (parts.size == 3) {
                        val slot = parts[0].toIntOrNull()
                        val feature = Feature.byKey(parts[1])
                        if (slot != null && feature != null) {
                            features.getOrPut(slot) { EnumMap(Feature::class.java) }[feature] =
                                parts[2].toBoolean()
                        }
                    }
                }
            }
        }

        return (ims.keys + features.keys).toSortedSet().map { slot ->
            SlotQuery(
                slot = slot,
                imsRegistered = ims[slot] == true,
                effective = features[slot]?.toMap() ?: emptyMap(),
            )
        }
    }
}
