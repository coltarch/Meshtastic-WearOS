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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.WatchSyncStateHolder
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.navigate_back
import org.meshtastic.core.resources.watch_message_retention_label
import org.meshtastic.core.resources.watch_settings_title
import org.meshtastic.core.resources.watch_sync_enabled_label
import org.meshtastic.core.resources.watch_sync_channels_label
import org.meshtastic.core.resources.watch_sync_messages_label
import org.meshtastic.core.resources.watch_sync_nodes_label
import org.meshtastic.core.resources.watch_sync_now_label
import org.meshtastic.core.testing.FakeWatchPrefs
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class WatchConfigurationScreenTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings entry navigates to typed watch route`() = runComposeUiTest {
        var route: Route? = null
        setContent { AppTheme { WatchSettingsEntry(onNavigate = { route = it }) } }

        onNodeWithText(getString(Res.string.watch_settings_title)).performClick()

        assertEquals(SettingsRoute.Watch, route)
    }

    @Test
    fun `watch connectivity off collapses dependent controls`() = runComposeUiTest {
        setWatchContent(state = WatchSettingsUiState(watchConnectivityEnabled = false))

        onNodeWithText(getString(Res.string.watch_sync_nodes_label)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.watch_sync_channels_label)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.watch_sync_messages_label)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.watch_sync_now_label)).assertDoesNotExist()
    }

    @Test
    fun `toggle actions reach the view model`() = runComposeUiTest {
        val watchPrefs = FakeWatchPrefs()
        val viewModel = WatchViewModel(watchPrefs, WatchSyncStateHolder())
        setContent { AppTheme { WatchConfigurationScreen(viewModel = viewModel, onBack = {}) } }

        onNodeWithText(getString(Res.string.watch_sync_nodes_label)).performClick()
        onNodeWithText(getString(Res.string.watch_sync_messages_label)).performClick()
        onNodeWithText(getString(Res.string.watch_sync_enabled_label)).performClick()

        assertFalse(watchPrefs.watchConnectivityEnabled.value)
        assertFalse(watchPrefs.syncPreferences.value.syncNodes)
        assertFalse(watchPrefs.syncPreferences.value.syncRecentMessages)
    }

    @Test
    fun `message retention is hidden when recent messages are disabled`() = runComposeUiTest {
        setWatchContent(
            state = WatchSettingsUiState(syncPreferences = WatchSyncPreferences(syncRecentMessages = false)),
        )

        onNodeWithText(getString(Res.string.watch_message_retention_label)).assertDoesNotExist()
    }

    @Test
    fun `rapid manual synchronization clicks invoke one view model action`() = runComposeUiTest {
        val watchPrefs = FakeWatchPrefs()
        val viewModel = WatchViewModel(watchPrefs, WatchSyncStateHolder())
        setContent { AppTheme { WatchConfigurationScreen(viewModel = viewModel, onBack = {}) } }

        onNodeWithText(getString(Res.string.watch_sync_now_label)).performClick()
        onNodeWithText(getString(Res.string.watch_sync_now_label)).performClick()

        assertEquals(1L, watchPrefs.syncRequest.value)
    }

    @Test
    fun `top app bar navigates back`() = runComposeUiTest {
        var navigatedBack = false
        setWatchContent(onBack = { navigatedBack = true })

        onNodeWithContentDescription(getString(Res.string.navigate_back)).performClick()

        assertTrue(navigatedBack)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setWatchContent(
        state: WatchSettingsUiState = WatchSettingsUiState(),
        onBack: () -> Unit = {},
    ) {
        setContent {
            AppTheme {
                WatchConfigurationScreen(
                    state = state,
                    onWatchConnectivityChange = {},
                    onSyncNodesChange = {},
                    onSyncChannelsChange = {},
                    onSyncRecentMessagesChange = {},
                    onRecentMessageLimitChange = {},
                    onSyncQuickRepliesChange = {},
                    onSyncRadioStatusChange = {},
                    onSyncNow = {},
                    onBack = onBack,
                )
            }
        }
    }
}
