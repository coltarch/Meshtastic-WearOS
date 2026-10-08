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
package org.meshtastic.wear.data

import kotlin.test.Test
import kotlin.test.assertEquals

class WearCompanionSyncStateHolderTest {
    @Test
    fun `initial timeout becomes waiting for first sync`() {
        val holder = WearCompanionSyncStateHolder()

        holder.beginAttempt()
        holder.syncTimedOut()

        assertEquals(WearCompanionSyncState.WaitingForFirstSync, holder.state.value)
    }

    @Test
    fun `refresh failure preserves completed sync status for later retry`() {
        val holder = WearCompanionSyncStateHolder()
        holder.syncCompleted()

        holder.beginAttempt()
        assertEquals(WearCompanionSyncState.Retrying, holder.state.value)

        holder.syncFailed()
        assertEquals(WearCompanionSyncState.SyncFailed, holder.state.value)

        holder.beginAttempt()
        assertEquals(WearCompanionSyncState.Retrying, holder.state.value)
    }

    @Test
    fun `phone unavailability is distinct from synchronization failure`() {
        val holder = WearCompanionSyncStateHolder()

        holder.phoneUnavailable()

        assertEquals(WearCompanionSyncState.PhoneUnavailable, holder.state.value)
    }

    @Test
    fun `malformed payload failure cannot be overwritten by completion`() {
        val holder = WearCompanionSyncStateHolder()

        holder.beginAttempt()
        holder.syncFailed()
        holder.syncCompleted()

        assertEquals(WearCompanionSyncState.SyncFailed, holder.state.value)
    }
}
