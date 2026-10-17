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

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.WearableReply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WearableIncomingActionRouterTest {
    private val router = WearableIncomingActionRouter()

    @Test
    fun `request sync preserves source node for targeted response`() {
        val action = router.route("/request_sync", byteArrayOf(), "wear-node")

        assertEquals(WearableIncomingAction.RequestSync("wear-node"), action)
    }

    @Test
    fun `send message routes direct reply through its canonical contact key`() {
        val payload = Json.encodeToString(WearableReply(address = "!a1b2c3d4", channelIndex = 0, text = "hello"))

        val action = router.route("/send_message", payload.encodeToByteArray(), "wear-node")

        assertEquals(WearableIncomingAction.SendMessage(text = "hello", contactKey = "!a1b2c3d4"), action)
    }

    @Test
    fun `send message routes PKI direct reply through PKI contact key`() {
        val payload =
            Json.encodeToString(
                WearableReply(address = "!a1b2c3d4", channelIndex = NodeAddress.PKC_CHANNEL_INDEX, text = "hello"),
            )

        val action = router.route("/send_message", payload.encodeToByteArray(), "wear-node")

        assertEquals(WearableIncomingAction.SendMessage(text = "hello", contactKey = "8!a1b2c3d4"), action)
    }

    @Test
    fun `send message creates channel contact key for broadcast reply`() {
        val payload = Json.encodeToString(WearableReply(address = null, channelIndex = 2, text = "hello"))

        val action = router.route("/send_message", payload.encodeToByteArray(), "wear-node")

        assertEquals(WearableIncomingAction.SendMessage(text = "hello", contactKey = "2^all"), action)
    }

    @Test
    fun `open on phone routes supported conversation`() {
        val action = router.route("/open_on_phone", "3^all".encodeToByteArray(), "wear-node")

        assertEquals(WearableIncomingAction.OpenConversation("3^all"), action)
    }

    @Test
    fun `open node on phone routes node number`() {
        val action = router.route("/open_node_on_phone", "305419896".encodeToByteArray(), "wear-node")

        assertEquals(WearableIncomingAction.OpenNode(305419896), action)
    }

    @Test
    fun `malformed payloads do not produce executable actions`() {
        assertIs<WearableIncomingAction.Malformed>(
            router.route("/send_message", "not-json".encodeToByteArray(), "wear-node"),
        )
        assertIs<WearableIncomingAction.Malformed>(
            router.route("/open_on_phone", byteArrayOf(0xC3.toByte()), "wear-node"),
        )
        assertIs<WearableIncomingAction.Malformed>(
            router.route("/open_node_on_phone", "not-a-node".encodeToByteArray(), "wear-node"),
        )
    }

    @Test
    fun `an out-of-range channel index is rejected instead of truncated into a live channel`() {
        val payload = Json.encodeToString(WearableReply(address = null, channelIndex = 42, text = "hello"))

        assertIs<WearableIncomingAction.Malformed>(
            router.route("/send_message", payload.encodeToByteArray(), "wear-node"),
        )
    }

    @Test
    fun `the pkc slot is not a broadcast channel`() {
        val pkcSlot = NodeAddress.PKC_CHANNEL_INDEX
        val payload = Json.encodeToString(WearableReply(address = null, channelIndex = pkcSlot, text = "hello"))

        assertIs<WearableIncomingAction.Malformed>(
            router.route("/send_message", payload.encodeToByteArray(), "wear-node"),
        )
    }

    @Test
    fun `the highest configured channel still routes`() {
        val payload = Json.encodeToString(WearableReply(address = null, channelIndex = 7, text = "hello"))

        val action = router.route("/send_message", payload.encodeToByteArray(), "wear-node")

        assertEquals(WearableIncomingAction.SendMessage("hello", "7^all"), action)
    }

    @Test
    fun `unknown paths are ignored`() {
        assertIs<WearableIncomingAction.Unknown>(router.route("/unknown", byteArrayOf(), "wear-node"))
    }

    @Test
    fun `handler dispatches manual synchronization request`() = runTest {
        var requestedNode: String? = null
        val handler =
            WearableIncomingActionHandler(
                requestSync = { requestedNode = it },
                sendMessage = { _, _ -> error("unexpected send") },
                openConversation = { error("unexpected open") },
                openNode = { error("unexpected node") },
            )

        handler.handle(WearableIncomingAction.RequestSync("wear-node"))

        assertEquals("wear-node", requestedNode)
    }

    @Test
    fun `handler dispatches validated message`() = runTest {
        var sent: Pair<String, String>? = null
        val handler =
            WearableIncomingActionHandler(
                requestSync = { error("unexpected sync") },
                sendMessage = { text, contactKey -> sent = text to contactKey },
                openConversation = { error("unexpected open") },
                openNode = { error("unexpected node") },
            )

        handler.handle(WearableIncomingAction.SendMessage("hello", "2^all"))

        assertEquals("hello" to "2^all", sent)
    }
}
