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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.BluetoothState
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.WearOperatingMode
import org.meshtastic.wear.di.WearStartupState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StandaloneRadioViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unsupported hardware is safe and resolves no radio dependency`() = runTest(dispatcher) {
        var resolutions = 0
        val viewModel =
            viewModel(
                startupState = startupState(supported = false),
                bleScanner =
                lazy {
                    resolutions += 1
                    error("must remain lazy")
                },
                bluetoothRepository =
                lazy {
                    resolutions += 1
                    error("must remain lazy")
                },
            )

        viewModel.startDiscovery()
        advanceUntilIdle()

        assertEquals(StandaloneRadioState.UnsupportedHardware, viewModel.state.value)
        assertEquals(0, resolutions)
    }

    @Test
    fun `permission denial produces a safe state without resolving the scanner`() = runTest(dispatcher) {
        var scannerResolutions = 0
        val bluetoothRepository = FakeBluetoothRepository(BluetoothState(hasPermissions = false, enabled = true))
        val viewModel =
            viewModel(
                startupState = startupState(supported = true),
                bleScanner =
                lazy {
                    scannerResolutions += 1
                    object : BleScanner {
                        override fun scan(
                            timeout: kotlin.time.Duration,
                            serviceUuid: kotlin.uuid.Uuid?,
                            address: String?,
                        ) = emptyFlow<BleDevice>()
                    }
                },
                bluetoothRepository = lazy { bluetoothRepository },
            )

        viewModel.startDiscovery()
        advanceUntilIdle()

        assertEquals(StandaloneRadioState.PermissionDenied, viewModel.state.value)
        assertEquals(0, scannerResolutions)
    }

    private fun viewModel(
        startupState: WearStartupState,
        bleScanner: Lazy<BleScanner>,
        bluetoothRepository: Lazy<BluetoothRepository>,
    ): StandaloneRadioViewModel = StandaloneRadioViewModel(
        bleScanner = bleScanner,
        bluetoothRepository = bluetoothRepository,
        radioController = lazy<RadioController> { error("radio controller must remain lazy") },
        radioPrefs = lazy<RadioPrefs> { error("radio preferences must remain lazy") },
        transportFactory = lazy<RadioTransportFactory> { error("transport factory must remain lazy") },
        startupState = startupState,
    )

    private fun startupState(supported: Boolean) =
        WearStartupState().also { it.initialize(WearOperatingMode.STANDALONE_RADIO, supported) }
}

private class FakeBluetoothRepository(initialState: BluetoothState) : BluetoothRepository {
    override val state = MutableStateFlow(initialState)

    override fun refreshState() = Unit

    override fun isValid(bleAddress: String): Boolean = true

    override fun isBonded(address: String): Boolean = false

    override suspend fun bond(device: BleDevice) = Unit
}
