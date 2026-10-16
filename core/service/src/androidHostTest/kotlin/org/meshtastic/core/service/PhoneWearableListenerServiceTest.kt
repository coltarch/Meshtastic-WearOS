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

import com.google.android.gms.wearable.MessageEvent
import dev.mokkery.MockMode
import dev.mokkery.answering.answers
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.WearableReply
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.usecase.SendMessageOutcome
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneWearableListenerServiceTest {

    private val sendMessageUseCase: SendMessageUseCase = mock(MockMode.autofill)
    private val syncService: WearableSyncService = mock(MockMode.autofill)
    private val watchPrefs: WatchPrefs = mock(MockMode.autofill)

    @Before
    fun setUp() {
        every { watchPrefs.watchConnectivityEnabled } returns MutableStateFlow(true)
        startKoin {
            modules(
                module {
                    single { sendMessageUseCase }
                    single { syncService }
                    single { watchPrefs }
                    single<ServiceScope> { CoroutineScope(Dispatchers.Default + SupervisorJob()).asServiceScope() }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `an in-flight reply is not cancelled when the listener is destroyed`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        everySuspend { sendMessageUseCase.invoke(any(), any(), any()) } answers {
            entered.complete(Unit)
            gate.await()
            completed.complete(Unit)
            SendMessageOutcome.Queued(1)
        }

        val service = Robolectric.buildService(PhoneWearableListenerService::class.java).create().get()
        service.onMessageReceived(replyEvent())

        entered.await() // the send is now in flight, suspended on the gate
        service.onDestroy() // must not cancel it
        gate.complete(Unit)

        // Completes only if the send survived onDestroy; the old per-listener scope cancelled it here.
        withTimeout(5_000) { completed.await() }
    }

    private fun replyEvent(): MessageEvent {
        val event: MessageEvent = mock(MockMode.autofill)
        every { event.path } returns WearableIncomingActionRouter.PATH_SEND_MESSAGE
        every { event.data } returns Json.encodeToString(
            WearableReply(address = "!a1b2c3d4", channelIndex = 0, text = "hello"),
        ).encodeToByteArray()
        every { event.sourceNodeId } returns "watch-node"
        return event
    }
}
