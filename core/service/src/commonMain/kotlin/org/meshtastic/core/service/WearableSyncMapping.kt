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
package org.meshtastic.core.service

import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.WearableNode
import org.meshtastic.core.model.WearableRadioStatus

/** Builds the privacy-bounded radio snapshot from the local node and connection state. */
internal fun createWearableRadioStatus(node: Node?, connectionState: ConnectionState): WearableRadioStatus =
    WearableRadioStatus(
        connected = connectionState == ConnectionState.Connected,
        batteryLevel = node?.batteryLevel,
        voltage = node?.voltage,
        snr = node?.snrOrNull,
        lastSeen = node?.lastHeard?.toLong()?.times(MILLIS_PER_SECOND),
        temperature = node?.environmentMetrics?.temperature,
        relativeHumidity = node?.environmentMetrics?.relative_humidity,
    )

/** Maps a mesh node to its wearable form, including telemetry only when the category is enabled. */
internal fun Node.toWearableNode(includeRadioStatus: Boolean): WearableNode =
    WearableNode(
        num = num,
        name = user.long_name,
        shortName = user.short_name,
        online = includeRadioStatus && isOnline,
        battery = batteryLevel.takeIf { includeRadioStatus },
        snr = snrOrNull.takeIf { includeRadioStatus },
        lastSeen = if (includeRadioStatus) lastHeard.toLong() * MILLIS_PER_SECOND else 0L,
        favorite = isFavorite,
    )

private const val MILLIS_PER_SECOND = 1_000L
