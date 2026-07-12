package com.takaisaisei.pixelims.ui

import android.app.Application
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.takaisaisei.pixelims.ApplyMode
import com.takaisaisei.pixelims.R
import com.takaisaisei.pixelims.adb.AdbController
import com.takaisaisei.pixelims.adb.AdbDiscovery
import com.takaisaisei.pixelims.adb.AdbEndpoint
import com.takaisaisei.pixelims.data.SettingsRepository
import com.takaisaisei.pixelims.domain.CfgValue
import com.takaisaisei.pixelims.domain.Feature
import com.takaisaisei.pixelims.domain.SlotQuery
import com.takaisaisei.pixelims.domain.SlotStatus
import com.takaisaisei.pixelims.system.ConnectivityMonitor
import com.takaisaisei.pixelims.system.NotificationController
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val adb = AdbController(application)
    private val discovery = AdbDiscovery(application)
    private val connectivity = ConnectivityMonitor(application)
    private val notifications = NotificationController(application)
    private val subscriptions = application.getSystemService(SubscriptionManager::class.java)
    private val telephony = application.getSystemService(TelephonyManager::class.java)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _pairingSucceeded = Channel<Unit>(Channel.BUFFERED)
    val pairingSucceeded = _pairingSucceeded.receiveAsFlow()

    private var authJob: Job? = null
    private var pollJob: Job? = null
    private var discoveryJob: Job? = null
    private var isForeground = true

    init {
        _uiState.update { withConfig(it, it.selectedSlot).copy(bootSlots = settings.bootSlots()) }
        startDiscovery()
        observeWifi()
    }

    /** Driven by the Activity lifecycle: stop all ADB work in the background, resume in foreground. */
    fun setForeground(foreground: Boolean) {
        if (foreground == isForeground) return
        isForeground = foreground
        if (foreground) {
            startDiscovery()
            // Discovery won't re-emit an unchanged port, so resume polling for the known one.
            if (_uiState.value.port != null) restartAdbForPort()
        } else {
            // Backgrounded: stop discovery and release the ADB port.
            if (_uiState.value.isAuthorized) stopDiscovery()
            authJob?.cancel()
            pollJob?.cancel()
        }
    }

    fun selectSlot(slot: Int) = _uiState.update { withConfig(it, slot).copy(selectedSlot = slot) }

    fun setApplyOnBoot(slot: Int, value: Boolean) {
        settings.setApplyOnBoot(slot, value)
        _uiState.update { it.copy(bootSlots = settings.bootSlots()) }
    }

    fun setValue(slot: Int, key: String, value: CfgValue) {
        settings.setValue(slot, key, value)
        _uiState.update { state ->
            state.copy(configs = state.configs + (slot to state.config(slot).withValue(key, value)))
        }
    }

    fun setFlag(slot: Int, feature: Feature, value: Boolean) {
        settings.setFlag(slot, feature, value)
        val siblingsToClear = if (value) feature.exclusiveSiblings else emptyList()
        siblingsToClear.forEach { settings.setFlag(slot, it, false) }
        _uiState.update { state ->
            var config = state.config(slot).with(feature, value)
            siblingsToClear.forEach { config = config.with(it, false) }
            state.copy(configs = state.configs + (slot to config))
        }
    }

    private fun setPort(port: Int) {
        if (port == _uiState.value.port) return
        _uiState.update { it.copy(port = port) }
        restartAdbForPort()
    }

    fun apply() = runApply(clear = false, failureMessage = R.string.msg_apply_failed)

    fun restore() {
        val slot = _uiState.value.selectedSlot
        settings.resetToDefaults(slot)
        _uiState.update {
            it.copy(
                configs = it.configs + (slot to settings.readConfig(slot)),
                bootSlots = settings.bootSlots(),
            )
        }
        runApply(clear = true, failureMessage = R.string.msg_restore_failed)
    }

    fun submitPairingCode(code: String) {
        val pairingPort = _uiState.value.pairingPort
        if (pairingPort == null) {
            emit(UiMessage(R.string.msg_pairing_no_port))
            notifications.showPairingStatus(getString(R.string.notif_pairing_status_no_port))
            return
        }
        emit(UiMessage(R.string.msg_pairing_in_progress))
        notifications.showPairingStatus(getString(R.string.notif_pairing_status, pairingPort))
        viewModelScope.launch {
            adb.pair(pairingPort, code).fold(
                onSuccess = {
                    emit(UiMessage(R.string.msg_pairing_success))
                    notifications.cancelPairing()
                    _pairingSucceeded.trySend(Unit)
                    restartAdbForPort()
                },
                onFailure = { error ->
                    emit(UiMessage(R.string.msg_pairing_failed, error.message))
                    notifications.showPairingStatus(
                        getString(R.string.notif_pairing_status_failed, error.message.orEmpty()),
                    )
                },
            )
        }
    }

    private fun runApply(clear: Boolean, failureMessage: Int) {
        val port = _uiState.value.port
        if (port == null) {
            emit(UiMessage(R.string.msg_enable_wireless_first))
            return
        }
        val slot = _uiState.value.selectedSlot
        _uiState.update {
            it.copy(isApplying = true, statuses = it.statuses + (slot to SlotStatus()))
        }
        viewModelScope.launch {
            if (ApplyMode.detached) {
                notifications.showApplying()
            }
            val result = adb.runApply(port, slot, clear, notify = false)
            _uiState.update { it.copy(isApplying = false) }
            result.onFailure { error -> emit(UiMessage(failureMessage, error.message)) }
        }
    }

    private fun withConfig(state: MainUiState, slot: Int): MainUiState =
        if (state.configs.containsKey(slot)) state
        else state.copy(configs = state.configs + (slot to settings.readConfig(slot)))

    private fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            discovery.discover().collect { endpoint ->
                when (endpoint) {
                    is AdbEndpoint.Connect -> setPort(endpoint.port)
                    is AdbEndpoint.Pairing ->
                        _uiState.update { it.copy(pairingPort = endpoint.port) }
                }
            }
        }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    private fun observeWifi() = viewModelScope.launch {
        connectivity.wifiConnected().collect { connected ->
            _uiState.update { it.copy(isWifiConnected = connected) }
        }
    }

    private fun restartAdbForPort() {
        authJob?.cancel()
        pollJob?.cancel()
        val port = _uiState.value.port
        if (port == null) {
            _uiState.update { it.copy(isAuthorized = false) }
            return
        }
        authJob = viewModelScope.launch {
            val authorized = adb.isAuthorized(port)
            _uiState.update { it.copy(isAuthorized = authorized) }
            if (authorized) adb.grantPermissionsIfNeeded(port)
        }
        pollJob = viewModelScope.launch {
            adb.pollImsStatus(
                port,
                isPaused = { _uiState.value.isApplying },
                onResult = ::applyQuery
            )
        }
    }

    private fun applyQuery(query: SlotQuery) = _uiState.update { state ->
        val status = SlotStatus(
            loaded = true,
            imsRegistered = query.imsRegistered,
            effective = query.effective
        )
        val knownSlots = state.knownSlots + query.slot
        val selectedSlot =
            if (state.selectedSlot in knownSlots) state.selectedSlot else knownSlots.min()
        val carrierNames = carrierName(query.slot)
            ?.let { state.carrierNames + (query.slot to it) }
            ?: state.carrierNames
        withConfig(state, query.slot).copy(
            statuses = state.statuses + (query.slot to status),
            knownSlots = knownSlots,
            selectedSlot = selectedSlot,
            carrierNames = carrierNames,
        )
    }

    private fun carrierName(slot: Int): String? = runCatching {
        val info = subscriptions?.getActiveSubscriptionInfoForSimSlotIndex(slot) ?: return null
        val tm = telephony?.createForSubscriptionId(info.subscriptionId) ?: return null
        tm.simOperatorName?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun emit(message: UiMessage) {
        _messages.trySend(message)
    }

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
