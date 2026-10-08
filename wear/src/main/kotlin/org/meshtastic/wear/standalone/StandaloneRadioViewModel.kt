/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
@file:Suppress("TooGenericExceptionCaught")

package org.meshtastic.wear.standalone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanStartException
import org.meshtastic.core.ble.BleScanStartFailureReason
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.wear.di.WearStartupState
import kotlin.time.Duration.Companion.seconds

sealed interface StandaloneRadioState {
    data object Ready : StandaloneRadioState

    data object Scanning : StandaloneRadioState

    data class Connecting(val deviceName: String?) : StandaloneRadioState

    data class Connected(val deviceName: String?) : StandaloneRadioState

    data object PermissionDenied : StandaloneRadioState

    data object UnsupportedHardware : StandaloneRadioState

    data object BluetoothDisabled : StandaloneRadioState

    data object LocationServicesDisabled : StandaloneRadioState

    data object ScanUnavailable : StandaloneRadioState

    data object ConnectionFailed : StandaloneRadioState
}

class StandaloneRadioViewModel(
    private val bleScanner: Lazy<BleScanner>,
    private val bluetoothRepository: Lazy<BluetoothRepository>,
    private val radioController: Lazy<RadioController>,
    private val radioPrefs: Lazy<RadioPrefs>,
    private val transportFactory: Lazy<RadioTransportFactory>,
    startupState: WearStartupState,
) : ViewModel() {
    private val standaloneSupported = startupState.standaloneSupported
    private val mutableState =
        MutableStateFlow<StandaloneRadioState>(
            if (standaloneSupported) StandaloneRadioState.Ready else StandaloneRadioState.UnsupportedHardware,
        )
    val state: StateFlow<StandaloneRadioState> = mutableState.asStateFlow()

    private val mutableDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = mutableDevices.asStateFlow()

    private var scanJob: Job? = null
    private var connectionStateJob: Job? = null
    private var connectionAttemptJob: Job? = null

    fun startDiscovery() {
        if (!standaloneSupported) {
            mutableState.value = StandaloneRadioState.UnsupportedHardware
            return
        }
        scanJob?.cancel()
        mutableDevices.value = emptyList()
        scanJob =
            viewModelScope.launch {
                val bluetooth = bluetoothRepository.value
                bluetooth.refreshState()
                when {
                    !bluetooth.state.value.hasPermissions -> mutableState.value = StandaloneRadioState.PermissionDenied
                    !bluetooth.state.value.enabled -> mutableState.value = StandaloneRadioState.BluetoothDisabled
                    else -> collectScanResults()
                }
            }
    }

    private suspend fun collectScanResults() {
        mutableState.value = StandaloneRadioState.Scanning
        try {
            bleScanner.value.scan(SCAN_DURATION).collect { device ->
                mutableDevices.update { current ->
                    if (current.any { it.address == device.address }) current else current + device
                }
            }
            mutableState.value = StandaloneRadioState.Ready
        } catch (error: BleScanStartException) {
            mutableState.value = error.reason.toUiState()
            Logger.w { "Standalone BLE discovery unavailable: ${error.reason.label}" }
        } catch (_: Exception) {
            mutableState.value = StandaloneRadioState.ScanUnavailable
            Logger.w { "Standalone BLE discovery unavailable" }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        mutableState.value = StandaloneRadioState.Ready
    }

    fun permissionDenied() {
        stopDiscovery()
        mutableState.value = StandaloneRadioState.PermissionDenied
    }

    fun connect(device: BleDevice) {
        scanJob?.cancel()
        connectionAttemptJob?.cancel()
        connectionAttemptJob =
            viewModelScope.launch {
                val deviceName = device.name
                mutableState.value = StandaloneRadioState.Connecting(deviceName)
                try {
                    val controller = radioController.value
                    observeConnectionState(controller, deviceName)
                    val address = transportFactory.value.toInterfaceAddress(InterfaceId.BLUETOOTH, device.address)
                    radioPrefs.value.setDevName(device.name)
                    withTimeout(CONNECTION_TIMEOUT) {
                        controller.setDeviceAddress(address)
                        controller.connectionState.first { it == ConnectionState.Connected }
                    }
                    mutableState.value = StandaloneRadioState.Connected(deviceName)
                } catch (_: TimeoutCancellationException) {
                    mutableState.value = StandaloneRadioState.ConnectionFailed
                    Logger.w { "Standalone radio connection timed out" }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    mutableState.value = StandaloneRadioState.ConnectionFailed
                    Logger.w { "Standalone radio connection could not start" }
                }
            }
    }

    fun disconnect() {
        connectionAttemptJob?.cancel()
        viewModelScope.launch {
            try {
                if (radioController.isInitialized()) radioController.value.setDeviceAddress("")
                if (radioPrefs.isInitialized()) {
                    radioPrefs.value.setDevAddr(null)
                    radioPrefs.value.setDevName(null)
                }
            } catch (_: Exception) {
                Logger.w { "Standalone radio disconnect could not complete" }
            } finally {
                mutableState.value = StandaloneRadioState.Ready
            }
        }
    }

    private fun observeConnectionState(controller: RadioController, deviceName: String?) {
        connectionStateJob?.cancel()
        connectionStateJob =
            viewModelScope.launch {
                controller.connectionState.collect { connectionState ->
                    mutableState.value =
                        when (connectionState) {
                            ConnectionState.Connected -> StandaloneRadioState.Connected(deviceName)

                            ConnectionState.Connecting -> StandaloneRadioState.Connecting(deviceName)

                            ConnectionState.Disconnected,
                            ConnectionState.DeviceSleep,
                            ->
                                if (mutableState.value is StandaloneRadioState.Connected) {
                                    StandaloneRadioState.Ready
                                } else {
                                    mutableState.value
                                }
                        }
                }
            }
    }

    override fun onCleared() {
        scanJob?.cancel()
        connectionStateJob?.cancel()
        connectionAttemptJob?.cancel()
    }

    private companion object {
        val SCAN_DURATION = 30.seconds
        val CONNECTION_TIMEOUT = 30.seconds
    }
}

private fun BleScanStartFailureReason.toUiState(): StandaloneRadioState = when (this) {
    BleScanStartFailureReason.MissingScanPermission -> StandaloneRadioState.PermissionDenied

    BleScanStartFailureReason.BluetoothDisabled -> StandaloneRadioState.BluetoothDisabled

    BleScanStartFailureReason.LocationServicesDisabled -> StandaloneRadioState.LocationServicesDisabled

    BleScanStartFailureReason.ApplicationRegistrationFailed,
    BleScanStartFailureReason.ScanningTooFrequently,
    -> StandaloneRadioState.ScanUnavailable
}
