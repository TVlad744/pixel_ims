package com.takaisaisei.pixelims.domain

/** Per-slot feature configuration the user can toggle. Backed by a [Feature]-keyed map. */
@JvmInline
value class SlotConfig(private val flags: Map<Feature, Boolean> = emptyMap()) {

    operator fun get(feature: Feature): Boolean = flags[feature] ?: feature.default

    fun with(feature: Feature, value: Boolean): SlotConfig = SlotConfig(flags + (feature to value))
}
