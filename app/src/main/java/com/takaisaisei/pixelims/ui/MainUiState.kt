package com.takaisaisei.pixelims.ui

import androidx.annotation.StringRes
import com.takaisaisei.pixelims.domain.SlotConfig
import com.takaisaisei.pixelims.domain.SlotStatus

/** Immutable snapshot of everything the main screen renders. */
data class MainUiState(
    val selectedSlot: Int = 0,
    /** Per-slot config/status, filled in lazily as slots are selected or discovered by the poll. */
    val configs: Map<Int, SlotConfig> = emptyMap(),
    val statuses: Map<Int, SlotStatus> = emptyMap(),
    /** Per-slot SIM operator name. */
    val carrierNames: Map<Int, String> = emptyMap(),
    /** Slots that actually hold a SIM, learned from the privileged poll. */
    val knownSlots: Set<Int> = emptySet(),
    /** Slots flagged to re-apply automatically after a reboot. */
    val bootSlots: Set<Int> = emptySet(),
    /** The device's own Wireless Debugging connect port, discovered via mDNS. */
    val port: Int? = null,
    val pairingPort: Int? = null,
    val isAuthorized: Boolean = false,
    val isWifiConnected: Boolean = false,
    val isApplying: Boolean = false,
) {
    val hasPort: Boolean get() = port != null

    /** SIM tabs to render: the detected SIMs, or just the first slot before the poll has run. */
    val slotTabs: List<Int> get() = knownSlots.sorted().ifEmpty { listOf(0) }

    fun config(slot: Int): SlotConfig = configs[slot] ?: SlotConfig()
    fun status(slot: Int): SlotStatus = statuses[slot] ?: SlotStatus()
    fun carrierName(slot: Int): String? = carrierNames[slot]
    fun applyOnBoot(slot: Int): Boolean = slot in bootSlots
}

/** One-shot message surfaced to the user (e.g. as a toast). */
data class UiMessage(@StringRes val resId: Int, val arg: String? = null)
