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
package org.meshtastic.core.common.wear

/**
 * Decides which messages in an arriving snapshot the watch has not seen, and should therefore notify about.
 *
 * Snapshots arrive whole rather than as events, so a notification needs the difference between the snapshot in hand and
 * the one before it: ids already seen and the user's own messages are filtered out, and the rest keeps its order.
 *
 * Pure on purpose — no state, no coroutines, no platform types — because it is the only part of the watch's
 * notification feature that a test can reach, and the set of seen ids belongs to the caller that keeps it.
 */
object NewMessageDetector {

    /** One snapshot message, reduced to what the decision needs. */
    data class MessageRef(val uuid: Long, val contactKey: String, val isMe: Boolean)

    /** The [current] messages that are neither in [seen] nor the user's own, in the order they arrived. */
    fun newIncoming(seen: Set<Long>, current: List<MessageRef>): List<MessageRef> =
        current.filter { ref -> !ref.isMe && ref.uuid !in seen }
}
