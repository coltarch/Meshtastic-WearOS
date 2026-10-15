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
package org.meshtastic.wear.standalone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.radio.BaseRadioTransportFactory
import org.meshtastic.core.network.radio.NopRadioTransport
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport

/** Wear-specific direct-radio factory: BLE only, with no USB or TCP platform dependencies. */
class WearBleRadioTransportFactory(
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
) : BaseRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {
    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE)
    override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    override val isReplayTransportAvailable: Boolean = false

    override fun isAddressValid(address: String?): Boolean =
        address?.startsWith(InterfaceId.BLUETOOTH.id) == true || address?.startsWith("!") == true

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
        NopRadioTransport(address)
}
