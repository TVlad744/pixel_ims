package com.takaisaisei.pixelims.domain

/**
 * Per-slot configuration the user edits: an on/off flag per [Feature] plus, for features with a
 * user-editable [Override], its selected [CfgValue] keyed by the override's CarrierConfig key.
 */
data class SlotConfig(
    private val enabled: Map<Feature, Boolean> = emptyMap(),
    private val values: Map<String, CfgValue> = emptyMap(),
) {
    operator fun get(feature: Feature): Boolean = enabled[feature] ?: feature.default

    /** The stored value for an editable override [key], or null when unset. */
    fun value(key: String): CfgValue? = values[key]

    /** Convenience for int overrides: the selected value, or [default] when unset. */
    fun intValue(key: String, default: Int): Int = (values[key] as? CfgValue.IntVal)?.v ?: default

    fun with(feature: Feature, on: Boolean): SlotConfig = copy(enabled = enabled + (feature to on))

    fun withValue(key: String, value: CfgValue): SlotConfig = copy(values = values + (key to value))
}
