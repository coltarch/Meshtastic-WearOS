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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.repository.WatchPrefs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var watchPrefs: RecordingWatchPrefs
    private lateinit var viewModel: WatchViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        watchPrefs = RecordingWatchPrefs()
        viewModel = WatchViewModel(watchPrefs)
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
            RecordingWatchPrefs(pushToWatchEnabled = false, syncNodesEnabled = true, syncMessagesEnabled = false)
        viewModel = WatchViewModel(watchPrefs)

        viewModel.uiState.test {
            assertEquals(
                WatchSettingsUiState(
                    pushToWatchEnabled = false,
                    syncNodesEnabled = true,
                    syncMessagesEnabled = false,
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

            watchPrefs.pushToWatchEnabled.value = false
            watchPrefs.syncNodesEnabled.value = false
            watchPrefs.syncMessagesEnabled.value = false
            runCurrent()

            assertEquals(
                WatchSettingsUiState(
                    pushToWatchEnabled = false,
                    syncNodesEnabled = false,
                    syncMessagesEnabled = false,
                ),
                expectMostRecentItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each toggle delegates to the matching preference`() {
        viewModel.setPushToWatchEnabled(false)
        viewModel.setSyncNodesEnabled(false)
        viewModel.setSyncMessagesEnabled(false)

        assertEquals(listOf(false), watchPrefs.pushToWatchCalls)
        assertEquals(listOf(false), watchPrefs.syncNodesCalls)
        assertEquals(listOf(false), watchPrefs.syncMessagesCalls)
    }

    @Test
    fun `disabling global synchronization preserves category preferences`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.setPushToWatchEnabled(false)
            runCurrent()

            assertEquals(
                WatchSettingsUiState(
                    pushToWatchEnabled = false,
                    syncNodesEnabled = true,
                    syncMessagesEnabled = true,
                ),
                expectMostRecentItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manual synchronization requests once per deliberate action`() = runTest(testDispatcher) {
        viewModel.requestSync()
        assertEquals(1, watchPrefs.syncRequestCalls)

        advanceTimeBy(500)
        runCurrent()
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
        advanceTimeBy(500)
        runCurrent()
        viewModel.requestSync()

        assertEquals(2, watchPrefs.syncRequestCalls)
    }

    private class RecordingWatchPrefs(
        pushToWatchEnabled: Boolean = true,
        syncNodesEnabled: Boolean = true,
        syncMessagesEnabled: Boolean = true,
    ) : WatchPrefs {
        override val pushToWatchEnabled = MutableStateFlow(pushToWatchEnabled)
        override val syncNodesEnabled = MutableStateFlow(syncNodesEnabled)
        override val syncMessagesEnabled = MutableStateFlow(syncMessagesEnabled)
        override val syncRequest = MutableStateFlow(0L)

        val pushToWatchCalls = mutableListOf<Boolean>()
        val syncNodesCalls = mutableListOf<Boolean>()
        val syncMessagesCalls = mutableListOf<Boolean>()
        var syncRequestCalls = 0
        var syncRequestFailure: Exception? = null

        override fun setPushToWatchEnabled(enabled: Boolean) {
            pushToWatchCalls += enabled
            pushToWatchEnabled.value = enabled
        }

        override fun setSyncNodesEnabled(enabled: Boolean) {
            syncNodesCalls += enabled
            syncNodesEnabled.value = enabled
        }

        override fun setSyncMessagesEnabled(enabled: Boolean) {
            syncMessagesCalls += enabled
            syncMessagesEnabled.value = enabled
        }

        override fun requestSync() {
            syncRequestCalls++
            syncRequestFailure?.let { throw it }
            syncRequest.value++
        }
    }
}
