package com.takaisaisei.pixelims.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.takaisaisei.pixelims.domain.Feature
import com.takaisaisei.pixelims.domain.SlotConfig

/**
 * Single source of truth for the persisted per-slot carrier configuration.
 *
 * The on-disk format (SharedPreferences keys such as `volte_slot_0`, `vonr_slot_1`, …) is read by
 * [com.takaisaisei.pixelims.BrokerInstrumentation] at apply time, so the key names must not
 * change.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readConfig(slot: Int): SlotConfig = SlotConfig(
        Feature.entries.associateWith { prefs.getBoolean(flagKey(it, slot), it.default) },
    )

    fun setFlag(slot: Int, feature: Feature, value: Boolean) {
        prefs.edit(commit = true) { putBoolean(flagKey(feature, slot), value) }
    }

    fun resetToDefaults(slot: Int) {
        prefs.edit(commit = true) {
            Feature.entries.forEach { putBoolean(flagKey(it, slot), it.default) }
            putStringSet(KEY_BOOT_SLOTS, encodeSlots(bootSlots() - slot))
        }
    }

    /** Slots the user opted to apply automatically on boot. */
    fun bootSlots(): Set<Int> =
        prefs.getStringSet(KEY_BOOT_SLOTS, emptySet()).orEmpty().mapNotNull(String::toIntOrNull)
            .toSet()

    fun setApplyOnBoot(slot: Int, enabled: Boolean) {
        val updated = if (enabled) bootSlots() + slot else bootSlots() - slot
        prefs.edit(commit = true) { putStringSet(KEY_BOOT_SLOTS, encodeSlots(updated)) }
    }

    private fun flagKey(feature: Feature, slot: Int) = "${feature.key}_slot_$slot"

    private fun encodeSlots(slots: Set<Int>): Set<String> = slots.map(Int::toString).toSet()

    companion object {
        private const val PREFS_NAME = "ims_settings"
        private const val KEY_BOOT_SLOTS = "apply_on_boot_slots"
    }
}
