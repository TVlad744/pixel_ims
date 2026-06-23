package com.takaisaisei.pixelims.domain

/**
 * The full per-slot snapshot produced by `ImsQueryTool` and parsed by the ADB poll: IMS registration
 * plus the effective carrier-config value for every displayed feature.
 */
data class SlotQuery(
    val slot: Int,
    val imsRegistered: Boolean,
    val effective: Map<Feature, Boolean>,
)
