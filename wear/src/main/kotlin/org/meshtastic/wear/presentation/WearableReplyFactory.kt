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
package org.meshtastic.wear.presentation

import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.model.WearableReply
import org.meshtastic.wear.presentation.model.WearableMessageRepository

/** Prepares local UI feedback and a reply whose destination comes only from the selected conversation. */
internal class WearableReplyFactory(private val repository: WearableMessageRepository) {
    fun create(contactKey: String, text: String): WearableReply {
        val now = System.currentTimeMillis()
        repository.addLocalMessage(
            WearableMessage(
                uuid = now,
                contactKey = contactKey,
                senderName = "You",
                senderShortName = "YO",
                text = text,
                isMe = true,
                timestamp = now,
                status = MessageStatus.QUEUED,
            ),
        )

        val selectedContact = ContactKey(contactKey)
        val address = selectedContact.address
        return WearableReply(
            address = address.toIdString().takeUnless { address is NodeAddress.Broadcast },
            channelIndex = selectedContact.channelOrNull ?: 0,
            text = text,
        )
    }
}
