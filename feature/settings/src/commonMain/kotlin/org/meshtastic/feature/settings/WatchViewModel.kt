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
import androidx.lifecycle.viewModelScope
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@KoinViewModel
class WatchViewModel(private val watchPrefs: WatchPrefs) : ViewModel() {
    val uiState: StateFlow<WatchSettingsUiState> =
        combine(watchPrefs.pushToWatchEnabled, watchPrefs.syncNodesEnabled, watchPrefs.syncMessagesEnabled) {
                pushToWatchEnabled,
                syncNodesEnabled,
                syncMessagesEnabled,
            ->
            WatchSettingsUiState(
                pushToWatchEnabled = pushToWatchEnabled,
                syncNodesEnabled = syncNodesEnabled,
                syncMessagesEnabled = syncMessagesEnabled,
            )
        }
            .stateInWhileSubscribed(
                initialValue =
                WatchSettingsUiState(
                    pushToWatchEnabled = watchPrefs.pushToWatchEnabled.value,
                    syncNodesEnabled = watchPrefs.syncNodesEnabled.value,
                    syncMessagesEnabled = watchPrefs.syncMessagesEnabled.value,
                ),
            )

    private val syncRequestGuard = atomic(false)

    fun setPushToWatchEnabled(enabled: Boolean) = watchPrefs.setPushToWatchEnabled(enabled)

    fun setSyncNodesEnabled(enabled: Boolean) = watchPrefs.setSyncNodesEnabled(enabled)

    fun setSyncMessagesEnabled(enabled: Boolean) = watchPrefs.setSyncMessagesEnabled(enabled)

    /** Requests one synchronization and ignores repeated taps during a short interaction guard window. */
    fun requestSync() {
        if (!syncRequestGuard.compareAndSet(expect = false, update = true)) return

        try {
            watchPrefs.requestSync()
        } finally {
            viewModelScope.launch {
                delay(SYNC_REQUEST_GUARD_MILLIS)
                syncRequestGuard.value = false
            }
        }
    }

    private companion object {
        const val SYNC_REQUEST_GUARD_MILLIS = 500L
    }
}

@Immutable
data class WatchSettingsUiState(
    val pushToWatchEnabled: Boolean = true,
    val syncNodesEnabled: Boolean = true,
    val syncMessagesEnabled: Boolean = true,
)
