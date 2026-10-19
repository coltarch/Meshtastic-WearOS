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
package org.meshtastic.core.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchSyncStateHolderTest {
    private val holder = WatchSyncStateHolder()

    @Test
    fun `a manual sync request is refused while a connection check is active`() {
        assertTrue(holder.beginManualSyncRequest())
        assertEquals(WatchConnectionState.CheckingConnection, holder.state.value.connectionState)
        assertFalse(holder.beginManualSyncRequest())
    }

    @Test
    fun `a manual sync request is refused while a sync is running`() {
        holder.beginManualSyncRequest()
        holder.syncing(WatchSyncStage.Nodes, completedStages = 0, totalStages = 3)

        assertFalse(holder.beginManualSyncRequest())
    }

    @Test
    fun `disconnect clears progress and reachability but keeps sync history`() {
        holder.syncSucceeded(completedAtMillis = 1_000L)
        holder.syncing(WatchSyncStage.Nodes, completedStages = 0, totalStages = 3)
        holder.disconnected()

        val state = holder.state.value
        assertEquals(WatchConnectionState.Disconnected, state.connectionState)
        assertEquals(0, state.reachableWatchCount)
        assertNull(state.syncProgress)
        assertNull(state.error)
        assertEquals(1_000L, state.lastSuccessfulSyncMillis)
    }

    @Test
    fun `a synchronization failure keeps the watch reachable`() {
        holder.connectionVerified(listOf("Watch A", "Watch B"))
        holder.syncFailed(WatchConnectionError.SynchronizationFailed)

        val state = holder.state.value
        assertEquals(WatchConnectionState.SyncFailed, state.connectionState)
        assertEquals(WatchConnectionError.SynchronizationFailed, state.error)
        assertEquals(2, state.reachableWatchCount)
    }

    @Test
    fun `an interrupted synchronization keeps the watch reachable`() {
        holder.connectionVerified(listOf("Watch A"))
        holder.syncFailed(WatchConnectionError.SynchronizationInterrupted)

        assertEquals(1, holder.state.value.reachableWatchCount)
    }

    @Test
    fun `a not-connected failure clears reachability`() {
        holder.connectionVerified(listOf("Watch A"))
        holder.syncFailed(WatchConnectionError.NotConnected)

        assertEquals(0, holder.state.value.reachableWatchCount)
    }

    @Test
    fun `a wear-services failure clears reachability`() {
        holder.connectionVerified(listOf("Watch A"))
        holder.syncFailed(WatchConnectionError.WearServicesUnavailable)

        assertEquals(0, holder.state.value.reachableWatchCount)
    }

    @Test
    fun `success after a failure clears the failure`() {
        holder.connectionVerified(listOf("Watch A"))
        holder.syncFailed(WatchConnectionError.SynchronizationFailed)
        holder.syncSucceeded(completedAtMillis = 2_000L)

        val state = holder.state.value
        assertEquals(WatchConnectionState.Connected, state.connectionState)
        assertNull(state.error)
        assertNull(state.syncProgress)
        assertEquals(2_000L, state.lastSuccessfulSyncMillis)
    }

    @Test
    fun `communication success updates only the last communication timestamp`() {
        holder.communicationSucceeded(communicatedAtMillis = 999L)

        assertEquals(999L, holder.state.value.lastCommunicationMillis)
        assertNull(holder.state.value.lastSuccessfulSyncMillis)
    }

    @Test
    fun `sync success updates both timestamps`() {
        holder.communicationSucceeded(communicatedAtMillis = 999L)
        holder.syncSucceeded(completedAtMillis = 1_500L)

        assertEquals(1_500L, holder.state.value.lastCommunicationMillis)
        assertEquals(1_500L, holder.state.value.lastSuccessfulSyncMillis)
    }

    @Test
    fun `progress requires a positive stage count and in-range completion`() {
        WatchSyncProgress(WatchSyncStage.Nodes, completedStages = 0, totalStages = 3)
        assertFailsWith<IllegalArgumentException> { WatchSyncProgress(WatchSyncStage.Nodes, 0, 0) }
        assertFailsWith<IllegalArgumentException> { WatchSyncProgress(WatchSyncStage.Nodes, 3, 3) }
    }
}
