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
package org.meshtastic.wear.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshtastic.core.repository.WearOperatingMode

sealed interface WearStartupStatus {
    data object Loading : WearStartupStatus

    data class Ready(val mode: WearOperatingMode) : WearStartupStatus

    data object Failed : WearStartupStatus
}

/** Process-lifetime snapshot selected before any mode-specific Koin module is loaded. */
class WearStartupState {
    private val mutableOperatingMode = MutableStateFlow(WearOperatingMode.COMPANION)
    private val mutableStatus = MutableStateFlow<WearStartupStatus>(WearStartupStatus.Loading)
    private var initialized = false

    val operatingMode: StateFlow<WearOperatingMode> = mutableOperatingMode.asStateFlow()
    val status: StateFlow<WearStartupStatus> = mutableStatus.asStateFlow()

    var standaloneSupported: Boolean = false
        private set

    /** Persisted configuration observed during this process restart. */
    var restartMode: WearOperatingMode = WearOperatingMode.COMPANION
        private set

    internal fun initialize(
        mode: WearOperatingMode,
        standaloneSupported: Boolean,
        restartMode: WearOperatingMode = mode,
    ) {
        check(!initialized) { "Wear startup mode may only be selected once per process" }
        initialized = true
        mutableOperatingMode.value = mode
        this.standaloneSupported = standaloneSupported
        this.restartMode = restartMode
        mutableStatus.value = WearStartupStatus.Ready(mode)
    }

    internal fun fail() {
        check(!initialized) { "Wear startup state is already initialized" }
        mutableStatus.value = WearStartupStatus.Failed
    }
}

internal fun selectWearStartupMode(storedMode: WearOperatingMode, standaloneSupported: Boolean): WearOperatingMode =
    if (storedMode == WearOperatingMode.STANDALONE_RADIO && standaloneSupported) {
        WearOperatingMode.STANDALONE_RADIO
    } else {
        WearOperatingMode.COMPANION
    }
