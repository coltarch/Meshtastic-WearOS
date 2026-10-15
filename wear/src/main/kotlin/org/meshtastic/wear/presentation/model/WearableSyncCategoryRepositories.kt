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
package org.meshtastic.wear.presentation.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshtastic.core.model.WearableRadioStatus

class WearableQuickReplyRepository {
    private val mutableReplies = MutableStateFlow<List<String>?>(null)
    val replies: StateFlow<List<String>?> = mutableReplies.asStateFlow()

    fun updateReplies(replies: List<String>) {
        mutableReplies.value = replies.filter(String::isNotBlank)
    }

    fun clear() {
        mutableReplies.value = emptyList()
    }
}

class WearableRadioStatusRepository {
    private val mutableStatus = MutableStateFlow<WearableRadioStatus?>(null)
    val status: StateFlow<WearableRadioStatus?> = mutableStatus.asStateFlow()

    fun updateStatus(status: WearableRadioStatus?) {
        mutableStatus.value = status
    }
}
