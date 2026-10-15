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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.repository.RecentMessageLimit
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.WearOperatingMode
import org.meshtastic.wear.data.WearCompanionSyncState
import org.meshtastic.wear.data.WearDataLayerClient
import org.meshtastic.wear.di.WearStartupState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmed mode is persisted without replacing the active graph`() = runTest(dispatcher) {
        val prefs = RecordingWatchPrefs()
        val startupState = WearStartupState().also { it.initialize(WearOperatingMode.COMPANION, true) }
        val viewModel = SettingsViewModel(prefs, startupState)

        viewModel.requestOperatingMode(WearOperatingMode.STANDALONE_RADIO)
        viewModel.confirmOperatingModeChange()
        advanceUntilIdle()

        assertEquals(WearOperatingMode.STANDALONE_RADIO, prefs.operatingMode.value)
        assertEquals(WearOperatingMode.COMPANION, startupState.operatingMode.value)
    }

    @Test
    fun `cancelling confirmation leaves the stored mode unchanged`() = runTest(dispatcher) {
        val prefs = RecordingWatchPrefs()
        val viewModel =
            SettingsViewModel(prefs, WearStartupState().also { it.initialize(WearOperatingMode.COMPANION, true) })

        viewModel.requestOperatingMode(WearOperatingMode.STANDALONE_RADIO)
        viewModel.cancelOperatingModeChange()
        advanceUntilIdle()

        assertEquals(WearOperatingMode.COMPANION, prefs.operatingMode.value)
        assertNull(viewModel.confirmationMode.value)
    }

    @Test
    fun `companion sync delegates to the bounded Data Layer operation`() = runTest(dispatcher) {
        val client = RecordingDataLayerClient()
        val viewModel = CompanionConnectionViewModel(client)

        viewModel.requestSync()
        advanceUntilIdle()

        assertEquals(1, client.syncCalls)
    }
}

private class RecordingDataLayerClient : WearDataLayerClient {
    override val syncState = MutableStateFlow<WearCompanionSyncState>(WearCompanionSyncState.PhoneUnavailable)
    var syncCalls = 0

    override suspend fun synchronize() {
        syncCalls += 1
    }

    override suspend fun sendCommand(path: String, payload: ByteArray?): Boolean = true
}

private class RecordingWatchPrefs : WatchPrefs {
    override val operatingMode = MutableStateFlow(WearOperatingMode.COMPANION)
    override val watchConnectivityEnabled = MutableStateFlow(true)
    override val syncPreferences = MutableStateFlow(WatchSyncPreferences())
    override val highContrastModeEnabled = MutableStateFlow(false)
    override val syncRequest = MutableStateFlow(0L)
    override val lastSuccessfulSyncMillis = MutableStateFlow<Long?>(null)

    override fun setOperatingMode(mode: WearOperatingMode) {
        operatingMode.value = mode
    }

    override suspend fun readOperatingMode(): WearOperatingMode = operatingMode.value

    override suspend fun setOperatingModeAndWait(mode: WearOperatingMode) {
        operatingMode.value = mode
    }

    override fun setWatchConnectivityEnabled(enabled: Boolean) {
        watchConnectivityEnabled.value = enabled
    }

    override fun setSyncNodesEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncNodes = enabled)
    }

    override fun setSyncChannelsEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncChannels = enabled)
    }

    override fun setSyncRecentMessagesEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncRecentMessages = enabled)
    }

    override fun setRecentMessageLimit(limit: RecentMessageLimit) {
        syncPreferences.value = syncPreferences.value.copy(recentMessageLimit = limit)
    }

    override fun setSyncQuickRepliesEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncQuickReplies = enabled)
    }

    override fun setSyncRadioStatusEnabled(enabled: Boolean) {
        syncPreferences.value = syncPreferences.value.copy(syncRadioStatus = enabled)
    }

    override fun setHighContrastModeEnabled(enabled: Boolean) {
        highContrastModeEnabled.value = enabled
    }

    override fun requestSync() {
        syncRequest.value += 1
    }

    override suspend fun setLastSuccessfulSyncMillis(timestampMillis: Long) {
        lastSuccessfulSyncMillis.value = timestampMillis
    }
}
