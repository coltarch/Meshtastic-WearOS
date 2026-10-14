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
package org.meshtastic.feature.settings

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.repository.RecentMessageLimit
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.WatchSyncStateHolder
import org.meshtastic.core.repository.WearOperatingMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var watchPrefs: RecordingWatchPrefs
    private lateinit var syncStateHolder: WatchSyncStateHolder
    private lateinit var viewModel: WatchViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        watchPrefs = RecordingWatchPrefs()
        syncStateHolder = WatchSyncStateHolder()
        viewModel = WatchViewModel(watchPrefs, syncStateHolder)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects preference defaults`() {
        assertEquals(WatchSettingsUiState(), viewModel.uiState.value)
    }

    @Test
    fun `initial state reads existing preference values`() = runTest(testDispatcher) {
        watchPrefs =
            RecordingWatchPrefs(
                watchConnectivityEnabled = false,
                syncPreferences = WatchSyncPreferences(syncRecentMessages = false),
            )
        viewModel = WatchViewModel(watchPrefs, syncStateHolder)

        viewModel.uiState.test {
            assertEquals(
                WatchSettingsUiState(
                    watchConnectivityEnabled = false,
                    syncPreferences = WatchSyncPreferences(syncRecentMessages = false),
                    connection = WatchConnectionUiState(canSync = false),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `preference changes update UI state`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            watchPrefs.watchConnectivityEnabled.value = false
            watchPrefs.syncPreferences.value = WatchSyncPreferences(
                syncNodes = false,
                syncChannels = false,
                syncRecentMessages = false,
                syncQuickReplies = false,
                syncRadioStatus = false,
            )
            runCurrent()

            assertEquals(
                WatchSettingsUiState(
                    watchConnectivityEnabled = false,
                    syncPreferences = watchPrefs.syncPreferences.value,
                    connection = WatchConnectionUiState(canSync = false, nothingSelected = true),
                ),
                expectMostRecentItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each toggle delegates to the matching preference`() {
        viewModel.setWatchConnectivityEnabled(false)
        viewModel.setSyncNodesEnabled(false)
        viewModel.setSyncChannelsEnabled(false)
        viewModel.setSyncRecentMessagesEnabled(false)
        viewModel.setRecentMessageLimit(RecentMessageLimit.FIFTY)
        viewModel.setSyncQuickRepliesEnabled(false)
        viewModel.setSyncRadioStatusEnabled(false)

        assertEquals(listOf(false), watchPrefs.pushToWatchCalls)
        assertEquals(listOf(false), watchPrefs.syncNodesCalls)
        assertEquals(listOf(false), watchPrefs.syncChannelsCalls)
        assertEquals(listOf(false), watchPrefs.syncRecentMessagesCalls)
        assertEquals(listOf(RecentMessageLimit.FIFTY), watchPrefs.messageLimitCalls)
        assertEquals(listOf(false), watchPrefs.syncQuickRepliesCalls)
        assertEquals(listOf(false), watchPrefs.syncRadioStatusCalls)
    }

    @Test
    fun `disabling global synchronization preserves category preferences`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.setWatchConnectivityEnabled(false)
            runCurrent()

            assertEquals(
                WatchSettingsUiState(
                    watchConnectivityEnabled = false,
                    connection = WatchConnectionUiState(canSync = false),
                ),
                expectMostRecentItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `all categories disabled reports nothing selected and disables manual sync`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            watchPrefs.syncPreferences.value =
                WatchSyncPreferences(
                    syncNodes = false,
                    syncChannels = false,
                    syncRecentMessages = false,
                    syncQuickReplies = false,
                    syncRadioStatus = false,
                )
            runCurrent()

            val state = expectMostRecentItem()
            assertEquals(false, state.connection.canSync)
            assertEquals(true, state.connection.nothingSelected)
            viewModel.requestSync()
            assertEquals(0, watchPrefs.syncRequestCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manual synchronization requests once per deliberate action`() = runTest(testDispatcher) {
        viewModel.requestSync()
        assertEquals(1, watchPrefs.syncRequestCalls)

        syncStateHolder.syncSucceeded(1L)
        viewModel.requestSync()

        assertEquals(2, watchPrefs.syncRequestCalls)
    }

    @Test
    fun `rapid repeated synchronization requests are collapsed`() = runTest(testDispatcher) {
        repeat(3) { viewModel.requestSync() }

        assertEquals(1, watchPrefs.syncRequestCalls)
    }

    @Test
    fun `failed synchronization request does not permanently block retry`() = runTest(testDispatcher) {
        watchPrefs.syncRequestFailure = IllegalStateException("test failure")

        assertFailsWith<IllegalStateException> { viewModel.requestSync() }

        watchPrefs.syncRequestFailure = null
        viewModel.requestSync()

        assertEquals(2, watchPrefs.syncRequestCalls)
    }

    private class RecordingWatchPrefs(
        watchConnectivityEnabled: Boolean = true,
        syncPreferences: WatchSyncPreferences = WatchSyncPreferences(),
    ) : WatchPrefs {
        override val operatingMode = MutableStateFlow(WearOperatingMode.COMPANION)
        override val watchConnectivityEnabled = MutableStateFlow(watchConnectivityEnabled)
        override val syncPreferences = MutableStateFlow(syncPreferences)
        override val highContrastModeEnabled = MutableStateFlow(false)
        override val syncRequest = MutableStateFlow(0L)
        override val lastSuccessfulSyncMillis = MutableStateFlow<Long?>(null)

        val pushToWatchCalls = mutableListOf<Boolean>()
        val syncNodesCalls = mutableListOf<Boolean>()
        val syncChannelsCalls = mutableListOf<Boolean>()
        val syncRecentMessagesCalls = mutableListOf<Boolean>()
        val messageLimitCalls = mutableListOf<RecentMessageLimit>()
        val syncQuickRepliesCalls = mutableListOf<Boolean>()
        val syncRadioStatusCalls = mutableListOf<Boolean>()
        var syncRequestCalls = 0
        var syncRequestFailure: Exception? = null

        override fun setOperatingMode(mode: WearOperatingMode) {
            operatingMode.value = mode
        }

        override suspend fun readOperatingMode(): WearOperatingMode = operatingMode.value

        override suspend fun setOperatingModeAndWait(mode: WearOperatingMode) {
            operatingMode.value = mode
        }

        override fun setWatchConnectivityEnabled(enabled: Boolean) {
            pushToWatchCalls += enabled
            watchConnectivityEnabled.value = enabled
        }

        override fun setSyncNodesEnabled(enabled: Boolean) {
            syncNodesCalls += enabled
            syncPreferences.value = syncPreferences.value.copy(syncNodes = enabled)
        }

        override fun setSyncChannelsEnabled(enabled: Boolean) {
            syncChannelsCalls += enabled
            syncPreferences.value = syncPreferences.value.copy(syncChannels = enabled)
        }

        override fun setSyncRecentMessagesEnabled(enabled: Boolean) {
            syncRecentMessagesCalls += enabled
            syncPreferences.value = syncPreferences.value.copy(syncRecentMessages = enabled)
        }

        override fun setRecentMessageLimit(limit: RecentMessageLimit) {
            messageLimitCalls += limit
            syncPreferences.value = syncPreferences.value.copy(recentMessageLimit = limit)
        }

        override fun setSyncQuickRepliesEnabled(enabled: Boolean) {
            syncQuickRepliesCalls += enabled
            syncPreferences.value = syncPreferences.value.copy(syncQuickReplies = enabled)
        }

        override fun setSyncRadioStatusEnabled(enabled: Boolean) {
            syncRadioStatusCalls += enabled
            syncPreferences.value = syncPreferences.value.copy(syncRadioStatus = enabled)
        }

        override fun setHighContrastModeEnabled(enabled: Boolean) {
            highContrastModeEnabled.value = enabled
        }

        override fun requestSync() {
            syncRequestCalls++
            syncRequestFailure?.let { throw it }
            syncRequest.value++
        }

        override suspend fun setLastSuccessfulSyncMillis(timestampMillis: Long) {
            lastSuccessfulSyncMillis.value = timestampMillis
        }
    }
}
