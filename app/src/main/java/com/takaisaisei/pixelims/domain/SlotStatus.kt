package com.takaisaisei.pixelims.domain

/**
 * Live, system-truth state for a slot, polled with shell privileges.
 *
 * [loaded] is false until the first poll for this slot returns. [effective] holds the *currently
 * active* carrier-config value per displayed feature, read straight from CarrierConfigManager.
 */
data class SlotStatus(
    val loaded: Boolean = false,
    val imsRegistered: Boolean = false,
    val effective: Map<Feature, Boolean> = emptyMap(),
)
