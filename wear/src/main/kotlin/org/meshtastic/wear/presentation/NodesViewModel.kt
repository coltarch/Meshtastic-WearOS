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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.model.WearableNode
import org.meshtastic.wear.data.WearCompanionSyncState
import org.meshtastic.wear.data.WearDataLayerClient
import org.meshtastic.wear.presentation.model.WearableNodeRepository

class NodesViewModel(repository: WearableNodeRepository, private val dataLayerClient: WearDataLayerClient) :
    ViewModel() {
    val nodes: StateFlow<List<WearableNode>> = repository.syncedNodes
    val syncState: StateFlow<WearCompanionSyncState> = dataLayerClient.syncState

    init {
        retrySync()
    }

    fun retrySync() {
        viewModelScope.launch { dataLayerClient.synchronize() }
    }
}
