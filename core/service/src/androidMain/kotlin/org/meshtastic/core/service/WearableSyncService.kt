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
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@file:Suppress("LongMethod")

package org.meshtastic.core.service

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.WatchSyncCategory
import org.meshtastic.core.model.WatchSyncPaths
import org.meshtastic.core.model.WearableChannel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.model.WearableNode
import org.meshtastic.core.model.WearableRadioStatus
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.WATCH_COMPANION_SYNC_CAPABILITY
import org.meshtastic.core.repository.WatchConnectionError
import org.meshtastic.core.repository.WatchConnectionState
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WatchSyncStage
import org.meshtastic.core.repository.WatchSyncStateHolder
import org.meshtastic.core.repository.toSyncPlan
import kotlin.time.Duration.Companion.milliseconds
import com.google.android.gms.wearable.Node as DataLayerNode
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import org.meshtastic.core.model.Channel as MeshChannel

/** Synchronizes phone repositories with connected Wear OS nodes through the Wearable Data Layer. */
@Single
@Suppress("TooManyFunctions")
class WearableSyncService(
    context: Context,
    private val watchPrefs: WatchPrefs,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val watchSyncStateHolder: WatchSyncStateHolder,
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
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val started = atomic(false)
    private val stopped = atomic(false)
    private val syncMutex = Mutex()
    private val repositoryInvalidations = CoroutineChannel<Unit>(CoroutineChannel.CONFLATED)
    private val manualSyncRequests = CoroutineChannel<Unit>(CoroutineChannel.CONFLATED)
    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { onCapabilityChanged(it) }

    /** Starts the process-lifetime collectors once. Repeated calls never create duplicate collectors. */
    fun start() {
        if (stopped.value) return
        if (!started.compareAndSet(expect = false, update = true)) return

        Logger.d { "Wearable synchronization started" }

        scope.launch {
            val listenerRegistration =
                safeCatching {
                    capabilityClient.addListener(capabilityListener, WATCH_COMPANION_SYNC_CAPABILITY).await()
                }
                    .onFailure { failureLogger.log(WearableFailureLog.ConnectedNodeQuery, it) }
            if (listenerRegistration.isFailure) {
                watchSyncStateHolder.reportWearServicesUnavailableUnlessActive()
                return@launch
            }

            safeCatching {
                capabilityClient
                    .getCapability(WATCH_COMPANION_SYNC_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
            }
                .onSuccess(::onCapabilityChanged)
                .onFailure {
                    failureLogger.log(WearableFailureLog.ConnectedNodeQuery, it)
                    watchSyncStateHolder.reportWearServicesUnavailableUnlessActive()
                }
        }

        scope.launch {
            repositoryInvalidations.receiveAsFlow().collectLatest {
                syncMutex.withLock { synchronizeConnectedWatch(reportDisconnectedAsError = false) }
            }
        }

        scope.launch {
            manualSyncRequests.receiveAsFlow().collect {
                syncMutex.withLock { synchronizeConnectedWatch(reportDisconnectedAsError = true) }
            }
        }

        scope.launch {
            combine(watchPrefs.watchConnectivityEnabled, watchPrefs.syncPreferences, ::Pair)
                .distinctUntilChanged()
                .collectLatest { (connectivityEnabled, _) ->
                    if (connectivityEnabled) repositoryInvalidations.trySend(Unit)
                }
        }

        scope.launch { watchPrefs.syncRequest.drop(1).collect { manualSyncRequests.trySend(Unit) } }

        scope.launch {
            watchPrefs.syncPreferences
                .flatMapLatest { preferences ->
                    val invalidations = buildList {
                        val needsNodes =
                            preferences.syncNodes || preferences.syncRecentMessages || preferences.syncRadioStatus
                        if (needsNodes) {
                            add(nodeRepository.nodeDBbyNum.drop(1).map { Unit })
                        }
                        if (preferences.syncRecentMessages) add(packetRepository.getContacts().drop(1).map { Unit })
                        if (preferences.syncChannels) {
                            add(radioConfigRepository.channelSetFlow.drop(1).map { Unit })
                        }
                        if (preferences.syncQuickReplies) {
                            add(quickChatActionRepository.getAllActions().drop(1).map { Unit })
                        }
                        if (preferences.syncRadioStatus) {
                            add(serviceRepository.connectionState.drop(1).map { Unit })
                        }
                    }
                    if (invalidations.isEmpty()) emptyFlow() else merge(*invalidations.toTypedArray())
                }
                .debounce(REPOSITORY_SYNC_DEBOUNCE)
                .collect { repositoryInvalidations.trySend(Unit) }
        }
    }

    /** Requests a full sync, optionally targeted to the Wear node that requested it. */
    fun forceSync(targetNodeId: String? = null) {
        start()
        scope.launch {
            syncMutex.withLock {
                synchronizeConnectedWatch(targetNodeId = targetNodeId, reportDisconnectedAsError = false)
            }
        }
    }

    /** Records a successfully validated command without retaining its Data Layer node identifier. */
    fun recordSuccessfulWatchCommunication() {
        watchSyncStateHolder.communicationSucceeded(nowMillis)
    }

    /** Cancels all synchronization observers and pending work owned by this service. */
    fun stop() {
        if (stopped.compareAndSet(expect = false, update = true)) {
            capabilityClient.removeListener(capabilityListener, WATCH_COMPANION_SYNC_CAPABILITY)
            scope.cancel()
            Logger.d { "Wearable synchronization stopped" }
        }
    }

    private suspend fun synchronizeConnectedWatch(targetNodeId: String? = null, reportDisconnectedAsError: Boolean) {
        try {
            performSynchronization(targetNodeId, reportDisconnectedAsError)
        } catch (cancellation: CancellationException) {
            if (reportDisconnectedAsError && watchSyncStateHolder.state.value.connectionState.isSynchronizationActive) {
                watchSyncStateHolder.syncFailed(WatchConnectionError.SynchronizationInterrupted)
            }
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failureLogger.log(WearableFailureLog.UnexpectedSynchronization, failure)
            targetNodeId?.let { sendSyncStatus(WatchSyncPaths.SYNC_FAILED, listOf(it)) }
            watchSyncStateHolder.syncFailed(WatchConnectionError.SynchronizationFailed)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun performSynchronization(targetNodeId: String?, reportDisconnectedAsError: Boolean) {
        if (!watchPrefs.watchConnectivityEnabled.value) {
            watchSyncStateHolder.disconnected()
            return
        }

        watchSyncStateHolder.checkingConnection()
        val targetNodes =
            capabilityClient
                .findReachableWatchNodes(targetNodeId, retryWhileSearching = reportDisconnectedAsError)
                .getOrElse {
                    failureLogger.log(WearableFailureLog.ConnectedNodeQuery, it)
                    watchSyncStateHolder.syncFailed(WatchConnectionError.WearServicesUnavailable)
                    return
                }
        if (targetNodes.isEmpty()) {
            if (reportDisconnectedAsError) {
                watchSyncStateHolder.syncFailed(WatchConnectionError.NotConnected)
            } else {
                watchSyncStateHolder.disconnected()
            }
            return
        }

        watchSyncStateHolder.connectionVerified(targetNodes.map { it.displayName })
        val targetNodeIds = targetNodes.map { it.id }

        val preferences = watchPrefs.syncPreferences.value
        val syncPlan = preferences.toSyncPlan()
        val totalStages = syncPlan.totalStages

        val nodesMap = nodeRepository.nodeDBbyNum.value
        val myNodeNum = nodeRepository.ourNodeInfo.value?.num
        var completedStages = 0
        var allPayloadsSent = sendSyncStatus(WatchSyncPaths.SYNC_STARTED, targetNodeIds)
        allPayloadsSent = clearDisabledCategories(syncPlan.disabledCategories, targetNodeIds) && allPayloadsSent

        if (preferences.syncNodes) {
            watchSyncStateHolder.syncing(WatchSyncStage.Nodes, completedStages, totalStages)
            val wearableNodes =
                nodesMap.values
                    .filter { it.num != myNodeNum }
                    .map { it.toWearableNode(includeRadioStatus = preferences.syncRadioStatus) }
            allPayloadsSent =
                sendPayload(PATH_NODES, KEY_NODES, json.encodeToString(wearableNodes), targetNodeIds) && allPayloadsSent
            completedStages++
        }

        if (preferences.syncChannels) {
            val wearableChannels = createWearableChannels()
            watchSyncStateHolder.syncing(WatchSyncStage.Channels, completedStages, totalStages)
            allPayloadsSent =
                sendPayload(PATH_CHANNELS, KEY_CHANNELS, json.encodeToString(wearableChannels), targetNodeIds) &&
                allPayloadsSent
            completedStages++
        }

        if (preferences.syncRecentMessages) {
            val wearableMessages = createWearableMessages(nodesMap, myNodeNum, preferences.recentMessageLimit.count)
            watchSyncStateHolder.syncing(WatchSyncStage.Messages, completedStages, totalStages)
            allPayloadsSent =
                sendPayload(PATH_MESSAGES, KEY_MESSAGES, json.encodeToString(wearableMessages), targetNodeIds) &&
                allPayloadsSent
            completedStages++
        }

        if (preferences.syncQuickReplies) {
            watchSyncStateHolder.syncing(WatchSyncStage.QuickReplies, completedStages, totalStages)
            val replies =
                quickChatActionRepository.getAllActions().first().map { it.message }.filter(String::isNotBlank)
            allPayloadsSent =
                sendPayload(PATH_QUICK_REPLIES, KEY_QUICK_REPLIES, json.encodeToString(replies), targetNodeIds) &&
                allPayloadsSent
            completedStages++
        }

        if (preferences.syncRadioStatus) {
            watchSyncStateHolder.syncing(WatchSyncStage.RadioStatus, completedStages, totalStages)
            allPayloadsSent =
                sendPayload(
                    PATH_RADIO_STATUS,
                    KEY_RADIO_STATUS,
                    json.encodeToString(createWearableRadioStatus()),
                    targetNodeIds,
                ) && allPayloadsSent
        }

        if (allPayloadsSent) {
            val completionSent = sendSyncStatus(WatchSyncPaths.SYNC_COMPLETED, targetNodeIds)
            if (completionSent) {
                val completedAtMillis = nowMillis
                watchPrefs.setLastSuccessfulSyncMillis(completedAtMillis)
                watchSyncStateHolder.syncSucceeded(completedAtMillis)
            } else {
                sendSyncStatus(WatchSyncPaths.SYNC_FAILED, targetNodeIds)
                watchSyncStateHolder.syncFailed(WatchConnectionError.SynchronizationFailed)
            }
        } else {
            sendSyncStatus(WatchSyncPaths.SYNC_FAILED, targetNodeIds)
            watchSyncStateHolder.syncFailed(WatchConnectionError.SynchronizationFailed)
        }
    }

    private fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val reachableNodes = capabilityInfo.nodes.toList()
        watchSyncStateHolder.reachableWatchesChanged(reachableNodes.map { it.displayName })
        if (reachableNodes.isNotEmpty() && watchPrefs.watchConnectivityEnabled.value) {
            repositoryInvalidations.trySend(Unit)
        }
    }

    private fun Node.toWearableNode(includeRadioStatus: Boolean) = WearableNode(
        num = num,
        name = user.long_name,
        shortName = user.short_name,
        online = includeRadioStatus && isOnline,
        battery = batteryLevel.takeIf { includeRadioStatus },
        snr = snrOrNull.takeIf { includeRadioStatus },
        lastSeen = if (includeRadioStatus) lastHeard.toLong() * MILLIS_PER_SECOND else 0L,
        favorite = isFavorite,
    )

    private suspend fun createWearableChannels(): List<WearableChannel> {
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val loraConfig = channelSet.lora_config ?: MeshChannel.default.loraConfig
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
        limit: Int,
    ): List<WearableMessage> {
        return packetRepository
            .getRecentMessages(limit) { id ->
                NodeAddress.idToNum(id)?.let(nodesMap::get) ?: nodeRepository.getNode(id.orEmpty())
            }
            .first()
            .mapNotNull { (contactKeyValue, message) ->
                val contactKey = ContactKey(contactKeyValue)
                val address = contactKey.addressString
                val isBroadcast = contactKey.address is NodeAddress.Broadcast
                val nodeNum = NodeAddress.idToNum(address)
                if (!isBroadcast && nodeNum == myNodeNum) return@mapNotNull null

                val threadInfo =
                    if (isBroadcast) {
                        val name = "Channel ${contactKey.channel}"
                        name to name.take(2).uppercase()
                    } else {
                        val contactNode = nodeNum?.let(nodesMap::get)
                        (contactNode?.user?.long_name ?: address) to
                            (contactNode?.user?.short_name ?: UNKNOWN_SHORT_NAME)
                    }

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

    private fun createWearableRadioStatus(): WearableRadioStatus {
        val node = nodeRepository.ourNodeInfo.value
        val position = node?.validPosition
        return WearableRadioStatus(
            connected = serviceRepository.connectionState.value == ConnectionState.Connected,
            batteryLevel = node?.batteryLevel,
            voltage = node?.voltage,
            snr = node?.snrOrNull,
            lastSeen = node?.lastHeard?.toLong()?.times(MILLIS_PER_SECOND),
            latitude = position?.latitude_i?.times(POSITION_SCALE),
            longitude = position?.longitude_i?.times(POSITION_SCALE),
            temperature = node?.environmentMetrics?.temperature,
            relativeHumidity = node?.environmentMetrics?.relative_humidity,
        )
    }

    private suspend fun clearDisabledCategories(
        disabledCategories: List<WatchSyncCategory>,
        targetNodeIds: List<String>,
    ): Boolean = disabledCategories
        .map { category ->
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(category.dataPath).build()
            val dataCleared =
                safeCatching { dataClient.deleteDataItems(uri).await() }
                    .onFailure { failureLogger.log(WearableFailureLog.DataSynchronization, it) }
                    .isSuccess
            val command = json.encodeToString(category).encodeToByteArray()
            val commandSent =
                targetNodeIds
                    .map { nodeId ->
                        safeCatching { messageClient.sendMessage(nodeId, PATH_CLEAR_CATEGORY, command).await() }
                            .onFailure { failureLogger.log(WearableFailureLog.MessageSynchronization, it) }
                            .isSuccess
                    }
                    .all { it }
            dataCleared && commandSent
        }
        .all { it }

    private suspend fun sendPayload(
        path: String,
        dataKey: String,
        payload: String,
        targetNodeIds: List<String>,
    ): Boolean {
        val dataRequest =
            PutDataMapRequest.create(path)
                .apply {
                    dataMap.putString(dataKey, payload)
                    dataMap.putLong(KEY_TIMESTAMP, nowMillis)
                }
                .asPutDataRequest()
                .setUrgent()

        val dataSent =
            safeCatching { dataClient.putDataItem(dataRequest).await() }
                .onFailure { failureLogger.log(WearableFailureLog.DataSynchronization, it) }
                .isSuccess

        val bytes = payload.encodeToByteArray()
        val messagesSent =
            targetNodeIds
                .map {
                    safeCatching { messageClient.sendMessage(it, path, bytes).await() }
                        .onFailure { failure -> failureLogger.log(WearableFailureLog.MessageSynchronization, failure) }
                        .isSuccess
                }
                .all { it }
        return dataSent && messagesSent
    }

    private suspend fun sendSyncStatus(path: String, targetNodeIds: List<String>): Boolean = targetNodeIds
        .map { nodeId ->
            safeCatching { messageClient.sendMessage(nodeId, path, null).await() }
                .onFailure { failure -> failureLogger.log(WearableFailureLog.MessageSynchronization, failure) }
                .isSuccess
        }
        .all { it }

    companion object {
        private const val PATH_NODES = "/nodes"
        private const val PATH_MESSAGES = "/messages"
        private const val PATH_CHANNELS = "/channels"
        private const val PATH_QUICK_REPLIES = "/quick_replies"
        private const val PATH_RADIO_STATUS = "/radio_status"
        private const val PATH_CLEAR_CATEGORY = "/clear_sync_category"
        private const val KEY_NODES = "nodes_json"
        private const val KEY_MESSAGES = "messages_json"
        private const val KEY_CHANNELS = "channels_json"
        private const val KEY_QUICK_REPLIES = "quick_replies_json"
        private const val KEY_RADIO_STATUS = "radio_status_json"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val MILLIS_PER_SECOND = 1_000L
        private const val POSITION_SCALE = 1e-7
        private const val UNKNOWN_SHORT_NAME = "??"
        private val REPOSITORY_SYNC_DEBOUNCE = 750.milliseconds
    }
}

private val WatchConnectionState.isSynchronizationActive: Boolean
    get() = this == WatchConnectionState.CheckingConnection || this == WatchConnectionState.Syncing

private fun WatchSyncStateHolder.reportWearServicesUnavailableUnlessActive() {
    if (!state.value.connectionState.isSynchronizationActive) {
        syncFailed(WatchConnectionError.WearServicesUnavailable)
    }
}

private suspend fun CapabilityClient.findReachableWatchNodes(
    targetNodeId: String?,
    retryWhileSearching: Boolean,
): Result<List<DataLayerNode>> {
    val attempts = if (retryWhileSearching) MANUAL_CONNECTION_SEARCH_ATTEMPTS else 1
    repeat(attempts) { attempt ->
        val result = safeCatching {
            getCapability(WATCH_COMPANION_SYNC_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes.filter {
                targetNodeId == null || it.id == targetNodeId
            }
        }
        if (result.isFailure || result.getOrThrow().isNotEmpty() || attempt == attempts - 1) return result
        delay(MANUAL_CONNECTION_SEARCH_INTERVAL)
    }
    return Result.success(emptyList())
}

private const val MANUAL_CONNECTION_SEARCH_ATTEMPTS = 4
private val MANUAL_CONNECTION_SEARCH_INTERVAL = 1_250.milliseconds

private val WatchSyncCategory.dataPath: String
    get() =
        when (this) {
            WatchSyncCategory.Nodes -> "/nodes"
            WatchSyncCategory.Channels -> "/channels"
            WatchSyncCategory.RecentMessages -> "/messages"
            WatchSyncCategory.QuickReplies -> "/quick_replies"
            WatchSyncCategory.RadioStatus -> "/radio_status"
        }
