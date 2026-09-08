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

import org.meshtastic.core.repository.WatchPrefs

class FakeWatchPrefs :
    BaseFake(),
    WatchPrefs {
    override val pushToWatchEnabled = mutableStateFlow(true)
    override val syncNodesEnabled = mutableStateFlow(true)
    override val syncMessagesEnabled = mutableStateFlow(true)
    override val syncRequest = mutableStateFlow(0L)

    override fun setPushToWatchEnabled(enabled: Boolean) {
        pushToWatchEnabled.value = enabled
    }

    override fun setSyncNodesEnabled(enabled: Boolean) {
        syncNodesEnabled.value = enabled
    }

    override fun setSyncMessagesEnabled(enabled: Boolean) {
        syncMessagesEnabled.value = enabled
    }

    override fun requestSync() {
        syncRequest.value++
    }
}
