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
package org.meshtastic.wear.data

import android.app.Application
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.service.WearableIncomingActionRouter
import kotlin.time.Duration.Companion.seconds

sealed interface WearCompanionSyncState {
    data object Loading : WearCompanionSyncState

    data object Ready : WearCompanionSyncState

    data object PhoneUnavailable : WearCompanionSyncState

    data object WaitingForFirstSync : WearCompanionSyncState

    data object SyncFailed : WearCompanionSyncState

    data object Retrying : WearCompanionSyncState
}

/** Process-local source of truth for the Companion-mode transport and synchronization lifecycle. */
class WearCompanionSyncStateHolder {
    private val mutableState = MutableStateFlow<WearCompanionSyncState>(WearCompanionSyncState.Loading)

    @Volatile private var completedSync = false

    @Volatile private var attemptFailed = false

    val state: StateFlow<WearCompanionSyncState> = mutableState.asStateFlow()

    fun beginAttempt() {
        attemptFailed = false
        mutableState.value = if (completedSync) WearCompanionSyncState.Retrying else WearCompanionSyncState.Loading
    }

    fun syncStarted() {
        beginAttempt()
    }

    fun syncCompleted() {
        if (attemptFailed) return
        completedSync = true
        mutableState.value = WearCompanionSyncState.Ready
    }

    fun phoneUnavailable() {
        attemptFailed = true
        mutableState.value = WearCompanionSyncState.PhoneUnavailable
    }

    fun syncFailed() {
        attemptFailed = true
        mutableState.value = WearCompanionSyncState.SyncFailed
    }

    fun syncTimedOut() {
        attemptFailed = true
        mutableState.value =
            if (completedSync) WearCompanionSyncState.SyncFailed else WearCompanionSyncState.WaitingForFirstSync
    }
}

/** Companion-mode boundary for phone reachability and Wear Message API commands. */
interface WearDataLayerClient {
    val syncState: StateFlow<WearCompanionSyncState>

    suspend fun synchronize()

    suspend fun sendCommand(path: String, payload: ByteArray? = null): Boolean
}

/** Play Services implementation; it contains no radio, database, network, USB, or worker dependencies. */
class PlayServicesWearDataLayerClient(
    application: Application,
    private val syncStateHolder: WearCompanionSyncStateHolder,
) : WearDataLayerClient {
    private val nodeClient = Wearable.getNodeClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val syncMutex = Mutex()

    override val syncState: StateFlow<WearCompanionSyncState> = syncStateHolder.state

    @Suppress("SuspendFunSwallowedCancellation")
    override suspend fun synchronize() {
        if (!syncMutex.tryLock()) return
        try {
            syncStateHolder.beginAttempt()
            val nodes = withTimeoutOrNull(CONNECTION_TIMEOUT) { nodeClient.connectedNodes.await() }
            if (nodes.isNullOrEmpty()) {
                syncStateHolder.phoneUnavailable()
            } else {
                val requestDelivered =
                    withTimeoutOrNull(COMMAND_TIMEOUT) {
                        nodes
                            .map { node ->
                                val path = WearableIncomingActionRouter.PATH_REQUEST_SYNC
                                messageClient.sendMessage(node.id, path, null).await()
                            }
                            .isNotEmpty()
                    } ?: false
                if (requestDelivered) {
                    val terminalState =
                        withTimeoutOrNull(SYNC_TIMEOUT) {
                            syncState.first { state ->
                                state != WearCompanionSyncState.Loading && state != WearCompanionSyncState.Retrying
                            }
                        }
                    if (terminalState == null) syncStateHolder.syncTimedOut()
                } else {
                    syncStateHolder.syncFailed()
                }
            }
        } catch (cancellation: CancellationException) {
            if (
                syncState.value == WearCompanionSyncState.Loading || syncState.value == WearCompanionSyncState.Retrying
            ) {
                syncStateHolder.syncTimedOut()
            }
            throw cancellation
        } catch (_: Exception) {
            syncStateHolder.syncFailed()
        } finally {
            syncMutex.unlock()
        }
    }

    override suspend fun sendCommand(path: String, payload: ByteArray?): Boolean = try {
        // Deliver-only: a dropped command is not a disconnect, so leave the sync state to synchronize().
        val nodes = withTimeoutOrNull(CONNECTION_TIMEOUT) { nodeClient.connectedNodes.await() }.orEmpty()
        if (nodes.isEmpty()) {
            false
        } else {
            withTimeoutOrNull(COMMAND_TIMEOUT) {
                nodes.forEach { node -> messageClient.sendMessage(node.id, path, payload).await() }
                true
            } ?: false
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }

    private companion object {
        val CONNECTION_TIMEOUT = 5.seconds
        val COMMAND_TIMEOUT = 10.seconds
        val SYNC_TIMEOUT = 30.seconds
    }
}
