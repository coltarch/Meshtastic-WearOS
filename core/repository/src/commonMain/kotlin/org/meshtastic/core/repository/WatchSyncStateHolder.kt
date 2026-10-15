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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val WATCH_COMPANION_SYNC_CAPABILITY = "meshtastic_companion_sync"

/** A logical payload stage in a phone-to-watch synchronization. */
enum class WatchSyncStage {
    Nodes,
    Channels,
    Messages,
    QuickReplies,
    RadioStatus,
}

/** Privacy-safe connection and synchronization states exposed to presentation code. */
enum class WatchConnectionState {
    Disconnected,
    CheckingConnection,
    Connected,
    Syncing,
    SyncFailed,
}

/** Privacy-safe failure categories. Raw Data Layer failures never cross this boundary. */
enum class WatchConnectionError {
    NotConnected,
    WearServicesUnavailable,
    SynchronizationFailed,
    SynchronizationInterrupted,
}

/** Determinate payload-stage progress. */
data class WatchSyncProgress(val stage: WatchSyncStage, val completedStages: Int, val totalStages: Int) {
    init {
        require(totalStages > 0)
        require(completedStages in 0 until totalStages)
    }
}

/** Process-local Companion-mode Data Layer state. It deliberately contains no node or capability identifiers. */
data class WatchSyncState(
    val connectionState: WatchConnectionState = WatchConnectionState.Disconnected,
    val watchName: String? = null,
    val reachableWatchCount: Int = 0,
    val lastCommunicationMillis: Long? = null,
    val lastSuccessfulSyncMillis: Long? = null,
    val syncProgress: WatchSyncProgress? = null,
    val error: WatchConnectionError? = null,
)

/** The single observable source shared by the synchronization service and settings presentation. */
class WatchSyncStateHolder {
    private val mutableState = MutableStateFlow(WatchSyncState())

    val state: StateFlow<WatchSyncState> = mutableState.asStateFlow()

    /** Marks an immediate user request and returns false when a connection check or sync is already active. */
    fun beginManualSyncRequest(): Boolean {
        while (true) {
            val current = mutableState.value
            if (
                current.connectionState == WatchConnectionState.CheckingConnection ||
                current.connectionState == WatchConnectionState.Syncing
            ) {
                return false
            }
            val checking =
                current.copy(
                    connectionState = WatchConnectionState.CheckingConnection,
                    syncProgress = null,
                    error = null,
                )
            if (mutableState.compareAndSet(current, checking)) return true
        }
    }

    fun checkingConnection() {
        mutableState.update {
            it.copy(connectionState = WatchConnectionState.CheckingConnection, syncProgress = null, error = null)
        }
    }

    fun reachableWatchesChanged(watchNames: List<String>) {
        val names = watchNames.filter(String::isNotBlank).sorted()
        mutableState.update { current ->
            if (watchNames.isEmpty()) {
                current.copy(
                    connectionState = WatchConnectionState.Disconnected,
                    reachableWatchCount = 0,
                    syncProgress = null,
                    error = null,
                )
            } else {
                current.copy(
                    connectionState =
                    if (current.connectionState.isActive) {
                        current.connectionState
                    } else {
                        WatchConnectionState.Connected
                    },
                    watchName = names.firstOrNull(),
                    reachableWatchCount = watchNames.size,
                    error = if (current.connectionState.isActive) current.error else null,
                )
            }
        }
    }

    fun connectionVerified(watchNames: List<String>) {
        val names = watchNames.filter(String::isNotBlank).sorted()
        mutableState.update {
            it.copy(
                connectionState = WatchConnectionState.Connected,
                watchName = names.firstOrNull(),
                reachableWatchCount = watchNames.size,
                syncProgress = null,
                error = null,
            )
        }
    }

    fun syncing(stage: WatchSyncStage, completedStages: Int, totalStages: Int) {
        mutableState.update {
            it.copy(
                connectionState = WatchConnectionState.Syncing,
                syncProgress = WatchSyncProgress(stage, completedStages, totalStages),
                error = null,
            )
        }
    }

    fun syncSucceeded(completedAtMillis: Long) {
        mutableState.update {
            it.copy(
                connectionState = WatchConnectionState.Connected,
                lastCommunicationMillis = completedAtMillis,
                lastSuccessfulSyncMillis = completedAtMillis,
                syncProgress = null,
                error = null,
            )
        }
    }

    fun communicationSucceeded(communicatedAtMillis: Long) {
        mutableState.update { it.copy(lastCommunicationMillis = communicatedAtMillis) }
    }

    fun disconnected() {
        mutableState.update {
            it.copy(
                connectionState = WatchConnectionState.Disconnected,
                reachableWatchCount = 0,
                syncProgress = null,
                error = null,
            )
        }
    }

    fun syncFailed(error: WatchConnectionError) {
        mutableState.update {
            it.copy(
                connectionState = WatchConnectionState.SyncFailed,
                reachableWatchCount = it.reachableWatchCount.takeUnless { error.clearsReachability } ?: 0,
                syncProgress = null,
                error = error,
            )
        }
    }

    private val WatchConnectionState.isActive: Boolean
        get() = this == WatchConnectionState.CheckingConnection || this == WatchConnectionState.Syncing

    private val WatchConnectionError.clearsReachability: Boolean
        get() = this == WatchConnectionError.NotConnected || this == WatchConnectionError.WearServicesUnavailable
}
