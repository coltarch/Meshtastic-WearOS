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

package org.meshtastic.wear

import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.WatchSyncCategory
import org.meshtastic.core.model.WatchSyncPaths
import org.meshtastic.core.model.WearableChannel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.model.WearableNode
import org.meshtastic.core.model.WearableRadioStatus
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WearOperatingMode
import org.meshtastic.wear.data.WearCompanionSyncStateHolder
import org.meshtastic.wear.di.WearStartupState
import org.meshtastic.wear.di.WearStartupStatus
import org.meshtastic.wear.presentation.model.WearableChannelRepository
import org.meshtastic.wear.presentation.model.WearableMessageRepository
import org.meshtastic.wear.presentation.model.WearableNodeRepository
import org.meshtastic.wear.presentation.model.WearableQuickReplyRepository
import org.meshtastic.wear.presentation.model.WearableRadioStatusRepository

/** Listener that receives data updates from the phone via the Wearable Data Layer and Message API. */
class DataLayerListenerService : WearableListenerService() {

    private val nodeRepository: WearableNodeRepository by inject()
    private val messageRepository: WearableMessageRepository by inject()
    private val channelRepository: WearableChannelRepository by inject()
    private val quickReplyRepository: WearableQuickReplyRepository by inject()
    private val radioStatusRepository: WearableRadioStatusRepository by inject()
    private val syncStateHolder: WearCompanionSyncStateHolder by inject()
    private val watchPrefs: WatchPrefs by inject()
    private val startupState: WearStartupState by inject()
    private val dispatchers: CoroutineDispatchers by inject()
    private val json = Json { ignoreUnknownKeys = true }
    private val serviceJob = SupervisorJob()
    private val scope by lazy { CoroutineScope(serviceJob + dispatchers.default) }

    override fun onCreate() {
        super.onCreate()
        Logger.d { "DataLayerListenerService created" }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                when (path) {
                    "/nodes" -> {
                        val nodesJson = dataMap.getString("nodes_json")
                        if (nodesJson != null) {
                            dispatchWhenCompanion { handleNodesJson(nodesJson) }
                        }
                    }

                    "/messages" -> {
                        val messagesJson = dataMap.getString("messages_json")
                        if (messagesJson != null) {
                            dispatchWhenCompanion { handleMessagesJson(messagesJson) }
                        }
                    }

                    "/channels" -> {
                        val channelsJson = dataMap.getString("channels_json")
                        if (channelsJson != null) {
                            dispatchWhenCompanion { handleChannelsJson(channelsJson) }
                        }
                    }

                    "/quick_replies" -> {
                        val repliesJson = dataMap.getString("quick_replies_json")
                        if (repliesJson != null) {
                            dispatchWhenCompanion { handleQuickRepliesJson(repliesJson) }
                        }
                    }

                    "/radio_status" -> {
                        val statusJson = dataMap.getString("radio_status_json")
                        if (statusJson != null) {
                            dispatchWhenCompanion { handleRadioStatusJson(statusJson) }
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/nodes" -> {
                val nodesJson = String(messageEvent.data)
                dispatchWhenCompanion { handleNodesJson(nodesJson) }
            }

            "/messages" -> {
                val messagesJson = String(messageEvent.data)
                dispatchWhenCompanion { handleMessagesJson(messagesJson) }
            }

            "/channels" -> {
                val channelsJson = String(messageEvent.data)
                dispatchWhenCompanion { handleChannelsJson(channelsJson) }
            }

            "/quick_replies" -> {
                val repliesJson = String(messageEvent.data)
                dispatchWhenCompanion { handleQuickRepliesJson(repliesJson) }
            }

            "/radio_status" -> {
                val statusJson = String(messageEvent.data)
                dispatchWhenCompanion { handleRadioStatusJson(statusJson) }
            }

            "/clear_sync_category" -> {
                val categoryJson = String(messageEvent.data)
                dispatchWhenCompanion { handleClearCategoryJson(categoryJson) }
            }

            WatchSyncPaths.SYNC_STARTED -> dispatchWhenCompanion(syncStateHolder::syncStarted)

            WatchSyncPaths.SYNC_COMPLETED ->
                dispatchWhenCompanion {
                    syncStateHolder.syncCompleted()
                    scope.launch { watchPrefs.setLastSuccessfulSyncMillis(nowMillis) }
                }

            WatchSyncPaths.SYNC_FAILED -> dispatchWhenCompanion(syncStateHolder::syncFailed)
        }
    }

    private fun dispatchWhenCompanion(block: () -> Unit) {
        scope.launch {
            val status = startupState.status.first { it !is WearStartupStatus.Loading }
            if (status is WearStartupStatus.Ready && status.mode == WearOperatingMode.COMPANION) block()
        }
    }

    private fun handleNodesJson(jsonStr: String) {
        try {
            val nodes = json.decodeFromString<List<WearableNode>>(jsonStr)
            nodeRepository.updateNodes(nodes)
        } catch (_: Exception) {
            syncStateHolder.syncFailed()
            Logger.w { "Unable to decode synchronized nodes" }
        }
    }

    private fun handleMessagesJson(jsonStr: String) {
        try {
            val messages = json.decodeFromString<List<WearableMessage>>(jsonStr)
            messageRepository.updateMessages(messages)
        } catch (_: Exception) {
            syncStateHolder.syncFailed()
            Logger.w { "Unable to decode synchronized messages" }
        }
    }

    private fun handleChannelsJson(jsonStr: String) {
        try {
            val channels = json.decodeFromString<List<WearableChannel>>(jsonStr)
            channelRepository.updateChannels(channels)
        } catch (_: Exception) {
            syncStateHolder.syncFailed()
            Logger.w { "Unable to decode synchronized channels" }
        }
    }

    private fun handleQuickRepliesJson(jsonStr: String) {
        try {
            quickReplyRepository.updateReplies(json.decodeFromString<List<String>>(jsonStr))
        } catch (_: Exception) {
            syncStateHolder.syncFailed()
            Logger.w { "Unable to decode synchronized quick replies" }
        }
    }

    private fun handleRadioStatusJson(jsonStr: String) {
        try {
            radioStatusRepository.updateStatus(json.decodeFromString<WearableRadioStatus>(jsonStr))
        } catch (_: Exception) {
            syncStateHolder.syncFailed()
            Logger.w { "Unable to decode synchronized radio status" }
        }
    }

    private fun handleClearCategoryJson(jsonStr: String) {
        try {
            when (json.decodeFromString<WatchSyncCategory>(jsonStr)) {
                WatchSyncCategory.Nodes -> nodeRepository.updateNodes(emptyList())
                WatchSyncCategory.Channels -> channelRepository.updateChannels(emptyList())
                WatchSyncCategory.RecentMessages -> messageRepository.updateMessages(emptyList())
                WatchSyncCategory.QuickReplies -> quickReplyRepository.clear()
                WatchSyncCategory.RadioStatus -> radioStatusRepository.updateStatus(null)
            }
        } catch (_: Exception) {
            syncStateHolder.syncFailed()
            Logger.w { "Unable to decode synchronized category clear command" }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
