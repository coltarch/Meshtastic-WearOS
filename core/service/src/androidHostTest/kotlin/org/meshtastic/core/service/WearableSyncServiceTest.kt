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
package org.meshtastic.core.service

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.WatchSyncPaths
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.WatchSyncStateHolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearableSyncServiceTest {

    private val clients: WearableClients = mock(MockMode.autofill)
    private val dataClient: DataClient = mock(MockMode.autofill)
    private val messageClient: MessageClient = mock(MockMode.autofill)
    private val capabilityClient: CapabilityClient = mock(MockMode.autofill)
    private val watchPrefs: WatchPrefs = mock(MockMode.autofill)

    @Test
    fun `a watch-requested sync with no category enabled is refused without clearing or stamping success`() =
        runBlocking {
            every { clients.dataClient } returns dataClient
            every { clients.messageClient } returns messageClient
            every { clients.capabilityClient } returns capabilityClient
            every { watchPrefs.watchConnectivityEnabled } returns MutableStateFlow(true)
            every { watchPrefs.syncPreferences } returns MutableStateFlow(
                WatchSyncPreferences(
                    syncNodes = false,
                    syncChannels = false,
                    syncRecentMessages = false,
                    syncQuickReplies = false,
                    syncRadioStatus = false,
                ),
            )
            every { messageClient.sendMessage(any(), any(), null) } returns Tasks.forResult(0)

            service().performSynchronization(targetNodeId = "watch-node", reportDisconnectedAsError = false)

            verify { messageClient.sendMessage("watch-node", WatchSyncPaths.SYNC_FAILED, null) }
            verify(VerifyMode.exactly(0)) { dataClient.deleteDataItems(any()) }
            verifySuspend(VerifyMode.exactly(0)) { watchPrefs.setLastSuccessfulSyncMillis(any()) }
        }

    private fun service() = WearableSyncService(
        clients = clients,
        watchPrefs = watchPrefs,
        nodeRepository = mock(MockMode.autofill),
        packetRepository = mock(MockMode.autofill),
        quickChatActionRepository = mock(MockMode.autofill),
        radioConfigRepository = mock(MockMode.autofill),
        serviceRepository = mock(MockMode.autofill),
        watchSyncStateHolder = mock(MockMode.autofill),
        dispatchers = CoroutineDispatchers(
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        ),
    )
}
