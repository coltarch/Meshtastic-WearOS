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
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.wear.data.WearCompanionSyncState
import org.meshtastic.wear.data.WearDataLayerClient

class SettingsViewModel(private val watchPrefs: WatchPrefs) : ViewModel() {
    val highContrastModeEnabled = watchPrefs.highContrastModeEnabled

    fun setHighContrastModeEnabled(enabled: Boolean) {
        watchPrefs.setHighContrastModeEnabled(enabled)
    }
}

class CompanionConnectionViewModel(private val dataLayerClient: WearDataLayerClient) : ViewModel() {
    val connectionState: StateFlow<WearCompanionSyncState> = dataLayerClient.syncState

    fun refreshPhoneLink() {
        viewModelScope.launch { dataLayerClient.synchronize() }
    }

    fun requestSync() {
        refreshPhoneLink()
    }
}
