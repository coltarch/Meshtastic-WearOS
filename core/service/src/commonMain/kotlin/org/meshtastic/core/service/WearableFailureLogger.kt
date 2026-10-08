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

import co.touchlab.kermit.Logger

internal enum class WearableFailureLog(val message: String, val isError: Boolean = false) {
    UnexpectedSynchronization("Unexpected wearable synchronization failure", isError = true),
    ConnectedNodeQuery("Unable to query connected Wear nodes"),
    DataSynchronization("Wear Data API synchronization failed"),
    MessageSynchronization("Wear Message API synchronization failed"),
    UnexpectedCommand("Unexpected wearable command handling failure", isError = true),
    InvalidCommand("Rejected invalid wearable command"),
    UnavailableCommand("Wearable command could not be completed"),
    FailedCommand("Wearable command failed"),
    OpenConversation("Unable to open wearable conversation"),
    OpenNode("Unable to open wearable node"),
}

/** Logs failures without forwarding exception messages, causes, stack traces, or other runtime data. */
internal class WearableFailureLogger(private val logger: Logger) {
    fun log(event: WearableFailureLog, failure: Throwable) {
        val safeType = failure::class.simpleName ?: "Exception"
        val safeMessage = "${event.message} ($safeType)"
        if (event.isError) {
            logger.e { safeMessage }
        } else {
            logger.w { safeMessage }
        }
    }
}
