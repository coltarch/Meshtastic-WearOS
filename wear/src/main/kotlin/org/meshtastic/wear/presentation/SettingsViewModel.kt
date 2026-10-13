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
@file:Suppress("MagicNumber")

package org.meshtastic.wear.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WearOperatingMode
import org.meshtastic.wear.data.WearCompanionSyncState
import org.meshtastic.wear.data.WearDataLayerClient
import org.meshtastic.wear.di.WearStartupState

class SettingsViewModel(private val watchPrefs: WatchPrefs, private val startupState: WearStartupState) : ViewModel() {
    private val mutableConfirmationMode = MutableStateFlow<WearOperatingMode?>(null)
    private val activeMode = startupState.operatingMode.value
    private val restartMode = startupState.restartMode
    private val mutableConfiguredMode = MutableStateFlow(restartMode)
    val confirmationMode: StateFlow<WearOperatingMode?> = mutableConfirmationMode.asStateFlow()

    val settingsState: StateFlow<WearSettingsUiState> =
        mutableConfiguredMode
            .map { configuredMode ->
                WearSettingsUiState(
                    configuredMode = configuredMode,
                    activeMode = activeMode,
                    restartRequired = configuredMode != restartMode,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                WearSettingsUiState(configuredMode = restartMode, activeMode = activeMode, restartRequired = false),
            )

    init {
        viewModelScope.launch { mutableConfiguredMode.value = watchPrefs.readOperatingMode() }
    }

    val highContrastModeEnabled = watchPrefs.highContrastModeEnabled

    val standaloneSupported: Boolean
        get() = startupState.standaloneSupported

    fun setHighContrastModeEnabled(enabled: Boolean) {
        watchPrefs.setHighContrastModeEnabled(enabled)
    }

    fun requestOperatingMode(mode: WearOperatingMode) {
        if (mode != mutableConfiguredMode.value) mutableConfirmationMode.value = mode
    }

    fun cancelOperatingModeChange() {
        mutableConfirmationMode.value = null
    }

    fun confirmOperatingModeChange() {
        val mode = mutableConfirmationMode.value ?: return
        viewModelScope.launch {
            watchPrefs.setOperatingModeAndWait(mode)
            mutableConfiguredMode.value = mode
            mutableConfirmationMode.value = null
        }
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

@Immutable
data class WearSettingsUiState(
    val configuredMode: WearOperatingMode = WearOperatingMode.COMPANION,
    val activeMode: WearOperatingMode = WearOperatingMode.COMPANION,
    val restartRequired: Boolean = false,
)
