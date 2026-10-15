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
package org.meshtastic.wear.di

import org.meshtastic.core.repository.WearOperatingMode
import kotlin.test.Test
import kotlin.test.assertEquals

class WearStartupStateTest {
    @Test
    fun `startup state transitions from loading to ready`() {
        val state = WearStartupState()

        assertEquals(WearStartupStatus.Loading, state.status.value)

        state.initialize(WearOperatingMode.STANDALONE_RADIO, standaloneSupported = true)

        assertEquals(WearStartupStatus.Ready(WearOperatingMode.STANDALONE_RADIO), state.status.value)
    }

    @Test
    fun `startup failure is explicit`() {
        val state = WearStartupState()

        state.fail()

        assertEquals(WearStartupStatus.Failed, state.status.value)
    }

    @Test
    fun `companion remains the startup default`() {
        assertEquals(
            WearOperatingMode.COMPANION,
            selectWearStartupMode(WearOperatingMode.COMPANION, standaloneSupported = true),
        )
    }

    @Test
    fun `unsupported standalone startup falls back to companion`() {
        assertEquals(
            WearOperatingMode.COMPANION,
            selectWearStartupMode(WearOperatingMode.STANDALONE_RADIO, standaloneSupported = false),
        )
    }

    @Test
    fun `supported standalone startup remains standalone`() {
        assertEquals(
            WearOperatingMode.STANDALONE_RADIO,
            selectWearStartupMode(WearOperatingMode.STANDALONE_RADIO, standaloneSupported = true),
        )
    }
}
