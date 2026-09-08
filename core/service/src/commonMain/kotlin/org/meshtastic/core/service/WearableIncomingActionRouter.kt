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

import kotlinx.serialization.json.Json
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.WearableReply

internal sealed interface WearableIncomingAction {
    data class RequestSync(val sourceNodeId: String) : WearableIncomingAction

    data class SendMessage(val text: String, val contactKey: String) : WearableIncomingAction

    data class OpenConversation(val contactKey: String) : WearableIncomingAction

    data object Unknown : WearableIncomingAction

    data object Malformed : WearableIncomingAction
}

/** Parses untrusted Wear Message API input without invoking Android or repository APIs. */
internal class WearableIncomingActionRouter(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun route(path: String, data: ByteArray, sourceNodeId: String): WearableIncomingAction = when (path) {
        PATH_REQUEST_SYNC -> WearableIncomingAction.RequestSync(sourceNodeId)
        PATH_SEND_MESSAGE -> parseReply(data)
        PATH_OPEN_ON_PHONE -> parseConversation(data)
        else -> WearableIncomingAction.Unknown
    }

    private fun parseReply(data: ByteArray): WearableIncomingAction = safeCatching {
        val reply = json.decodeFromString<WearableReply>(data.decodeToString(throwOnInvalidSequence = true))
        val contactKey =
            reply.address?.let { address ->
                val normalizedAddress = NodeAddress.fromString(address).toIdString()
                if (normalizedAddress == NodeAddress.ID_BROADCAST) {
                    ContactKey.broadcast(reply.channelIndex).value
                } else if (reply.channelIndex == NodeAddress.PKC_CHANNEL_INDEX) {
                    NodeAddress.ById(normalizedAddress).toContactKey(reply.channelIndex).value
                } else {
                    normalizedAddress
                }
            } ?: ContactKey.broadcast(reply.channelIndex).value

        if (reply.text.isBlank() || !contactKey.isSupportedContactKey()) {
            WearableIncomingAction.Malformed
        } else {
            WearableIncomingAction.SendMessage(text = reply.text, contactKey = contactKey)
        }
    }
        .getOrElse { WearableIncomingAction.Malformed }

    private fun parseConversation(data: ByteArray): WearableIncomingAction =
        safeCatching { data.decodeToString(throwOnInvalidSequence = true) }
            .map { contactKey -> contactKey.takeIf { it.isSupportedContactKey() } }
            .fold(
                onSuccess = { contactKey ->
                    contactKey?.let(WearableIncomingAction::OpenConversation) ?: WearableIncomingAction.Malformed
                },
                onFailure = { WearableIncomingAction.Malformed },
            )

    private fun String.isSupportedContactKey(): Boolean {
        if (isBlank() || length > MAX_CONTACT_KEY_LENGTH) return false
        val key = ContactKey(this)
        return when (key.address) {
            NodeAddress.Broadcast -> key.channelOrNull != null

            is NodeAddress.ByNum -> true

            is NodeAddress.ById,
            NodeAddress.Local,
            -> false
        }
    }

    companion object {
        const val PATH_REQUEST_SYNC = "/request_sync"
        const val PATH_SEND_MESSAGE = "/send_message"
        const val PATH_OPEN_ON_PHONE = "/open_on_phone"

        private const val MAX_CONTACT_KEY_LENGTH = 16
    }
}

/** Executes an already validated action through narrow callbacks supplied by the Android listener service. */
internal class WearableIncomingActionHandler(
    private val requestSync: (String) -> Unit,
    private val sendMessage: suspend (text: String, contactKey: String) -> Unit,
    private val openConversation: (String) -> Unit,
) {
    suspend fun handle(action: WearableIncomingAction) {
        when (action) {
            is WearableIncomingAction.RequestSync -> requestSync(action.sourceNodeId)

            is WearableIncomingAction.SendMessage -> sendMessage(action.text, action.contactKey)

            is WearableIncomingAction.OpenConversation -> openConversation(action.contactKey)

            WearableIncomingAction.Malformed,
            WearableIncomingAction.Unknown,
            -> Unit
        }
    }
}
