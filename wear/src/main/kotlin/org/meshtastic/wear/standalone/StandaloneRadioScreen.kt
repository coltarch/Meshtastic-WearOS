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
@file:Suppress("ModifierMissing")

package org.meshtastic.wear.standalone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth_disabled
import org.meshtastic.core.resources.bluetooth_permission_blocked_notice
import org.meshtastic.core.resources.bluetooth_permission_rationale
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.resources.retry
import org.meshtastic.core.resources.standalone_radio_connected
import org.meshtastic.core.resources.standalone_radio_connecting
import org.meshtastic.core.resources.standalone_radio_connection
import org.meshtastic.core.resources.standalone_radio_connection_failed
import org.meshtastic.core.resources.standalone_radio_location_required
import org.meshtastic.core.resources.standalone_radio_no_radio
import org.meshtastic.core.resources.standalone_radio_permission_action
import org.meshtastic.core.resources.standalone_radio_scan
import org.meshtastic.core.resources.standalone_radio_scan_failed
import org.meshtastic.core.resources.standalone_radio_scanning
import org.meshtastic.core.resources.standalone_radio_stop_scan
import org.meshtastic.core.resources.standalone_radio_unsupported
import org.meshtastic.core.resources.unknown_device
import org.meshtastic.core.resources.wear_mode_change_continue
import org.meshtastic.core.ui.util.PermissionGateAction
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.permissionGateAction
import org.meshtastic.core.ui.util.rememberBluetoothPermissionState
import org.meshtastic.wear.presentation.COLOR_BG_DEEP
import org.meshtastic.wear.presentation.COLOR_SURFACE1
import org.meshtastic.wear.presentation.components.SectionHeader

@Composable
fun StandaloneRadioScreen(viewModel: StandaloneRadioViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val permission = rememberBluetoothPermissionState()
    var permissionStatusBeforeRequest by remember { mutableStateOf<PermissionStatus?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    LaunchedEffect(permission.status) {
        val previousStatus = permissionStatusBeforeRequest ?: return@LaunchedEffect
        if (permission.status == previousStatus) return@LaunchedEffect
        permissionStatusBeforeRequest = null
        if (permission.status == PermissionStatus.GRANTED) {
            viewModel.startDiscovery()
        } else {
            viewModel.permissionDenied()
        }
    }

    if (showPermissionRationale) {
        PermissionRationaleScreen(
            onContinue = {
                showPermissionRationale = false
                permissionStatusBeforeRequest = permission.status
                permission.request()
            },
            onCancel = {
                showPermissionRationale = false
                viewModel.permissionDenied()
            },
        )
        return
    }

    val beginDiscovery = {
        when (permissionGateAction(permission.status)) {
            PermissionGateAction.PROCEED -> viewModel.startDiscovery()

            PermissionGateAction.REQUEST -> {
                permissionStatusBeforeRequest = permission.status
                permission.request()
            }

            PermissionGateAction.SHOW_RATIONALE -> showPermissionRationale = true

            PermissionGateAction.OPEN_SETTINGS -> {
                viewModel.permissionDenied()
                permission.openAppSettings()
            }
        }
    }

    StandaloneRadioContent(
        state = state,
        devices = devices,
        permissionPermanentlyDenied = permission.status == PermissionStatus.PERMANENTLY_DENIED,
        onPrimaryAction =
        if (
            state == StandaloneRadioState.PermissionDenied &&
            permission.status == PermissionStatus.PERMANENTLY_DENIED
        ) {
            permission.openAppSettings
        } else {
            beginDiscovery
        },
        onStopDiscovery = viewModel::stopDiscovery,
        onDisconnect = viewModel::disconnect,
        onConnect = { device ->
            if (permission.status == PermissionStatus.GRANTED) viewModel.connect(device) else beginDiscovery()
        },
    )
}

@Composable
private fun StandaloneRadioContent(
    state: StandaloneRadioState,
    devices: List<BleDevice>,
    permissionPermanentlyDenied: Boolean,
    onPrimaryAction: () -> Unit,
    onStopDiscovery: () -> Unit,
    onDisconnect: () -> Unit,
    onConnect: (BleDevice) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { SectionHeader(stringResource(Res.string.standalone_radio_connection)) }
            item {
                Text(
                    text = standaloneStatusText(state),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Button(
                    onClick =
                    when (state) {
                        StandaloneRadioState.Scanning -> onStopDiscovery
                        is StandaloneRadioState.Connected -> onDisconnect
                        else -> onPrimaryAction
                    },
                    enabled =
                    state !is StandaloneRadioState.Connecting && state != StandaloneRadioState.UnsupportedHardware,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Text(
                        text =
                        when {
                            state == StandaloneRadioState.Scanning ->
                                stringResource(Res.string.standalone_radio_stop_scan)

                            state == StandaloneRadioState.PermissionDenied ->
                                stringResource(
                                    if (permissionPermanentlyDenied) {
                                        Res.string.open_settings
                                    } else {
                                        Res.string.standalone_radio_permission_action
                                    },
                                )

                            state is StandaloneRadioState.Connected -> stringResource(Res.string.disconnect)

                            state == StandaloneRadioState.ConnectionFailed ||
                                state == StandaloneRadioState.ScanUnavailable -> stringResource(Res.string.retry)

                            else -> stringResource(Res.string.standalone_radio_scan)
                        },
                    )
                }
            }
            items(devices) { device -> RadioDeviceButton(device = device, onClick = { onConnect(device) }) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun RadioDeviceButton(device: BleDevice, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE1),
    ) {
        Text(text = device.name ?: stringResource(Res.string.unknown_device))
    }
}

@Composable
private fun standaloneStatusText(state: StandaloneRadioState): String = when (state) {
    StandaloneRadioState.Ready -> stringResource(Res.string.standalone_radio_no_radio)

    StandaloneRadioState.Scanning -> stringResource(Res.string.standalone_radio_scanning)

    is StandaloneRadioState.Connecting ->
        stringResource(
            Res.string.standalone_radio_connecting,
            state.deviceName ?: stringResource(Res.string.unknown_device),
        )

    is StandaloneRadioState.Connected ->
        stringResource(
            Res.string.standalone_radio_connected,
            state.deviceName ?: stringResource(Res.string.unknown_device),
        )

    StandaloneRadioState.PermissionDenied -> stringResource(Res.string.bluetooth_permission_blocked_notice)

    StandaloneRadioState.UnsupportedHardware -> stringResource(Res.string.standalone_radio_unsupported)

    StandaloneRadioState.BluetoothDisabled -> stringResource(Res.string.bluetooth_disabled)

    StandaloneRadioState.LocationServicesDisabled -> stringResource(Res.string.standalone_radio_location_required)

    StandaloneRadioState.ScanUnavailable -> stringResource(Res.string.standalone_radio_scan_failed)

    StandaloneRadioState.ConnectionFailed -> stringResource(Res.string.standalone_radio_connection_failed)
}

@Composable
private fun PermissionRationaleScreen(onContinue: () -> Unit, onCancel: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { SectionHeader(stringResource(Res.string.standalone_radio_connection)) }
            item {
                Text(
                    text = stringResource(Res.string.bluetooth_permission_rationale),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            item {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.wear_mode_change_continue))
                }
            }
            item {
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        }
    }
}
