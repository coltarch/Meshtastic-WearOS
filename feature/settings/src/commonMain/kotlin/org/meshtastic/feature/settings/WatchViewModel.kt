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
package org.meshtastic.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.RecentMessageLimit
import org.meshtastic.core.repository.WatchConnectionError
import org.meshtastic.core.repository.WatchConnectionState
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.WatchSyncProgress
import org.meshtastic.core.repository.WatchSyncState
import org.meshtastic.core.repository.WatchSyncStateHolder
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@KoinViewModel
class WatchViewModel(private val watchPrefs: WatchPrefs, private val watchSyncStateHolder: WatchSyncStateHolder) :
    ViewModel() {
    val uiState: StateFlow<WatchSettingsUiState> =
        combine(
            watchPrefs.watchConnectivityEnabled,
            watchPrefs.syncPreferences,
            watchPrefs.lastSuccessfulSyncMillis,
            watchSyncStateHolder.state,
            ::buildWatchSettingsUiState,
        )
            .stateInWhileSubscribed(
                initialValue =
                buildWatchSettingsUiState(
                    watchConnectivityEnabled = watchPrefs.watchConnectivityEnabled.value,
                    syncPreferences = watchPrefs.syncPreferences.value,
                    persistedLastSuccessfulSyncMillis = watchPrefs.lastSuccessfulSyncMillis.value,
                    syncState = watchSyncStateHolder.state.value,
                ),
            )

    fun setWatchConnectivityEnabled(enabled: Boolean) = watchPrefs.setWatchConnectivityEnabled(enabled)

    fun setSyncNodesEnabled(enabled: Boolean) = watchPrefs.setSyncNodesEnabled(enabled)

    fun setSyncChannelsEnabled(enabled: Boolean) = watchPrefs.setSyncChannelsEnabled(enabled)

    fun setSyncRecentMessagesEnabled(enabled: Boolean) = watchPrefs.setSyncRecentMessagesEnabled(enabled)

    fun setRecentMessageLimit(limit: RecentMessageLimit) = watchPrefs.setRecentMessageLimit(limit)

    fun setSyncQuickRepliesEnabled(enabled: Boolean) = watchPrefs.setSyncQuickRepliesEnabled(enabled)

    fun setSyncRadioStatusEnabled(enabled: Boolean) = watchPrefs.setSyncRadioStatusEnabled(enabled)

    /** Requests one serialized synchronization unless a connection check or sync is already active. */
    fun requestSync() {
        if (!watchPrefs.syncPreferences.value.hasEnabledCategory) return
        if (!watchSyncStateHolder.beginManualSyncRequest()) return

        try {
            watchPrefs.requestSync()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            watchSyncStateHolder.syncFailed(WatchConnectionError.SynchronizationFailed)
            throw failure
        }
    }
}

@Immutable
data class WatchConnectionUiState(
    val connectionState: WatchConnectionState = WatchConnectionState.Disconnected,
    val watchName: String? = null,
    val reachableWatchCount: Int = 0,
    val isReachable: Boolean = false,
    val lastCommunicationMillis: Long? = null,
    val lastSuccessfulSyncMillis: Long? = null,
    val syncProgress: WatchSyncProgress? = null,
    val canSync: Boolean = true,
    val nothingSelected: Boolean = false,
    val error: WatchConnectionError? = null,
)

@Immutable
data class WatchSettingsUiState(
    val watchConnectivityEnabled: Boolean = true,
    val syncPreferences: WatchSyncPreferences = WatchSyncPreferences(),
    val connection: WatchConnectionUiState = WatchConnectionUiState(),
)

private fun buildWatchSettingsUiState(
    watchConnectivityEnabled: Boolean,
    syncPreferences: WatchSyncPreferences,
    persistedLastSuccessfulSyncMillis: Long?,
    syncState: WatchSyncState,
): WatchSettingsUiState {
    val hasSyncContent = syncPreferences.hasEnabledCategory
    val isActive =
        syncState.connectionState == WatchConnectionState.CheckingConnection ||
            syncState.connectionState == WatchConnectionState.Syncing
    val visibleConnection =
        if (watchConnectivityEnabled) {
            WatchConnectionUiState(
                connectionState = syncState.connectionState,
                watchName = syncState.watchName,
                reachableWatchCount = syncState.reachableWatchCount,
                isReachable = syncState.reachableWatchCount > 0,
                lastCommunicationMillis = syncState.lastCommunicationMillis,
                lastSuccessfulSyncMillis = syncState.lastSuccessfulSyncMillis ?: persistedLastSuccessfulSyncMillis,
                syncProgress = syncState.syncProgress,
                canSync = hasSyncContent && !isActive,
                nothingSelected = !hasSyncContent,
                error = syncState.error,
            )
        } else {
            WatchConnectionUiState(
                canSync = false,
                nothingSelected = !hasSyncContent,
                lastSuccessfulSyncMillis = persistedLastSuccessfulSyncMillis,
            )
        }

    return WatchSettingsUiState(
        watchConnectivityEnabled = watchConnectivityEnabled,
        syncPreferences = syncPreferences,
        connection = visibleConnection,
    )
}
