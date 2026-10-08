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
package org.meshtastic.wear.presentation

import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.wear.presentation.model.WearableMessageRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WearableReplyFactoryTest {
    private lateinit var repository: WearableMessageRepository
    private lateinit var factory: WearableReplyFactory

    @BeforeTest
    fun setUp() {
        repository = WearableMessageRepository()
        factory = WearableReplyFactory(repository)
    }

    @Test
    fun `primary channel routes through channel zero`() {
        val reply = factory.create(contactKey = "0^all", text = "hello")

        assertNull(reply.address)
        assertEquals(0, reply.channelIndex)
    }

    @Test
    fun `private secondary channel routes without message history`() {
        assertEquals(emptyList(), repository.syncedMessages.value)

        val reply = factory.create(contactKey = "1^all", text = "hello")

        assertNull(reply.address)
        assertEquals(1, reply.channelIndex)
        assertEquals("1^all", repository.syncedMessages.value.single().contactKey)
    }

    @Test
    fun `another secondary channel routes through its selected index`() {
        val reply = factory.create(contactKey = "2^all", text = "hello")

        assertNull(reply.address)
        assertEquals(2, reply.channelIndex)
    }

    @Test
    fun `selected channel remains authoritative over conflicting history`() {
        repository.updateMessages(
            listOf(
                WearableMessage(
                    uuid = 1L,
                    contactKey = "0^all",
                    senderName = "Channel 0",
                    senderShortName = "C0",
                    text = "old",
                    isMe = false,
                    timestamp = 1L,
                    channelIndex = 0,
                ),
            ),
        )

        val reply = factory.create(contactKey = "1^all", text = "hello")

        assertNull(reply.address)
        assertEquals(1, reply.channelIndex)
        assertEquals(listOf("1^all", "0^all"), repository.syncedMessages.value.map(WearableMessage::contactKey))
    }

    @Test
    fun `legacy direct message remains addressed to selected node`() {
        val reply = factory.create(contactKey = "!a1b2c3d4", text = "hello")

        assertEquals("!a1b2c3d4", reply.address)
        assertEquals(0, reply.channelIndex)
    }

    @Test
    fun `PKI direct message preserves PKI channel and selected node`() {
        val reply = factory.create(contactKey = "${NodeAddress.PKC_CHANNEL_INDEX}!a1b2c3d4", text = "hello")

        assertEquals("!a1b2c3d4", reply.address)
        assertEquals(NodeAddress.PKC_CHANNEL_INDEX, reply.channelIndex)
    }
}
