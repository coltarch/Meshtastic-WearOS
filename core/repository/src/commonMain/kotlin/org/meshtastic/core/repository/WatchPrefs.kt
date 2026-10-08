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
@file:Suppress("MagicNumber", "TooManyFunctions")

package org.meshtastic.core.repository

import kotlinx.coroutines.flow.StateFlow

enum class RecentMessageLimit(val count: Int) {
    TWENTY_FIVE(25),
    FIFTY(50),
    ONE_HUNDRED(100),
    ;

    companion object {
        fun fromStoredValue(value: String?): RecentMessageLimit =
            entries.firstOrNull { it.count.toString() == value } ?: TWENTY_FIVE
    }
}

data class WatchSyncPreferences(
    val syncNodes: Boolean = true,
    val syncChannels: Boolean = true,
    val syncRecentMessages: Boolean = true,
    val recentMessageLimit: RecentMessageLimit = RecentMessageLimit.TWENTY_FIVE,
    val syncQuickReplies: Boolean = true,
    val syncRadioStatus: Boolean = true,
) {
    val hasEnabledCategory: Boolean
        get() = syncNodes || syncChannels || syncRecentMessages || syncQuickReplies || syncRadioStatus
}

/** Preferences that control synchronization from the phone to a paired Wear OS device. */
interface WatchPrefs {
    val operatingMode: StateFlow<WearOperatingMode>

    fun setOperatingMode(mode: WearOperatingMode)

    /** Reads the persisted value rather than the [StateFlow] bootstrap value. Used before mode-specific startup. */
    suspend fun readOperatingMode(): WearOperatingMode

    /** Persists a confirmed mode change before the UI reports that a restart is required. */
    suspend fun setOperatingModeAndWait(mode: WearOperatingMode)

    /** Whether the phone exchanges synchronized data and commands with a paired Wear OS watch. */
    val watchConnectivityEnabled: StateFlow<Boolean>

    fun setWatchConnectivityEnabled(enabled: Boolean)

    val syncPreferences: StateFlow<WatchSyncPreferences>

    fun setSyncNodesEnabled(enabled: Boolean)

    fun setSyncChannelsEnabled(enabled: Boolean)

    fun setSyncRecentMessagesEnabled(enabled: Boolean)

    fun setRecentMessageLimit(limit: RecentMessageLimit)

    fun setSyncQuickRepliesEnabled(enabled: Boolean)

    fun setSyncRadioStatusEnabled(enabled: Boolean)

    val highContrastModeEnabled: StateFlow<Boolean>

    fun setHighContrastModeEnabled(enabled: Boolean)

    /** Monotonically increasing generation observed by the wearable synchronization service. */
    val syncRequest: StateFlow<Long>

    fun requestSync()

    /** Completion time of the most recent fully successful phone-to-watch synchronization. */
    val lastSuccessfulSyncMillis: StateFlow<Long?>

    suspend fun setLastSuccessfulSyncMillis(timestampMillis: Long)
}
