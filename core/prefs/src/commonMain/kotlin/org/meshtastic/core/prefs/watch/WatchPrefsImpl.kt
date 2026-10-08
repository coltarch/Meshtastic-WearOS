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
@file:Suppress("TooManyFunctions")

package org.meshtastic.core.prefs.watch

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.WatchDataStore
import org.meshtastic.core.repository.RecentMessageLimit
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.WearOperatingMode

@Single
class WatchPrefsImpl(private val dataStore: WatchDataStore, dispatchers: CoroutineDispatchers) : WatchPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val operatingMode: StateFlow<WearOperatingMode> =
        dataStore.data
            .map { preferences -> parseOperatingMode(preferences[KEY_OPERATING_MODE]) }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_OPERATING_MODE)

    override fun setOperatingMode(mode: WearOperatingMode) {
        scope.launch { dataStore.edit { it[KEY_OPERATING_MODE] = mode.name } }
    }

    override suspend fun readOperatingMode(): WearOperatingMode =
        parseOperatingMode(dataStore.data.first()[KEY_OPERATING_MODE])

    override suspend fun setOperatingModeAndWait(mode: WearOperatingMode) {
        dataStore.edit { it[KEY_OPERATING_MODE] = mode.name }
    }

    override val watchConnectivityEnabled: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_WATCH_CONNECTIVITY] ?: DEFAULT_WATCH_CONNECTIVITY_ENABLED }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_WATCH_CONNECTIVITY_ENABLED)

    override fun setWatchConnectivityEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_WATCH_CONNECTIVITY] = enabled } }
    }

    override val syncPreferences: StateFlow<WatchSyncPreferences> =
        dataStore.data
            .map { preferences ->
                WatchSyncPreferences(
                    syncNodes = preferences[KEY_SYNC_NODES] ?: DEFAULT_SYNC_PREFERENCES.syncNodes,
                    syncChannels = preferences[KEY_SYNC_CHANNELS] ?: DEFAULT_SYNC_PREFERENCES.syncChannels,
                    syncRecentMessages = preferences[KEY_SYNC_MESSAGES] ?: DEFAULT_SYNC_PREFERENCES.syncRecentMessages,
                    recentMessageLimit = RecentMessageLimit.fromStoredValue(preferences[KEY_MESSAGE_LIMIT]),
                    syncQuickReplies = preferences[KEY_SYNC_QUICK_REPLIES] ?: DEFAULT_SYNC_PREFERENCES.syncQuickReplies,
                    syncRadioStatus = preferences[KEY_SYNC_RADIO_STATUS] ?: DEFAULT_SYNC_PREFERENCES.syncRadioStatus,
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SYNC_PREFERENCES)

    override fun setSyncNodesEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SYNC_NODES] = enabled } }
    }

    override fun setSyncChannelsEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SYNC_CHANNELS] = enabled } }
    }

    override fun setSyncRecentMessagesEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SYNC_MESSAGES] = enabled } }
    }

    override fun setRecentMessageLimit(limit: RecentMessageLimit) {
        scope.launch { dataStore.edit { it[KEY_MESSAGE_LIMIT] = limit.count.toString() } }
    }

    override fun setSyncQuickRepliesEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SYNC_QUICK_REPLIES] = enabled } }
    }

    override fun setSyncRadioStatusEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SYNC_RADIO_STATUS] = enabled } }
    }

    override val highContrastModeEnabled: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_HIGH_CONTRAST_MODE] ?: DEFAULT_HIGH_CONTRAST_MODE_ENABLED }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_HIGH_CONTRAST_MODE_ENABLED)

    override fun setHighContrastModeEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_HIGH_CONTRAST_MODE] = enabled } }
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

    override val lastSuccessfulSyncMillis: StateFlow<Long?> =
        dataStore.data.map { it[KEY_LAST_SUCCESSFUL_SYNC] }.stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun setLastSuccessfulSyncMillis(timestampMillis: Long) {
        dataStore.edit { it[KEY_LAST_SUCCESSFUL_SYNC] = timestampMillis }
    }

    companion object {
        internal val DEFAULT_OPERATING_MODE = WearOperatingMode.COMPANION
        const val DEFAULT_WATCH_CONNECTIVITY_ENABLED = true
        internal val DEFAULT_SYNC_PREFERENCES = WatchSyncPreferences()
        const val DEFAULT_HIGH_CONTRAST_MODE_ENABLED = false
        const val INITIAL_SYNC_REQUEST = 0L

        internal val KEY_OPERATING_MODE = stringPreferencesKey("wear_operating_mode")

        // Keep the existing key so users retain their preference after the setting becomes a connectivity control.
        internal val KEY_WATCH_CONNECTIVITY = booleanPreferencesKey("watch_push_enabled")
        internal val KEY_SYNC_NODES = booleanPreferencesKey("watch_sync_nodes")
        internal val KEY_SYNC_CHANNELS = booleanPreferencesKey("watch_sync_channels")
        internal val KEY_SYNC_MESSAGES = booleanPreferencesKey("watch_sync_messages")
        internal val KEY_MESSAGE_LIMIT = stringPreferencesKey("watch_message_limit")
        internal val KEY_SYNC_QUICK_REPLIES = booleanPreferencesKey("watch_sync_quick_replies")
        internal val KEY_SYNC_RADIO_STATUS = booleanPreferencesKey("watch_sync_radio_status")
        internal val KEY_HIGH_CONTRAST_MODE = booleanPreferencesKey("watch_high_contrast_mode")
        internal val KEY_SYNC_REQUEST = longPreferencesKey("watch_sync_request")
        internal val KEY_LAST_SUCCESSFUL_SYNC = longPreferencesKey("watch_last_successful_sync")

        internal fun parseOperatingMode(storedValue: String?): WearOperatingMode =
            WearOperatingMode.entries.firstOrNull { it.name == storedValue } ?: DEFAULT_OPERATING_MODE
    }
}
