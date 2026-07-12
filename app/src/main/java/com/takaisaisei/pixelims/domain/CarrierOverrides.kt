package com.takaisaisei.pixelims.domain

import android.os.PersistableBundle

/**
 * Projects a [SlotConfig] onto the CarrierConfigManager bundle by walking each [Feature] and applying
 * its declared [Override]s.
 */
fun SlotConfig.toCarrierOverrides(): PersistableBundle = PersistableBundle().apply {
    for (feature in Feature.entries) {
        val enabled = this@toCarrierOverrides[feature]
        for (override in feature.overrides) {
            val user = this@toCarrierOverrides.value(override.key)
            override.bundleValue(enabled, user).putInto(this, override.key)
        }
    }
}
