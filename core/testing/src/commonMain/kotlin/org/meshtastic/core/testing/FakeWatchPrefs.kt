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
package org.meshtastic.core.testing

import org.meshtastic.core.repository.RecentMessageLimit
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.WearOperatingMode

class FakeWatchPrefs :
    BaseFake(),
    WatchPrefs {
    override val operatingMode = mutableStateFlow(WearOperatingMode.COMPANION)
    override val watchConnectivityEnabled = mutableStateFlow(true)
    override val syncPreferences = mutableStateFlow(WatchSyncPreferences())
    override val highContrastModeEnabled = mutableStateFlow(false)
    override val syncRequest = mutableStateFlow(0L)
    override val lastSuccessfulSyncMillis = mutableStateFlow<Long?>(null)

    override fun setOperatingMode(mode: WearOperatingMode) {
        operatingMode.value = mode
    }

    override suspend fun readOperatingMode(): WearOperatingMode = operatingMode.value

    override suspend fun setOperatingModeAndWait(mode: WearOperatingMode) {
        operatingMode.value = mode
    }

    override fun setWatchConnectivityEnabled(enabled: Boolean) {
        watchConnectivityEnabled.value = enabled
    }

    override fun setSyncNodesEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncNodes = enabled)
    }

    override fun setSyncChannelsEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncChannels = enabled)
    }

    override fun setSyncRecentMessagesEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncRecentMessages = enabled)
    }

    override fun setRecentMessageLimit(limit: RecentMessageLimit) {
        syncPreferences.value = syncPreferences.value.copy(recentMessageLimit = limit)
    }

    override fun setSyncQuickRepliesEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncQuickReplies = enabled)
    }

    override fun setSyncRadioStatusEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncRadioStatus = enabled)
    }

    override fun setHighContrastModeEnabled(enabled: Boolean) {
        highContrastModeEnabled.value = enabled
    }

    override fun requestSync() {
        syncRequest.value++
    }

    override suspend fun setLastSuccessfulSyncMillis(timestampMillis: Long) {
        lastSuccessfulSyncMillis.value = timestampMillis
    }
}
