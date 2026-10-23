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
@file:Suppress("TooGenericExceptionCaught")

package org.meshtastic.wear.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.meshtastic.core.common.text.QuickReplySelector
import org.meshtastic.core.model.WearableChannel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.service.WearableIncomingActionRouter
import org.meshtastic.wear.data.WearCompanionSyncState
import org.meshtastic.wear.data.WearDataLayerClient
import org.meshtastic.wear.presentation.model.WearableChannelRepository
import org.meshtastic.wear.presentation.model.WearableMessageRepository
import org.meshtastic.wear.presentation.model.WearableQuickReplyRepository

class ChatViewModel(
    repository: WearableMessageRepository,
    channelRepository: WearableChannelRepository,
    quickReplyRepository: WearableQuickReplyRepository,
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {
    private val json = Json
    private val replyFactory = WearableReplyFactory(repository)

    val messages: StateFlow<List<WearableMessage>> = repository.syncedMessages
    val channels: StateFlow<List<WearableChannel>> = channelRepository.syncedChannels
    val quickReplies: StateFlow<List<String>?> =
        combine(quickReplyRepository.replies, repository.syncedMessages, ::rankQuickReplies)
            .stateIn(viewModelScope, SharingStarted.Eagerly, quickReplyRepository.replies.value)
    val syncState: StateFlow<WearCompanionSyncState> = dataLayerClient.syncState

    fun retrySync() {
        viewModelScope.launch { dataLayerClient.synchronize() }
    }

    fun sendMessage(contactKey: String, text: String) {
        val reply = replyFactory.create(contactKey = contactKey, text = text)
        viewModelScope.launch {
            try {
                val replyJson = json.encodeToString(reply)

                val path = WearableIncomingActionRouter.PATH_SEND_MESSAGE
                if (dataLayerClient.sendCommand(path, replyJson.encodeToByteArray())) {
                    Logger.d { "Wear reply sent through the phone" }
                } else {
                    Logger.w { "Wear reply could not be sent through the phone" }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Logger.w { "Wear reply could not be sent through the phone" }
            }
        }
    }

    fun openOnPhone(contactKey: String) {
        viewModelScope.launch {
            try {
                dataLayerClient.sendCommand(
                    WearableIncomingActionRouter.PATH_OPEN_ON_PHONE,
                    contactKey.encodeToByteArray(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Logger.w { "Conversation could not be opened on the phone" }
            }
        }
    }

    /**
     * Orders the synced phrases against the conversation, or `null` when the phone has never sent them — the screen
     * treats those two states differently, so the distinction is preserved here.
     */
    private fun rankQuickReplies(replies: List<String>?, syncedMessages: List<WearableMessage>): List<String>? =
        replies?.let {
            QuickReplySelector.select(
                replies = it,
                recentTexts = syncedMessages.filterNot(WearableMessage::isMe).map(WearableMessage::text),
                limit = QUICK_REPLY_LIMIT,
            )
        }

    private companion object {
        /** The reply row is laid out for three chips. */
        const val QUICK_REPLY_LIMIT = 3
    }
}
