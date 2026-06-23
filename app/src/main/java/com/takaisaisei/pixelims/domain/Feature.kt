package com.takaisaisei.pixelims.domain

/**
 * The single catalog of every IMS feature the app manages. Each entry owns all of its *behavior*:
 * - [key]: SharedPreferences key prefix (must stay stable - read by the broker at apply time);
 * - [default]: first-run / restore value;
 * - [userFacing]: whether it is shown as a toggle (hidden ones are still applied with their default);
 * - [overrides]: the CarrierConfigManager keys this feature drives.
 */
enum class Feature(
    val key: String,
    val default: Boolean,
    val userFacing: Boolean,
    val overrides: List<Override>,
) {
    VOLTE(
        "volte", default = true, userFacing = true, overrides = listOf(
            Override("carrier_volte_available_bool"),
            Override("enhanced_4g_lte_on_by_default_bool"),
            Override("hide_enhanced_4g_lte_bool", inverted = true),
            Override("editable_enhanced_4g_lte_bool"),
            Override("carrier_volte_provisioned_bool"),
            Override("carrier_volte_provisioning_required_bool", const = false),
        )
    ),
    VONR(
        "vonr", default = true, userFacing = true, overrides = listOf(
            Override("vonr_enabled_bool"),
            Override("vonr_setting_visibility_bool"),
        )
    ),
    VOWIFI(
        "vowifi", default = true, userFacing = true, overrides = listOf(
            Override("carrier_wfc_ims_available_bool"),
            Override("carrier_default_wfc_ims_enabled_bool"),
            Override("carrier_wfc_ims_provisioned_bool"),
            Override("editable_wfc_mode_bool"),
            Override("editable_wfc_roaming_mode_bool"),
        )
    ),
    WFC_ROAMING(
        "wfc_roaming", default = true, userFacing = true, overrides = listOf(
            Override("carrier_default_wfc_ims_roaming_enabled_bool"),
        )
    ),
    SS_UT(
        "ss_ut", default = true, userFacing = true, overrides = listOf(
            Override("carrier_supports_ss_over_ut_bool"),
        )
    ),
    CROSS_SIM(
        "cross_sim", default = false, userFacing = false, overrides = listOf(
            Override("carrier_cross_sim_ims_available_bool"),
            Override("enable_cross_sim_calling_on_opportunistic_data_bool"),
        )
    ),
    SHOW_IMS(
        "show_ims", default = true, userFacing = false, overrides = listOf(
            Override("show_ims_registration_status_bool"),
        )
    ),
    ALLOW_APN(
        "allow_apn", default = false, userFacing = false, overrides = listOf(
            Override("allow_adding_apns_bool"),
        )
    ),
    ;

    /** The representative key read back to show the feature's effective on/off state. */
    val effectiveKey: String get() = overrides.first().key

    companion object {
        /** Features rendered as toggles / status rows, in display order. */
        val displayed: List<Feature> get() = entries.filter { it.userFacing }

        fun byKey(key: String): Feature? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One CarrierConfigManager key driven by a [Feature].
 * - [inverted]: store the negated feature value (e.g. "hide_*" keys);
 * - [const]: store this fixed value regardless of the feature value.
 */
data class Override(val key: String, val inverted: Boolean = false, val const: Boolean? = null) {
    fun valueFor(enabled: Boolean): Boolean = const ?: (enabled != inverted)
}
