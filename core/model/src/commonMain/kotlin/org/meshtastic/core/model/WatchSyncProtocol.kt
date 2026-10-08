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
package org.meshtastic.core.model

import kotlinx.serialization.Serializable

/** Stable Data Layer paths shared by the phone and watch synchronization endpoints. */
object WatchSyncPaths {
    const val SYNC_STARTED = "/sync_started"
    const val SYNC_COMPLETED = "/sync_completed"
    const val SYNC_FAILED = "/sync_failed"
}

/** Independently clearable phone-to-watch synchronization categories. */
@Serializable
enum class WatchSyncCategory {
    Nodes,
    Channels,
    RecentMessages,
    QuickReplies,
    RadioStatus,
}

/** Privacy-bounded snapshot of the connected radio and its locally reported telemetry. */
@Serializable
data class WearableRadioStatus(
    val connected: Boolean,
    val batteryLevel: Int? = null,
    val voltage: Float? = null,
    val snr: Float? = null,
    val lastSeen: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val temperature: Float? = null,
    val relativeHumidity: Float? = null,
)
