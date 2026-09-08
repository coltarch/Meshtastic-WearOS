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
@file:OptIn(kotlinx.coroutines.FlowPreview::class)
@file:Suppress("LongMethod")

package org.meshtastic.core.service

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.WearableChannel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.model.WearableNode
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.proto.Config.LoRaConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import org.meshtastic.core.model.Channel as MeshChannel

/** Synchronizes phone repositories with connected Wear OS nodes through the Wearable Data Layer. */
@Single
class WearableSyncService(
    context: Context,
    private val watchPrefs: WatchPrefs,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val radioConfigRepository: RadioConfigRepository,
    dispatchers: CoroutineDispatchers,
) {
    private val failureLogger = WearableFailureLogger(Logger.withTag("WearableSyncService"))
    private val serviceJob: Job = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        failureLogger.log(WearableFailureLog.UnexpectedSynchronization, throwable)
    }
    private val scope = CoroutineScope(serviceJob + dispatchers.io + exceptionHandler)
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val started = atomic(false)
    private val stopped = atomic(false)
    private val syncMutex = Mutex()
    private val repositoryInvalidations = CoroutineChannel<Unit>(CoroutineChannel.CONFLATED)

    /** Starts the process-lifetime collectors once. Repeated calls never create duplicate collectors. */
    fun start() {
        if (stopped.value) return
        if (!started.compareAndSet(expect = false, update = true)) return

        Logger.d { "Wearable synchronization started" }

        scope.launch {
            repositoryInvalidations.receiveAsFlow().collectLatest { syncMutex.withLock { synchronizeConnectedWatch() } }
        }

        scope.launch {
            combine(watchPrefs.pushToWatchEnabled, watchPrefs.syncNodesEnabled, watchPrefs.syncMessagesEnabled) {
                    push,
                    nodes,
                    messages,
                ->
                Triple(push, nodes, messages)
            }
                .distinctUntilChanged()
                .collectLatest { (push, nodes, messages) ->
                    if (push && (nodes || messages)) repositoryInvalidations.trySend(Unit)
                }
        }

        scope.launch { watchPrefs.syncRequest.drop(1).collect { repositoryInvalidations.trySend(Unit) } }

        scope.launch {
            merge(
                nodeRepository.nodeDBbyNum.drop(1).map { Unit },
                packetRepository.getContacts().drop(1).map { Unit },
                radioConfigRepository.channelSetFlow.drop(1).map { Unit },
            )
                .debounce(REPOSITORY_SYNC_DEBOUNCE)
                .collect { repositoryInvalidations.trySend(Unit) }
        }
    }

    /** Requests a full sync, optionally targeted to the Wear node that requested it. */
    fun forceSync(targetNodeId: String? = null) {
        start()
        scope.launch { syncMutex.withLock { synchronizeConnectedWatch(targetNodeId) } }
    }

    /** Cancels all synchronization observers and pending work owned by this service. */
    fun stop() {
        if (stopped.compareAndSet(expect = false, update = true)) {
            scope.cancel()
            Logger.d { "Wearable synchronization stopped" }
        }
    }

    private suspend fun synchronizeConnectedWatch(targetNodeId: String? = null) {
        if (
            !watchPrefs.pushToWatchEnabled.value ||
            (!watchPrefs.syncNodesEnabled.value && !watchPrefs.syncMessagesEnabled.value)
        ) {
            return
        }

        val targetNodeIds =
            targetNodeId?.let(::listOf)
                ?: safeCatching { nodeClient.connectedNodes.await().map { it.id } }
                    .onFailure { failureLogger.log(WearableFailureLog.ConnectedNodeQuery, it) }
                    .getOrDefault(emptyList())
        if (targetNodeIds.isEmpty()) return

        val nodesMap = nodeRepository.nodeDBbyNum.value
        val myNodeNum = nodeRepository.ourNodeInfo.value?.num

        if (watchPrefs.syncNodesEnabled.value) {
            val wearableNodes = nodesMap.values.filter { it.num != myNodeNum }.map { it.toWearableNode() }
            sendPayload(PATH_NODES, KEY_NODES, json.encodeToString(wearableNodes), targetNodeIds)
        }

        if (watchPrefs.syncMessagesEnabled.value) {
            val wearableChannels = createWearableChannels()
            sendPayload(PATH_CHANNELS, KEY_CHANNELS, json.encodeToString(wearableChannels), targetNodeIds)

            val wearableMessages = createWearableMessages(nodesMap, myNodeNum, wearableChannels)
            sendPayload(PATH_MESSAGES, KEY_MESSAGES, json.encodeToString(wearableMessages), targetNodeIds)
        }
    }

    private fun Node.toWearableNode() = WearableNode(
        num = num,
        name = user.long_name,
        shortName = user.short_name,
        online = isOnline,
        battery = batteryLevel,
        snr = snr.takeIf { it != Float.MAX_VALUE },
        lastSeen = lastHeard.toLong() * MILLIS_PER_SECOND,
        favorite = isFavorite,
    )

    private suspend fun createWearableChannels(): List<WearableChannel> {
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val loraConfig = channelSet.lora_config ?: LoRaConfig()
        return channelSet.settings.mapIndexed { index, settings ->
            WearableChannel(
                index = index,
                name = MeshChannel(settings, loraConfig).name,
                contactKey = ContactKey.broadcast(index).value,
            )
        }
    }

    private suspend fun createWearableMessages(
        nodesMap: Map<Int, Node>,
        myNodeNum: Int?,
        wearableChannels: List<WearableChannel>,
    ): List<WearableMessage> {
        val contacts = packetRepository.getContacts().first()
        val messages = mutableListOf<WearableMessage>()

        nodesMap.values
            .filter { it.num != myNodeNum }
            .forEach { node ->
                val contactKey = NodeAddress.numToDefaultId(node.num)
                messages +=
                    WearableMessage(
                        uuid = node.num.toLong(),
                        contactKey = contactKey,
                        senderName = node.user.long_name,
                        senderShortName = node.user.short_name,
                        text = NO_MESSAGES_PLACEHOLDER,
                        isMe = false,
                        timestamp = 0L,
                        address = contactKey,
                        status = MessageStatus.RECEIVED,
                    )
            }

        contacts.keys.take(MAX_CONTACTS).forEach { contactKeyValue ->
            val contactKey = ContactKey(contactKeyValue)
            val address = contactKey.addressString
            val isBroadcast = contactKey.address is NodeAddress.Broadcast
            val nodeNum = NodeAddress.idToNum(address)
            if (!isBroadcast && nodeNum == myNodeNum) return@forEach

            val threadInfo =
                if (isBroadcast) {
                    val name = wearableChannels.getOrNull(contactKey.channel)?.name ?: "Channel ${contactKey.channel}"
                    name to name.take(2).uppercase()
                } else {
                    val contactNode = nodeNum?.let(nodesMap::get)
                    (contactNode?.user?.long_name ?: address) to (contactNode?.user?.short_name ?: UNKNOWN_SHORT_NAME)
                }

            packetRepository
                .getMessagesFrom(
                    contact = contactKeyValue,
                    limit = MAX_MESSAGES_PER_CONTACT,
                    getNode = { id ->
                        NodeAddress.idToNum(id)?.let(nodesMap::get) ?: nodeRepository.getNode(id.orEmpty())
                    },
                )
                .first()
                .forEach { message ->
                    messages +=
                        WearableMessage(
                            uuid = message.uuid,
                            contactKey = contactKeyValue,
                            senderName = threadInfo.first,
                            senderShortName = threadInfo.second,
                            text = message.text,
                            isMe = message.fromLocal,
                            timestamp = message.receivedTime,
                            address = address.takeUnless { isBroadcast },
                            channelIndex = contactKey.channel,
                            status = message.status ?: MessageStatus.RECEIVED,
                        )
                }
        }

        return messages.sortedByDescending { it.timestamp }.distinctBy { it.contactKey }
    }

    private suspend fun sendPayload(path: String, dataKey: String, payload: String, targetNodeIds: List<String>) {
        val dataRequest =
            PutDataMapRequest.create(path)
                .apply {
                    dataMap.putString(dataKey, payload)
                    dataMap.putLong(KEY_TIMESTAMP, nowMillis)
                }
                .asPutDataRequest()
                .setUrgent()

        safeCatching { dataClient.putDataItem(dataRequest).await() }
            .onFailure { failureLogger.log(WearableFailureLog.DataSynchronization, it) }

        val bytes = payload.encodeToByteArray()
        targetNodeIds.forEach {
            safeCatching { messageClient.sendMessage(it, path, bytes).await() }
                .onFailure { failure -> failureLogger.log(WearableFailureLog.MessageSynchronization, failure) }
        }
    }

    companion object {
        private const val PATH_NODES = "/nodes"
        private const val PATH_MESSAGES = "/messages"
        private const val PATH_CHANNELS = "/channels"
        private const val KEY_NODES = "nodes_json"
        private const val KEY_MESSAGES = "messages_json"
        private const val KEY_CHANNELS = "channels_json"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val MAX_CONTACTS = 20
        private const val MAX_MESSAGES_PER_CONTACT = 10
        private const val MILLIS_PER_SECOND = 1_000L
        private const val NO_MESSAGES_PLACEHOLDER = "No messages yet"
        private const val UNKNOWN_SHORT_NAME = "??"
        private val REPOSITORY_SYNC_DEBOUNCE = 750.milliseconds
    }
}
