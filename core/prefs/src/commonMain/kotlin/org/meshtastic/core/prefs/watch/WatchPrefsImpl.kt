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
package org.meshtastic.core.prefs.watch

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.WatchDataStore
import org.meshtastic.core.repository.WatchPrefs

@Single
class WatchPrefsImpl(private val dataStore: WatchDataStore, dispatchers: CoroutineDispatchers) : WatchPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val pushToWatchEnabled: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_PUSH_TO_WATCH] ?: DEFAULT_PUSH_TO_WATCH_ENABLED }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_PUSH_TO_WATCH_ENABLED)

    override fun setPushToWatchEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_PUSH_TO_WATCH] = enabled } }
    }

    override val syncNodesEnabled: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_SYNC_NODES] ?: DEFAULT_SYNC_NODES_ENABLED }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SYNC_NODES_ENABLED)

    override fun setSyncNodesEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SYNC_NODES] = enabled } }
    }

    override val syncMessagesEnabled: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_SYNC_MESSAGES] ?: DEFAULT_SYNC_MESSAGES_ENABLED }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SYNC_MESSAGES_ENABLED)

    override fun setSyncMessagesEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SYNC_MESSAGES] = enabled } }
    }

    override val syncRequest: StateFlow<Long> =
        dataStore.data
            .map { it[KEY_SYNC_REQUEST] ?: INITIAL_SYNC_REQUEST }
            .stateIn(scope, SharingStarted.Eagerly, INITIAL_SYNC_REQUEST)

    override fun requestSync() {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_SYNC_REQUEST] = (preferences[KEY_SYNC_REQUEST] ?: INITIAL_SYNC_REQUEST) + 1
            }
        }
    }

    companion object {
        const val DEFAULT_PUSH_TO_WATCH_ENABLED = true
        const val DEFAULT_SYNC_NODES_ENABLED = true
        const val DEFAULT_SYNC_MESSAGES_ENABLED = true
        const val INITIAL_SYNC_REQUEST = 0L

        internal val KEY_PUSH_TO_WATCH = booleanPreferencesKey("watch_push_enabled")
        internal val KEY_SYNC_NODES = booleanPreferencesKey("watch_sync_nodes")
        internal val KEY_SYNC_MESSAGES = booleanPreferencesKey("watch_sync_messages")
        internal val KEY_SYNC_REQUEST = longPreferencesKey("watch_sync_request")
    }
}
