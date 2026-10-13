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
package org.meshtastic.core.prefs.watch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.asWatchDataStore
import org.meshtastic.core.repository.RecentMessageLimit
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.WearOperatingMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WatchPrefsImplTest {
    private lateinit var tmpDir: Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: WatchPrefsImpl
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "watchPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        prefs =
            WatchPrefsImpl(
                dataStore.asWatchDataStore(),
                CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher),
            )
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun `synchronization is enabled by default`() = testScope.runTest {
        assertEquals(WearOperatingMode.COMPANION, prefs.operatingMode.value)
        assertEquals(WearOperatingMode.COMPANION, prefs.readOperatingMode())
        assertTrue(prefs.watchConnectivityEnabled.value)
        assertEquals(WatchSyncPreferences(), prefs.syncPreferences.value)
        assertFalse(prefs.highContrastModeEnabled.value)
        assertEquals(0L, prefs.syncRequest.value)
    }

    @Test
    fun `stored operating modes are restored`() = testScope.runTest {
        WearOperatingMode.entries.forEach { mode ->
            prefs.setOperatingModeAndWait(mode)

            assertEquals(
                mode.name,
                dataStore.data
                    .first {
                        it[WatchPrefsImpl.KEY_OPERATING_MODE] == mode.name
                    }[WatchPrefsImpl.KEY_OPERATING_MODE],
            )
            assertEquals(mode, prefs.operatingMode.first { it == mode })
            assertEquals(mode, prefs.readOperatingMode())
        }
    }

    @Test
    fun `unknown stored operating mode falls back to companion`() = testScope.runTest {
        prefs.setOperatingMode(WearOperatingMode.STANDALONE_RADIO)
        prefs.operatingMode.first { it == WearOperatingMode.STANDALONE_RADIO }

        dataStore.edit { it[WatchPrefsImpl.KEY_OPERATING_MODE] = "FUTURE_MODE" }
        dataStore.data.first { it[WatchPrefsImpl.KEY_OPERATING_MODE] == "FUTURE_MODE" }

        assertEquals(WearOperatingMode.COMPANION, WatchPrefsImpl.parseOperatingMode("FUTURE_MODE"))
        assertEquals(WearOperatingMode.COMPANION, prefs.readOperatingMode())
        assertEquals(WearOperatingMode.COMPANION, prefs.operatingMode.first { it == WearOperatingMode.COMPANION })
    }

    @Test
    fun `synchronization preferences persist updates`() = testScope.runTest {
        prefs.setWatchConnectivityEnabled(false)
        prefs.setSyncNodesEnabled(false)
        prefs.setSyncChannelsEnabled(false)
        prefs.setSyncRecentMessagesEnabled(false)
        prefs.setRecentMessageLimit(RecentMessageLimit.ONE_HUNDRED)
        prefs.setSyncQuickRepliesEnabled(false)
        prefs.setSyncRadioStatusEnabled(false)
        prefs.setHighContrastModeEnabled(true)

        assertEquals(
            false,
            dataStore.data
                .first {
                    it[WatchPrefsImpl.KEY_WATCH_CONNECTIVITY] == false
                }[WatchPrefsImpl.KEY_WATCH_CONNECTIVITY],
        )
        assertEquals(
            false,
            dataStore.data.first { it[WatchPrefsImpl.KEY_SYNC_NODES] == false }[WatchPrefsImpl.KEY_SYNC_NODES],
        )
        assertEquals(
            false,
            dataStore.data.first {
                it[WatchPrefsImpl.KEY_SYNC_MESSAGES] == false
            }[WatchPrefsImpl.KEY_SYNC_MESSAGES],
        )
        dataStore.data.first { it[WatchPrefsImpl.KEY_SYNC_RADIO_STATUS] == false }

        assertEquals(
            WatchSyncPreferences(
                syncNodes = false,
                syncChannels = false,
                syncRecentMessages = false,
                recentMessageLimit = RecentMessageLimit.ONE_HUNDRED,
                syncQuickReplies = false,
                syncRadioStatus = false,
            ),
            prefs.syncPreferences.first { !it.hasEnabledCategory },
        )
        val restored =
            WatchPrefsImpl(
                dataStore.asWatchDataStore(),
                UnconfinedTestDispatcher(testScope.testScheduler).let { dispatcher ->
                    CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
                },
            )
        assertEquals(prefs.syncPreferences.value, restored.syncPreferences.first { !it.hasEnabledCategory })
        assertEquals(
            true,
            dataStore.data
                .first { it[WatchPrefsImpl.KEY_HIGH_CONTRAST_MODE] == true }[WatchPrefsImpl.KEY_HIGH_CONTRAST_MODE],
        )
    }

    @Test
    fun `invalid message limit falls back to twenty five`() = testScope.runTest {
        dataStore.edit { it[WatchPrefsImpl.KEY_MESSAGE_LIMIT] = "100" }
        assertEquals(
            RecentMessageLimit.ONE_HUNDRED,
            prefs.syncPreferences
                .first { it.recentMessageLimit == RecentMessageLimit.ONE_HUNDRED }
                .recentMessageLimit,
        )

        dataStore.edit { it[WatchPrefsImpl.KEY_MESSAGE_LIMIT] = "FUTURE_LIMIT" }
        dataStore.data.first { it[WatchPrefsImpl.KEY_MESSAGE_LIMIT] == "FUTURE_LIMIT" }

        assertEquals(
            RecentMessageLimit.TWENTY_FIVE,
            prefs.syncPreferences
                .first { it.recentMessageLimit == RecentMessageLimit.TWENTY_FIVE }
                .recentMessageLimit,
        )
    }

    @Test
    fun `each synchronization request advances the persisted generation`() = testScope.runTest {
        prefs.requestSync()
        prefs.requestSync()

        val stored = dataStore.data.first { it[WatchPrefsImpl.KEY_SYNC_REQUEST] == 2L }

        assertEquals(2L, stored[WatchPrefsImpl.KEY_SYNC_REQUEST])
        assertEquals(2L, prefs.syncRequest.first { it == 2L })
    }
}
